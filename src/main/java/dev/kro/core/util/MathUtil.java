package dev.kro.core.util;

public final class MathUtil {
	public static final float PI = (float) Math.PI;
	public static final float TAU = (float) (Math.PI * 2.0);

	private MathUtil() {
	}

	public static float clamp(float v, float min, float max) {
		return v < min ? min : Math.min(v, max);
	}

	public static double clamp(double v, double min, double max) {
		return v < min ? min : Math.min(v, max);
	}

	public static int clamp(int v, int min, int max) {
		return v < min ? min : Math.min(v, max);
	}

	public static float clamp01(float v) {
		return clamp(v, 0f, 1f);
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	public static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** Inverse lerp, unclamped. */
	public static float unlerp(float a, float b, float v) {
		return a == b ? 0f : (v - a) / (b - a);
	}

	/** Maps v from [a0,a1] to [b0,b1], clamped. */
	public static float map(float v, float a0, float a1, float b0, float b1) {
		return lerp(b0, b1, clamp01(unlerp(a0, a1, v)));
	}

	/** Wraps degrees to [-180, 180). */
	public static float wrapDegrees(float deg) {
		float d = deg % 360f;
		if (d >= 180f) {
			d -= 360f;
		}
		if (d < -180f) {
			d += 360f;
		}
		return d;
	}

	public static double wrapDegrees(double deg) {
		double d = deg % 360.0;
		if (d >= 180.0) {
			d -= 360.0;
		}
		if (d < -180.0) {
			d += 360.0;
		}
		return d;
	}

	public static float smoothstep(float e0, float e1, float x) {
		float t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3f - 2f * t);
	}

	/** Rounds to the given step, e.g. 0.05. */
	public static double snap(double v, double step) {
		if (step <= 0) {
			return v;
		}
		return Math.round(v / step) * step;
	}

	public static boolean inRect(double px, double py, double x, double y, double w, double h) {
		return px >= x && py >= y && px < x + w && py < y + h;
	}

	public static boolean inCircle(double px, double py, double cx, double cy, double r) {
		double dx = px - cx, dy = py - cy;
		return dx * dx + dy * dy <= r * r;
	}

	/** Angle of (x,y) in screen space (y down), radians, 0 = +x, increasing clockwise, range [0, TAU). */
	public static float angle(float x, float y) {
		float a = (float) Math.atan2(y, x);
		return a < 0 ? a + TAU : a;
	}

	/** Shortest signed angular difference b - a in radians, [-PI, PI). */
	public static float angleDelta(float a, float b) {
		float d = (b - a) % TAU;
		if (d >= PI) {
			d -= TAU;
		}
		if (d < -PI) {
			d += TAU;
		}
		return d;
	}
}
