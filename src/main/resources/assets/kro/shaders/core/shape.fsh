#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

#ifdef TEXTURED
uniform sampler2D Sampler0;
#endif

in vec4 vColor;
in vec2 vLocal;
in vec2 vTex;
flat in vec4 vShape;
flat in vec4 vColor2;
flat in int vMode;

out vec4 fragColor;

const float PI = 3.14159265358979;
const float TAU = 6.28318530717959;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// Abramowitz & Stegun 7.1.27, max error 5e-4: plenty for a shadow falloff.
float erfApprox(float x) {
    float s = sign(x);
    float a = abs(x);
    float t = 1.0 + (0.278393 + (0.230389 + 0.078108 * (a * a)) * a) * a;
    t *= t;
    return s - s / (t * t);
}

void main() {
    // Local units covered by one screen pixel; keeps edges exactly one pixel soft at any scale.
    float px = max(length(vec2(dFdx(vLocal.x), dFdy(vLocal.x))), 1.0e-4);
    vec4 color = vColor;
    float coverage;

    if (vMode == 0) {
        float d = sdRoundBox(vLocal, vShape.xy, vShape.z);
        coverage = clamp(0.5 - d / px, 0.0, 1.0);
        if (vShape.w > 0.0) {
            coverage -= clamp(0.5 - (d + vShape.w) / px, 0.0, 1.0);
        }
    } else if (vMode == 1) {
        float d = sdRoundBox(vLocal, vShape.xy, vShape.z);
        float sigma = max(vShape.w * 0.5, 1.0e-3);
        coverage = 0.5 - 0.5 * erfApprox(d / (sigma * 1.41421356));
    } else if (vMode == 2) {
        float mid = vShape.x;
        float halfThickness = vShape.y;
        float startAngle = vShape.z;
        float sweepAngle = vShape.w;
        float rel = mod(atan(vLocal.y, vLocal.x) - startAngle, TAU);
        float d;
        float t;
        if (sweepAngle >= TAU - 1.0e-3) {
            d = abs(length(vLocal) - mid) - halfThickness;
            t = rel / TAU;
        } else {
            // Inigo Quilez's capped arc, rotated so the arc's middle points along +y.
            float rot = PI * 0.5 - (startAngle + sweepAngle * 0.5);
            float cr = cos(rot);
            float sr = sin(rot);
            vec2 p = vec2(vLocal.x * cr - vLocal.y * sr, vLocal.x * sr + vLocal.y * cr);
            p.x = abs(p.x);
            float halfAperture = sweepAngle * 0.5;
            vec2 sc = vec2(sin(halfAperture), cos(halfAperture));
            d = ((sc.y * p.x > sc.x * p.y) ? length(p - sc * mid) : abs(length(p) - mid)) - halfThickness;
            if (rel <= sweepAngle) {
                t = rel / max(sweepAngle, 1.0e-4);
            } else {
                t = (rel - sweepAngle) < (TAU - rel) ? 1.0 : 0.0;
            }
        }
        coverage = clamp(0.5 - d / px, 0.0, 1.0);
        color = mix(vColor, vColor2, t);
    } else {
#ifdef TEXTURED
        color = texture(Sampler0, vTex) * vColor;
#endif
        float d = sdRoundBox(vLocal, vShape.xy, vShape.z);
        coverage = clamp(0.5 - d / px, 0.0, 1.0);
    }

    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
