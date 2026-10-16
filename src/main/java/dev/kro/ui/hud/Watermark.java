package dev.kro.ui.hud;

import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Icons;
import dev.kro.ui.render.Theme;
import dev.kro.ui.widget.Draw;

/** A discreet brand pill with frame rate, plus an optional sprint chip beneath it. */
public final class Watermark {
	private Watermark() {
	}

	public static void render(Canvas c, int fps, boolean showMark, boolean sprinting) {
		float x = 8f, y = 8f;
		if (showMark) {
			String fpsText = fps + " fps";
			float fw = c.textWidth(Fonts.medium(), 7.5f, fpsText);
			float w = 10f + 14f + 6f + c.textWidth(Fonts.semibold(), 8.5f, "Kro") + 9f + 1f + 9f + fw + 10f, h = 22f;
			Draw.panel(c, x, y, w, h, 8f, 4f, 12f, 0x66000000);
			float lx = x + 8f;
			c.rect(lx, y + 4f, 14f, 14f, 4.5f, Theme.accentLight(), Theme.accent(), Theme.accent(), Theme.accentLight());
			c.text(Fonts.display(), 8.5f, "K", lx + 7f, y + 11f, Theme.onAccent(), Canvas.CENTER);
			float tw = c.text(Fonts.semibold(), 8.5f, "Kro", lx + 20f, y + 11f, Theme.TEXT);
			float dx = lx + 20f + tw + 9f;
			c.rect(dx, y + 6f, 1f, 10f, 0f, 0x1FFFFFFF);
			c.text(Fonts.medium(), 7.5f, fpsText, dx + 9f, y + 11f, Theme.TEXT_DIM);
			y += h + 5f;
		}
		if (sprinting) {
			float w = 12f + 10f + c.textWidth(Fonts.medium(), 7.5f, "Sprinting") + 10f, h = 18f;
			c.rect(x, y, w, h, 6f, Theme.PANEL_TOP, Theme.PANEL_TOP, Theme.PANEL_BOTTOM, Theme.PANEL_BOTTOM);
			c.outline(x, y, w, h, 6f, 1f, 0x1CFFFFFF);
			c.icon(Icons.WIND, x + 11f, y + h / 2f, 8.5f, Theme.accentLight());
			c.text(Fonts.medium(), 7.5f, "Sprinting", x + 19f, y + h / 2f, Theme.TEXT_DIM);
		}
	}
}
