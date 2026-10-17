package dev.kro.mc.fx;

import dev.kro.Kro;
import dev.kro.core.module.impl.JumpCircles;
import dev.kro.core.module.impl.KillEffects;
import dev.kro.core.module.impl.NoExplosions;
import dev.kro.core.module.impl.PopEffects;
import dev.kro.core.module.impl.TargetRing;
import dev.kro.core.module.impl.Trails;
import dev.kro.core.util.Colors;
import dev.kro.ui.render.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kro's world effects: bursts of light when totems pop, strikes and souls for kills, ripples under
 * jumping feet, a shockwave where explosions were cleared, a halo around your opponent and
 * ribbons of light along your path. Everything is simulated per frame for perfectly smooth motion
 * and drawn through {@link Fx}; it is purely visual and exists only on your screen.
 */
public final class WorldEffects {
	private static final long KILL_CREDIT_MS = 5000L;
	private static final long DEATH_DEDUPE_MS = 3000L;
	private static final double PLAYER_RANGE_SQ = 64.0 * 64.0;
	private static final int GOLD = 0xFFFFD45A;
	private static final int EMERALD = 0xFF62F59A;

	private final Kro kro;
	private final Fx fx = new Fx();
	private final List<Effect> effects = new ArrayList<>();
	private final Map<Integer, Long> attacked = new HashMap<>();
	private final Map<Integer, Long> deathShown = new HashMap<>();
	private final Set<Integer> dead = new HashSet<>();
	private final Map<Integer, Boolean> grounded = new HashMap<>();
	private final Map<Integer, Double> lastY = new HashMap<>();
	private final Map<Integer, Trail> trails = new HashMap<>();
	private final Halo halo = new Halo();
	private ClientLevel level;
	private float partialTick = 1f;
	private long lastFrame = System.nanoTime();
	private double clock;

	/** One running effect; returns false once it has fully faded. */
	private interface Effect {
		boolean draw(Fx fx, double t, float dt, float partialTick);
	}

	private abstract static class Timed implements Effect {
		double age;

		abstract double life();

		abstract void draw(Fx fx, double t, float dt, float partialTick, double age);

		@Override
		public final boolean draw(Fx fx, double t, float dt, float partialTick) {
			age += dt;
			if (age >= life()) {
				return false;
			}
			draw(fx, t, dt, partialTick, age);
			return true;
		}
	}

	public WorldEffects(Kro kro) {
		this.kro = kro;
	}

	// ------------------------------------------------------------------ events

	/** Called by the attack hook: credits you with this entity for a few seconds. */
	public void onAttack(Entity target) {
		attacked.put(target.getId(), System.currentTimeMillis());
	}

	public boolean replacesTotemParticles(Entity entity) {
		PopEffects m = kro.modules.popEffects;
		return m.enabled() && m.replaceParticles.on() && popShownFor(m, entity);
	}

	private boolean popShownFor(PopEffects m, Entity entity) {
		boolean self = entity == Minecraft.getInstance().player;
		return m.who.is("Everyone") || (m.who.is("Me") == self);
	}

	public void onTotemPop(Entity entity) {
		PopEffects m = kro.modules.popEffects;
		if (m.enabled() && popShownFor(m, entity) && entity instanceof LivingEntity living) {
			int a, b;
			switch (m.palette.get()) {
				case "Accent" -> {
					a = Theme.accent();
					b = Theme.accentLight();
				}
				case "Custom" -> {
					a = 0xFF000000 | m.customColor.color();
					b = Colors.lerp(a, 0xFFFFFFFF, 0.45f);
				}
				default -> {
					a = GOLD;
					b = EMERALD;
				}
			}
			effects.add(new PopBurst(living, a, b, m.scale.floatValue()));
		}
	}

	public void onDeath(Entity entity) {
		KillEffects m = kro.modules.killEffects;
		if (!m.enabled() || !(entity instanceof LivingEntity living)) {
			return;
		}
		long now = System.currentTimeMillis();
		Long shown = deathShown.get(entity.getId());
		if (shown != null && now - shown < DEATH_DEDUPE_MS) {
			return;
		}
		Long hit = attacked.get(entity.getId());
		boolean mine = hit != null && now - hit < KILL_CREDIT_MS;
		boolean counts = m.targets.is("All players") ? entity instanceof Player || mine : mine;
		if (!counts || entity == Minecraft.getInstance().player) {
			return;
		}
		deathShown.put(entity.getId(), now);
		int color = m.palette.is("Custom") ? 0xFF000000 | m.customColor.color() : Theme.accent();
		Vec3 feet = living.position();
		switch (m.style.get()) {
			case "Soul" -> effects.add(new SoulRise(feet, living.getBbWidth(), living.getBbHeight(), color));
			case "Shockwave" -> effects.add(new Shockwave(feet, living.getBbHeight(), color));
			default -> {
				effects.add(new Lightning(feet, color));
				if (m.sound.on() && level != null) {
					ThreadLocalRandom r = ThreadLocalRandom.current();
					level.playLocalSound(feet.x, feet.y, feet.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.6f, 0.8f + r.nextFloat() * 0.2f, false);
					level.playLocalSound(feet.x, feet.y, feet.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.0f, 0.5f + r.nextFloat() * 0.2f, false);
				}
			}
		}
	}

