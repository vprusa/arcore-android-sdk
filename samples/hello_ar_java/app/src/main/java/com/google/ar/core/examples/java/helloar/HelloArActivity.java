/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.helloar.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.helloar.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.helloar.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.helloar.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PointCloudRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PickingRay;
import com.google.ar.core.examples.java.helloar.rendering.Vector3f;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = HelloArActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private GestureDetector gestureDetector;
  private Snackbar messageSnackbar;
  private DisplayRotationHelper displayRotationHelper;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final ObjectRenderer virtualObject = new ObjectRenderer();
  private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
  private final PlaneRenderer planeRenderer = new PlaneRenderer();
  private final PointCloudRenderer pointCloud = new PointCloudRenderer();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];

  // Tap handling and UI.
  private final ArrayBlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);
  private final ArrayList<Anchor> anchors = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up tap listener.
    gestureDetector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onSingleTapUp(MotionEvent e) {
                onSingleTap(e);
                return true;
              }

              @Override
              public boolean onDown(MotionEvent e) {
                return true;
              }
            });

    surfaceView.setOnTouchListener(
        new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
          }
        });

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    installRequested = false;
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (Exception e) {
        message = "This device does not support AR";
        exception = e;
      }

      if (message != null) {
        showSnackbarMessage(message, true);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }

      // Create default config and check if supported.
      Config config = new Config(session);
      if (!session.isSupported(config)) {
        showSnackbarMessage("This device does not support AR", true);
      }
      session.configure(config);
    }

    showLoadingMessage();
    // Note that order matters - see the note in onPause(), the reverse applies here.
    session.resume();
    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      // Standard Android full-screen functionality.
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  private void onSingleTap(MotionEvent e) {
    // Queue tap if there is space. Tap is lost if queue is full.
    queuedSingleTaps.offer(e);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Create the texture and pass it to ARCore session to be filled during update().
    backgroundRenderer.createOnGlThread(/*context=*/ this);

    // Prepare the other rendering objects.
    try {
      virtualObject.createOnGlThread(/*context=*/ this, "andy.obj", "andy.png");
      virtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);

      virtualObjectShadow.createOnGlThread(/*context=*/ this, "andy_shadow.obj", "andy_shadow.png");
      virtualObjectShadow.setBlendMode(BlendMode.Shadow);
      virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read obj file");
    }
    try {
      planeRenderer.createOnGlThread(/*context=*/ this, "trigrid.png");
    } catch (IOException e) {
      Log.e(TAG, "Failed to read plane texture");
    }
    pointCloud.createOnGlThread(/*context=*/ this);
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  public void rayPicking(float x, float y, Camera camera, int program, Context context)
      throws IOException {

    //x = getMouseX() // scalar
    //y = getMouseY() // scalar

    InputStream objInputStream = context.getAssets().open("andy.obj");
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
    //IntBuffer wideIndices = ObjData.getFaceVertexIndices(obj, 3);
    FloatBuffer vertices = ObjData.getVertices(obj);
    //FloatBuffer texCoords = ObjData.getTexCoords(obj, 2);
   // FloatBuffer normals = ObjData.getNormals(obj);



    Vector3f cameraLookAt = new Vector3f(camera.getDisplayOrientedPose());
    Vector3f cameraPosition = new Vector3f(camera.getPose());
    Vector3f view = cameraLookAt.sub(cameraPosition); // 3D float vector
    view.normalize();
    Vector3f h;

    h = view.cross(new Vector3f(camera.getDisplayOrientedPose().getZAxis()));// ) // 3D float vector
    h.normalize();

    Vector3f v;

    v = h.cross(view); // 3D float vector
    v.normalize();
    //GLES20.glDepth

    float[] viewPort= new float[4];

    GLES20.glGetUniformfv(program,  GLES20.GL_VIEWPORT, viewPort,0);
    float width = viewPort[2];
    float height = viewPort[3];

    float nearClippingPlaneDistance = 0.0f;
    // convert fovy to radians
    float rad = 90 * (float)Math.PI / 180;
    float vLength = (float)Math.tan((double) (rad / 2.0f)  ) * nearClippingPlaneDistance;
    float hLength = vLength * (width / height);
    v.scale(vLength);
    h.scale(hLength);
    //scale v by vLength
    //scale h by hLength

    // translate mouse coordinates so that the origin lies in the center
    // of the view port
    x -= width / 2;
    y -= height / 2;

    // scale mouse coordinates so that half the view port width and height
    // becomes 1
    y /= (height / 2);
    x /= (width / 2);

    Vector3f cameraPos = cameraPosition;
    // linear combination to compute intersection of picking ray with
    // view port plane
    Vector3f pos = cameraPos.sum(view.mul(nearClippingPlaneDistance).sum(h.mul(x).sum(v.mul(y))));

// compute direction of picking ray by subtracting intersection point
// with camera position
    Vector3f dir = pos.sub(cameraPos);


    // brute force
    //for all objects in the scene
    //test for intersection and keep closest
    //end for

    PickingRay pr = new PickingRay(pos, dir);
    pr.intersectionWithXyPlane(worldPos);

  }

  public void picking(float screenX, float screenY, PickingRay pickingRay, Camera camera, int program)
  {
    //Vector3f position = new Vector3f(camera.getPose().tx(), camera.getPose().ty(), camera.getPose().tz());
    //Vector3f view = new Vector3f((float)View.getLocationOnScreen()[0], (float)View.getLocationOnScreen()[1], 0.0f);

    float[] viewMatrix= new float[16];

    float[] viewPort= new float[16];

    camera.getViewMatrix(viewMatrix,0);

    Vector3f view  = new Vector3f(viewMatrix[ 2 ],viewMatrix[ 6 ],viewMatrix[ 10 ]);
    float[] positionMatrix= new float[16];

    //camera.getDisplayOrientedPose().ViewMatrix(viewMatrix,0);
    Vector3f position  = new Vector3f(camera.getDisplayOrientedPose().tx(),camera.getDisplayOrientedPose().ty(),camera.getDisplayOrientedPose().tz());

    pickingRay.getClickPosInWorld().set(position);
    pickingRay.getClickPosInWorld().add(view);

    GLES20.glGetUniformfv(program,  GLES20.GL_VIEWPORT, viewPort,0);
    float viewportHeight = viewPort[2];
    float viewportWidth = viewPort[3];
    //camera.getViewMatrix();
    screenX -= (float)viewportWidth/2f;
    screenY -= (float)viewportHeight/2f;

    // normalize to 1
    screenX /= ((float)viewportWidth/2f);
    screenY /= ((float)viewportHeight/2f);

    Vector3f screenHoritzontally = new Vector3f(1.0f,1.0f,1.0f);
    Vector3f screenVertically = new Vector3f(1.0f,1.0f,1.0f);

    pickingRay.getClickPosInWorld().x += screenHoritzontally.x*screenX + screenVertically.x*screenY;
    pickingRay.getClickPosInWorld().y += screenHoritzontally.y*screenX + screenVertically.y*screenY;
    pickingRay.getClickPosInWorld().z += screenHoritzontally.z*screenX + screenVertically.z*screenY;

    pickingRay.getDirection().set(pickingRay.getClickPosInWorld());
    pickingRay.getDirection().sub(position);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // Handle taps. Handling only one tap per frame, as taps are usually low frequency
      // compared to frame rate.

      MotionEvent tap = queuedSingleTaps.poll();

    /*
      // look direction
      view.subAndAssign(lookAt, position).normalize();

      // screenX
      screenHoritzontally.crossAndAssign(view, up).normalize();

      // screenY
      screenVertically.crossAndAssign(screenHoritzontally, view).normalize();

      final float radians = (float) (viewAngle*Math.PI / 180f);
      float halfHeight = (float) (Math.tan(radians/2)*nearClippingPlaneDistance);
      float halfScaledAspectRatio = halfHeight*getViewportAspectRatio();

      screenVertically.scale(halfHeight);
      screenHoritzontally.scale(halfScaledAspectRatio);
  */


      if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
        for (HitResult hit : frame.hitTest(tap)) {
          // Check if any plane was hit, and if it was hit inside the plane polygon
          Trackable trackable = hit.getTrackable();
          // Creates an anchor if a plane or an oriented point was hit.
          if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
              || (trackable instanceof Point
                  && ((Point) trackable).getOrientationMode()
                      == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
            // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
            // Cap the number of objects created. This avoids overloading both the
            // rendering system and ARCore.
            if (anchors.size() >= 20) {
              anchors.get(0).detach();
              anchors.remove(0);
            }
            // Adding an Anchor tells ARCore that it should track this position in
            // space. This anchor is created on the Plane to place the 3D model
            // in the correct position relative both to the world and to the plane.
            Anchor newAnchor = hit.createAnchor();
            rayPicking
                    newAnchor.getPose()

            anchors.add(newAnchor);
            break;
          }
        }
      }

      // Draw background.
      backgroundRenderer.draw(frame);

      // If not tracking, don't draw 3d objects.
      if (camera.getTrackingState() == TrackingState.PAUSED) {
        return;
      }

      // Get projection matrix.
      float[] projmtx = new float[16];
      camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewmtx = new float[16];
      camera.getViewMatrix(viewmtx, 0);

      // Compute lighting from average intensity of the image.
      final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

      // Visualize tracked points.
      PointCloud pointCloud = frame.acquirePointCloud();
      this.pointCloud.update(pointCloud);
      this.pointCloud.draw(viewmtx, projmtx);

      // Application is responsible for releasing the point cloud resources after
      // using it.
      pointCloud.release();

      // Check if we detected at least one plane. If so, hide the loading message.
      if (messageSnackbar != null) {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
          if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
              && plane.getTrackingState() == TrackingState.TRACKING) {
            hideLoadingMessage();
            break;
          }
        }
      }

      // Visualize planes.
      planeRenderer.drawPlanes(
          session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

      // Visualize anchors created by touch.
      float scaleFactor = 1.0f;
      for (Anchor anchor : anchors) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) {
          continue;
        }
        // Get the current pose of an Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        anchor.getPose().toMatrix(anchorMatrix, 0);

        // Update and draw the model and its shadow.
        virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
        virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
        virtualObject.draw(viewmtx, projmtx, lightIntensity);
        virtualObjectShadow.draw(viewmtx, projmtx, lightIntensity);
      }

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }
  }

  private void showSnackbarMessage(String message, boolean finishOnDismiss) {
    messageSnackbar =
        Snackbar.make(
            HelloArActivity.this.findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_INDEFINITE);
    messageSnackbar.getView().setBackgroundColor(0xbf323232);
    if (finishOnDismiss) {
      messageSnackbar.setAction(
          "Dismiss",
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              messageSnackbar.dismiss();
            }
          });
      messageSnackbar.addCallback(
          new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
              super.onDismissed(transientBottomBar, event);
              finish();
            }
          });
    }
    messageSnackbar.show();
  }

  private void showLoadingMessage() {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            showSnackbarMessage("Searching for surfaces...", false);
          }
        });
  }

  private void hideLoadingMessage() {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            if (messageSnackbar != null) {
              messageSnackbar.dismiss();
            }
            messageSnackbar = null;
          }
        });
  }
}
