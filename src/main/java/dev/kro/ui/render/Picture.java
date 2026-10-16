package dev.kro.ui.render;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * A decoded ARGB picture with a high quality resampler, so a wallpaper can be shown at exactly
 * one texel per screen pixel at any resolution, plus a locator for the subject of a photo shot
 * against a dark backdrop.
 */
public final class Picture {
	private static final int SRGB_STEPS = 16384;
	private static final float[] TO_LINEAR = new float[256];
	private static final byte[] TO_SRGB = new byte[SRGB_STEPS];

	static {
		for (int i = 0; i < 256; i++) {
			double c = i / 255.0;
			TO_LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
		}
		for (int i = 0; i < SRGB_STEPS; i++) {
			double l = i / (double) (SRGB_STEPS - 1);
			double c = l <= 0.0031308 ? l * 12.92 : 1.055 * Math.pow(l, 1.0 / 2.4) - 0.055;
			TO_SRGB[i] = (byte) Math.round(c * 255.0);
		}
	}

	public final int width;
	public final int height;
	/** Row-major ARGB pixels, not premultiplied. */
	public final int[] argb;

	public Picture(int width, int height, int[] argb) {
		if (argb.length != width * height) {
			throw new IllegalArgumentException("pixel count " + argb.length + " != " + width + "x" + height);
		}
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	/** Width over height. */
	public float aspect() {
		return width / (float) height;
	}

	/** Decodes PNG, JPEG, BMP, TGA or GIF data with stb_image. */
	public static Picture decode(byte[] encoded) throws IOException {
		ByteBuffer data = MemoryUtil.memAlloc(encoded.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			data.put(encoded).flip();
			IntBuffer w = stack.mallocInt(1);
			IntBuffer h = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);
			ByteBuffer pixels = STBImage.stbi_load_from_memory(data, w, h, channels, 4);
			if (pixels == null) {
				throw new IOException("Unreadable image: " + STBImage.stbi_failure_reason());
			}
			try {
				int pw = w.get(0), ph = h.get(0);
				int[] out = new int[pw * ph];
				for (int i = 0, o = 0; i < out.length; i++, o += 4) {
					int r = pixels.get(o) & 255, g = pixels.get(o + 1) & 255, b = pixels.get(o + 2) & 255, a = pixels.get(o + 3) & 255;
					out[i] = a << 24 | r << 16 | g << 8 | b;
				}
				return new Picture(pw, ph, out);
			} finally {
				STBImage.stbi_image_free(pixels);
			}
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	public static Picture read(InputStream in) throws IOException {
		try (in) {
			return decode(in.readAllBytes());
		}
	}

	// ------------------------------------------------------------------ resampling

	/**
	 * Resamples to {@code w x h} with a Mitchell-Netravali filter whose support widens when
	 * shrinking, working in linear light on premultiplied colour so fine bright detail (hair,
	 * highlights) keeps its brightness and dark edges stay free of fringes.
	 */
	public Picture resize(int w, int h) {
		if (w <= 0 || h <= 0) {
			throw new IllegalArgumentException("size " + w + "x" + h);
		}
		if (w == width && h == height) {
			return this;
		}
		Taps tx = Taps.of(width, w);
		Taps ty = Taps.of(height, h);

		// horizontal pass: every source row into w premultiplied linear samples
		float[] rows = new float[w * height * 4];
		for (int y = 0; y < height; y++) {
			int src = y * width;
			int dst = y * w * 4;
			for (int x = 0; x < w; x++) {
				float r = 0f, g = 0f, b = 0f, a = 0f;
				int start = tx.start[x], n = tx.count[x], wo = x * tx.stride;
				for (int t = 0; t < n; t++) {
					int c = argb[src + start + t];
					float k = tx.weights[wo + t] * ((c >>> 24) / 255f);
					r += TO_LINEAR[(c >> 16) & 255] * k;
					g += TO_LINEAR[(c >> 8) & 255] * k;
					b += TO_LINEAR[c & 255] * k;
					a += k;
				}
				int o = dst + x * 4;
				rows[o] = r;
				rows[o + 1] = g;
				rows[o + 2] = b;
				rows[o + 3] = a;
			}
		}

		// vertical pass, accumulated a whole output row at a time to stay cache friendly
		int[] out = new int[w * h];
		float[] acc = new float[w * 4];
		for (int y = 0; y < h; y++) {
			Arrays.fill(acc, 0f);
			int start = ty.start[y], n = ty.count[y], wo = y * ty.stride;
			for (int t = 0; t < n; t++) {
				float k = ty.weights[wo + t];
				int base = (start + t) * w * 4;
				for (int i = 0; i < acc.length; i++) {
					acc[i] += rows[base + i] * k;
				}
			}
			int row = y * w;
			for (int x = 0; x < w; x++) {
				int o = x * 4;
				float a = acc[o + 3];
				int alpha = Math.round(Math.min(1f, a) * 255f);
				if (alpha <= 0) {
					out[row + x] = 0;
					continue;
				}
				float inv = 1f / a;
				out[row + x] = alpha << 24 | srgb(acc[o] * inv) << 16 | srgb(acc[o + 1] * inv) << 8 | srgb(acc[o + 2] * inv);
			}
		}
		return new Picture(w, h, out);
	}

	private static int srgb(float linear) {
		float l = linear <= 0f ? 0f : Math.min(1f, linear);
		return TO_SRGB[(int) (l * (SRGB_STEPS - 1) + 0.5f)] & 255;
	}

	/** Mitchell-Netravali, B = C = 1/3: sharp, with only a whisper of ringing. */
	private static float mitchell(float x) {
		x = Math.abs(x);
		if (x < 1f) {
			return (7f * x * x * x - 12f * x * x + 16f / 3f) / 6f;
		}
		if (x < 2f) {
			return (-7f / 3f * x * x * x + 12f * x * x - 20f * x + 32f / 3f) / 6f;
		}
		return 0f;
	}

	/** Precomputed, normalised filter taps for one axis. */
	private static final class Taps {
		final int[] start;
		final int[] count;
		final float[] weights;
		final int stride;

		private Taps(int[] start, int[] count, float[] weights, int stride) {
			this.start = start;
			this.count = count;
			this.weights = weights;
			this.stride = stride;
		}

		static Taps of(int src, int dst) {
			float scale = src / (float) dst;
			float filterScale = Math.max(1f, scale);
			float support = 2f * filterScale;
			int stride = (int) Math.ceil(support * 2f) + 2;
			int[] start = new int[dst];
			int[] count = new int[dst];
			float[] weights = new float[dst * stride];
			for (int i = 0; i < dst; i++) {
				float centre = (i + 0.5f) * scale - 0.5f;
				int lo = Math.max(0, (int) Math.floor(centre - support) + 1);
				int hi = Math.min(src - 1, (int) Math.floor(centre + support));
				int n = 0;
				float sum = 0f;
				for (int j = lo; j <= hi && n < stride; j++) {
					float k = mitchell((j - centre) / filterScale);
					weights[i * stride + n] = k;
					sum += k;
					n++;
				}
				if (n == 0) {
					// degenerate: fall back to the nearest source sample
					lo = Math.min(src - 1, Math.max(0, Math.round(centre)));
					weights[i * stride] = 1f;
					n = 1;
					sum = 1f;
				}
				for (int t = 0; t < n; t++) {
					weights[i * stride + t] /= sum;
				}
				start[i] = lo;
				count[i] = n;
			}
			return new Taps(start, count, weights, stride);
		}
	}

	// ------------------------------------------------------------------ glass

	/**
	 * Gaussian blur (three box passes) in linear light on premultiplied colour, so bright detail
	 * spreads the way light does instead of turning muddy.
	 */
	public Picture blur(float sigma) {
		int n = width * height;
		float[][] ch = new float[4][n];
		for (int i = 0; i < n; i++) {
			int c = argb[i];
			float a = (c >>> 24) / 255f;
			ch[0][i] = TO_LINEAR[(c >> 16) & 255] * a;
			ch[1][i] = TO_LINEAR[(c >> 8) & 255] * a;
			ch[2][i] = TO_LINEAR[c & 255] * a;
			ch[3][i] = a;
		}
		if (sigma > 0.25f) {
			float[] tmp = new float[n];
			for (int size : boxSizes(sigma)) {
				int r = (size - 1) / 2;
				for (float[] plane : ch) {
					boxRows(plane, tmp, width, height, r);
					boxColumns(tmp, plane, width, height, r);
				}
			}
		}
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			float a = ch[3][i];
			int alpha = Math.round(Math.min(1f, a) * 255f);
			if (alpha <= 0) {
				continue;
			}
			float inv = 1f / a;
			out[i] = alpha << 24 | srgb(ch[0][i] * inv) << 16 | srgb(ch[1][i] * inv) << 8 | srgb(ch[2][i] * inv);
		}
		return new Picture(width, height, out);
	}

	/** Box widths whose three passes approximate a Gaussian of {@code sigma} (Kutskir). */
	private static int[] boxSizes(float sigma) {
		int n = 3;
		double ideal = Math.sqrt(12.0 * sigma * sigma / n + 1.0);
		int lower = (int) Math.floor(ideal);
		if (lower % 2 == 0) {
			lower--;
		}
		int upper = lower + 2;
		double m = (12.0 * sigma * sigma - n * lower * lower - 4.0 * n * lower - 3.0 * n) / (-4.0 * lower - 4.0);
		long small = Math.round(m);
		int[] sizes = new int[n];
		for (int i = 0; i < n; i++) {
			sizes[i] = i < small ? lower : upper;
		}
		return sizes;
	}

	private static void boxRows(float[] src, float[] dst, int w, int h, int r) {
		float inv = 1f / (2 * r + 1);
		for (int y = 0; y < h; y++) {
			int row = y * w;
			float first = src[row];
			float acc = (r + 1) * first;
			for (int j = 0; j < r; j++) {
				acc += src[row + Math.min(j, w - 1)];
			}
			for (int x = 0; x < w; x++) {
				acc += src[row + Math.min(x + r, w - 1)] - src[row + Math.max(x - r - 1, 0)];
				dst[row + x] = acc * inv;
			}
		}
	}

	private static void boxColumns(float[] src, float[] dst, int w, int h, int r) {
		float inv = 1f / (2 * r + 1);
		for (int x = 0; x < w; x++) {
			float first = src[x];
			float acc = (r + 1) * first;
			for (int j = 0; j < r; j++) {
				acc += src[Math.min(j, h - 1) * w + x];
			}
			for (int y = 0; y < h; y++) {
				acc += src[Math.min(y + r, h - 1) * w + x] - src[Math.max(y - r - 1, 0) * w + x];
				dst[y * w + x] = acc * inv;
			}
		}
	}

	/**
	 * Tinted-glass tone: highlights roll off smoothly towards {@code cap} (linear luminance) so
	 * white text laid on top always keeps its contrast, while hue survives and a touch of extra
	 * saturation keeps the colours glowing through, like light through smoked glass.
	 */
	public Picture glassTone(float knee, float cap, float saturation) {
		int[] out = new int[argb.length];
		float span = Math.max(1.0e-4f, cap - knee);
		for (int i = 0; i < argb.length; i++) {
			int c = argb[i];
			float r = TO_LINEAR[(c >> 16) & 255];
			float g = TO_LINEAR[(c >> 8) & 255];
			float b = TO_LINEAR[c & 255];
			float l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
			r = l + (r - l) * saturation;
			g = l + (g - l) * saturation;
			b = l + (b - l) * saturation;
			float target = l <= knee ? l : knee + span * (1f - (float) Math.exp(-(l - knee) / span));
			float k = l > 1.0e-6f ? target / l : 1f;
			out[i] = (c & 0xFF000000) | srgb(r * k) << 16 | srgb(g * k) << 8 | srgb(b * k);
		}
		return new Picture(width, height, out);
	}

	// ------------------------------------------------------------------ analysis

	/**
	 * Where the eye goes in a portrait shot against a dark backdrop: the subject's head, as
	 * {x, y} fractions. Falls back to a pleasing upper-centre point when there is no clear
	 * dark surround.
	 */
	public float[] focus() {
		float[] span = subjectSpan();
		if (span == null) {
			return new float[] {0.5f, 0.4f};
		}
		int step = Math.max(1, Math.min(width, height) / 200);
		int x0 = (int) (span[0] * width), x1 = Math.min(width, (int) (span[1] * width));
		int columns = Math.max(1, (x1 - x0) / step);
		int top = -1;
		for (int y = 0; y < height && top < 0; y += step) {
			int bright = 0;
			for (int x = x0; x < x1; x += step) {
				if (bright(argb[y * width + x])) {
					bright++;
				}
			}
			if (bright > columns * 0.03f) {
				top = y;
			}
		}
		if (top < 0) {
			return new float[] {0.5f, 0.4f};
		}
		// the head: centroid of the subject's topmost tenth
		int band = Math.max(step, height / 10);
		double sum = 0;
		int count = 0;
		for (int y = top; y < Math.min(height, top + band); y += step) {
			for (int x = x0; x < x1; x += step) {
				if (bright(argb[y * width + x])) {
					sum += x;
					count++;
				}
			}
		}
		float fx = count > 0 ? (float) (sum / count) / width : (span[0] + span[1]) / 2f;
		float fy = Math.min(0.9f, top / (float) height + 0.07f);
		return new float[] {fx, fy};
	}

	private static boolean bright(int c) {
		int lum = (((c >> 16) & 255) * 54 + ((c >> 8) & 255) * 183 + (c & 255) * 19) >> 8;
		return lum > 60 && (c >>> 24) > 128;
	}

	/**
	 * Horizontal extent of the subject of a photo shot against a dark backdrop, as fractions of
	 * the width, or {@code null} when there is no clear dark surround to find it by.
	 */
	public float[] subjectSpan() {
		int step = Math.max(1, Math.min(width, height) / 200);
		int samples = (height + step - 1) / step;
		int left = -1, right = -1;
		for (int x = 0; x < width; x += step) {
			int bright = 0;
			for (int y = 0; y < height; y += step) {
				if (bright(argb[y * width + x])) {
					bright++;
				}
			}
			if (bright > samples * 0.03f) {
				if (left < 0) {
					left = x;
				}
				right = x;
			}
		}
		if (left < 0 || right - left > width * 0.88f) {
			return null;
		}
		return new float[] {left / (float) width, Math.min(1f, (right + step) / (float) width)};
	}
}
