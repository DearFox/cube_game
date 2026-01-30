#version 330 core
layout(location = 0) in vec2 aPos; // pixel coords
layout(location = 1) in vec2 aUV;

out vec2 vUV;
uniform vec2 uViewport; // (width, height) in pixels

void main() {
    // convert pixel coords to NDC
    vec2 ndc = (aPos / uViewport) * 2.0 - 1.0;
    ndc.y = -ndc.y; // flip Y for screen space -> NDC
    gl_Position = vec4(ndc, 0.0, 1.0);
    vUV = aUV;
}
