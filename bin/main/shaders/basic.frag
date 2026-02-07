#version 330 core
out vec4 FragColor;

flat in vec3 vNormal;    // must match
in vec2 vUV;             // NOT flat
in float vLight;

uniform sampler2D uTex;

uniform vec3 uColor;
uniform vec3 uLightDir;   // still used as a "yaw" reference if you want
uniform vec3 uAmbient;
uniform float uPlantFactor; // 0.0..1.0

float faceBrightness(vec3 N) {
    vec3 a = abs(N);
    if (a.y >= a.x && a.y >= a.z) {
        return (N.y > 0.0) ? 1.00 : 0.80; // top / bottom
    } else {
        return 0.97; // sides
    }
}

// plant-specific: make it brighter so it looks like it's top facing
float plantBrightness(vec3 N) {
    return 1.05;
}

float blendedBrightness(vec3 N, float plantFactor) {
    float fbBlock = faceBrightness(N);     // old behavior
    float fbPlant = plantBrightness(N);    // top-biased behavior
    return mix(fbBlock, fbPlant, clamp(plantFactor, 0.0, 1.0));
}

void main() {
    vec3 N = normalize(vNormal);
    vec4 texel = texture(uTex, vUV);
    
    // --- Alpha cutout ---
    if (texel.a < 0.1) discard;

    // --- Hemisphere (sky + ground) ---
    float up = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 sky    = vec3(1.0);
    vec3 ground = vec3(0.4);
    vec3 hemi = mix(ground, sky, up);

    // ------------------------------------------------------------
    // Minecraft-ish "two-source" diffuse:
    // Two lights from opposite horizontal directions, both from above.
    // This makes opposite side faces have (roughly) the same brightness.
    // ------------------------------------------------------------

    // Pick a horizontal direction to define "which way the pair points".
    // If uLightDir is your sun direction, we only use its XZ yaw here.
    vec3 yaw = normalize(vec3(uLightDir.x, 0.0, uLightDir.z));
    // If uLightDir is ever straight up/down, fall back to something safe:
    if (length(yaw) < 1e-4) yaw = vec3(1.0, 0.0, 0.0);

    // Two opposing directions, both tilted down from above.
    // (Bigger Y => more top-lit, less harsh sides.)
    vec3 L1 = normalize(vec3( yaw.x, 1.0,  yaw.z));
    vec3 L2 = normalize(vec3(-yaw.x, 1.0, -yaw.z));

    float d1 = max(dot(N, L1), 0.0);
    float d2 = max(dot(N, L2), 0.0);

    // Weight them. (You can tweak these to taste.)
    float diffuse2 = d1 * 0.55 + d2 * 0.45;

    // Optional: keep sides from getting *too* dark (Minecraft feels a bit “lifted”)
    diffuse2 = max(diffuse2, 0.10);

    // --- Combine lighting ---
    vec3 light = uAmbient + hemi * 0.45 + vec3(diffuse2) * 0.45;

    //vec3 lit = texel.rgb * light * vLight;
    float fb = blendedBrightness(N, uPlantFactor);
	vec3 lit = texel.rgb * light * fb * vLight;
	
    FragColor = vec4(lit, 1.0);
}
