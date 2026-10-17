package dev.kro.core.fx;

import dev.kro.core.util.Colors;
import dev.kro.ui.render.Theme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kro's world effects, as pure animation: bursts for totem pops, strikes, souls and shockwaves
 * for kills, ripples under jumping feet, a clean wave where an explosion was cleared, the halo
 * around your opponent and ribbons of light along a path. Each is simulated per frame from its
 * own clock and drawn as light through {@link Fx}.
 */
public final class Effects {
	private static final int GOLD = 0xFFFFD45A;
	private static final int EMERALD = 0xFF62F59A;
	private static final int WHITE = 0xFFFFFFFF;

	private Effects() {
	}

	/** One running effect; returns false once it has fully faded. */
	public interface Effect {
		boolean draw(Fx fx, double clock, float dt, float partialTick);
	}

	private abstract static class Timed implements Effect {
		double age;

		abstract double life();

		abstract void frame(Fx fx, double clock, float dt, float partialTick, double age);

		@Override
		public final boolean draw(Fx fx, double clock, float dt, float partialTick) {
			age += dt;
			if (age >= life()) {
				return false;
			}
			frame(fx, clock, dt, partialTick, age);
			return true;
		}
	}

	// ------------------------------------------------------------------ factories

	/** Totem gold and emerald, the accent pair, or a custom colour with its pale twin. */
	public static int[] popColors(String palette, int custom) {
		return switch (palette) {
			case "Accent" -> new int[] {Theme.accent(), Theme.accentLight()};
			case "Custom" -> {
				int a = 0xFF000000 | custom;
				yield new int[] {a, Colors.lerp(a, WHITE, 0.45f)};
			}
			default -> new int[] {GOLD, EMERALD};
		};
	}

	public static Effect pop(Anchor who, int a, int b, double scale) {
		return new PopBurst(who, a, b, scale);
	}

	public static Effect lightning(double x, double y, double z, int color) {
		return new Lightning(x, y, z, color);
	}

	public static Effect soul(double x, double y, double z, double width, double height, int color) {
		return new SoulRise(x, y, z, width, height, color);
	}

	public static Effect shockwave(double x, double y, double z, double height, int color) {
		return new Shockwave(x, y, z, height, color);
	}

	public static Effect ripple(double x, double y, double z, String palette, int custom, double size, boolean landing) {
		return new Ripple(x, y, z, palette, custom, size, landing);
	}

	public static Effect blast(double x, double y, double z, double radius) {
		return new BlastWave(x, y, z, radius);
	}

	// ------------------------------------------------------------------ colours and easing

	/** A colour from a palette at position {@code t} (0..1), drifting slowly with {@code clock}. */
	public static int palette(String palette, int custom, double t, double clock) {
		return switch (palette) {
			case "Rainbow" -> Colors.hsv((float) (((t * 0.85 + clock * 0.12) % 1.0 + 1.0) % 1.0), 0.72f, 1f);
			case "Custom" -> Colors.lerp(0xFF000000 | custom, WHITE, (float) (0.32 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0))));
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

		Sparks(double ox, double oy, double oz, int count, double speed, double up, double width, double minLife, double maxLife, int a, int b) {
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
				x[i] = ox;
				y[i] = oy;
				z[i] = oz;
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
		private static final int MOTES = 36;
		final Anchor who;
		final int a;
		final int b;
		final double scale;
		final double height;
		final double width;
		final double ox;
		final double oy;
		final double oz;
		double lx;
		double ly;
		double lz;
		final Sparks sparks;
		final double spin = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);

		PopBurst(Anchor who, int a, int b, double scale) {
			this.who = who;
			this.a = a;
			this.b = b;
			this.scale = scale;
			this.height = Math.max(0.6, who.height());
			this.width = Math.max(0.4, who.width());
			this.ox = lx = who.x(1f);
			this.oy = ly = who.y(1f);
			this.oz = lz = who.z(1f);
			this.sparks = new Sparks(ox, oy + height * 0.55, oz, 22, 5.4 * scale, 0.9, 0.042 * scale, 0.5, 0.95, a, b);
		}

		@Override
		double life() {
			return 1.9;
		}

