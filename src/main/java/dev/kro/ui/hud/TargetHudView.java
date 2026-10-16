package dev.kro.ui.hud;

import dev.kro.core.anim.Anim;
import dev.kro.core.anim.Clock;
import dev.kro.core.anim.Ease;
import dev.kro.core.anim.Spring;
import dev.kro.core.module.impl.TargetHud;
import dev.kro.core.util.Colors;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Fonts;
import dev.kro.ui.render.Icons;
import dev.kro.ui.render.Theme;
import dev.kro.ui.widget.Draw;

import java.util.Locale;

/**
 * The opponent card: avatar, animated health with a trailing damage segment, armor, distance
 * and the state of the duel. Pure drawing; the game layer supplies {@link TargetInfo}.
 */
public final class TargetHudView {
	public static final float CARD_W = 196f;
	public static final float CARD_H = 66f;
	public static final float COMPACT_W = 156f;
	public static final float COMPACT_H = 34f;

	private final Spring presence = new Spring(0f, 200f, 22f);
	private final Anim health = new Anim(0f, 14f);
	private final Anim healthText = new Anim(0f, 10f);
	private final Anim absorption = new Anim(0f, 12f);
	private final Anim ghost = new Anim(0f, 5f);
	private final Anim lead = new Anim(0.5f, 6f);
	private TargetInfo shown;
	private int lastId = Integer.MIN_VALUE;
	private float ghostHold;
	private long ghostSince;
	private long lostAt = -1L;

	/** Feed the current target (or null). Call once per frame before {@link #render}. */
	public void update(TargetInfo info) {
		long now = Clock.nanos();
		if (info != null) {
			lostAt = -1L;
			float frac = info.maxHealth <= 0f ? 0f : Math.max(0f, Math.min(1f, info.health / info.maxHealth));
			if (info.id != lastId) {
				lastId = info.id;
				health.snap(frac);
				healthText.snap(info.health);
				absorption.snap(info.absorption);
				ghost.snap(frac);
				ghostHold = frac;
				if (shown == null) {
					presence.snap(0f);
				}
			}
			if (frac < ghostHold - 0.001f && ghostSince == 0L) {
				ghostSince = now;
			}
			if (frac > ghostHold) {
				ghostHold = frac;
				ghost.snap(frac);
			}
			if (ghostSince != 0L && (now - ghostSince) / 1.0e9f > 0.38f) {
				ghostHold = frac;
				ghostSince = 0L;
			}
			ghost.to(ghostHold);
			health.to(frac);
			healthText.to(info.health);
			absorption.to(info.absorption);
			shown = info.copy();
			presence.to(1f);
		} else {
			if (lostAt < 0L) {
				lostAt = now;
			}
			if ((now - lostAt) / 1.0e9f > 1.1f) {
				presence.to(0f);
			}
		}
	}

	public boolean visible() {
		return presence.get() > 0.01f && shown != null;
	}

	public float width(TargetHud s) {
		return (s.style.is("Compact") ? COMPACT_W : CARD_W) * s.scale.floatValue();
	}

	public float height(TargetHud s) {
		return (s.style.is("Compact") ? COMPACT_H : CARD_H) * s.scale.floatValue();
	}

	/** Draws the card centred on (cx, cy) in GUI units. */
	public void render(Canvas c, float cx, float cy, TargetHud s) {
		float p = Math.max(0f, Math.min(1f, presence.get()));
		if (p <= 0.01f || shown == null) {
			return;
		}
		boolean compact = s.style.is("Compact");
		float w = compact ? COMPACT_W : CARD_W;
		float h = compact ? COMPACT_H : CARD_H;
		float scale = s.scale.floatValue();
		c.push();
		c.translate(cx, cy);
		c.scale(scale * (0.94f + 0.06f * Ease.outCubic(p)));
		c.translate(-w / 2f, -h / 2f + (1f - p) * 6f);
		c.alpha(p);
		if (compact) {
			renderCompact(c, s, w, h);
		} else {
			renderCard(c, s, w, h);
		}
		c.pop();
	}

