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
 * one texel per screen pixel at any resolution, and the tone used for clear glass over it.
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

	/**
	 * Fades the outermost margins to pure black (smoothly, in linear light) so the picture melts
	 * into a black window with no visible edge, and sampling beyond it returns exact black.
	 */
	public Picture fadeEdges(float marginX, float marginY) {
		float[] fx = ramp(width, marginX);
		float[] fy = ramp(height, marginY);
		int[] out = new int[argb.length];
		for (int y = 0; y < height; y++) {
			int row = y * width;
			for (int x = 0; x < width; x++) {
				int c = argb[row + x];
				float k = fx[x] * fy[y];
				if (k >= 1f) {
					out[row + x] = c;
					continue;
				}
				out[row + x] = (c & 0xFF000000)
						| srgb(TO_LINEAR[(c >> 16) & 255] * k) << 16
						| srgb(TO_LINEAR[(c >> 8) & 255] * k) << 8
						| srgb(TO_LINEAR[c & 255] * k);
			}
		}
		return new Picture(width, height, out);
	}

	/** 0 at both ends rising smoothly to 1 over {@code margin} of the length; the very edge is 0. */
	private static float[] ramp(int n, float margin) {
		float[] r = new float[n];
		float m = Math.max(1f, margin * n);
		for (int i = 0; i < n; i++) {
			float d = Math.min(i, n - 1 - i) / m;
			float t = Math.max(0f, Math.min(1f, d));
			r[i] = t * t * (3f - 2f * t);
		}
		return r;
	}

	// ------------------------------------------------------------------ glass

	/**
	 * Clear tinted-glass tone: no blur, every detail stays sharp, but highlights roll off
	 * smoothly towards {@code cap} (linear luminance) so white text laid on top always keeps its
	 * contrast, while hue survives and a touch of saturation keeps the colours glowing through.
	 */
	public Picture tint(float knee, float cap, float saturation) {
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
}
