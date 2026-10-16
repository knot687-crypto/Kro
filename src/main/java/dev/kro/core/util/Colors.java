package dev.kro.core.util;

/**
 * ARGB colour helpers. All colours in Kro are packed 0xAARRGGBB ints.
 */
public final class Colors {
	private Colors() {
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
	}

	public static int rgb(int rgb) {
		return 0xFF000000 | rgb;
	}

	public static int argbF(float a, float r, float g, float b) {
		return argb(Math.round(a * 255f), Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
	}

	public static int alpha(int c) {
		return c >>> 24;
	}

	public static int red(int c) {
		return (c >> 16) & 0xFF;
	}

	public static int green(int c) {
		return (c >> 8) & 0xFF;
	}

	public static int blue(int c) {
		return c & 0xFF;
	}

	/** Replaces the alpha channel. */
	public static int withAlpha(int c, int a) {
		return (clamp255(a) << 24) | (c & 0xFFFFFF);
	}

	/** Replaces the alpha channel with a 0..1 float. */
	public static int withAlpha(int c, float a) {
		return withAlpha(c, Math.round(a * 255f));
	}

	/** Multiplies the existing alpha by {@code f}. */
	public static int fade(int c, float f) {
		if (f >= 1f) {
			return c;
		}
		if (f <= 0f) {
			return c & 0xFFFFFF;
		}
		return withAlpha(c, Math.round(alpha(c) * f));
	}

	public static int lerp(int a, int b, float t) {
		if (t <= 0f) {
			return a;
		}
		if (t >= 1f) {
			return b;
		}
		int aa = alpha(a), ar = red(a), ag = green(a), ab = blue(a);
		return argb(
				Math.round(aa + (alpha(b) - aa) * t),
				Math.round(ar + (red(b) - ar) * t),
				Math.round(ag + (green(b) - ag) * t),
				Math.round(ab + (blue(b) - ab) * t));
	}

	/** Mixes towards white by {@code t} keeping alpha. */
	public static int lighten(int c, float t) {
		return withAlpha(lerp(c | 0xFF000000, 0xFFFFFFFF, t), alpha(c));
	}

	/** Mixes towards black by {@code t} keeping alpha. */
	public static int darken(int c, float t) {
		return withAlpha(lerp(c | 0xFF000000, 0xFF000000, t), alpha(c));
	}

	/** HSV (h in 0..1, s 0..1, v 0..1) to opaque ARGB. */
	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float r, g, b;
		float hh = h * 6f;
		int i = (int) Math.floor(hh);
		float f = hh - i;
		float p = v * (1f - s);
		float q = v * (1f - s * f);
		float t = v * (1f - s * (1f - f));
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return argbF(1f, r, g, b);
	}

	/** ARGB to HSV, returns {h, s, v} each 0..1. */
	public static float[] toHsv(int c) {
		float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0f) {
			h = 0f;
		} else if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h /= 6f;
		if (h < 0f) {
			h += 1f;
		}
		float s = max == 0f ? 0f : d / max;
		return new float[] {h, s, max};
	}

	/** Perceived luminance 0..1. */
	public static float luminance(int c) {
		return (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f;
	}

	public static String toHex(int c) {
		return String.format("#%06X", c & 0xFFFFFF);
	}

	private static int clamp255(int v) {
		return v < 0 ? 0 : Math.min(v, 255);
	}
}