	private int barColor(TargetHud s, float frac) {
		if (s.healthColor.is("Accent")) {
			return Theme.accent();
		}
		if (frac > 0.5f) {
			return Colors.lerp(Theme.WARN, Theme.GOOD, (frac - 0.5f) / 0.5f);
		}
		return Colors.lerp(Theme.BAD, Theme.WARN, frac / 0.5f);
	}

	private void surface(Canvas c, float w, float h, float r) {
		Draw.panel(c, 0f, 0f, w, h, r, 6f, 18f, 0x80000000);
		float hurt = shown.hurt;
		if (hurt > 0.01f) {
			c.outline(0f, 0f, w, h, r, 1.5f, Colors.withAlpha(Theme.BAD, 0.75f * hurt));
			c.rect(0f, 0f, w, h, r, Colors.withAlpha(Theme.BAD, 0.10f * hurt));
		}
	}

	private void renderCard(Canvas c, TargetHud s, float w, float h) {
		TargetInfo t = shown;
		surface(c, w, h, 12f);

		// avatar
		float av = 50f, ax = 8f, ay = 8f;
		float shake = t.hurt > 0.01f ? (float) Math.sin(Clock.seconds() * 70.0) * 1.2f * t.hurt : 0f;
		c.rect(ax, ay, av, av, 9f, 0xFF171718);
		if (s.avatar.is("Model") && t.model != null) {
			requestModel(c, t, ax, ay, av);
		} else if (t.face != null) {
			c.image(t.face, ax + shake, ay, av, av, 9f, 0xFFFFFFFF, t.faceU0, t.faceV0, t.faceU1, t.faceV1);
			c.image(t.face, ax + shake, ay, av, av, 9f, 0xFFFFFFFF, t.hatU0, t.hatV0, t.hatU1, t.hatV1);
		} else if (t.model != null) {
			requestModel(c, t, ax, ay, av);
		} else {
			c.icon(Icons.USER_ROUND, ax + av / 2f, ay + av / 2f, 20f, Theme.TEXT_DIM);
		}
		if (t.hurt > 0.01f) {
			c.rect(ax, ay, av, av, 9f, Colors.withAlpha(Theme.BAD, 0.38f * t.hurt));
		}
		c.outline(ax, ay, av, av, 9f, 1f, 0x1AFFFFFF);

		// name and distance
		float tx = ax + av + 10f;
		float right = w - 10f;
		float distW = 0f;
		if (s.distance.on()) {
			String d = String.format(Locale.ROOT, "%.1f m", t.distance);
			distW = c.text(Fonts.medium(), 7.5f, d, right, 18f, Theme.TEXT_DIM, Canvas.RIGHT) + 8f;
		}
		c.text(Fonts.semibold(), 10f, c.ellipsize(Fonts.semibold(), 10f, t.name, right - tx - distW), tx, 18f, Theme.TEXT);

		// health bar with trailing damage
		float bx = tx, by = 31f, bw = right - tx, bh = 6f;
		float hf = Math.max(0f, Math.min(1f, health.get()));
		float gf = Math.max(hf, Math.min(1f, ghost.get()));
		c.rect(bx, by, bw, bh, bh / 2f, 0x1CFFFFFF);
		if (gf > hf + 0.002f) {
			c.rect(bx, by, Math.max(bh, bw * gf), bh, bh / 2f, Colors.withAlpha(0xFFFFFF, 0.30f));
		}
		if (hf > 0.001f) {
			int col = barColor(s, hf);
			c.rect(bx, by, Math.max(bh, bw * hf), bh, bh / 2f, Colors.lighten(col, 0.12f), col, col, Colors.lighten(col, 0.12f));
		}
		float abs = absorption.get();
		if (abs > 0.05f && t.maxHealth > 0f) {
			float af = Math.min(1f, abs / t.maxHealth);
			c.rect(bx, by - 3.5f, Math.max(2f, bw * af), 2f, 1f, 0xFFF5C542);
		}

		// stats row
		float sy = 51f;
		float hp = Math.max(0f, healthText.get());
		String hpText = hp >= 10f || Math.abs(hp - Math.round(hp)) < 0.05f ? String.format(Locale.ROOT, "%.0f", hp) : String.format(Locale.ROOT, "%.1f", hp);
		c.icon(Icons.HEART, tx + 4f, sy, 9f, barColor(s, hf));
		float hw = c.text(Fonts.semibold(), 8.5f, hpText, tx + 12f, sy, Theme.TEXT);
		float cursor = tx + 12f + hw + 10f;
		if (s.armor.on()) {
			c.icon(Icons.SHIELD, cursor + 4f, sy, 9f, Theme.TEXT_DIM);
			c.text(Fonts.medium(), 8f, Integer.toString(t.armor), cursor + 12f, sy, Theme.TEXT_DIM);
		}
		if (s.duel.on()) {
			float mine = t.selfMax <= 0f ? 0f : t.selfHealth / t.selfMax;
			float theirs = t.maxHealth <= 0f ? 0f : t.health / t.maxHealth;
			float l = lead.to(Math.max(0f, Math.min(1f, 0.5f + (mine - theirs)))).get();
			String label;
			int col;
			if (l > 0.56f) {
				label = "Winning";
				col = Theme.GOOD;
			} else if (l < 0.44f) {
				label = "Losing";
				col = Theme.BAD;
			} else {
				label = "Even";
				col = Theme.TEXT_DIM;
			}
			float lw = c.textWidth(Fonts.semibold(), 7f, label) + 18f;
			float lx = right - lw;
			c.rect(lx, sy - 7f, lw, 14f, 7f, Colors.withAlpha(col, 0.14f));
			c.circle(lx + 7f, sy, 2.2f, col);
			c.text(Fonts.semibold(), 7f, label, lx + 12f, sy, Colors.lerp(col, 0xFFFFFFFF, 0.25f));
		}
	}

