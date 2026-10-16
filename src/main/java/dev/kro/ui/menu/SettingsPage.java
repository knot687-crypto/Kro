package dev.kro.ui.menu;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Clock;
import dev.kro.core.anim.Ease;
import dev.kro.core.anim.Spring;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.KeySetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.core.setting.Setting;
import dev.kro.core.util.Colors;
import dev.kro.ui.Mouse;
import dev.kro.ui.UiHost;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Icons;
import dev.kro.ui.render.Theme;
import dev.kro.ui.widget.BoolRow;
import dev.kro.ui.widget.ButtonRow;
import dev.kro.ui.widget.ColorRow;
import dev.kro.ui.widget.Draw;
import dev.kro.ui.widget.KeyRow;
import dev.kro.ui.widget.ModeRow;
import dev.kro.ui.widget.NumberRow;
import dev.kro.ui.widget.RangeRow;
import dev.kro.ui.widget.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A module's settings laid out in two balanced columns inside the menu's content area. */
final class SettingsPage {
	private static final float HEADER = 58f;
	private static final float SIDE = 20f;
	private static final float GUTTER = 18f;
	private static final float ROW_GAP = 2f;

	final Module module;
	private final UiHost host;
	private final List<Row> rows = new ArrayList<>();
	private final Spring masterOn;
	private final Anim backHover = new Anim(0f, 16f);
	private final Anim masterHover = new Anim(0f, 16f);
	private final Anim scroll = new Anim(0f, 16f);
	private final Anim scrollbar = new Anim(0f, 8f);
	private final long openedAt = Clock.nanos();
	private float scrollTarget;
	private float viewportTop;
	private float viewportHeight;
	private float x;
	private float y;
	private float w;
	private float h;
	private Row tooltipRow;
	private long tooltipSince;
	private Row active;

	SettingsPage(Module module, UiHost host, Function<String, int[]> accentSwatches, Function<String, int[]> themeSwatches) {
		this.module = module;
		this.host = host;
		this.masterOn = new Spring(module.enabled() ? 1f : 0f, Spring.SNAPPY);
		for (Setting<?> s : module.settings()) {
			Row row;
			if (s instanceof BoolSetting b) {
				row = new BoolRow(b, host);
			} else if (s instanceof NumberSetting n) {
				row = new NumberRow(n, host);
			} else if (s instanceof RangeSetting r) {
				row = new RangeRow(r, host);
			} else if (s instanceof ModeSetting m) {
				Function<String, int[]> sw = null;
				if (s.name().equals("Accent")) {
					sw = accentSwatches;
				} else if (s.name().equals("Preset")) {
					sw = themeSwatches;
				}
				row = new ModeRow(m, host, sw);
			} else if (s instanceof ColorSetting col) {
				row = new ColorRow(col, host);
			} else if (s instanceof KeySetting k) {
				row = new KeyRow(k, host, false);
			} else {
				continue;
			}
			rows.add(row);
		}
		if (!module.alwaysOn()) {
			rows.add(new KeyRow(module.bind(), host, true));
		}
		rows.add(new ButtonRow("Reset to defaults", Icons.ROTATE_CCW, "Restore every setting of " + module.name() + ".", host, () -> {
			for (Setting<?> s : module.settings()) {
				s.reset();
			}
		}));
	}

	boolean overBack(Mouse m) {
		return m.inCircle(x + SIDE + 14f, y + HEADER / 2f, 15f);
	}

	private boolean overMaster(Mouse m) {
		float sx = x + w - SIDE - Draw.TOGGLE_W;
		return !module.alwaysOn() && m.in(sx - 70f, y + HEADER / 2f - 12f, Draw.TOGGLE_W + 76f, 24f);
	}

	void render(Canvas c, Mouse m, float px, float py, float pw, float ph, boolean interactive) {
		x = px;
		y = py;
		w = pw;
		h = ph;
		renderHeader(c, m, interactive);
		c.rect(x + SIDE, y + HEADER, w - SIDE * 2f, 1f, 0f, Theme.DIVIDER);
		viewportTop = y + HEADER + 1f;
		viewportHeight = h - HEADER - 1f;
		renderColumns(c, m, interactive);
	}

	private void renderHeader(Canvas c, Mouse m, boolean interactive) {
		float cy = y + HEADER / 2f;
		float bh = backHover.to(interactive && overBack(m)).get();
		float bx = x + SIDE + 14f;
		c.circle(bx, cy, 14f, Colors.lerp(Theme.FIELD, Theme.SURFACE_ACTIVE, bh));
		c.icon(Icons.CHEVRON_LEFT, bx - 0.5f, cy, 11f, Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, bh));

		float on = Math.max(0f, Math.min(1f, masterOn.to(module.enabled()).get()));
		float nx = bx + 28f;
		c.text(Fonts.semibold(), 12f, module.name(), nx, cy - 6f, Theme.TEXT);
		float maxDesc = w - (nx - x) - SIDE - 150f;
		c.text(Fonts.medium(), 7.5f, c.ellipsize(Fonts.medium(), 7.5f, module.description(), maxDesc), nx, cy + 8f, Theme.TEXT_DIM);

