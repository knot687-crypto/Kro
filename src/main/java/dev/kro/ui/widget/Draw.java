package dev.kro.ui.widget;

import dev.kro.core.util.Colors;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

import java.util.Locale;

/** Shared, restrained building blocks so every surface in Kro speaks the same language. */
public final class Draw {
	public static final float TOGGLE_W = 26f;
	public static final float TOGGLE_H = 15f;

	private Draw() {
	}

	/** The main window: near-opaque black with a hairline rim that catches light at the top. */
	public static void window(Canvas c, float x, float y, float w, float h, float r) {
		c.shadow(x, y + 18f, w, h, r, 46f, 0x99000000);
		c.shadow(x, y + 2f, w, h, r, 6f, 0x4D000000);
		c.rect(x, y, w, h, r, Theme.WINDOW_TOP, Theme.WINDOW_TOP, Theme.WINDOW_BOTTOM, Theme.WINDOW_BOTTOM);
		c.outline(x, y, w, h, r, 1f, 0x21FFFFFF, 0x21FFFFFF, 0x0FFFFFFF, 0x0FFFFFFF);
	}

	/** A small floating surface such as a tooltip or popover. */
	public static void popover(Canvas c, float x, float y, float w, float h, float r) {
		c.shadow(x, y + 6f, w, h, r, 18f, 0x8C000000);
		c.rect(x, y, w, h, r, Theme.POPOVER);
		c.outline(x, y, w, h, r, 1f, 0x21FFFFFF);
	}

	/** A floating HUD surface (Target HUD, toasts, watermark): black glass with a soft rim. */
	public static void panel(Canvas c, float x, float y, float w, float h, float r, float shadowY, float shadowSoftness, int shadow) {
		c.shadow(x, y + shadowY, w, h, r, shadowSoftness, shadow);
		c.rect(x, y, w, h, r, Theme.PANEL_TOP, Theme.PANEL_TOP, Theme.PANEL_BOTTOM, Theme.PANEL_BOTTOM);
		c.outline(x, y, w, h, r, 1f, 0x24FFFFFF, 0x24FFFFFF, 0x0BFFFFFF, 0x0BFFFFFF);
	}

	/** Switch. {@code on} and {@code hover} are animated 0..1 values. */
	public static void toggle(Canvas c, float x, float y, float on, float hover) {
		float w = TOGGLE_W, h = TOGGLE_H;
		float t = Math.max(0f, Math.min(1f, on));
		if (t > 0.01f) {
			// the lit track blooms softly into its surroundings
			c.shadow(x, y + 1f, w, h, h / 2f, 7f, Theme.accent(0.42f * t));
		}
		int off = Colors.lerp(0x29FFFFFF, 0x38FFFFFF, hover);
		int top = Colors.lerp(off, Theme.accentLight(), t);
		int bottom = Colors.lerp(off, Theme.accent(), t);
		c.rect(x, y, w, h, h / 2f, top, top, bottom, bottom);
		if (t > 0.01f) {
			c.outline(x, y, w, h, h / 2f, 1f, Colors.withAlpha(0xFFFFFF, 0.22f * t), Colors.withAlpha(0xFFFFFF, 0.22f * t), 0x00FFFFFF, 0x00FFFFFF);
		}
		float knob = h - 4f;
		float kx = x + 2f + (w - knob - 4f) * on + knob / 2f;
		float ky = y + h / 2f;
		c.glow(kx, ky + 0.8f, knob / 2f - 0.5f, 2.5f, 0x59000000);
		c.circle(kx, ky, knob / 2f, 0xFFFFFFFF);
	}

	/** A compact rounded label, e.g. a value readout or a key name. */
	public static float pill(Canvas c, String text, float x, float cy, int fill, int textColor, int align) {
		float size = 7.5f;
		float tw = c.textWidth(Fonts.semibold(), size, text);
		float w = tw + 12f, h = 16f;
		float px = align == Canvas.RIGHT ? x - w : align == Canvas.CENTER ? x - w / 2f : x;
		c.rect(px, cy - h / 2f, w, h, 6f, fill);
		c.text(Fonts.semibold(), size, text, px + w / 2f, cy, textColor, Canvas.CENTER);
		return w;
	}

	/** Small letter-spaced caps label. */
	public static void overline(Canvas c, String text, float x, float cy, int color, int align) {
		c.text(Fonts.semibold(), 6.5f, text.toUpperCase(Locale.ROOT), x, cy, color, align, 0.12f);
	}

	/** Slider track; values are 0..1 fractions of the filled span. */
	public static void track(Canvas c, float x, float y, float w, float from, float to, float hover, float drag) {
		float h = 3f;
		c.rect(x, y - h / 2f, w, h, h / 2f, Colors.lerp(0x1FFFFFFF, 0x2BFFFFFF, Math.max(hover, drag)));
		float x0 = x + w * from, x1 = x + w * to;
		if (x1 - x0 > 0.01f) {
			c.rect(x0, y - h / 2f, Math.max(h, x1 - x0), h, h / 2f, Theme.accent());
		}
	}

	public static void knob(Canvas c, float cx, float cy, float hover, float drag) {
		float r = 5f + 0.8f * Math.max(hover, drag);
		if (drag > 0.01f) {
			c.circle(cx, cy, r + 4f * drag, Theme.accent(0.22f * drag));
		}
		c.glow(cx, cy + 1f, r - 1f, 3f, 0x66000000);
		c.circle(cx, cy, r, 0xFFFFFFFF);
	}

	/** Subtle wash behind an interactive row. */
	public static void rowHover(Canvas c, float x, float y, float w, float h, float hover) {
		if (hover > 0.005f) {
			c.rect(x, y, w, h, 8f, Colors.withAlpha(0xFFFFFF, 0.03f * hover));
		}
	}

	/** Circular icon button. */
	public static void iconButton(Canvas c, char icon, float cx, float cy, float size, float hover, int iconColor) {
		if (hover > 0.005f) {
			c.circle(cx, cy, size / 2f, Colors.withAlpha(0xFFFFFF, 0.08f * hover));
		}
		c.icon(icon, cx, cy, size * 0.42f, Colors.lerp(iconColor, Theme.TEXT, hover));
	}
}
