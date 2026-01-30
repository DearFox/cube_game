#version 330 core
layout(location=0) out vec4 outAccum;
layout(location=1) out float outReveal;

in vec3 vNormal;
in vec2 vUV;
in float vLight;

uniform sampler2D uTex;
uniform vec3 uLightDir;
uniform vec3 uAmbient;

void main() {
    vec3 N = normalize(vNormal);
    vec4 texel = texture(uTex, vUV);

    // For water later, alpha might be ~0.3-0.7
    float a = clamp(texel.a, 0.0, 1.0);
    if (a <= 0.001) discard;

    // you can reuse the same lighting model:
    float up = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 sky = vec3(1.0);
    vec3 ground = vec3(0.4);
    vec3 hemi = mix(ground, sky, up);

    vec3 L = normalize(-uLightDir);
    float sun = max(dot(N, L), 0.0);

    vec3 light = uAmbient + hemi * 0.45 + vec3(sun) * 0.45;

    vec3 rgb = texel.rgb * light * vLight;

    // Weighted blended OIT:
    // Accum stores premultiplied color + alpha
    outAccum = vec4(rgb * a, a);

    // Revealage multiplicative term stored via blending
    outReveal = a;
}
