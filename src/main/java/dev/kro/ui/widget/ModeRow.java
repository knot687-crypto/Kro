package dev.kro.ui.widget;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Spring;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.util.Colors;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;

import java.util.List;
import java.util.function.Function;

/**
 * A segmented control. Options share one track when they fit; otherwise they flow into chips
 * over several lines. The selection glides between options on a spring.
 */
public final class ModeRow extends Row {
	private static final float CHIP_H = 22f;
	private static final float GAP = 5f;
	private static final float TEXT_SIZE = 7.5f;

	private final ModeSetting mode;
	private final Function<String, int[]> swatches;
	private final Spring hx = new Spring(0f, Spring.SNAPPY);
	private final Spring hy = new Spring(0f, Spring.SNAPPY);
	private final Spring hw = new Spring(0f, Spring.SNAPPY);
	private final Anim[] hovers;
	private boolean placed;
	/** Per option layout relative to the row: x, y, w. */
	private float[][] layout = new float[0][];
	private float layoutWidth = -1f;
	private float controlHeight = CHIP_H;
	private boolean track;

	public ModeRow(ModeSetting setting, UiHost host, Function<String, int[]> swatches) {
		super(setting, host);
		this.mode = setting;
		this.swatches = swatches;
		this.hovers = new Anim[setting.modes().size()];
		for (int i = 0; i < hovers.length; i++) {
			hovers[i] = new Anim(0f, 16f);
		}
	}

	@Override
	public float height() {
		return 24f + controlHeight + 10f;
	}

	private float optionWidth(Canvas c, String s) {
		float sw = swatches != null ? 13f : 0f;
		return c.textWidth(Fonts.semibold(), TEXT_SIZE, s) + 20f + sw;
	}

	private void relayout(Canvas c) {
		if (layoutWidth == w) {
			return;
		}
		layoutWidth = w;
		List<String> modes = mode.modes();
		float avail = w - INSET * 2f;
		float total = 0f;
		float[] widths = new float[modes.size()];
		for (int i = 0; i < modes.size(); i++) {
			widths[i] = optionWidth(c, modes.get(i));
			total += widths[i];
		}
		layout = new float[modes.size()][];
		track = swatches == null && total + 6f <= avail;
		if (track) {
			float seg = (avail - 6f) / modes.size();
			for (int i = 0; i < modes.size(); i++) {
				layout[i] = new float[] {INSET + 3f + seg * i, 3f, seg};
			}
			controlHeight = CHIP_H + 6f;
		} else {
			float cx = INSET, cy = 0f;
			for (int i = 0; i < modes.size(); i++) {
				if (cx + widths[i] > INSET + avail + 0.01f) {
					cx = INSET;
					cy += CHIP_H + GAP;
				}
				layout[i] = new float[] {cx, cy, widths[i]};
				cx += widths[i] + GAP;
			}
			controlHeight = cy + CHIP_H;
		}
	}

	@Override
	public void render(Canvas c, Mouse m) {
		relayout(c);
		float hv = hoverAnim(m);
		Draw.rowHover(c, x, y, w, height(), hv * 0.5f);
		c.text(Fonts.medium(), LABEL_SIZE, mode.name(), x + INSET, y + 13f, Theme.TEXT);

		float top = y + 25f;
		int sel = mode.index();
		float[] s = layout[sel];
		if (!placed) {
			hx.snap(s[0]);
			hy.snap(s[1]);
			hw.snap(s[2]);
			placed = true;
		}
		float bx = hx.to(s[0]).get(), by = hy.to(s[1]).get(), bw = hw.to(s[2]).get();
		List<String> modes = mode.modes();

		if (track) {
			c.rect(x + INSET, top, w - INSET * 2f, CHIP_H + 6f, 9f, Theme.FIELD);
			c.shadow(x + bx, top + by + 1f, bw, CHIP_H, 7f, 3f, 0x4D000000);
			c.rect(x + bx, top + by, bw, CHIP_H, 7f, 0x2EFFFFFF, 0x2EFFFFFF, 0x22FFFFFF, 0x22FFFFFF);
			c.outline(x + bx, top + by, bw, CHIP_H, 7f, 1f, 0x1FFFFFFF, 0x1FFFFFFF, 0x08FFFFFF, 0x08FFFFFF);
		} else {
			for (int i = 0; i < modes.size(); i++) {
				float[] l = layout[i];
				boolean over = pointerInViewport && m.in(x + l[0], top + l[1], l[2], CHIP_H);
				float h = hovers[i].to(over && i != sel).get();
				c.rect(x + l[0], top + l[1], l[2], CHIP_H, 7f, Colors.lerp(Theme.FIELD, Theme.SURFACE_ACTIVE, h));
			}
			c.rect(x + bx, top + by, bw, CHIP_H, 7f, Theme.accent(0.16f));
			c.outline(x + bx, top + by, bw, CHIP_H, 7f, 1f, Theme.accent(0.55f));
		}
		for (int i = 0; i < modes.size(); i++) {
			float[] l = layout[i];
			if (track) {
				boolean over = pointerInViewport && m.in(x + l[0], top + l[1], l[2], CHIP_H);
				hovers[i].to(over && i != sel);
			}
			boolean selected = i == sel;
			int col = selected ? Theme.TEXT : Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hovers[i].get());
			float cx = x + l[0] + l[2] / 2f;
			float cy = top + l[1] + CHIP_H / 2f;
			if (swatches != null) {
				int[] sw = swatches.apply(modes.get(i));
				float tw = c.textWidth(Fonts.semibold(), TEXT_SIZE, modes.get(i));
				float start = cx - (tw + 13f) / 2f;
				c.circle(start + 4f, cy, 4f, sw[0], sw[1], sw[1], sw[0]);
				c.ring(start + 4f, cy, 4f, 1f, 0x29FFFFFF);
				c.text(Fonts.semibold(), TEXT_SIZE, modes.get(i), start + 13f, cy, col);
			} else {
				c.text(Fonts.semibold(), TEXT_SIZE, modes.get(i), cx, cy, col, Canvas.CENTER);
			}
		}
	}

	@Override
	public boolean mouseClicked(Mouse m, int button, boolean doubleClick) {
		if (!hovered(m)) {
			return false;
		}
		float top = y + 25f;
		for (int i = 0; i < layout.length; i++) {
			float[] l = layout[i];
			if (m.in(x + l[0], top + l[1], l[2], CHIP_H)) {
				if (i != mode.index()) {
					mode.setIndex(i);
					host.sound("click");
				}
				return true;
			}
		}
		return true;
	}
}
