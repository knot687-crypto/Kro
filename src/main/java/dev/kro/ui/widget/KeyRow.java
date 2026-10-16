package dev.kro.ui.widget;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Clock;
import dev.kro.core.setting.KeySetting;
import dev.kro.core.util.Colors;
import dev.kro.core.util.Keys;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

/** Click, then press any key or side mouse button. Esc cancels, Backspace clears. */
public final class KeyRow extends Row {
	private final KeySetting key;
	private final Anim listen = new Anim(0f, 14f);
	private final boolean allowNone;
	private boolean capturing;

	public KeyRow(KeySetting setting, UiHost host, boolean allowNone) {
		super(setting, host);
		this.key = setting;
		this.allowNone = allowNone;
	}

	@Override
	public float height() {
		return 32f;
	}

	@Override
	public void render(Canvas c, Mouse m) {
		float hv = hoverAnim(m);
		float l = listen.to(capturing).get();
		float h = height();
		Draw.rowHover(c, x, y, w, h, Math.max(hv, l));
		c.text(Fonts.medium(), LABEL_SIZE, key.name(), x + INSET, y + h / 2f, Theme.TEXT);
		String label = capturing ? "Press a key" : key.label();
		float pulse = 0.5f + 0.5f * (float) Math.sin(Clock.seconds() * 6.0);
		int fill = Colors.lerp(Theme.FIELD, Theme.accent(0.14f + 0.10f * pulse), l);
		int text = capturing || key.bound() ? Theme.TEXT : Theme.TEXT_FAINT;
		float pw = Draw.pill(c, label, x + w - INSET, y + h / 2f, fill, text, Canvas.RIGHT);
		if (l > 0.01f) {
			c.outline(x + w - INSET - pw, y + h / 2f - 8f, pw, 16f, 6f, 1f, Theme.accent(l * (0.45f + 0.35f * pulse)));
		}
	}

	@Override
	public boolean capturing() {
		return capturing;
	}

	@Override
	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		if (capturing) {
			if (button >= 2) {
				key.set(Keys.MOUSE_BASE + button);
				capturing = false;
				host.sound("on");
				return true;
			}
			capturing = false;
			return hovered(m);
		}
		if (button == 0 && hovered(m)) {
			capturing = true;
			host.sound("click");
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int code, int mods) {
		if (!capturing) {
			return false;
		}
		if (code == Keys.ESCAPE) {
			capturing = false;
		} else if (code == Keys.BACKSPACE || code == Keys.DELETE) {
			if (allowNone) {
				key.set(Keys.NONE);
			}
			capturing = false;
			host.sound("off");
		} else {
			key.set(code);
			capturing = false;
			host.sound("on");
		}
		return true;
	}

	@Override
	public String tooltip() {
		return key.description() + (allowNone ? " Backspace clears it." : "");
	}
}
