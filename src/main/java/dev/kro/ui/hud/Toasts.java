package dev.kro.ui.hud;

import dev.kro.core.anim.Clock;
import dev.kro.core.anim.Ease;
import dev.kro.core.anim.Spring;
import dev.kro.core.module.Module;
import dev.kro.core.util.Colors;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Theme;
import dev.kro.ui.widget.Draw;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small toggle notifications stacked in the top right corner. */
public final class Toasts {
	private static final float W = 168f;
	private static final float H = 36f;
	private static final float GAP = 6f;
	private static final float LIFE = 1.9f;

	private final List<Toast> toasts = new ArrayList<>();

	private static final class Toast {
		final Module module;
		boolean enabled;
		long born = Clock.nanos();
		final Spring enter = new Spring(0f, 230f, 24f);
		final Spring y = new Spring(0f, 220f, 26f);
		boolean placed;

		Toast(Module module, boolean enabled) {
			this.module = module;
			this.enabled = enabled;
		}

		float age() {
			return (Clock.nanos() - born) / 1.0e9f * Clock.speed();
		}
	}

	public void push(Module module, boolean enabled) {
		for (Toast t : toasts) {
			if (t.module == module && t.age() < LIFE) {
				t.enabled = enabled;
				t.born = Clock.nanos();
				t.enter.kick(1.5f);
				return;
			}
		}
		toasts.add(0, new Toast(module, enabled));
		if (toasts.size() > 5) {
			toasts.remove(toasts.size() - 1);
		}
	}

	public void render(Canvas c, float screenW) {
		float top = 10f;
		int index = 0;
		Iterator<Toast> it = toasts.iterator();
		while (it.hasNext()) {
			Toast t = it.next();
			float age = t.age();
			boolean alive = age < LIFE;
			t.enter.to(alive ? 1f : 0f);
			float e = Math.max(0f, Math.min(1f, t.enter.get()));
			if (!alive && e < 0.01f) {
				it.remove();
				continue;
			}
			float targetY = top + index * (H + GAP);
			if (!t.placed) {
				t.y.snap(targetY);
				t.placed = true;
			}
			float y = t.y.to(targetY).get();
			float x = screenW - W - 10f + (1f - Ease.outCubic(e)) * 30f;
			c.push();
			c.alpha(e);
			draw(c, t, x, y, age);
			c.pop();
			if (alive) {
				index++;
			}
		}
	}

	private void draw(Canvas c, Toast t, float x, float y, float age) {
		Draw.panel(c, x, y, W, H, 10f, 5f, 14f, 0x73000000);
		// a lit (or unlit) indicator instead of an icon
		float dx = x + 15f, dy = y + H / 2f - 1f;
		if (t.enabled) {
			c.glow(dx, dy, 3.5f, 6f, Theme.accent(0.55f));
			c.circle(dx, dy, 3.5f, Theme.accentLight(), Theme.accentLight(), Theme.accent(), Theme.accent());
		} else {
			c.ring(dx, dy, 3.5f, 1.2f, 0x6BFFFFFF);
		}
		c.text(Fonts.semibold(), 8.5f, t.module.name(), x + 28f, y + 13f, Theme.TEXT);
		c.text(Fonts.medium(), 7f, t.enabled ? "Enabled" : "Disabled", x + 28f, y + 24.5f, t.enabled ? Colors.lerp(Theme.accentLight(), 0xFFFFFFFF, 0.35f) : Theme.TEXT_DIM);
		float life = Math.max(0f, 1f - age / LIFE);
		c.rect(x + 10f, y + H - 3f, (W - 20f) * life, 1.5f, 0.75f, Colors.withAlpha(t.enabled ? Theme.accent() : 0xFFFFFFFF, 0.55f));
	}
}
