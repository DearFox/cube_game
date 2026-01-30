#version 330 core
in vec2 vUV;
out vec4 FragColor;

uniform sampler2D uTex;

void main() {
    vec4 tex = texture(uTex, vUV);
    // For icon rendering we keep cutout behavior (binary alpha) to avoid
    // showing black background behind cutout pixels in atlas.
    if (tex.a < 0.5) discard;
    FragColor = tex;
}