	private void renderCompact(Canvas c, TargetHud s, float w, float h) {
		TargetInfo t = shown;
		surface(c, w, h, 9f);
		float hp = Math.max(0f, healthText.get());
		String hpText = String.format(Locale.ROOT, hp >= 10f ? "%.0f" : "%.1f", hp);
		float hpW = c.text(Fonts.semibold(), 8.5f, hpText, w - 10f, 12f, Theme.TEXT, Canvas.RIGHT);
		c.icon(Icons.HEART, w - 10f - hpW - 7f, 12f, 8f, barColor(s, health.get()));
		c.text(Fonts.semibold(), 8.5f, c.ellipsize(Fonts.semibold(), 8.5f, t.name, w - 40f - hpW), 10f, 12f, Theme.TEXT);
		float bx = 10f, by = 22f, bw = w - 20f, bh = 4f;
		float hf = Math.max(0f, Math.min(1f, health.get()));
		float gf = Math.max(hf, Math.min(1f, ghost.get()));
		c.rect(bx, by, bw, bh, 2f, 0x1CFFFFFF);
		if (gf > hf + 0.002f) {
			c.rect(bx, by, Math.max(bh, bw * gf), bh, 2f, 0x4DFFFFFF);
		}
		if (hf > 0.001f) {
			c.rect(bx, by, Math.max(bh, bw * hf), bh, 2f, barColor(s, hf));
		}
	}

	private static void requestModel(Canvas c, TargetInfo t, float x, float y, float size) {
		c.custom(new AvatarRequest(t.model, c.screenX(x, y), c.screenY(x, y), c.screenX(x + size, y + size), c.screenY(x + size, y + size), c.currentAlpha()));
	}

	/** Asks the backend to draw a live entity model into a screen-space box (GUI units). */
	public record AvatarRequest(Object model, float x0, float y0, float x1, float y1, float alpha) {
	}
}
