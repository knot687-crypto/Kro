package dev.kro.preview;

import dev.kro.ui.render.ImageRef;
import dev.kro.ui.render.Picture;

import java.awt.image.BufferedImage;

/** Image for previews: nearest sampled for pixel art (skins), bilinear for photos. */
public final class PreviewImage implements ImageRef {
	private final BufferedImage image;
	private final boolean smooth;

	public PreviewImage(BufferedImage image) {
		this(image, false);
	}

	public PreviewImage(BufferedImage image, boolean smooth) {
		this.image = image;
		this.smooth = smooth;
	}

	public static PreviewImage of(Picture p) {
		BufferedImage img = new BufferedImage(p.width, p.height, BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, p.width, p.height, p.argb, 0, p.width);
		return new PreviewImage(img, true);
	}

	@Override
	public Object handle() {
		return image;
	}

	BufferedImage image() {
		return image;
	}

	boolean smooth() {
		return smooth;
	}

	float[] sample(float u, float v) {
		int w = image.getWidth(), h = image.getHeight();
		if (!smooth) {
			int x = Math.min(w - 1, Math.max(0, (int) (u * w)));
			int y = Math.min(h - 1, Math.max(0, (int) (v * h)));
			return unpack(image.getRGB(x, y));
		}
		float fx = u * w - 0.5f, fy = v * h - 0.5f;
		int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
		float tx = fx - x0, ty = fy - y0;
		float[] a = unpack(image.getRGB(clamp(x0, w), clamp(y0, h)));
		float[] b = unpack(image.getRGB(clamp(x0 + 1, w), clamp(y0, h)));
		float[] c = unpack(image.getRGB(clamp(x0, w), clamp(y0 + 1, h)));
		float[] d = unpack(image.getRGB(clamp(x0 + 1, w), clamp(y0 + 1, h)));
		float[] out = new float[4];
		for (int k = 0; k < 4; k++) {
			float top = a[k] + (b[k] - a[k]) * tx;
			float bottom = c[k] + (d[k] - c[k]) * tx;
			out[k] = top + (bottom - top) * ty;
		}
		return out;
	}

	private static int clamp(int i, int n) {
		return i < 0 ? 0 : Math.min(i, n - 1);
	}

	private static float[] unpack(int c) {
		return new float[] {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, ((c >>> 24) & 255) / 255f};
	}
}
