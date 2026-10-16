package dev.kro.ui.widget;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Spring;
import dev.kro.core.setting.RangeSetting;
import dev.kro.core.util.Colors;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

public final class RangeRow extends Row {
	private final RangeSetting range;
	private final Spring low;
	private final Spring high;
	private final Anim drag = new Anim(0f, 18f);
	/** 0 = none, 1 = low knob, 2 = high knob. */
	private int dragging;
	private String lastText;

	public RangeRow(RangeSetting setting, UiHost host) {
		super(setting, host);
		this.range = setting;
		this.low = new Spring((float) setting.fractionOf(setting.low()), 600f, 49f);
		this.high = new Spring((float) setting.fractionOf(setting.high()), 600f, 49f);
		this.lastText = setting.format();
	}

	@Override
	public float height() {
		return 44f;
	}

	private float trackX() {
		return x + INSET + 5f;
	}

	private float trackW() {
		return w - INSET * 2f - 10f;
	}

	private float trackY() {
		return y + 31f;
	}

	@Override
	public void render(Canvas c, Mouse m) {
		float hv = hoverAnim(m);
		float dg = drag.to(dragging != 0).get();
		Draw.rowHover(c, x, y, w, height(), Math.max(hv, dg));
		c.text(Fonts.medium(), LABEL_SIZE, range.name(), x + INSET, y + 13f, Theme.TEXT);
		int fill = Colors.lerp(Theme.FIELD, Theme.accent(0.22f), dg);
		Draw.pill(c, range.format(), x + w - INSET, y + 13f, fill, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(hv * 0.6f, dg)), Canvas.RIGHT);

		float a = clamp(low.to((float) range.fractionOf(range.low())).get());
		float b = clamp(high.to((float) range.fractionOf(range.high())).get());
		Draw.track(c, trackX(), trackY(), trackW(), a, b, hv, dg);
		Draw.knob(c, trackX() + trackW() * a, trackY(), hv, dragging == 1 ? dg : 0f);
		Draw.knob(c, trackX() + trackW() * b, trackY(), hv, dragging == 2 ? dg : 0f);
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private void apply(Mouse m) {
		double v = range.valueAt((m.x - trackX()) / trackW());
		if (dragging == 1) {
			range.setLow(Math.min(v, range.high()));
		} else if (dragging == 2) {
			range.setHigh(Math.max(v, range.low()));
		}
		String t = range.format();
		if (!t.equals(lastText)) {
			lastText = t;
			host.sound("tick");
		}
	}

	@Override
	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		if (!hovered(m) || button != 0) {
			return false;
		}
		if (doubleClick) {
			range.reset();
			lastText = range.format();
			host.sound("click");
			return true;
		}
		if (m.y >= y + 21f) {
			float lx = trackX() + trackW() * (float) range.fractionOf(range.low());
			float hx = trackX() + trackW() * (float) range.fractionOf(range.high());
			if (Math.abs(lx - hx) < 0.5f) {
				dragging = m.x < lx ? 1 : 2;
			} else {
				dragging = Math.abs(m.x - lx) <= Math.abs(m.x - hx) ? 1 : 2;
			}
			apply(m);
		}
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

	@Override
	public String tooltip() {
		return range.description() + " Double-click to reset.";
	}
}
