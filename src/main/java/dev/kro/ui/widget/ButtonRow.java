package dev.kro.ui.widget;

import dev.kro.core.util.Colors;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

/** A quiet bordered button spanning the row, used for secondary actions. */
public final class ButtonRow extends Row {
	private final String label;
	private final char icon;
	private final Runnable action;
	private final String tip;

	public ButtonRow(String label, char icon, String tooltip, UiHost host, Runnable action) {
		super(null, host);
		this.label = label;
		this.icon = icon;
		this.action = action;
		this.tip = tooltip;
	}

	@Override
	public float height() {
		return 34f;
	}

	@Override
	public void render(Canvas c, Mouse m) {
		float hv = hoverAnim(m);
		float bx = x + INSET, bw = w - INSET * 2f, by = y + 6f, bh = 22f;
		c.rect(bx, by, bw, bh, 7f, Colors.lerp(0x00FFFFFF, Theme.SURFACE_HOVER, hv));
		c.outline(bx, by, bw, bh, 7f, 1f, Colors.lerp(Theme.BORDER, Theme.BORDER_STRONG, hv));
		int col = Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hv);
		float tw = c.textWidth(Fonts.semibold(), 7.5f, label);
		float start = bx + bw / 2f - (tw + 13f) / 2f;
		c.icon(icon, start + 4.5f, by + bh / 2f, 9f, col);
		c.text(Fonts.semibold(), 7.5f, label, start + 13f, by + bh / 2f, col);
	}

	@Override
	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		if (button == 0 && hovered(m) && m.y >= y + 6f && m.y <= y + 28f) {
			host.sound("click");
			action.run();
			return true;
		}
		return false;
	}

	@Override
	public String tooltip() {
		return tip;
	}
}
