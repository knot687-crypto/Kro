package dev.kro.ui.widget;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Spring;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.util.Colors;
import dev.kro.core.util.Keys;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

public final class NumberRow extends Row {
	private final NumberSetting number;
	private final Spring fraction;
	private final Anim drag = new Anim(0f, 18f);
	private boolean dragging;
	private double lastValue;

	public NumberRow(NumberSetting setting, UiHost host) {
		super(setting, host);
		this.number = setting;
		this.fraction = new Spring((float) setting.fraction(), 600f, 49f);
		this.lastValue = setting.value();
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
		float dg = drag.to(dragging).get();
		Draw.rowHover(c, x, y, w, height(), Math.max(hv, dg));
		c.text(Fonts.medium(), LABEL_SIZE, number.name(), x + INSET, y + 13f, Theme.TEXT);
		int fill = Colors.lerp(Theme.FIELD, Theme.accent(0.22f), dg);
		Draw.pill(c, number.format(), x + w - INSET, y + 13f, fill, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(hv * 0.6f, dg)), Canvas.RIGHT);

		float f = Math.max(0f, Math.min(1f, fraction.to((float) number.fraction()).get()));
		Draw.track(c, trackX(), trackY(), trackW(), 0f, f, hv, dg);
		Draw.knob(c, trackX() + trackW() * f, trackY(), hv, dg);
	}

	private void apply(Mouse m) {
		double f = (m.x - trackX()) / trackW();
		number.setFraction(f);
		if (number.value() != lastValue) {
			lastValue = number.value();
			host.sound("tick");
		}
	}

	@Override
	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		if (!hovered(m) || button != 0) {
			return false;
		}
		if (doubleClick) {
			number.reset();
			lastValue = number.value();
			host.sound("click");
			return true;
		}
		if (m.y >= y + 21f) {
			dragging = true;
			apply(m);
		}
		return true;
	}

	@Override
	public void mouseDragged(Mouse m) {
		if (dragging) {
			apply(m);
		}
	}

	@Override
	public void mouseReleased(Mouse m, int button) {
		dragging = false;
	}

	@Override
	public boolean mouseScrolled(Mouse m, double amount) {
		if (!hovered(m) || !host.isKeyDown(Keys.LEFT_SHIFT)) {
			return false;
		}
		number.set(number.value() + Math.signum(amount) * number.step());
		lastValue = number.value();
		host.sound("tick");
		return true;
	}

	@Override
	public String tooltip() {
		return number.description() + " Double-click to reset.";
	}
}
