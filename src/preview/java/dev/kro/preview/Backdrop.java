package dev.kro.preview;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Builds a blurred in-game style backdrop from a panorama face, approximating the menu blur. */
public final class Backdrop {
	private Backdrop() {
	}

	public static BufferedImage make(File panorama, int w, int h, int blurRadius, float dim) throws IOException {
		BufferedImage src = ImageIO.read(panorama);
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		// cover crop
		float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
		int dw = Math.round(src.getWidth() * s), dh = Math.round(src.getHeight() * s);
		g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
		g.dispose();
		int[] px = out.getRGB(0, 0, w, h, null, 0, w);
		for (int pass = 0; pass < 3 && blurRadius > 0; pass++) {
			px = boxH(px, w, h, blurRadius);
			px = boxV(px, w, h, blurRadius);
		}
		for (int i = 0; i < px.length; i++) {
			int c = px[i];
			int r = Math.round(((c >> 16) & 255) * (1 - dim));
			int gg = Math.round(((c >> 8) & 255) * (1 - dim));
			int b = Math.round((c & 255) * (1 - dim));
			px[i] = (r << 16) | (gg << 8) | b;
		}
		out.setRGB(0, 0, w, h, px, 0, w);
		return out;
	}

	private static int[] boxH(int[] in, int w, int h, int r) {
		int[] out = new int[in.length];
		for (int y = 0; y < h; y++) {
			int sr = 0, sg = 0, sb = 0;
			for (int x = -r; x <= r; x++) {
				int c = in[y * w + Math.min(w - 1, Math.max(0, x))];
				sr += (c >> 16) & 255;
				sg += (c >> 8) & 255;
				sb += c & 255;
			}
			for (int x = 0; x < w; x++) {
				int n = 2 * r + 1;
				out[y * w + x] = ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
				int add = in[y * w + Math.min(w - 1, x + r + 1)];
				int sub = in[y * w + Math.max(0, x - r)];
				sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
				sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
				sb += (add & 255) - (sub & 255);
			}
		}
		return out;
	}

	private static int[] boxV(int[] in, int w, int h, int r) {
		int[] out = new int[in.length];
		for (int x = 0; x < w; x++) {
			int sr = 0, sg = 0, sb = 0;
			for (int y = -r; y <= r; y++) {
				int c = in[Math.min(h - 1, Math.max(0, y)) * w + x];
				sr += (c >> 16) & 255;
				sg += (c >> 8) & 255;
				sb += c & 255;
			}
			for (int y = 0; y < h; y++) {
				int n = 2 * r + 1;
				out[y * w + x] = ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
				int add = in[Math.min(h - 1, y + r + 1) * w + x];
				int sub = in[Math.max(0, y - r) * w + x];
				sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
				sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
				sb += (add & 255) - (sub & 255);
			}
		}
		return out;
	}
}
