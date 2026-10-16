package dev.kro.ui.render;

/**
 * How a {@link ShapeCmd} is packed into shape.vsh's vertex attributes. Shared by the game
 * backend and the GPU test harness so both feed the shader exactly the same data.
 * <ul>
 *   <li>Color: per-corner colour</li>
 *   <li>UV0: quad half extents (RECT/SHADOW), packed end colour (ARC) or texture coords (IMAGE)</li>
 *   <li>UV1: signed (half size * 16 + 1); the signs say which corner the vertex is</li>
 *   <li>UV2: radius * 16 and border or softness * 16, or arc start and sweep in pi/4096 steps</li>
 *   <li>Normal z: (mode + 0.5) / 127</li>
 * </ul>
 */
public final class ShapeEncoding {
	private static final float TAU = (float) (Math.PI * 2.0);
	private static final float ANGLE_UNIT = (float) (Math.PI / 4096.0);

	/** Receives the four corner vertices of a shape, in ShapeCmd corner order. */
	@FunctionalInterface
	public interface Sink {
		void vertex(float x, float y, int argb, float u, float v, int uv1x, int uv1y, int uv2x, int uv2y, float normalZ);
	}

	private ShapeEncoding() {
	}

	private static int fixed(float v) {
		int i = Math.round(v * 16f);
		return i < 0 ? 0 : Math.min(i, 32766);
	}

	public static void encode(ShapeCmd s, Sink out) {
		int ha = fixed(s.a) + 1;
		int hb = fixed(s.b) + 1;
		int p0;
		int p1;
		float e0 = 0f;
		float e1 = 0f;
		if (s.mode == ShapeCmd.ARC) {
			float start = s.c % TAU;
			if (start < 0f) {
				start += TAU;
			}
			p0 = Math.round(start / ANGLE_UNIT);
			p1 = Math.min(32767, Math.round(s.d / ANGLE_UNIT));
			int c2 = s.color2;
			e0 = (c2 >>> 24) * 256 + ((c2 >> 16) & 0xFF);
			e1 = ((c2 >> 8) & 0xFF) * 256 + (c2 & 0xFF);
		} else {
			p0 = fixed(s.c);
			p1 = fixed(s.d);
		}
		float mode = (s.mode + 0.5f) / 127f;
		for (int i = 0; i < 4; i++) {
			int sx = ShapeCmd.SIGN_X[i] < 0f ? -1 : 1;
			int sy = ShapeCmd.SIGN_Y[i] < 0f ? -1 : 1;
			float u;
			float v;
			switch (s.mode) {
				case ShapeCmd.ARC -> {
					u = e0;
					v = e1;
				}
				case ShapeCmd.IMAGE -> {
					u = s.u(i);
					v = s.v(i);
				}
				default -> {
					u = s.qx;
					v = s.qy;
				}
			}
			out.vertex(s.x[i], s.y[i], s.color[i], u, v, sx * ha, sy * hb, p0, p1, mode);
		}
	}
}
