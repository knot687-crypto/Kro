package dev.kro.ui.widget;

import dev.kro.core.anim.Spring;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.util.Colors;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

/** A swatch that unfolds into a saturation/value field and a hue rail. */
public final class ColorRow extends Row {
	private static final float BASE = 32f;
	private static final float PICKER = 94f;
	private static final int[] HUES = {0xFFFF3B3B, 0xFFFFD43B, 0xFF3BFF6B, 0xFF3BFFF2, 0xFF3B6BFF, 0xFFE13BFF, 0xFFFF3B3B};

	private final ColorSetting color;
	private final Spring open = new Spring(0f, Spring.SMOOTH);
	private boolean expanded;
	private float hue;
	private float sat;
	private float val;
	/** 0 none, 1 SV field, 2 hue rail. */
	private int dragging;

	public ColorRow(ColorSetting setting, UiHost host) {
		super(setting, host);
		this.color = setting;
		syncFromSetting();
	}

	private void syncFromSetting() {
		float[] hsv = Colors.toHsv(color.color());
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
	}

	@Override
	public float height() {
		return BASE + PICKER * Math.max(0f, Math.min(1f, open.get()));
	}

	@Override
	public void render(Canvas c, Mouse m) {
		float o = Math.max(0f, Math.min(1f, open.to(expanded).get()));
		boolean overHeader = pointerInViewport && m.in(x, y, w, BASE);
		float hv = hover.to(overHeader).get();
		Draw.rowHover(c, x, y, w, BASE, hv);
		c.text(Fonts.medium(), LABEL_SIZE, color.name(), x + INSET, y + BASE / 2f, Theme.TEXT);
		float sx = x + w - INSET - 7f;
		c.circle(sx, y + BASE / 2f, 7f, color.color());
		c.ring(sx, y + BASE / 2f, 7f, 1f, 0x40FFFFFF);
		c.text(Fonts.medium(), 7.5f, Colors.toHex(color.color()), sx - 13f, y + BASE / 2f, Theme.TEXT_DIM, Canvas.RIGHT);

		if (o <= 0.01f) {
			return;
		}
		c.push();
		c.alpha(o);
		float fx = x + INSET, fy = y + BASE + 2f, fw = w - INSET * 2f, fh = 60f;
		int pure = Colors.hsv(hue, 1f, 1f);
		c.rect(fx, fy, fw, fh, 7f, 0xFFFFFFFF, pure, pure, 0xFFFFFFFF);
		c.rect(fx, fy, fw, fh, 7f, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
		c.outline(fx, fy, fw, fh, 7f, 1f, 0x1AFFFFFF);
		float kx = fx + fw * sat, ky = fy + fh * (1f - val);
		c.glow(kx, ky, 5f, 4f, 0x59000000);
		c.ring(kx, ky, 5.5f, 2f, 0xFFFFFFFF);

		float hy = fy + fh + 10f, hh = 8f;
		c.rect(fx, hy, hh, hh, hh / 2f, HUES[0]);
		c.rect(fx + fw - hh, hy, hh, hh, hh / 2f, HUES[6]);
		float inner = fw - hh;
		for (int i = 0; i < 6; i++) {
			float s0 = fx + hh / 2f + inner * i / 6f;
			float s1 = fx + hh / 2f + inner * (i + 1) / 6f;
			c.rectH(s0 - 0.25f, hy, s1 - s0 + 0.5f, hh, 0f, HUES[i], HUES[i + 1]);
		}
		float hk = fx + hh / 2f + inner * hue;
		c.glow(hk, hy + hh / 2f, 4f, 3f, 0x59000000);
		c.circle(hk, hy + hh / 2f, 6f, 0xFFFFFFFF);
		c.circle(hk, hy + hh / 2f, 4f, Colors.hsv(hue, 1f, 1f));
		c.pop();
	}

	private void apply(Mouse m) {
		float fx = x + INSET, fy = y + BASE + 2f, fw = w - INSET * 2f, fh = 60f;
		if (dragging == 1) {
			sat = Math.max(0f, Math.min(1f, (m.x - fx) / fw));
			val = 1f - Math.max(0f, Math.min(1f, (m.y - fy) / fh));
		} else if (dragging == 2) {
			float hh = 8f;
			hue = Math.max(0f, Math.min(0.9999f, (m.x - fx - hh / 2f) / (fw - hh)));
		}
		color.set(Colors.hsv(hue, sat, val));
	}

	@Override
	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		if (!hovered(m) || button != 0) {
			return false;
		}
		if (m.y < y + BASE) {
			expanded = !expanded;
			if (expanded) {
				syncFromSetting();
			}
			host.sound("click");
			return true;
		}
		if (!expanded) {
			return false;
		}
		dragging = m.y <= y + BASE + 2f + 60f + 4f ? 1 : 2;
		apply(m);
		return true;
	}

	@Override
	public void mouseDragged(Mouse m) {
		if (dragging != 0) {
			apply(m);
		}
	}

	@Override
	public void mouseReleased(Mouse m, int button) {
		dragging = 0;
	}
}
