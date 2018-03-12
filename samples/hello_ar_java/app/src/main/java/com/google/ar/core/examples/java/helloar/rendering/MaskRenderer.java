/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.helloar.rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.ar.core.examples.java.helloar.AnchorData;
import com.google.ar.core.examples.java.helloar.R;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;

/** Renders an object loaded from an OBJ file in OpenGL. */
public class MaskRenderer {

  private static final float MAX_MASKED_OBJECTS = 255.0f;
  private static final String TAG = MaskRenderer.class.getSimpleName();

  private static final int COORDS_PER_VERTEX = 3;

  // Object vertex buffer variables.
  private int vertexBufferId;
  private int verticesBaseAddress;
  private int indexBufferId;
  private int indexCount;

  private int framebufferId;
  private int renderColorbufferId;
  private int renderStencilbufferId;

  private int program;

  // Shader location: model view projection matrix.
  private int modelViewUniform;
  private int modelViewProjectionUniform;

  // Shader location: object attributes.
  private int positionAttribute;

  private int colorUniform;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];
    ByteBuffer resultsBuffer;

  //Somewhere at initialization
  int [] fbo = new int[1];
  int [] render_buf = new int[1];

  int width= -1;
  int height = -1;

  public MaskRenderer() {}

  void loadViewport(){
      if(width == -1 || height == -1) {
          int[] m_viewport = new int[4];
          GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, m_viewport, 0);
          width = m_viewport[2];
          height = m_viewport[3];
      }
  }

    public AnchorData pickAnchorData(ArrayList<AnchorData> anchors, float f_x, float f_y) {
    int x = (int) f_x;
    int y = (int) f_y;
        if(resultsBuffer != null && anchors != null && anchors.size() != 0 && resultsBuffer.array() != null && resultsBuffer.array().length != 0 ){
            loadViewport();

            byte[] barr = ((ByteBuffer)resultsBuffer).array();
            byte colorR = barr[( width *  height * 4  ) - (width * 4 * y + (width - x )*4)];
            /*
            byte colorG = barr[( width *  height * 4  ) - (width * 4 * y + (width - x )*4) + 1];
            byte colorB = barr[( width *  height * 4  ) - (width * 4 * y + (width - x )*4) + 2];
            byte colorA = barr[( width *  height * 4  ) - (width * 4 * y + (width - x )*4) + 3];

          Log.i("colorR",""+colorR);
          Log.i("colorG",""+colorG);
          Log.i("colorB",""+colorB);
          Log.i("colorA",""+colorA);
          */
          // no need to convert because that is how we store it in byte buffer...
          //int objectId = ((int)(colorR/MAX_MASKED_OBJECTS));
          int objectId = colorR;
            for(AnchorData anchor : anchors){
                if(anchor.index == objectId){
                    return anchor;
                }
            }
        }
        return null;
    }


    /**
   * Creates and initializes OpenGL resources needed for rendering the model.
   *
   * @param context Context for loading the shader and below-named model and texture assets.
   * @param objAssetName Name of the OBJ file containing the model geometry.
   */
  public void createOnGlThread(Context context, String objAssetName)
      throws IOException {
    // Read the texture.

    ShaderUtil.checkGLError(TAG, "Texture loading");

    // Read the obj file.
    InputStream objInputStream = context.getAssets().open(objAssetName);
    Obj obj = ObjReader.read(objInputStream);

    // Prepare the Obj so that its structure is suitable for
    // rendering with OpenGL:
    // 1. Triangulate it
    // 2. Make sure that texture coordinates are not ambiguous
    // 3. Make sure that normals are not ambiguous
    // 4. Convert it to single-indexed data
    obj = ObjUtils.convertToRenderable(obj);

    // OpenGL does not use Java arrays. ByteBuffers are used instead to provide data in a format
    // that OpenGL understands.

    // Obtain the data from the OBJ, as direct buffers:
    IntBuffer wideIndices = ObjData.getFaceVertexIndices(obj, 3);
    FloatBuffer vertices = ObjData.getVertices(obj);

    // Convert int indices to shorts for GL ES 2.0 compatibility
    ShortBuffer indices =
        ByteBuffer.allocateDirect(2 * wideIndices.limit())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();
    while (wideIndices.hasRemaining()) {
      indices.put((short) wideIndices.get());
    }
    indices.rewind();

    int[] buffers = new int[2];
    GLES20.glGenBuffers(2, buffers, 0);
    vertexBufferId = buffers[0];
    indexBufferId = buffers[1];

    // Load vertex buffer
    verticesBaseAddress = 0;
    final int totalBytes = 4 * vertices.limit();

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW);
    GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, verticesBaseAddress, 4 * vertices.limit(), vertices);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    // Load index buffer
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
    indexCount = indices.limit();
    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * indexCount, indices, GLES20.GL_STATIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    ShaderUtil.checkGLError(TAG, "Load indicies");

    final int[] framebuffer = new int[1];
    GLES20.glGenFramebuffers(1, framebuffer, 0);

    framebufferId = framebuffer[0];

    ShaderUtil.checkGLError(TAG, "gen frame buffer");

    loadViewport();

    final int[] renderColorbuffer = new int[1];
    GLES20.glGenRenderbuffers(1, renderColorbuffer, 0);
    renderColorbufferId = renderColorbuffer[0];
    GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderColorbufferId);
    GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_RGBA4, width, height);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId);
    GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_RENDERBUFFER, renderColorbufferId);

    ShaderUtil.checkGLError(TAG, "load render color buffers");


    final int[] renderStencilbuffer = new int[1];
    GLES20.glGenRenderbuffers(1, renderStencilbuffer, 0);

    renderStencilbufferId = renderStencilbuffer[0];

    GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderStencilbufferId);

    GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);

    GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, renderStencilbufferId);

    ShaderUtil.checkGLError(TAG, "load render stencil buffers");


    GLES20.glGenFramebuffers(1,fbo,0);
    GLES20.glGenRenderbuffers(1,render_buf, 0);
    GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, render_buf[0]);
    GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_RGBA4, width, height);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo[0]);
    GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_RENDERBUFFER, render_buf[0]);

    //At deinit:
    //glDeleteFramebuffers(1,&fbo);
    //glDeleteRenderbuffers(1,&render_buf);

    ShaderUtil.checkGLError(TAG, "OBJ buffer load");

    final int vertexShader =
        ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, R.raw.mask_vertex);
    final int fragmentShader =
        ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, R.raw.mask_fragment);

    program = GLES20.glCreateProgram();
    GLES20.glAttachShader(program, vertexShader);
    GLES20.glAttachShader(program, fragmentShader);
    GLES20.glLinkProgram(program);
    GLES20.glUseProgram(program);

    ShaderUtil.checkGLError(TAG, "Program creation");

    modelViewUniform = GLES20.glGetUniformLocation(program, "u_ModelView");
    modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");

    colorUniform = GLES20.glGetUniformLocation(program, "u_code");

    if (colorUniform != -1) {
      GLES20.glUniform1f(colorUniform, 0.0f);
    }
    positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
    ShaderUtil.checkGLError(TAG, "Program parameters");

    Matrix.setIdentityM(modelMatrix, 0);
  }

  /**
   * Updates the object model matrix and applies scaling.
   *
   * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
   * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
   * @see Matrix
   */
  public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
    float[] scaleMatrix = new float[16];
    Matrix.setIdentityM(scaleMatrix, 0);
    scaleMatrix[0] = scaleFactor;
    scaleMatrix[5] = scaleFactor;
    scaleMatrix[10] = scaleFactor;
    Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
  }

  /**
   * Draws the model.
   *
   * @param cameraView A 4x4 view matrix, in column-major order.
   * @param cameraPerspective A 4x4 projection matrix, in column-major order.
   *     properties.
   * @ param helloArActivity
   * @see # setBlendMode(BlendMode)
   * @see #updateModelMatrix(float[], float)
   * @see # setMaterialProperties(float, float, float, float)
   * @see Matrix
   */
  public void draw(float[] cameraView, float[] cameraPerspective, int objectId) {

    ShaderUtil.checkGLError(TAG, "Before draw");

    // Build the ModelView and ModelViewProjection matrices
    // for calculating object position and light.
    Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

    GLES20.glUseProgram(program);

    // Set the vertex attributes.
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);

    GLES20.glVertexAttribPointer(positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, verticesBaseAddress);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);
    GLES20.glUniform1f(colorUniform, ((float)((objectId/MAX_MASKED_OBJECTS))));

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(positionAttribute);

    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId);
    ShaderUtil.checkGLError(TAG, "load render color buffers1");

    loadViewport();

    //Before drawing
    GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER,fbo[0]);
    //after drawing

    GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
    GLES20.glDisable(GLES20.GL_BLEND);

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

    // Disable vertex arrays
    GLES20.glDisableVertexAttribArray(positionAttribute);

    ShaderUtil.checkGLError(TAG, "After draw");
  }

  public void loadMaskBuffer(){
    loadViewport();
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId);
    ShaderUtil.checkGLError(TAG, "load render color buffers1");

    //Before drawing
    GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER,fbo[0]);
    //after drawing

    GLES20.glDisable(GLES20.GL_BLEND);
    loadViewport();
    // https://www.programcreek.com/java-api-examples/?class=android.opengl.GLES20&method=glReadPixels
    // ByteBuffer init was not working in createOnGlThread

    if(resultsBuffer == null)
      resultsBuffer = ByteBuffer.allocateDirect(width *height * 4);
    GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, resultsBuffer);

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

    // Disable vertex arrays
    GLES20.glDisableVertexAttribArray(positionAttribute);

    ShaderUtil.checkGLError(TAG, "After draw");
  }


}