		@Override
		void frame(Fx fx, double clock, float dt, float pt, double age) {
			if (who.present()) {
				lx = who.x(pt);
				ly = who.y(pt);
				lz = who.z(pt);
			}
			double gy = oy + 0.03;

			// a pool of light flashing on the ground
			double pg = age / 0.6;
			if (pg < 1.0) {
				fx.groundGlow(ox, oy + 0.02, oz, (1.1 + 1.4 * outCubic(pg)) * scale, Fx.fade(Fx.lerpColor(a, WHITE, 0.2f), Math.pow(1.0 - pg, 2.0) * 0.55));
			}

			// shockwaves on the ground where it happened
			double p1 = age / 0.95;
			if (p1 < 1.0) {
				double r = (0.35 + 2.5 * outQuint(p1)) * scale;
				double w = (0.24 * (1.0 - p1) + 0.05) * scale;
				double f = Math.pow(1.0 - p1, 1.5);
				fx.groundRing(ox, gy, oz, r, w, Fx.fade(a, f), Fx.fade(b, f), spin + age * 2.4);
			}
			double p2 = (age - 0.14) / 0.85;
			if (p2 > 0.0 && p2 < 1.0) {
				double r = (0.25 + 1.75 * outCubic(p2)) * scale;
				double f = Math.pow(1.0 - p2, 2.0) * 0.8;
				fx.groundRing(ox, gy, oz, r, (0.1 * (1.0 - p2) + 0.03) * scale, Fx.fade(b, f), Fx.fade(a, f), -spin - age * 3.0);
			}

			// a pillar of light that flares and fades
			double pp = age / 0.7;
			if (pp < 1.0) {
				double f = pulse(pp, 0.12);
				double w = (0.85 * (1.0 - pp) + 0.22) * scale;
				double top = ly + 3.8 * scale;
				fx.beam(lx, ly, lz, lx, top, lz, w, w * 0.4, Fx.fade(a, f * 0.62), Fx.fade(b, 0.0));
				fx.beam(lx, ly, lz, lx, top, lz, w * 0.32, w * 0.12, Fx.fade(Fx.lerpColor(a, WHITE, 0.55f), f * 0.85), Fx.fade(WHITE, 0.0));
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
				double y = ly + (0.08 + (height + 0.9) * outCubic(q)) * scale;
				double f = Math.pow(Math.sin(q * Math.PI), 0.8);
				int col = Fx.lerpColor(a, b, (float) i / MOTES);
				double size = (0.085 + 0.05 * Math.sin(q * Math.PI)) * scale;
				fx.orb(lx + Math.cos(ang) * rad, y, lz + Math.sin(ang) * rad, size, Fx.fade(col, f));
				for (int k = 1; k <= 3; k++) {
					double ta = ang - k * 0.2, ty = y - k * 0.07 * scale;
					fx.orb(lx + Math.cos(ta) * rad, ty, lz + Math.sin(ta) * rad, size * (1.0 - k * 0.16), Fx.fade(col, f * (0.5 / k)));
				}
			}

			// a halo blooming at head height
			double ph = age / 0.5;
			if (ph < 1.0) {
				fx.halo(lx, ly + height * 0.92, lz, (0.3 + 1.3 * outCubic(ph)) * scale, Fx.fade(a, Math.pow(1.0 - ph, 2.0) * 0.9));
			}
			sparks.draw(fx, dt, 1.0);
		}
	}

	// ------------------------------------------------------------------ kills

	/** A forked bolt from the sky that strikes twice, with an impact flash and a scorch ring. */
	private static final class Lightning extends Timed {
		final double gx;
		final double gy;
		final double gz;
		final int color;
		final List<double[]> bolts = new ArrayList<>();
		final Sparks sparks;
		double[] channel;
		boolean restruck;

		Lightning(double x, double y, double z, int color) {
			this.gx = x;
			this.gy = y;
			this.gz = z;
			this.color = color;
			this.sparks = new Sparks(x, y + 0.15, z, 26, 6.5, 0.7, 0.04, 0.35, 0.8, WHITE, Fx.lerpColor(color, WHITE, 0.5f));
			strike();
		}

		/** The first strike opens a channel; the second runs down the same one, slightly shifted, with new forks. */
		private void strike() {
			bolts.clear();
			ThreadLocalRandom r = ThreadLocalRandom.current();
			double[] main;
			if (channel == null) {
				double tx = gx + r.nextGaussian() * 2.0, tz = gz + r.nextGaussian() * 2.0;
				main = channel = jagged(tx, gy + 36.0, tz, gx, gy, gz, 7, 0.24, r);
			} else {
				main = channel.clone();
				for (int i = 3; i < main.length - 3; i++) {
					main[i] += r.nextGaussian() * 0.12;
				}
			}
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

		/**
		 * One glowing layer of a bolt through every {@code stride}-th point; forks taper to nothing
		 * at their tips.
		 */
		private static void bolt(Fx fx, double[] xs, double[] ys, double[] zs, int n, int stride, double width, int color, boolean taper) {
			int m = (n - 1) / stride + 1;
			double[] px = new double[m], py = new double[m], pz = new double[m];
			float[] ws = new float[m];
			int[] cs = new int[m];
			for (int k = 0; k < m; k++) {
				int i = Math.min(n - 1, k * stride);
				px[k] = xs[i];
				py[k] = ys[i];
				pz[k] = zs[i];
				double t = taper ? 1.0 - (double) i / (n - 1) : 1.0;
				ws[k] = (float) (width * (0.25 + 0.75 * t));
				cs[k] = Fx.fade(color, 0.3 + 0.7 * t);
			}
			fx.ribbon(px, py, pz, ws, cs, m);
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
		void frame(Fx fx, double clock, float dt, float pt, double age) {
			if (age >= 0.13 && !restruck) {
				restruck = true;
				strike();
			}
			double f = flicker(age);
			if (f > 0.01) {
				int glow = Fx.lerpColor(color, WHITE, 0.2f);
				int mid = Fx.lerpColor(color, WHITE, 0.6f);
				for (int bi = 0; bi < bolts.size(); bi++) {
					double[] p = bolts.get(bi);
					double scale = bi == 0 ? 1.0 : 0.55;
					double bright = bi == 0 ? 1.0 : 0.85;
					int n = p.length / 3;
					double[] xs = new double[n], ys = new double[n], zs = new double[n];
					for (int i = 0; i < n; i++) {
						xs[i] = p[i * 3];
						ys[i] = p[i * 3 + 1];
						zs[i] = p[i * 3 + 2];
					}
					// one continuous ribbon per layer; the wide glows follow a smoothed path so
					// they bloom around the bolt instead of bristling at every zigzag
					if (bi == 0) {
						bolt(fx, xs, ys, zs, n, 16, 1.9, Fx.fade(color, 0.08 * f), false);
					}
					bolt(fx, xs, ys, zs, n, 4, 0.85 * scale, Fx.fade(glow, 0.2 * f * bright), bi > 0);
					bolt(fx, xs, ys, zs, n, 1, 0.22 * scale, Fx.fade(mid, 0.6 * f * bright), bi > 0);
					bolt(fx, xs, ys, zs, n, 1, 0.06 * scale, Fx.fade(WHITE, f * bright), bi > 0);
				}
				// the cloud it fell from lights up
				double[] main = bolts.get(0);
				fx.orb(main[0], main[1], main[2], 9.0, Fx.fade(Fx.lerpColor(color, WHITE, 0.3f), 0.16 * f));
			}
			// impact: a white flash, a pool of light, a scorch ring racing outward and a spray of sparks
			double pf = age / 0.4;
			if (pf < 1.0) {
				fx.orb(gx, gy + 0.5, gz, 2.6 * (1.0 - 0.5 * pf), Fx.fade(Fx.lerpColor(color, WHITE, 0.6f), Math.pow(1.0 - pf, 2.0)));
			}
			double pg = age / 0.6;
			if (pg < 1.0) {
				fx.groundGlow(gx, gy + 0.02, gz, 3.2, Fx.fade(Fx.lerpColor(color, WHITE, 0.4f), Math.pow(1.0 - pg, 2.0) * 0.7));
			}
			double pr = age / 0.75;
			if (pr < 1.0) {
				double f2 = Math.pow(1.0 - pr, 1.6);
				fx.groundRing(gx, gy + 0.03, gz, 0.4 + 3.0 * outQuint(pr), 0.28 * (1.0 - pr) + 0.05,
						Fx.fade(WHITE, f2), Fx.fade(color, f2), age * 3.0);
			}
			sparks.draw(fx, dt, 1.0);
		}
	}

	/** Wisps of light lift out of the body and drift upward, under a halo that rises and fades. */
	private static final class SoulRise extends Timed {
		private static final int WISPS = 18;
		final double fx0;
		final double fy0;
		final double fz0;
		final double height;
		final int color;
		final double[] ox = new double[WISPS];
		final double[] oy = new double[WISPS];
		final double[] oz = new double[WISPS];
		final double[] speed = new double[WISPS];
		final double[] sway = new double[WISPS];
		final double[] phase = new double[WISPS];
		final double[] delay = new double[WISPS];

		SoulRise(double x, double y, double z, double width, double height, int color) {
			this.fx0 = x;
			this.fy0 = y;
			this.fz0 = z;
			this.height = Math.max(0.6, height);
			this.color = color;
			ThreadLocalRandom r = ThreadLocalRandom.current();
			for (int i = 0; i < WISPS; i++) {
				double ang = r.nextDouble() * Math.PI * 2.0, rad = Math.max(0.36, width * 0.72) + r.nextDouble() * 0.18;
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
		void frame(Fx fx, double clock, float dt, float pt, double age) {
			int light = Fx.lerpColor(color, WHITE, 0.55f);
			// a soft column of light
			double pc = age / 1.5;
			if (pc < 1.0) {
				double f = pulse(pc, 0.2) * 0.32;
				fx.beam(fx0, fy0, fz0, fx0, fy0 + height + 3.2, fz0, 0.95, 0.3, Fx.fade(color, f), Fx.fade(color, 0.0));
			}
			// the soul itself: a bright orb rising out of the body, trailing a comet tail
			double ps = age / 2.0;
			if (ps < 1.0) {
				int tail = 18;
				double[] xs = new double[tail], ys = new double[tail], zs = new double[tail];
				float[] ws = new float[tail];
				int[] cs = new int[tail];
				double fade = Math.min(1.0, age / 0.2) * Math.pow(1.0 - ps, 1.1);
				for (int k = 0; k < tail; k++) {
					double ka = Math.max(0.0, age - k * 0.035);
					double kp = ka / 2.0;
					xs[k] = fx0 + Math.sin(ka * 2.4) * 0.14;
					ys[k] = fy0 + height * 0.55 + (height * 0.45 + 3.2) * (kp * kp * (3.0 - 2.0 * kp));
					zs[k] = fz0 + Math.cos(ka * 1.9) * 0.14;
					double kf = 1.0 - (double) k / tail;
					ws[k] = (float) (0.2 * kf);
					cs[k] = Fx.fade(Fx.lerpColor(color, light, (float) kf), fade * kf * 0.8);
				}
				fx.ribbon(xs, ys, zs, ws, cs, tail);
				fx.orb(xs[0], ys[0], zs[0], 0.5 - 0.18 * ps, Fx.fade(light, fade));
				fx.orb(xs[0], ys[0], zs[0], 1.3, Fx.fade(color, fade * 0.24));
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
				double y = fy0 + oy[i] + a * speed[i];
				double x = fx0 + ox[i] + Math.sin(a * sway[i] + phase[i]) * 0.22;
				double z = fz0 + oz[i] + Math.cos(a * sway[i] * 0.8 + phase[i]) * 0.22;
				fx.orb(x, y, z, 0.15, Fx.fade(light, f));
				for (int k = 1; k <= 4; k++) {
					double ka = Math.max(0.0, a - k * 0.06);
					double ky = fy0 + oy[i] + ka * speed[i];
					double kx = fx0 + ox[i] + Math.sin(ka * sway[i] + phase[i]) * 0.22;
					double kz = fz0 + oz[i] + Math.cos(ka * sway[i] * 0.8 + phase[i]) * 0.22;
					fx.orb(kx, ky, kz, 0.15 * (1.0 - k * 0.15), Fx.fade(color, f * 0.45 / k));
				}
			}
			// a halo above the head that floats up and fades
			double ph = age / 1.8;
			if (ph < 1.0) {
				double f = pulse(ph, 0.15) * 0.9;
				double y = fy0 + height + 0.15 + 1.1 * outCubic(ph);
				fx.groundRing(fx0, y, fz0, 0.32 + 0.12 * ph, 0.14, Fx.fade(light, f), Fx.fade(color, f), age * 2.0);
				fx.orb(fx0, y, fz0, 0.55, Fx.fade(light, f * 0.22));
			}
			double pr = age / 0.9;
			if (pr < 1.0) {
				double f = Math.pow(1.0 - pr, 1.8) * 0.7;
				fx.groundRing(fx0, fy0 + 0.03, fz0, 0.3 + 1.6 * outCubic(pr), 0.12 * (1.0 - pr) + 0.03, Fx.fade(color, f), Fx.fade(light, f), -age * 2.0);
			}
		}
	}

	/** Three rings racing outward, a flash at the chest and a ring of sparks. */
	private static final class Shockwave extends Timed {
		final double x;
		final double y;
		final double z;
		final double height;
		final int color;
		final Sparks sparks;

		Shockwave(double x, double y, double z, double height, int color) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.height = Math.max(0.6, height);
			this.color = color;
			this.sparks = new Sparks(x, y + this.height * 0.5, z, 34, 7.2, 0.25, 0.042, 0.35, 0.75, color, WHITE);
		}

		@Override
		double life() {
			return 1.1;
		}

		@Override
		void frame(Fx fx, double clock, float dt, float pt, double age) {
			int light = Fx.lerpColor(color, WHITE, 0.5f);
			double pg = age / 0.45;
			if (pg < 1.0) {
				fx.groundGlow(x, y + 0.02, z, 1.4 + 1.4 * outCubic(pg), Fx.fade(light, Math.pow(1.0 - pg, 2.0) * 0.6));
			}
			double[] delays = {0.0, 0.09, 0.18};
			double[] reach = {3.6, 2.7, 1.9};
			for (int k = 0; k < 3; k++) {
				double p = (age - delays[k]) / 0.8;
				if (p <= 0.0 || p >= 1.0) {
					continue;
				}
				double f = Math.pow(1.0 - p, 1.5) * (1.0 - k * 0.2);
				fx.groundRing(x, y + 0.03, z, 0.3 + reach[k] * outQuint(p), 0.26 * (1.0 - p) + 0.04,
						Fx.fade(k % 2 == 0 ? light : color, f), Fx.fade(k % 2 == 0 ? color : light, f), age * (k % 2 == 0 ? 2.5 : -2.5));
			}
			double ph = age / 0.4;
			if (ph < 1.0) {
				fx.halo(x, y + height * 0.5, z, 0.4 + 1.6 * outCubic(ph), Fx.fade(light, Math.pow(1.0 - ph, 2.0)));
				fx.orb(x, y + height * 0.5, z, 0.9 * (1.0 - ph), Fx.fade(WHITE, Math.pow(1.0 - ph, 2.0) * 0.8));
			}
			sparks.draw(fx, dt, 1.0);
		}
	}

	// ------------------------------------------------------------------ jumps and blasts

	/** A ring of light rippling out from a player's feet. */
	private static final class Ripple extends Timed {
		final double x;
		final double y;
		final double z;
		final String palette;
		final int custom;
		final double size;
		final boolean landing;
		final double phase = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);

		Ripple(double x, double y, double z, String palette, int custom, double size, boolean landing) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.palette = palette;
			this.custom = custom;
			this.size = size * (landing ? 0.75 : 1.0);
			this.landing = landing;
		}

		@Override
		double life() {
			return 0.8;
		}

		@Override
		void frame(Fx fx, double clock, float dt, float pt, double age) {
			double p = age / life();
			double f = Math.pow(1.0 - p, 1.4);
			int a = palette(palette, custom, 0.0, clock), b = palette(palette, custom, 0.5, clock);
			double gy = y + 0.025;
			double radius = (0.15 + 1.0 * outCubic(p)) * size;
			fx.groundGlow(x, gy, z, radius * 1.1, Fx.fade(a, f * 0.22));
			fx.groundRing(x, gy, z, radius, (0.15 * (1.0 - p) + 0.035) * size, Fx.fade(a, f), Fx.fade(b, f), phase + age * 3.0);
			double p2 = (age - 0.06) / (life() - 0.06);
			if (p2 > 0.0 && !landing) {
				double f2 = Math.pow(1.0 - p2, 1.8) * 0.5;
				fx.groundRing(x, gy, z, (0.1 + 0.62 * outCubic(p2)) * size, (0.08 * (1.0 - p2) + 0.025) * size,
						Fx.fade(b, f2), Fx.fade(a, f2), -phase - age * 3.0);
			}
		}
	}

	/** Where No Explosions removed a blast: a crisp ring sweeps out and a flash blooms. */
	private static final class BlastWave extends Timed {
		final double x;
		final double y;
		final double z;
		final double reach;
		final Sparks embers;

		BlastWave(double x, double y, double z, double radius) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.reach = Math.max(1.5, Math.min(10.0, radius * 0.9));
			this.embers = new Sparks(x, y + 0.4, z, 16, 6.0, 0.6, 0.04, 0.4, 0.8, 0xFFFFE7C4, 0xFFFF9A5A);
		}

		@Override
		double life() {
			return 0.95;
		}

		@Override
		void frame(Fx fx, double clock, float dt, float pt, double age) {
			int warm = 0xFFFFE7C4, ember = 0xFFFF9A5A;
			double pg = age / 0.5;
			if (pg < 1.0) {
				fx.groundGlow(x, y + 0.03, z, reach * (0.45 + 0.35 * outCubic(pg)), Fx.fade(warm, Math.pow(1.0 - pg, 2.0) * 0.5));
			}
			double p = age / life();
			double f = Math.pow(1.0 - p, 1.5) * 0.85;
			fx.groundRing(x, y + 0.04, z, 0.4 + reach * outQuint(p), 0.3 * (1.0 - p) + 0.05,
					Fx.fade(warm, f), Fx.fade(ember, f), age * 2.0);
			double ph = age / 0.35;
			if (ph < 1.0) {
				fx.halo(x, y + 0.6, z, 0.6 + reach * 0.55 * outCubic(ph), Fx.fade(warm, Math.pow(1.0 - ph, 2.0) * 0.8));
			}
			double pf = age / 0.18;
			if (pf < 1.0) {
				fx.orb(x, y + 0.5, z, 1.3, Fx.fade(WHITE, Math.pow(1.0 - pf, 2.0) * 0.75));
			}
			embers.draw(fx, dt, 1.0);
		}
	}

	// ------------------------------------------------------------------ target halo

	/** The ring (or orbiting lights) around your current opponent, fading in and out smoothly. */
	public static final class Halo {
		private Anchor target;
		private double presence;

		public void clear() {
			target = null;
			presence = 0.0;
		}

		/**
		 * @param current the opponent now, or null; pass the same object for the same opponent
		 */
		public void draw(Fx fx, Anchor current, String style, String palette, int custom, double speed, boolean hurtFlash, float dt, float pt, double clock) {
			// a new opponent takes over once the old ring has faded out
			if (current != target && presence < 0.05) {
				target = current;
			}
			boolean show = target != null && target == current && target.present();
			presence += ((show ? 1.0 : 0.0) - presence) * Math.min(1.0, dt * 7.0);
			if (target == null || presence < 0.01) {
				return;
			}
			double px = target.x(pt), py = target.y(pt), pz = target.z(pt);
			double h = Math.max(0.5, target.height()), w = Math.max(0.3, target.width());
			double radius = w * 0.82 + 0.1;
			float hurt = hurtFlash ? target.hurt() : 0f;
			int a = flash(palette(palette, custom, 0.0, clock), hurt), b = flash(palette(palette, custom, 0.5, clock), hurt);
			fx.groundGlow(px, py + 0.02, pz, radius * 1.35, Fx.fade(a, 0.16 * presence));
			if (style.equals("Orbit")) {
				for (int k = 0; k < 3; k++) {
					int col = flash(palette(palette, custom, k / 3.0, clock), hurt);
					int tail = 22;
					double[] xs = new double[tail], ys = new double[tail], zs = new double[tail];
					float[] ws = new float[tail];
					int[] cs = new int[tail];
					for (int j = 0; j < tail; j++) {
						double tt = clock - j * 0.022;
						double ang = tt * speed * 2.6 + k * Math.PI * 2.0 / 3.0;
						xs[j] = px + Math.cos(ang) * radius;
						ys[j] = py + h * (0.22 + 0.3 * k) + 0.12 * Math.sin(tt * 2.0 + k * 1.7);
						zs[j] = pz + Math.sin(ang) * radius;
						double kf = 1.0 - (double) j / tail;
						ws[j] = (float) (0.075 * kf);
						cs[j] = Fx.fade(col, presence * kf * kf * 0.85);
					}
					fx.ribbon(xs, ys, zs, ws, cs, tail);
					fx.orb(xs[0], ys[0], zs[0], 0.13, Fx.fade(Fx.lerpColor(col, 0xFFFFFFFF, 0.25f), presence));
					fx.orb(xs[0], ys[0], zs[0], 0.34, Fx.fade(col, presence * 0.3));
				}
				fx.groundRing(px, py + 0.03, pz, radius, 0.06, Fx.fade(a, presence * 0.6), Fx.fade(b, presence * 0.6), clock * 1.5);
				return;
			}
			// the halo glides up and down the body, a curtain of light streaming behind it
			double s = 0.5 - 0.5 * Math.cos(clock * speed * 2.3);
			double sPast = 0.5 - 0.5 * Math.cos((clock - 0.2) * speed * 2.3);
			double y = py + 0.08 + s * (h - 0.16);
			double tailY = py + 0.08 + sPast * (h - 0.16);
			if (Math.abs(tailY - y) > 0.01) {
				fx.cylinder(px, y, pz, radius, tailY, Fx.fade(a, presence * 0.55), Fx.fade(b, presence * 0.55), clock * 1.6);
			}
			fx.groundRing(px, y, pz, radius, 0.12, Fx.fade(a, presence), Fx.fade(b, presence), clock * 1.6);
		}

		private static int flash(int color, float hurt) {
			return hurt <= 0f ? color : Fx.lerpColor(color, Theme.BAD, hurt * 0.85f);
		}
	}

	// ------------------------------------------------------------------ trails

	/** A path remembered as timed points, drawn as a ribbon that thins and fades toward its tail. */
	public static final class Trail {
		private final ArrayDeque<double[]> points = new ArrayDeque<>();

		/** Records the newest position; points older than {@code life} seconds are forgotten. */
		public void sample(double x, double y, double z, double clock, double life) {
			double[] last = points.peekLast();
			double dx = last == null ? 1.0 : x - last[0], dy = last == null ? 1.0 : y - last[1], dz = last == null ? 1.0 : z - last[2];
			if (dx * dx + dy * dy + dz * dz > 0.0016) {
				points.addLast(new double[] {x, y, z, clock});
			}
			while (!points.isEmpty() && clock - points.peekFirst()[3] > life) {
				points.removeFirst();
			}
			while (points.size() > 480) {
				points.removeFirst();
			}
		}

		/**
		 * Draws the ribbon {@code lift} blocks above the path. Points newer than {@code skip} seconds
		 * are left out (your own trail starts a moment behind you in first person); otherwise the
		 * ribbon runs right up to the head position.
		 */
		public void draw(Fx fx, String palette, int custom, double widthScale, double clock, double life, double skip, double lift,
				double hx, double hy, double hz) {
			int n = points.size();
			if (n < 2) {
				return;
			}
			double[] xs = new double[n + 1], ys = new double[n + 1], zs = new double[n + 1], heights = new double[n + 1];
			double[] gys = new double[n + 1];
			float[] widths = new float[n + 1];
			int[] wall = new int[n + 1];
			int[] line = new int[n + 1];
			int count = 0;
			double tall = 0.8 * widthScale;
			for (double[] p : points) {
				double age = clock - p[3];
				if (age < skip) {
					continue;
				}
				count = put(xs, ys, gys, zs, heights, widths, wall, line, count, p[0], p[1], p[2], 1.0 - age / life, palette, custom, clock, tall, widthScale, lift);
			}
			if (skip <= 0.0 && count > 0) {
				count = put(xs, ys, gys, zs, heights, widths, wall, line, count, hx, hy, hz, 1.0, palette, custom, clock, tall, widthScale, lift);
			}
			// an aurora: a wall of light standing on the path, brightest at its foot and fading upward
			fx.curtain(xs, ys, zs, heights, wall, count);
			// and a crisp line of light along the ground
			fx.ribbon(xs, gys, zs, widths, line, count);
		}

		private static int put(double[] xs, double[] ys, double[] gys, double[] zs, double[] heights, float[] widths, int[] wall, int[] line, int i,
				double x, double y, double z, double fresh, String palette, int custom, double clock, double tall, double widthScale, double lift) {
			fresh = Math.max(0.0, Math.min(1.0, fresh));
			int col = palette(palette, custom, fresh, clock);
			xs[i] = x;
			ys[i] = y + 0.02;
			gys[i] = y + lift * 0.3;
			zs[i] = z;
			heights[i] = tall * (0.35 + 0.65 * fresh);
			widths[i] = (float) (0.05 * widthScale * (0.3 + 0.7 * fresh));
			wall[i] = Fx.fade(col, Math.pow(fresh, 1.5) * 0.7);
			line[i] = Fx.fade(Fx.lerpColor(col, 0xFFFFFFFF, 0.2f), fresh * fresh * 0.9);
			return i + 1;
		}
	}
}
