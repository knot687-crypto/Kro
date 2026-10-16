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

/**
 * One module tile in the grid: name and switch on top, a short description, then category and
 * key bind. Over a window picture it is clear tinted glass showing exactly the picture behind it.
 * Positions glide on springs when the grid is filtered.
 */
final class ModuleCard {
	static final float PAD = 16f;
	private static final float RADIUS = 12f;
	/** Centre line of the name and switch row, from the card's top. */
	private static final float TITLE_Y = PAD + 8f;

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

	boolean contains(Mouse m) {
		return visible.get() > 0.5f && shown && m.in(x.get(), y.get(), w, h);
	}

	boolean overSwitch(Mouse m) {
		if (module.alwaysOn()) {
			return false;
		}
		float sx = x.get() + w - PAD - Draw.TOGGLE_W, sy = y.get() + TITLE_Y - Draw.TOGGLE_H / 2f;
		return m.in(sx - 6f, sy - 6f, Draw.TOGGLE_W + 12f, Draw.TOGGLE_H + 12f);
	}

	void render(Canvas c, Mouse m, boolean interactive, Glass glass) {
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

		float scale = (0.96f + 0.04f * vis) * (1f - 0.015f * pr);
		float lift = -2f * hv + (1f - vis) * 8f;
		c.push();
		c.alpha(vis);
		c.translate(0f, lift);
		c.scale(scale, px + w / 2f, py + h / 2f);

		if (glass.active()) {
			surfaceGlass(c, m, glass, px, py, scale, lift, hv, onT);
		} else {
			surfaceBlack(c, px, py, hv, onT);
		}

		// name and switch
		float ty = py + TITLE_Y;
		float nameMax = w - PAD * 2f - (module.alwaysOn() ? 0f : Draw.TOGGLE_W + 10f);
		c.text(Fonts.semibold(), 11f, c.ellipsize(Fonts.semibold(), 11f, module.name(), nameMax), px + PAD, ty, Theme.TEXT);
		if (!module.alwaysOn()) {
			Draw.toggle(c, px + w - PAD - Draw.TOGGLE_W, ty - Draw.TOGGLE_H / 2f, onT, Math.max(sw, hv * 0.4f));
		}

		// description
		List<String> lines = c.wrap(Fonts.medium(), 7.5f, module.description(), w - PAD * 2f);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			String line = lines.get(i);
			if (i == 2 && lines.size() > 3) {
				line = c.ellipsize(Fonts.medium(), 7.5f, line + " …", w - PAD * 2f);
			}
			c.text(Fonts.medium(), 7.5f, line, px + PAD, py + 45f + i * 10.5f, Theme.TEXT_DIM);
		}

		// footer: category, key bind, chevron
		float fy = py + h - PAD - 2f;
		int cat = module.category().color();
		c.circle(px + PAD + 2.5f, fy, 2.5f, Colors.withAlpha(cat, 0.95f));
		Draw.overline(c, module.category().title(), px + PAD + 10f, fy, Colors.withAlpha(Colors.lerp(cat, 0xFFFFFFFF, 0.3f), 0.85f), Canvas.LEFT);
		float right = px + w - PAD;
		c.icon(Icons.CHEVRON_RIGHT, right - 4f, fy, 10f, Colors.lerp(Theme.TEXT_FAINT, Theme.TEXT_DIM, hv));
		if (module.bind().bound()) {
			Draw.pill(c, module.bind().label(), right - 14f, fy, Theme.FIELD, Theme.TEXT_DIM, Canvas.RIGHT);
		}
		c.pop();
	}

	/** Plain black surface for the picture-less window. */
	private void surfaceBlack(Canvas c, float px, float py, float hv, float onT) {
		if (hv > 0.01f) {
			c.shadow(px, py + 8f, w, h, RADIUS, 22f, Colors.withAlpha(0x000000, 0.45f * hv));
		}
		c.rect(px, py, w, h, RADIUS, Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hv));
		if (onT > 0.01f) {
			c.rect(px, py, w, h * 0.55f, RADIUS, Theme.accent(0.075f * onT), Theme.accent(0.03f * onT), 0x00000000, 0x00000000);
		}
		int border = Colors.lerp(Colors.lerp(Theme.BORDER, Theme.BORDER_STRONG, hv), Theme.accent(0.38f), onT * 0.8f);
		int low = Colors.withAlpha(border, Colors.alpha(border) / 255f * 0.45f);
		c.outline(px, py, w, h, RADIUS, 1f, border, border, low, low);
	}

	/**
	 * Clear tinted glass: the picture behind the card, just as sharp with its highlights tamed,
	 * under a light smoke, a soft sheen from above, an accent bloom when on, and a hairline rim
	 * that brightens toward the cursor as if lit by it.
	 */
	private void surfaceGlass(Canvas c, Mouse m, Glass glass, float px, float py, float scale, float lift, float hv, float onT) {
		float sw = w * scale, sh = h * scale;
		float sx = px + (w - sw) / 2f, sy = py + (h - sh) / 2f + lift;
		c.shadow(px, py + 7f, w, h, RADIUS, 18f, Colors.withAlpha(0x000000, 0.34f + 0.16f * hv));
		if (onT > 0.01f) {
			// enabled cards give off a faint halo of the accent
			c.shadow(px, py, w, h, RADIUS, 16f, Theme.accent(0.16f * onT));
		}
		glass.fill(c, px, py, w, h, RADIUS, 0xFFFFFFFF, sx, sy, sw, sh);
		c.rect(px, py, w, h, RADIUS, Colors.withAlpha(0x000000, 0.24f - 0.08f * hv));
		c.rectV(px, py, w, h * 0.5f, RADIUS, Colors.withAlpha(0xFFFFFF, 0.05f + 0.03f * hv), 0x00FFFFFF);
		if (onT > 0.01f) {
			c.rect(px, py, w, h * 0.7f, RADIUS, Theme.accent(0.20f * onT), Theme.accent(0.07f * onT), 0x00000000, 0x00000000);
		}

		// rim light: each corner brightens with the cursor's closeness
		float reach = Math.max(w, h) * 1.1f;
		int tl = rim(m, px, py, reach, hv, onT, 0.24f);
		int tr = rim(m, px + w, py, reach, hv, onT, 0.24f);
		int br = rim(m, px + w, py + h, reach, hv, onT, 0.09f);
		int bl = rim(m, px, py + h, reach, hv, onT, 0.09f);
		c.outline(px, py, w, h, RADIUS, 1f, tl, tr, br, bl);
		// inner bevel: the glass has thickness
		c.outline(px + 1f, py + 1f, w - 2f, h - 2f, RADIUS - 1f, 1f, 0x12FFFFFF, 0x12FFFFFF, 0x00FFFFFF, 0x00FFFFFF);
	}

	private static int rim(Mouse m, float cx, float cy, float reach, float hv, float onT, float base) {
		float dx = m.x - cx, dy = m.y - cy;
		float near = Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy) / reach);
		float a = base + 0.30f * hv * near * near;
		int white = Colors.withAlpha(0xFFFFFF, Math.min(1f, a));
		return Colors.lerp(white, Theme.accent(Math.min(1f, a + 0.22f)), onT * 0.75f);
	}
}
