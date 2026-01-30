#version 330 core
out vec4 FragColor;

in vec2 vUV;

uniform sampler2D uAccum;
uniform sampler2D uReveal;

void main() {
    vec4 accum = texture(uAccum, vUV);
    float reveal = texture(uReveal, vUV).r;

    float a = 1.0 - reveal;
    if (a <= 0.001) discard;

    vec3 color = accum.rgb / max(accum.a, 1e-5);

    FragColor = vec4(color, a);
}
