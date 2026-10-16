package dev.kro.ui.widget;

import dev.kro.core.anim.Spring;
import dev.kro.core.setting.BoolSetting;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

public final class BoolRow extends Row {
	private final BoolSetting bool;
	private final Spring on;

	public BoolRow(BoolSetting setting, UiHost host) {
		super(setting, host);
		this.bool = setting;
		this.on = new Spring(setting.on() ? 1f : 0f, Spring.SNAPPY);
	}

	@Override
	public float height() {
		return 32f;
	}

	@Override
	public void render(Canvas c, Mouse m) {
		float hv = hoverAnim(m);
		float h = height();
		Draw.rowHover(c, x, y, w, h, hv);
		c.text(Fonts.medium(), LABEL_SIZE, bool.name(), x + INSET, y + h / 2f, Theme.TEXT);
		float t = Math.max(0f, Math.min(1f, on.to(bool.on()).get()));
		Draw.toggle(c, x + w - INSET - Draw.TOGGLE_W, y + h / 2f - Draw.TOGGLE_H / 2f, t, hv);
	}

	@Override
	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		if (button == 0 && hovered(m)) {
			bool.toggle();
			host.sound(bool.on() ? "on" : "off");
			return true;
		}
		return false;
	}
}