	public void onExplosion(Vec3 center, float radius) {
		NoExplosions m = kro.modules.noExplosions;
		if (m.enabled() && m.shockwave.on()) {
			effects.add(new BlastWave(center, radius));
		}
	}

	// ------------------------------------------------------------------ ticking

	/** End of every client tick: jumps, deaths and bookkeeping. */
	public void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			effects.clear();
			trails.clear();
			grounded.clear();
			lastY.clear();
			dead.clear();
			halo.clear();
		}
		LocalPlayer self = mc.player;
		if (level == null || self == null) {
			return;
		}
		long now = System.currentTimeMillis();
		attacked.values().removeIf(t -> now - t > KILL_CREDIT_MS * 2);
		deathShown.values().removeIf(t -> now - t > DEATH_DEDUPE_MS * 2);

		JumpCircles jc = kro.modules.jumpCircles;
		Set<Integer> seen = new HashSet<>();
		for (Player p : level.players()) {
			if (p.distanceToSqr(self) > PLAYER_RANGE_SQ) {
				continue;
			}
			int id = p.getId();
			seen.add(id);
			// deaths, in case the death event never arrives
			boolean isDead = p.isDeadOrDying();
			if (isDead && dead.add(id)) {
				onDeath(p);
			} else if (!isDead) {
				dead.remove(id);
			}
			// jumps and landings
			boolean on = p.onGround();
			Boolean was = grounded.put(id, on);
			Double py = lastY.put(id, p.getY());
			if (was == null || !jc.enabled() || p.isSpectator() || (p != self && !jc.everyone.on())) {
				continue;
			}
			double rise = py == null ? 0.0 : p.getY() - py;
			if (was && !on && (p.getDeltaMovement().y > 0.2 || rise > 0.08)) {
				effects.add(new Ripple(p.position(), jc, false));
			} else if (!was && on && jc.onLand.on()) {
				effects.add(new Ripple(p.position(), jc, true));
			}
		}
		grounded.keySet().retainAll(seen);
		lastY.keySet().retainAll(seen);
		dead.retainAll(seen);
	}

	// ------------------------------------------------------------------ rendering

	public void capture(float partialTick) {
		this.partialTick = partialTick;
	}

	/** World render hook, drawn after the opaque world. */
	public void render(WorldRenderContext ctx) {
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		clock += dt;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		fx.begin(ctx.worldState().cameraRenderState.pos);
		float pt = partialTick;

		TargetRing ring = kro.modules.targetRing;
		halo.draw(fx, ring, ring.enabled() ? kro.targets.current() : null, dt, pt, clock);
		drawTrails(mc, dt, pt);

		Iterator<Effect> it = effects.iterator();
		while (it.hasNext()) {
			if (!it.next().draw(fx, clock, dt, pt)) {
				it.remove();
			}
		}
		while (effects.size() > 64) {
			effects.remove(0);
		}
		fx.flush(ctx.matrices().last().pose(), ctx.consumers());
	}

	// ------------------------------------------------------------------ colours and easing

	static int palette(String palette, int custom, double t, double clock) {
		return switch (palette) {
			case "Rainbow" -> Colors.hsv((float) (((t * 0.85 + clock * 0.12) % 1.0 + 1.0) % 1.0), 0.72f, 1f);
			case "Custom" -> Colors.lerp(0xFF000000 | custom, 0xFFFFFFFF, (float) (0.32 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0))));
			default -> Colors.lerp(Theme.accent(), Theme.accentLight(), (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
		};
	}

	static double outCubic(double p) {
		p = clamp01(p);
		double q = 1.0 - p;
		return 1.0 - q * q * q;
	}

	static double outQuint(double p) {
		p = clamp01(p);
		double q = 1.0 - p;
		return 1.0 - q * q * q * q * q;
	}

	static double clamp01(double v) {
		return v < 0.0 ? 0.0 : Math.min(v, 1.0);
	}

	/** Soft in, long tail out: 0 at both ends, 1 at {@code peak}. */
	static double pulse(double p, double peak) {
		p = clamp01(p);
		return p < peak ? Math.sin(p / peak * Math.PI * 0.5) : Math.pow(1.0 - (p - peak) / (1.0 - peak), 1.6);
	}

	// ------------------------------------------------------------------ sparks shared by bursts

	/** A small physical spark set: streaks of light thrown out under gravity. */
	private static final class Sparks {
		final double[] x;
		final double[] y;
		final double[] z;
		final double[] vx;
		final double[] vy;
		final double[] vz;
		final double[] life;
		final int[] color;
		final double width;
		double age;

		Sparks(Vec3 origin, int count, double speed, double up, double width, double minLife, double maxLife, int a, int b) {
			ThreadLocalRandom r = ThreadLocalRandom.current();
			x = new double[count];
			y = new double[count];
			z = new double[count];
			vx = new double[count];
			vy = new double[count];
			vz = new double[count];
			life = new double[count];
			color = new int[count];
			this.width = width;
			for (int i = 0; i < count; i++) {
				double gx = r.nextGaussian(), gy = r.nextGaussian() * 0.6 + up, gz = r.nextGaussian();
				double len = Math.sqrt(gx * gx + gy * gy + gz * gz);
				double s = speed * (0.55 + r.nextDouble() * 0.7);
				x[i] = origin.x;
				y[i] = origin.y;
				z[i] = origin.z;
				vx[i] = gx / len * s;
				vy[i] = gy / len * s;
				vz[i] = gz / len * s;
				life[i] = minLife + r.nextDouble() * (maxLife - minLife);
				color[i] = Fx.lerpColor(a, b, r.nextFloat());
			}
		}

		void draw(Fx fx, float dt, double brightness) {
			age += dt;
			double drag = Math.exp(-2.4 * dt);
			for (int i = 0; i < x.length; i++) {
				double p = age / life[i];
				if (p >= 1.0) {
					continue;
				}
				vy[i] -= 9.5 * dt;
				vx[i] *= drag;
				vy[i] *= drag;
				vz[i] *= drag;
				x[i] += vx[i] * dt;
				y[i] += vy[i] * dt;
				z[i] += vz[i] * dt;
				double sp = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
				double len = Math.max(0.05, Math.min(0.5, sp * 0.055)) * Math.sqrt(1.0 - p);
				double nx = sp < 1.0e-4 ? 0.0 : vx[i] / sp, ny = sp < 1.0e-4 ? 1.0 : vy[i] / sp, nz = sp < 1.0e-4 ? 0.0 : vz[i] / sp;
				fx.streak(x[i] - nx * len, y[i] - ny * len, z[i] - nz * len, x[i], y[i], z[i], width * (1.0 - 0.4 * p),
						Fx.fade(color[i], Math.pow(1.0 - p, 1.3) * brightness));
			}
		}
	}

	// ------------------------------------------------------------------ totem pop

	/** Ground shockwaves, a pillar of light, a rising double helix of motes, a halo and sparks. */
	private static final class PopBurst extends Timed {
		private static final int MOTES = 26;
		final LivingEntity entity;
		final int a;
		final int b;
		final double scale;
		final double height;
		final double width;
		final Vec3 origin;
		Vec3 last;
		final Sparks sparks;
		final double spin = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);

		PopBurst(LivingEntity entity, int a, int b, double scale) {
			this.entity = entity;
			this.a = a;
			this.b = b;
			this.scale = scale;
			this.height = Math.max(0.6, entity.getBbHeight());
			this.width = Math.max(0.4, entity.getBbWidth());
			this.origin = entity.position();
			this.last = origin;
			this.sparks = new Sparks(origin.add(0.0, height * 0.55, 0.0), 18, 5.2 * scale, 0.9, 0.03 * scale, 0.5, 0.95, a, b);
		}

		@Override
		double life() {
			return 1.9;
		}

		@Override
		void draw(Fx fx, double t, float dt, float pt, double age) {
			if (!entity.isRemoved()) {
				last = entity.getPosition(pt);
			}
			double gx = origin.x, gy = origin.y + 0.03, gz = origin.z;

			// shockwaves on the ground where it happened
			double p1 = age / 0.95;
			if (p1 < 1.0) {
				double r = (0.35 + 2.5 * outQuint(p1)) * scale;
				double w = (0.24 * (1.0 - p1) + 0.05) * scale;
				double f = Math.pow(1.0 - p1, 1.5);
				fx.groundRing(gx, gy, gz, r, w, Fx.fade(a, f), Fx.fade(b, f), spin + age * 2.4);
			}
			double p2 = (age - 0.14) / 0.85;
			if (p2 > 0.0 && p2 < 1.0) {
				double r = (0.25 + 1.75 * outCubic(p2)) * scale;
				double f = Math.pow(1.0 - p2, 2.0) * 0.8;
				fx.groundRing(gx, gy, gz, r, (0.1 * (1.0 - p2) + 0.03) * scale, Fx.fade(b, f), Fx.fade(a, f), -spin - age * 3.0);
			}

			// a pillar of light that flares and fades
			double pp = age / 0.65;
			if (pp < 1.0) {
				double f = pulse(pp, 0.12) * 0.6;
				double w = (0.5 * (1.0 - pp) + 0.12) * scale;
				fx.beam(last.x, last.y, last.z, last.x, last.y + 3.4 * scale, last.z, w, w * 0.35, Fx.fade(Fx.lerpColor(a, 0xFFFFFFFF, 0.35f), f), Fx.fade(b, 0.0));
			}

			// a double helix of motes winding up around them
			for (int i = 0; i < MOTES; i++) {
				double delay = (double) i / MOTES * 0.45;
				double q = (age - delay) / 1.15;
				if (q <= 0.0 || q >= 1.0) {
					continue;
				}
				double strand = (i % 2) * Math.PI;
				double ang = spin + strand + (double) i / MOTES * Math.PI * 4.0 + q * 3.4;
				double rad = (width * 0.75 + 0.2 + 0.35 * q) * scale;
				double y = last.y + (0.08 + (height + 0.9) * outCubic(q)) * scale;
				double f = Math.pow(Math.sin(q * Math.PI), 0.8);
				int col = Fx.lerpColor(a, b, (float) i / MOTES);
				double size = (0.075 + 0.04 * Math.sin(q * Math.PI)) * scale;
				fx.orb(last.x + Math.cos(ang) * rad, y, last.z + Math.sin(ang) * rad, size, Fx.fade(col, f));
				for (int k = 1; k <= 3; k++) {
					double ta = ang - k * 0.2, ty = y - k * 0.07 * scale;
					fx.orb(last.x + Math.cos(ta) * rad, ty, last.z + Math.sin(ta) * rad, size * (1.0 - k * 0.16), Fx.fade(col, f * (0.5 / k)));
				}
			}

			// a halo blooming at head height
			double ph = age / 0.5;
			if (ph < 1.0) {
				fx.halo(last.x, last.y + height * 0.92, last.z, (0.3 + 1.3 * outCubic(ph)) * scale, Fx.fade(a, Math.pow(1.0 - ph, 2.0) * 0.9));
			}
			sparks.draw(fx, dt, 1.0);
		}
	}

	// ------------------------------------------------------------------ kills

	/** A forked bolt from the sky that strikes twice, with an impact flash and a scorch ring. */
	private static final class Lightning extends Timed {
		final Vec3 ground;
		final int color;
		final List<double[]> bolts = new ArrayList<>();
		final Sparks sparks;
		boolean restruck;

		Lightning(Vec3 ground, int color) {
			this.ground = ground;
			this.color = color;
			this.sparks = new Sparks(ground.add(0.0, 0.15, 0.0), 22, 6.0, 0.7, 0.028, 0.35, 0.8, 0xFFFFFFFF, Fx.lerpColor(color, 0xFFFFFFFF, 0.5f));
			strike();
		}

		private void strike() {
			bolts.clear();
			ThreadLocalRandom r = ThreadLocalRandom.current();
			double tx = ground.x + r.nextGaussian() * 2.0, tz = ground.z + r.nextGaussian() * 2.0;
			double[] main = jagged(tx, ground.y + 36.0, tz, ground.x, ground.y, ground.z, 7, 0.24, r);
			bolts.add(main);
			int points = main.length / 3;
			for (int k = 0; k < 3; k++) {
				int at = (int) (points * (0.12 + r.nextDouble() * 0.55));
				double sx = main[at * 3], sy = main[at * 3 + 1], sz = main[at * 3 + 2];
				double len = 4.0 + r.nextDouble() * 6.0;
				double ang = r.nextDouble() * Math.PI * 2.0;
				double ex = sx + Math.cos(ang) * len * 0.6, ey = sy - len * 0.8, ez = sz + Math.sin(ang) * len * 0.6;
				bolts.add(jagged(sx, sy, sz, ex, ey, ez, 5, 0.3, r));
			}
		}

		/** Midpoint displacement: a straight line split again and again, each midpoint nudged aside. */
		private static double[] jagged(double x0, double y0, double z0, double x1, double y1, double z1, int depth, double rough, ThreadLocalRandom r) {
			int n = (1 << depth) + 1;
			double[] p = new double[n * 3];
			p[0] = x0;
			p[1] = y0;
			p[2] = z0;
			p[(n - 1) * 3] = x1;
			p[(n - 1) * 3 + 1] = y1;
			p[(n - 1) * 3 + 2] = z1;
			double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
			double amp = len * rough;
			for (int step = n - 1; step > 1; step /= 2) {
				int half = step / 2;
				for (int i = 0; i + step < n; i += step) {
					int m = i + half;
					for (int c = 0; c < 3; c++) {
						p[m * 3 + c] = (p[i * 3 + c] + p[(i + step) * 3 + c]) * 0.5;
					}
					p[m * 3] += r.nextGaussian() * amp * 0.5;
					p[m * 3 + 1] += r.nextGaussian() * amp * 0.18;
					p[m * 3 + 2] += r.nextGaussian() * amp * 0.5;
				}
				amp *= 0.52;
			}
			return p;
		}

		@Override
		double life() {
			return 1.0;
		}

		/** Strike, a dark beat, a second strike, then a fading afterglow. */
		private static double flicker(double age) {
			if (age < 0.09) {
				return 1.0;
			}
			if (age < 0.13) {
				return 0.15;
			}
			if (age < 0.3) {
				return 1.0;
			}
			return Math.max(0.0, 1.0 - (age - 0.3) / 0.22);
		}

		@Override
		void draw(Fx fx, double t, float dt, float pt, double age) {
			if (age >= 0.13 && !restruck) {
				restruck = true;
				strike();
			}
			double f = flicker(age);
			if (f > 0.01) {
				int glow = Fx.lerpColor(color, 0xFFFFFFFF, 0.25f);
				int mid = Fx.lerpColor(color, 0xFFFFFFFF, 0.65f);
				for (int bi = 0; bi < bolts.size(); bi++) {
					double[] p = bolts.get(bi);
					double scale = bi == 0 ? 1.0 : 0.55;
					double bright = bi == 0 ? 1.0 : 0.7;
					int n = p.length / 3;
					for (int i = 0; i < n - 1; i++) {
						double x0 = p[i * 3], y0 = p[i * 3 + 1], z0 = p[i * 3 + 2];
						double x1 = p[i * 3 + 3], y1 = p[i * 3 + 4], z1 = p[i * 3 + 5];
						fx.beam(x0, y0, z0, x1, y1, z1, 0.5 * scale, 0.5 * scale, Fx.fade(glow, 0.32 * f * bright), Fx.fade(glow, 0.32 * f * bright));
						fx.beam(x0, y0, z0, x1, y1, z1, 0.17 * scale, 0.17 * scale, Fx.fade(mid, 0.65 * f * bright), Fx.fade(mid, 0.65 * f * bright));
						fx.beam(x0, y0, z0, x1, y1, z1, 0.05 * scale, 0.05 * scale, Fx.fade(0xFFFFFFFF, f * bright), Fx.fade(0xFFFFFFFF, f * bright));
					}
				}
			}
			// impact: a white flash, a scorch ring racing outward and a spray of sparks
			double pf = age / 0.4;
			if (pf < 1.0) {
				fx.orb(ground.x, ground.y + 0.4, ground.z, 1.8 * (1.0 - 0.6 * pf), Fx.fade(Fx.lerpColor(color, 0xFFFFFFFF, 0.6f), Math.pow(1.0 - pf, 2.0)));
			}
			double pr = age / 0.75;
			if (pr < 1.0) {
				double f2 = Math.pow(1.0 - pr, 1.6);
				fx.groundRing(ground.x, ground.y + 0.03, ground.z, 0.4 + 3.0 * outQuint(pr), 0.28 * (1.0 - pr) + 0.05,
						Fx.fade(0xFFFFFFFF, f2), Fx.fade(color, f2), age * 3.0);
			}
			sparks.draw(fx, dt, 1.0);
		}
	}

	/** Wisps of light lift out of the body and drift upward, under a halo that rises and fades. */
	private static final class SoulRise extends Timed {
		private static final int WISPS = 18;
		final Vec3 feet;
		final double height;
		final int color;
		final double[] ox = new double[WISPS];
		final double[] oy = new double[WISPS];
		final double[] oz = new double[WISPS];
		final double[] speed = new double[WISPS];
		final double[] sway = new double[WISPS];
		final double[] phase = new double[WISPS];
		final double[] delay = new double[WISPS];

		SoulRise(Vec3 feet, double width, double height, int color) {
			this.feet = feet;
			this.height = Math.max(0.6, height);
			this.color = color;
			ThreadLocalRandom r = ThreadLocalRandom.current();
			for (int i = 0; i < WISPS; i++) {
				double ang = r.nextDouble() * Math.PI * 2.0, rad = Math.sqrt(r.nextDouble()) * Math.max(0.25, width * 0.55);
				ox[i] = Math.cos(ang) * rad;
				oz[i] = Math.sin(ang) * rad;
				oy[i] = r.nextDouble() * this.height * 0.9;
				speed[i] = 0.75 + r.nextDouble() * 0.7;
				sway[i] = 1.4 + r.nextDouble() * 1.4;
				phase[i] = r.nextDouble() * Math.PI * 2.0;
				delay[i] = r.nextDouble() * 0.5;
			}
		}

		@Override
		double life() {
			return 2.4;
		}

		@Override
		void draw(Fx fx, double t, float dt, float pt, double age) {
			int light = Fx.lerpColor(color, 0xFFFFFFFF, 0.55f);
			// a faint column of light
			double pc = age / 1.4;
			if (pc < 1.0) {
				double f = pulse(pc, 0.2) * 0.35;
				fx.beam(feet.x, feet.y, feet.z, feet.x, feet.y + height + 2.8, feet.z, 0.55, 0.15, Fx.fade(color, f), Fx.fade(color, 0.0));
			}
			// the wisps
			for (int i = 0; i < WISPS; i++) {
				double a = age - delay[i];
				if (a <= 0.0) {
					continue;
				}
				double q = a / (life() - delay[i]);
				if (q >= 1.0) {
					continue;
				}
				double f = Math.min(1.0, a / 0.25) * Math.pow(1.0 - q, 1.2);
				double y = feet.y + oy[i] + a * speed[i];
				double sx = Math.sin(a * sway[i] + phase[i]) * 0.22, sz = Math.cos(a * sway[i] * 0.8 + phase[i]) * 0.22;
				double x = feet.x + ox[i] + sx, z = feet.z + oz[i] + sz;
				fx.orb(x, y, z, 0.11, Fx.fade(light, f));
				for (int k = 1; k <= 4; k++) {
					double ka = Math.max(0.0, a - k * 0.06);
					double ky = feet.y + oy[i] + ka * speed[i];
					double kx = feet.x + ox[i] + Math.sin(ka * sway[i] + phase[i]) * 0.22;
					double kz = feet.z + oz[i] + Math.cos(ka * sway[i] * 0.8 + phase[i]) * 0.22;
					fx.orb(kx, ky, kz, 0.11 * (1.0 - k * 0.15), Fx.fade(color, f * 0.45 / k));
				}
			}
			// a halo above the head that floats up and fades
			double ph = age / 1.8;
			if (ph < 1.0) {
				double f = pulse(ph, 0.15) * 0.9;
				double y = feet.y + height + 0.15 + 1.1 * outCubic(ph);
				fx.groundRing(feet.x, y, feet.z, 0.32 + 0.12 * ph, 0.07, Fx.fade(light, f), Fx.fade(color, f), age * 2.0);
			}
			double pr = age / 0.9;
			if (pr < 1.0) {
				double f = Math.pow(1.0 - pr, 1.8) * 0.7;
				fx.groundRing(feet.x, feet.y + 0.03, feet.z, 0.3 + 1.6 * outCubic(pr), 0.12 * (1.0 - pr) + 0.03, Fx.fade(color, f), Fx.fade(light, f), -age * 2.0);
			}
		}
	}

	/** Three rings racing outward, a flash at the chest and a ring of sparks. */
	private static final class Shockwave extends Timed {
		final Vec3 feet;
		final double height;
		final int color;
		final Sparks sparks;

		Shockwave(Vec3 feet, double height, int color) {
			this.feet = feet;
			this.height = Math.max(0.6, height);
			this.color = color;
			this.sparks = new Sparks(feet.add(0.0, this.height * 0.5, 0.0), 30, 7.0, 0.25, 0.03, 0.35, 0.75, color, 0xFFFFFFFF);
		}

		@Override
		double life() {
			return 1.1;
		}

		@Override
		void draw(Fx fx, double t, float dt, float pt, double age) {
			int light = Fx.lerpColor(color, 0xFFFFFFFF, 0.5f);
			double[] delays = {0.0, 0.09, 0.18};
			double[] reach = {3.6, 2.7, 1.9};
			for (int k = 0; k < 3; k++) {
				double p = (age - delays[k]) / 0.8;
				if (p <= 0.0 || p >= 1.0) {
					continue;
				}
				double f = Math.pow(1.0 - p, 1.5) * (1.0 - k * 0.2);
				fx.groundRing(feet.x, feet.y + 0.03, feet.z, 0.3 + reach[k] * outQuint(p), 0.26 * (1.0 - p) + 0.04,
						Fx.fade(k % 2 == 0 ? light : color, f), Fx.fade(k % 2 == 0 ? color : light, f), age * (k % 2 == 0 ? 2.5 : -2.5));
			}
			double ph = age / 0.4;
			if (ph < 1.0) {
				fx.halo(feet.x, feet.y + height * 0.5, feet.z, 0.4 + 1.6 * outCubic(ph), Fx.fade(light, Math.pow(1.0 - ph, 2.0)));
				fx.orb(feet.x, feet.y + height * 0.5, feet.z, 0.9 * (1.0 - ph), Fx.fade(0xFFFFFFFF, Math.pow(1.0 - ph, 2.0) * 0.8));
			}
			sparks.draw(fx, dt, 1.0);
		}
	}

	// ------------------------------------------------------------------ jumps and blasts

	/** A ring of light rippling out from a player's feet. */
	private static final class Ripple extends Timed {
		final Vec3 feet;
		final String palette;
		final int custom;
		final double size;
		final boolean landing;
		final double phase = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);

		Ripple(Vec3 feet, JumpCircles m, boolean landing) {
			this.feet = feet;
			this.palette = m.palette.get();
			this.custom = m.customColor.color();
			this.size = m.size.value() * (landing ? 0.75 : 1.0);
			this.landing = landing;
		}

		@Override
		double life() {
			return 0.8;
		}

		@Override
		void draw(Fx fx, double t, float dt, float pt, double age) {
			double p = age / life();
			double f = Math.pow(1.0 - p, 1.4);
			int a = palette(palette, custom, 0.0, t), b = palette(palette, custom, 0.5, t);
			double y = feet.y + 0.025;
			fx.groundRing(feet.x, y, feet.z, (0.15 + 1.0 * outCubic(p)) * size, (0.15 * (1.0 - p) + 0.035) * size,
					Fx.fade(a, f), Fx.fade(b, f), phase + age * 3.0);
			double p2 = (age - 0.06) / (life() - 0.06);
			if (p2 > 0.0 && !landing) {
				double f2 = Math.pow(1.0 - p2, 1.8) * 0.5;
				fx.groundRing(feet.x, y, feet.z, (0.1 + 0.62 * outCubic(p2)) * size, (0.08 * (1.0 - p2) + 0.025) * size,
						Fx.fade(b, f2), Fx.fade(a, f2), -phase - age * 3.0);
			}
		}
	}

	/** Where No Explosions removed a blast: a crisp ring sweeps out and a flash blooms. */
	private static final class BlastWave extends Timed {
		final Vec3 center;
		final double reach;

		BlastWave(Vec3 center, float radius) {
			this.center = center;
			this.reach = Math.max(1.5, Math.min(10.0, radius * 0.9));
		}

		@Override
		double life() {
			return 0.95;
		}

		@Override
		void draw(Fx fx, double t, float dt, float pt, double age) {
			int warm = 0xFFFFE7C4, ember = 0xFFFF9A5A;
			double p = age / life();
			double f = Math.pow(1.0 - p, 1.5) * 0.85;
			fx.groundRing(center.x, center.y + 0.04, center.z, 0.4 + reach * outQuint(p), 0.3 * (1.0 - p) + 0.05,
					Fx.fade(warm, f), Fx.fade(ember, f), age * 2.0);
			double ph = age / 0.35;
			if (ph < 1.0) {
				fx.halo(center.x, center.y + 0.6, center.z, 0.6 + reach * 0.55 * outCubic(ph), Fx.fade(warm, Math.pow(1.0 - ph, 2.0) * 0.8));
			}
			double pf = age / 0.18;
			if (pf < 1.0) {
				fx.orb(center.x, center.y + 0.5, center.z, 1.3, Fx.fade(0xFFFFFFFF, Math.pow(1.0 - pf, 2.0) * 0.75));
			}
		}
	}

	// ------------------------------------------------------------------ target halo

	/** The ring (or orbiting lights) around your current opponent, fading in and out smoothly. */
	private static final class Halo {
		LivingEntity target;
		double presence;

		void clear() {
			target = null;
			presence = 0.0;
		}

		void draw(Fx fx, TargetRing m, LivingEntity current, float dt, float pt, double clock) {
			// a new opponent takes over once the old ring has faded out
			if (current != target && presence < 0.05) {
				target = current;
			}
			boolean show = target != null && target == current && target.isAlive() && !target.isRemoved();
			presence += ((show ? 1.0 : 0.0) - presence) * Math.min(1.0, dt * 7.0);
			if (target == null || presence < 0.01) {
				return;
			}
			if (target.isRemoved()) {
				target = null;
				presence = 0.0;
				return;
			}
			Vec3 pos = target.getPosition(pt);
			double h = Math.max(0.5, target.getBbHeight()), w = Math.max(0.3, target.getBbWidth());
			double radius = w * 0.82 + 0.1;
			double speed = m.speed.value();
			float hurt = target.hurtTime > 0 && m.hurtFlash.on() ? target.hurtTime / 10f : 0f;
			String palette = m.palette.get();
			int custom = m.customColor.color();
			if (m.style.is("Orbit")) {
				for (int k = 0; k < 3; k++) {
					int col = flash(palette(palette, custom, k / 3.0, clock), hurt);
					for (int j = 0; j <= 10; j++) {
						double tt = clock - j * 0.035;
						double ang = tt * speed * 2.6 + k * Math.PI * 2.0 / 3.0;
						double y = pos.y + h * (0.22 + 0.3 * k) + 0.12 * Math.sin(tt * 2.0 + k * 1.7);
						double f = presence * (j == 0 ? 1.0 : 0.55 * Math.pow(1.0 - j / 11.0, 1.5));
						fx.orb(pos.x + Math.cos(ang) * radius, y, pos.z + Math.sin(ang) * radius, 0.12 * (1.0 - j * 0.06), Fx.fade(col, f));
					}
				}
				fx.groundRing(pos.x, pos.y + 0.03, pos.z, radius, 0.05, Fx.fade(flash(palette(palette, custom, 0.0, clock), hurt), presence * 0.6),
						Fx.fade(flash(palette(palette, custom, 0.5, clock), hurt), presence * 0.6), clock * 1.5);
				return;
			}
			for (int j = 7; j >= 0; j--) {
				double tt = clock - j * 0.045;
				double s = 0.5 - 0.5 * Math.cos(tt * speed * 2.3);
				double y = pos.y + 0.08 + s * (h - 0.16);
				double f = presence * (j == 0 ? 1.0 : 0.5 * Math.pow(1.0 - j / 8.0, 2.0));
				double width = j == 0 ? 0.09 : 0.06 * (1.0 - j / 9.0);
				int a = flash(palette(palette, custom, 0.0, clock), hurt), b = flash(palette(palette, custom, 0.5, clock), hurt);
				fx.groundRing(pos.x, y, pos.z, radius, width, Fx.fade(a, f), Fx.fade(b, f), clock * 1.6);
			}
		}

		private static int flash(int color, float hurt) {
			return hurt <= 0f ? color : Fx.lerpColor(color, Theme.BAD, hurt * 0.85f);
		}
	}

	// ------------------------------------------------------------------ trails

	private static final class Trail {
		final ArrayDeque<double[]> points = new ArrayDeque<>();
	}

	private void drawTrails(Minecraft mc, float dt, float pt) {
		Trails m = kro.modules.trails;
		if (!m.enabled()) {
			trails.clear();
			return;
		}
		double life = m.length.value();
		double now = clock;
		Set<Integer> seen = new HashSet<>();
		for (Player p : mc.level.players()) {
			boolean self = p == mc.player;
			if ((!self && !m.everyone.on()) || p.isSpectator() || p.isInvisible() || p.distanceToSqr(mc.player) > PLAYER_RANGE_SQ) {
				continue;
			}
			seen.add(p.getId());
			Trail trail = trails.computeIfAbsent(p.getId(), id -> new Trail());
			Vec3 pos = p.getPosition(pt);
			double[] last = trail.points.peekLast();
			double dx = last == null ? 1.0 : pos.x - last[0], dy = last == null ? 1.0 : pos.y - last[1], dz = last == null ? 1.0 : pos.z - last[2];
			if (dx * dx + dy * dy + dz * dz > 0.0016) {
				trail.points.addLast(new double[] {pos.x, pos.y, pos.z, now});
			}
			while (!trail.points.isEmpty() && now - trail.points.peekFirst()[3] > life) {
				trail.points.removeFirst();
			}
			while (trail.points.size() > 480) {
				trail.points.removeFirst();
			}
			// your own trail starts a moment behind you, so it never floats under the camera
			boolean firstPerson = self && mc.options.getCameraType().isFirstPerson();
			drawTrail(trail, m, now, life, firstPerson ? 0.12 : 0.0, pos);
		}
		trails.keySet().retainAll(seen);
	}

	private void drawTrail(Trail trail, Trails m, double now, double life, double skip, Vec3 head) {
		int n = trail.points.size();
		if (n < 2) {
			return;
		}
		double[] xs = new double[n + 1], ys = new double[n + 1], zs = new double[n + 1];
		float[] widths = new float[n + 1];
		int[] colors = new int[n + 1];
		int count = 0;
		double base = 0.065 * m.width.value();
		String palette = m.palette.get();
		int custom = m.customColor.color();
		for (double[] pt : trail.points) {
			double age = now - pt[3];
			if (age < skip) {
				continue;
			}
			double fresh = 1.0 - age / life;
			xs[count] = pt[0];
			ys[count] = pt[1] + 0.18;
			zs[count] = pt[2];
			widths[count] = (float) (base * (0.25 + 0.75 * fresh));
			colors[count] = Fx.fade(palette(palette, custom, fresh, now), fresh * fresh * 0.9);
			count++;
		}
		if (skip <= 0.0 && count > 0) {
			xs[count] = head.x;
			ys[count] = head.y + 0.18;
			zs[count] = head.z;
			widths[count] = (float) base;
			colors[count] = Fx.fade(palette(palette, custom, 1.0, now), 0.9);
			count++;
		}
		fx.ribbon(xs, ys, zs, widths, colors, count);
	}
}
