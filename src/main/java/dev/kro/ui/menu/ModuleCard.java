package dev.kro.ui.menu;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Spring;
import dev.kro.core.module.Module;
import dev.kro.core.util.Colors;
import dev.kro.ui.Mouse;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Icons;
import dev.kro.ui.render.Theme;
import dev.kro.ui.widget.Draw;

import java.util.List;

/** One module tile in the grid. Positions glide on springs when the grid is filtered. */
final class ModuleCard {
	static final float PAD = 16f;
	static final float ICON = 34f;

	final Module module;
	final Spring x = new Spring(0f, 190f, 25f);
	final Spring y = new Spring(0f, 190f, 25f);
	final Spring visible = new Spring(0f, 210f, 26f);
	final Spring on;
	final Anim hover = new Anim(0f, 15f);
	final Anim switchHover = new Anim(0f, 16f);
	final Spring press = new Spring(0f, 420f, 30f);
	boolean placed;
	boolean shown = true;
	float w;
	float h;
	/** Seconds after opening before this card may appear (stagger). */
	float delay;

	ModuleCard(Module module) {
		this.module = module;
		this.on = new Spring(module.enabled() && !module.alwaysOn() ? 1f : 0f, Spring.SMOOTH);
	}

	float cx() {
		return x.get();
	}

	float cy() {
		return y.get();
	}

	boolean contains(Mouse m) {
		return visible.get() > 0.5f && shown && m.in(x.get(), y.get(), w, h);
	}

	boolean overSwitch(Mouse m) {
		if (module.alwaysOn()) {
			return false;
		}
		float sx = x.get() + w - PAD - Draw.TOGGLE_W, sy = y.get() + PAD + (ICON - Draw.TOGGLE_H) / 2f;
		return m.in(sx - 6f, sy - 6f, Draw.TOGGLE_W + 12f, Draw.TOGGLE_H + 12f);
	}

	void render(Canvas c, Mouse m, boolean interactive) {
		float vis = Math.max(0f, Math.min(1f, visible.get()));
		if (vis <= 0.01f) {
			return;
		}
		float px = x.get(), py = y.get();
		boolean over = interactive && shown && m.in(px, py, w, h);
		float hv = hover.to(over).get();
		float sw = switchHover.to(over && overSwitch(m)).get();
		float onT = Math.max(0f, Math.min(1f, on.to(module.enabled() && !module.alwaysOn()).get()));
		float pr = press.get();

		c.push();
		c.alpha(vis);
		float lift = -2f * hv;
		c.translate(0f, lift + (1f - vis) * 8f);
		c.scale((0.96f + 0.04f * vis) * (1f - 0.015f * pr), px + w / 2f, py + h / 2f);

		float r = 12f;
		if (hv > 0.01f) {
			c.shadow(px, py + 8f, w, h, r, 22f, Colors.withAlpha(0x000000, 0.45f * hv));
		}
		int bg = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hv);
		c.rect(px, py, w, h, r, bg);
		if (onT > 0.01f) {
			c.rect(px, py, w, h * 0.55f, r, Theme.accent(0.075f * onT), Theme.accent(0.03f * onT), 0x00000000, 0x00000000);
		}
		int border = Colors.lerp(Colors.lerp(Theme.BORDER, Theme.BORDER_STRONG, hv), Theme.accent(0.38f), onT * 0.8f);
		c.outline(px, py, w, h, r, 1f, border, border, Colors.withAlpha(border, Colors.alpha(border) / 255f * 0.45f), Colors.withAlpha(border, Colors.alpha(border) / 255f * 0.45f));

		// icon tile
		float ix = px + PAD, iy = py + PAD;
		int tileOff = Colors.lerp(Theme.FIELD, 0x17FFFFFF, hv);
		int tile = Colors.lerp(tileOff, Theme.accent(), onT);
		int tile2 = Colors.lerp(tileOff, Theme.accentLight(), onT);
		c.rect(ix, iy, ICON, ICON, 10f, tile2, tile, tile, tile);
		if (onT > 0.01f) {
			c.outline(ix, iy, ICON, ICON, 10f, 1f, Colors.withAlpha(0xFFFFFF, 0.22f * onT), Colors.withAlpha(0xFFFFFF, 0.10f * onT), 0x00000000, 0x00000000);
		}
		int iconCol = Colors.lerp(Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), Theme.onAccent(), onT);
		c.icon(module.icon(), ix + ICON / 2f, iy + ICON / 2f, 15f, iconCol);

		// switch
		if (!module.alwaysOn()) {
			Draw.toggle(c, px + w - PAD - Draw.TOGGLE_W, iy + (ICON - Draw.TOGGLE_H) / 2f, onT, Math.max(sw, hv * 0.4f));
		}

		// text
		c.text(Fonts.semibold(), 10.5f, module.name(), px + PAD, py + 68f, Theme.TEXT);
		List<String> lines = c.wrap(Fonts.medium(), 7.5f, module.description(), w - PAD * 2f);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			String line = lines.get(i);
			if (i == 2 && lines.size() > 3) {
				line = c.ellipsize(Fonts.medium(), 7.5f, line + " …", w - PAD * 2f);
			}
			c.text(Fonts.medium(), 7.5f, line, px + PAD, py + 84f + i * 10.5f, Theme.TEXT_DIM);
		}

		// footer
		float fy = py + h - PAD - 4f;
		int cat = module.category().color();
		c.circle(px + PAD + 2.5f, fy, 2.5f, Colors.withAlpha(cat, 0.9f));
		Draw.overline(c, module.category().title(), px + PAD + 10f, fy, Colors.withAlpha(Colors.lerp(cat, 0xFFFFFFFF, 0.25f), 0.8f), Canvas.LEFT);
		float right = px + w - PAD;
		c.icon(Icons.CHEVRON_RIGHT, right - 4f, fy, 10f, Colors.lerp(Theme.TEXT_FAINT, Theme.TEXT_DIM, hv));
		if (module.bind().bound()) {
			Draw.pill(c, module.bind().label(), right - 14f, fy, Theme.FIELD, Theme.TEXT_DIM, Canvas.RIGHT);
		}
		c.pop();
	}
}