		if (module.alwaysOn()) {
			Draw.pill(c, "Always on", x + w - SIDE, cy, Theme.FIELD, Theme.TEXT_DIM, Canvas.RIGHT);
		} else {
			float mh = masterHover.to(interactive && overMaster(m)).get();
			float sx = x + w - SIDE - Draw.TOGGLE_W;
			Draw.toggle(c, sx, cy - Draw.TOGGLE_H / 2f, on, mh);
			c.text(Fonts.medium(), 8f, module.enabled() ? "Enabled" : "Disabled", sx - 9f, cy, module.enabled() ? Theme.TEXT : Theme.TEXT_DIM, Canvas.RIGHT);
		}
	}

	private void renderColumns(Canvas c, Mouse m, boolean interactive) {
		float colW = (w - SIDE * 2f - GUTTER) / 2f;
		float total = 0f;
		for (Row r : rows) {
			total += r.animatedHeight() + ROW_GAP;
		}
		// order-preserving split: the left column takes rows until it holds about half
		float leftH = 0f;
		int split = rows.size();
		for (int i = 0; i < rows.size(); i++) {
			float rh = rows.get(i).animatedHeight() + ROW_GAP;
			if (leftH + rh / 2f > total / 2f && i > 0) {
				split = i;
				break;
			}
			leftH += rh;
		}
		float rightH = total - leftH;
		float content = Math.max(leftH, rightH) + 14f;
		float maxScroll = Math.max(0f, content - viewportHeight);
		scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
		float sc = scroll.to(scrollTarget).get();
		boolean inViewport = interactive && m.in(x, viewportTop, w, viewportHeight);

		float elapsed = (Clock.nanos() - openedAt) / 1.0e9f * Clock.speed();
		c.pushClip(x, viewportTop, w, viewportHeight);
		Row hoveredRow = null;
		float[] cursors = {viewportTop + 10f - sc, viewportTop + 10f - sc};
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			int col = i < split ? 0 : 1;
			float reveal = r.reveal();
			float rh = r.height() * reveal;
			float ry = cursors[col];
			if (rh > 0.25f) {
				r.x = x + SIDE - 6f + col * (colW + GUTTER);
				r.y = ry;
				r.w = colW + 12f;
				r.pointerInViewport = inViewport;
				float stagger = Ease.outCubic((elapsed - 0.06f - i * 0.022f) / 0.30f);
				c.push();
				c.alpha(reveal * reveal * stagger);
				c.translate(10f * (1f - stagger), 0f);
				if (reveal < 0.999f) {
					c.pushClip(r.x, ry, r.w, rh);
				}
				r.render(c, m);
				if (reveal < 0.999f) {
					c.popClip();
				}
				c.pop();
				if (inViewport && m.in(r.x, ry, r.w, rh) && reveal > 0.9f) {
					hoveredRow = r;
				}
			}
			cursors[col] += rh + ROW_GAP;
		}
		c.popClip();

		if (maxScroll > 0.5f) {
			float vis = scrollbar.to(inViewport ? 1f : 0.3f).get();
			float barH = Math.max(28f, viewportHeight * viewportHeight / content);
			float barY = viewportTop + 4f + (viewportHeight - barH - 8f) * (sc / maxScroll);
			c.rect(x + w - 8f, barY, 3f, barH, 1.5f, Colors.withAlpha(0xFFFFFF, 0.08f + 0.14f * vis));
		}
		if (hoveredRow != tooltipRow) {
			tooltipRow = hoveredRow;
			tooltipSince = Clock.nanos();
		}
	}

	/** Description of the row under the pointer, for the menu's status line. */
	String hoveredDescription() {
		if (tooltipRow == null || tooltipRow.capturing()) {
			return null;
		}
		return tooltipRow.tooltip();
	}

	/** Natural height of the page for its current rows. */
	float contentHeight() {
		float total = 0f;
		for (Row r : rows) {
			total += r.animatedHeight() + ROW_GAP;
		}
		float leftH = 0f;
		for (int i = 0; i < rows.size(); i++) {
			float rh = rows.get(i).animatedHeight() + ROW_GAP;
			if (leftH + rh / 2f > total / 2f && i > 0) {
				break;
			}
			leftH += rh;
		}
		return HEADER + 1f + 10f + Math.max(leftH, total - leftH) + 14f;
	}

	boolean capturing() {
		for (Row r : rows) {
			if (r.capturing()) {
				return true;
			}
		}
		return false;
	}

	/** @return 1 if consumed, 2 if the page asked to go back, 0 if not handled */
	int mouseClicked(Mouse m, int button, boolean dbl) {
		for (Row r : rows) {
			if (r.capturing()) {
				r.mouseClicked(m, button, dbl);
				return 1;
			}
		}
		if (button == 0 && overBack(m)) {
			return 2;
		}
		if (button == 0 && overMaster(m)) {
			module.toggle();
			host.sound(module.enabled() ? "on" : "off");
			return 1;
		}
		if (m.y >= viewportTop && m.y <= viewportTop + viewportHeight) {
			for (Row r : rows) {
				if (r.visible() && r.mouseClicked(m, button, dbl)) {
					active = r;
					tooltipRow = null;
					return 1;
				}
			}
		}
		return 0;
	}

	void mouseDragged(Mouse m) {
		if (active != null) {
			active.mouseDragged(m);
		}
	}

	void mouseReleased(Mouse m, int button) {
		if (active != null) {
			active.mouseReleased(m, button);
			active = null;
		}
	}

	boolean mouseScrolled(Mouse m, double amount) {
		for (Row r : rows) {
			if (r.visible() && r.mouseScrolled(m, amount)) {
				return true;
			}
		}
		scrollTarget -= (float) amount * 36f;
		return true;
	}

	boolean keyPressed(int key, int mods) {
		for (Row r : rows) {
			if (r.capturing()) {
				return r.keyPressed(key, mods);
			}
		}
		return false;
	}
}
