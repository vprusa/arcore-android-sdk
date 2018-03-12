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

precision mediump float;

uniform float u_code;

//out vec4 outputF;

//input from the vertex
//in float u_code_out;

//output to the framebuffer
//out float u_code_out_out;


void main()
{
    //outputF = vec4(code/20, 0, 0, 0);
    //float tmp = (u_code / 255.0);
    gl_FragColor = vec4(u_code, 0, 0, 0);
}
