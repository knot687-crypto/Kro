#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// Procedural light, no textures. UV spans -1..1 across the quad. Sparks only ever appear within
// arm's reach, so they skip fog and stay bright and clean.
//   SPARK: x across the streak, y along it (+1 is the hot head).
//   RING:  a soft expanding halo for the impact flash.
void main() {
#ifdef RING
    float r = length(vUv);
    float ring = exp(-pow((r - 0.72) / 0.16, 2.0));
    float core = exp(-r * r * 5.0) * 0.35;
    float intensity = (ring + core) * smoothstep(1.0, 0.92, r);
    vec3 rgb = mix(vColor.rgb, vec3(1.0), 0.25 * ring);
#else
    float across = exp(-vUv.x * vUv.x * 5.5);
    float along = clamp(vUv.y * 0.5 + 0.5, 0.0, 1.0);
    float taper = pow(along, 1.6) * smoothstep(1.0, 0.82, vUv.y);
    float intensity = across * taper;
    float hot = exp(-vUv.x * vUv.x * 30.0) * along;
    vec3 rgb = mix(vColor.rgb, vec3(1.0), clamp(hot, 0.0, 1.0) * 0.85);
#endif
    float a = clamp(intensity * vColor.a, 0.0, 1.0);
    if (a <= 0.002) {
        discard;
    }
    // additive: premultiplied light
    fragColor = vec4(rgb * a, a) * ColorModulator;
}
