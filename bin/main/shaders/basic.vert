#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aUV;
layout (location = 3) in float aLight;

uniform mat4 uViewProj;
uniform mat4 uModel;

flat out vec3 vNormal;   // ONLY this is flat
out vec2 vUV;
out float vLight;

void main() {
    vec4 worldPos = uModel * vec4(aPos, 1.0);

    vNormal = normalize(mat3(uModel) * aNormal);
    vUV = aUV;
    vLight = aLight;

    gl_Position = uViewProj * worldPos;
}
