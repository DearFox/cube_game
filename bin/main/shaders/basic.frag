#version 330 core
out vec4 FragColor;

in vec3 vNormal;
in vec2 vUV;
in float vLight;

uniform sampler2D uTex;

uniform vec3 uColor;
uniform vec3 uLightDir;
uniform vec3 uAmbient;

void main() {
    vec3 N = normalize(vNormal);
    vec4 texel = texture(uTex, vUV);

    // --- Alpha cutout (fixes black "transparent" pixels + avoids sorting issues) ---
    // If your glass texture uses fully transparent pixels for holes, discard them.
    if (texel.a < 0.1) discard;

    // --- Hemisphere (sky + ground) ---
    float up = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);

    vec3 sky    = vec3(1.0);
    vec3 ground = vec3(0.4);

    vec3 hemi = mix(ground, sky, up);

    // --- Sun (directional) ---
    vec3 L = normalize(-uLightDir);
    float sun = max(dot(N, L), 0.0);

    // --- Combine lighting ---
    vec3 light = uAmbient + hemi * 0.45 + vec3(sun) * 0.45;

    // Keep vLight in the chain (acts like "block light" multiplier)
    vec3 lit = texel.rgb * light * vLight;

    // For cutout we usually write opaque alpha so depth behaves nicely.
    // (If you keep texel.a here, you re-enter blending/sorting territory.)
    FragColor = vec4(lit, 1.0);
}
