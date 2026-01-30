#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal;
layout (location = 2) in vec2 aUV;
layout (location = 3) in float aLight;

uniform mat4 uViewProj;
uniform mat4 uModel;

out vec3 vNormal;
out vec2 vUV;
out float vLight;

void main() {
    vec4 worldPos = uModel * vec4(aPos, 1.0);

    // If you never scale uModel, this is fine:
    vNormal = normalize(mat3(uModel) * aNormal);

    // If you *might* scale later, use this instead:
    // mat3 normalMat = transpose(inverse(mat3(uModel)));
    // vNormal = normalize(normalMat * aNormal);

    vUV = aUV;
    vLight = aLight;

    gl_Position = uViewProj * worldPos;
}
