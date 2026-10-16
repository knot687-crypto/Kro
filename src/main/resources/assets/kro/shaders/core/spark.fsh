#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
in vec4 vColor;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

// Procedural light, no textures. UV spans -1..1 across the quad.
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
    float fog = 1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    float a = clamp(intensity * vColor.a * fog, 0.0, 1.0);
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(rgb * a, a) * ColorModulator;
}
