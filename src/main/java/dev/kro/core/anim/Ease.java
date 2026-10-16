package dev.kro.core.anim;

/** Classic easing curves, all mapping 0..1 to 0..1. */
public final class Ease {
	private Ease() {
	}

	public static float clamp(float t) {
		return t < 0f ? 0f : Math.min(t, 1f);
	}

	public static float outCubic(float t) {
		t = clamp(t);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float outQuint(float t) {
		t = clamp(t);
		float u = 1f - t;
		return 1f - u * u * u * u * u;
	}

	public static float inOutCubic(float t) {
		t = clamp(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	public static float outExpo(float t) {
		t = clamp(t);
		return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
	}

	public static float outBack(float t) {
		t = clamp(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	public static float inQuad(float t) {
		t = clamp(t);
		return t * t;
	}

	public static float outQuad(float t) {
		t = clamp(t);
		return 1f - (1f - t) * (1f - t);
	}
}
