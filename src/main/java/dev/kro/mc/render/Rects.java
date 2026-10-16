package dev.kro.mc.render;

import dev.kro.ui.render.ClipRect;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/** Conversions between Kro's float bounds and Minecraft's integer screen rectangles. */
final class Rects {
	private Rects() {
	}

	static ScreenRectangle scissor(ClipRect clip) {
		return clip == null ? null : new ScreenRectangle(clip.x(), clip.y(), clip.w(), clip.h());
	}

	/** Integer bounds covering {minX, minY, maxX, maxY}, clipped; null when nothing is visible. */
	static ScreenRectangle bounds(float[] b, ClipRect clip) {
		int x0 = (int) Math.floor(b[0]);
		int y0 = (int) Math.floor(b[1]);
		int x1 = (int) Math.ceil(b[2]);
		int y1 = (int) Math.ceil(b[3]);
		if (clip != null) {
			x0 = Math.max(x0, clip.x());
			y0 = Math.max(y0, clip.y());
			x1 = Math.min(x1, clip.x() + clip.w());
			y1 = Math.min(y1, clip.y() + clip.h());
		}
		if (x1 <= x0 || y1 <= y0) {
			return null;
		}
		return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
	}
}
