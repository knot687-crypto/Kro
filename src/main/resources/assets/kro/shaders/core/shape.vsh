#version 330

// Kro shape shader. Every quad carries an analytic shape description:
//   Color   per-corner colour (gradients come from interpolation)
//   UV0     RECT/SHADOW: quad half extents | ARC: packed end colour | IMAGE: texture coords
//   UV1     signed (half size * 16 + 1); the signs encode which corner this vertex is
//   UV2     RECT/IMAGE: radius*16, border*16 | SHADOW: radius*16, softness*16 | ARC: start, sweep
//   Normal  z = mode / 127

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vColor;
out vec2 vLocal;
out vec2 vTex;
flat out vec4 vShape;
flat out vec4 vColor2;
flat out int vMode;

const float ANGLE_UNIT = 3.14159265358979 / 4096.0;
const float ARC_PAD = 1.0;

vec4 unpackColor(vec2 e) {
    float a = floor(e.x / 256.0);
    float r = e.x - a * 256.0;
    float g = floor(e.y / 256.0);
    float b = e.y - g * 256.0;
    return vec4(r, g, b, a) / 255.0;
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vColor = Color;
    vMode = int(Normal.z * 127.0 + 0.5);

    vec2 corner = sign(vec2(UV1));
    vec2 halfSize = (abs(vec2(UV1)) - 1.0) / 16.0;

    if (vMode == 2) {
        vShape = vec4(halfSize, float(UV2.x) * ANGLE_UNIT, float(UV2.y) * ANGLE_UNIT);
        vLocal = corner * (halfSize.x + halfSize.y + ARC_PAD);
        vColor2 = unpackColor(UV0);
        vTex = vec2(0.0);
    } else {
        vShape = vec4(halfSize, vec2(UV2) / 16.0);
        vec2 extents = vMode == 3 ? halfSize : UV0;
        vLocal = corner * extents;
        vColor2 = Color;
        vTex = UV0;
    }
}
