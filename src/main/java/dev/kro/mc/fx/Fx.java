package dev.kro.mc.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kro.mc.render.SparkPipelines;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Kro's in-world light. Effects describe glowing primitives (soft orbs, halos, ribbons of light,
 * rings laid on the ground, spark streaks) in world coordinates; they are collected per shape and
 * drawn in one additive batch each, relative to the camera, after the opaque world.
 */
public final class Fx {
	/** Primitives further than this from the camera are skipped. */
	private static final double MAX_DISTANCE_SQ = 160.0 * 160.0;

	private final Batch orbs = new Batch(SparkPipelines.ORB_TYPE);
	private final Batch bands = new Batch(SparkPipelines.BAND_TYPE);
	private final Batch halos = new Batch(SparkPipelines.RING_TYPE);
	private final Batch streaks = new Batch(SparkPipelines.SPARK_TYPE);
	private double cx;
	private double cy;
	private double cz;

	/** Starts a frame seen from {@code camera}. */
	public void begin(Vec3 camera) {
		cx = camera.x;
		cy = camera.y;
		cz = camera.z;
	}

	public double cameraX() {
		return cx;
	}

	public double cameraY() {
		return cy;
	}

	public double cameraZ() {
		return cz;
	}

	/** A round glow of {@code radius} blocks, always facing the camera. */
	public void orb(double x, double y, double z, double radius, int argb) {
		billboard(orbs, x, y, z, radius, argb);
	}

	/** A soft expanding halo (a ring with a faint core), facing the camera. */
	public void halo(double x, double y, double z, double radius, int argb) {
		billboard(halos, x, y, z, radius, argb);
	}

	/**
	 * A ring of light lying flat at height {@code y}. Its colour sweeps from {@code a} to {@code b}
	 * and back around the circle, the sweep turned by {@code phase} (radians).
	 */
	public void groundRing(double x, double y, double z, double radius, double width, int a, int b, double phase) {
		if ((a >>> 24) == 0 && (b >>> 24) == 0 || radius <= 0.0) {
			return;
		}
		double px = x - cx, py = y - cy, pz = z - cz;
		if (px * px + py * py + pz * pz > MAX_DISTANCE_SQ) {
			return;
		}
		int segments = Math.max(24, Math.min(128, (int) (radius * 44.0)));
		double inner = Math.max(0.0, radius - width * 0.5), outer = radius + width * 0.5;
		double step = Math.PI * 2.0 / segments;
		double c0 = Math.cos(0.0), s0 = Math.sin(0.0);
		int col0 = sweep(a, b, phase);
		for (int i = 1; i <= segments; i++) {
			double ang = i * step;
			double c1 = Math.cos(ang), s1 = Math.sin(ang);
			int col1 = sweep(a, b, ang + phase);
			bands.vertex(px + c0 * inner, py, pz + s0 * inner, -1f, 0f, col0);
			bands.vertex(px + c0 * outer, py, pz + s0 * outer, 1f, 0f, col0);
			bands.vertex(px + c1 * outer, py, pz + s1 * outer, 1f, 0f, col1);
			bands.vertex(px + c1 * inner, py, pz + s1 * inner, -1f, 0f, col1);
			c0 = c1;
			s0 = s1;
			col0 = col1;
		}
	}

	private static int sweep(int a, int b, double angle) {
		float t = (float) (0.5 - 0.5 * Math.cos(angle));
		return lerpColor(a, b, t);
	}

	/** A ribbon of light from one point to another, turned to face the camera. */
	public void beam(double x1, double y1, double z1, double x2, double y2, double z2, double w1, double w2, int c1, int c2) {
		double ax = x1 - cx, ay = y1 - cy, az = z1 - cz;
		double bx = x2 - cx, by = y2 - cy, bz = z2 - cz;
		if (ax * ax + ay * ay + az * az > MAX_DISTANCE_SQ && bx * bx + by * by + bz * bz > MAX_DISTANCE_SQ) {
			return;
		}
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5, mz = (az + bz) * 0.5;
		double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
		double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (sl < 1.0e-9) {
			return;
		}
		sx /= sl;
		sy /= sl;
		sz /= sl;
		bands.vertex(ax - sx * w1, ay - sy * w1, az - sz * w1, -1f, 0f, c1);
		bands.vertex(ax + sx * w1, ay + sy * w1, az + sz * w1, 1f, 0f, c1);
		bands.vertex(bx + sx * w2, by + sy * w2, bz + sz * w2, 1f, 0f, c2);
		bands.vertex(bx - sx * w2, by - sy * w2, bz - sz * w2, -1f, 0f, c2);
	}

	/**
	 * A continuous ribbon through {@code n} points, facing the camera, with a half-width and colour
	 * per point. Neighbouring segments share their edges, so the ribbon bends without gaps.
	 */
	public void ribbon(double[] xs, double[] ys, double[] zs, float[] halfWidths, int[] colors, int n) {
		if (n < 2) {
			return;
		}
		double[] side = new double[n * 3];
		for (int i = 0; i < n; i++) {
			int prev = Math.max(0, i - 1), next = Math.min(n - 1, i + 1);
			double tx = xs[next] - xs[prev], ty = ys[next] - ys[prev], tz = zs[next] - zs[prev];
			double vx = xs[i] - cx, vy = ys[i] - cy, vz = zs[i] - cz;
			double sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
			double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (sl < 1.0e-9) {
				if (i > 0) {
					side[i * 3] = side[i * 3 - 3];
					side[i * 3 + 1] = side[i * 3 - 2];
					side[i * 3 + 2] = side[i * 3 - 1];
				}
				continue;
			}
			side[i * 3] = sx / sl;
			side[i * 3 + 1] = sy / sl;
			side[i * 3 + 2] = sz / sl;
		}
		for (int i = 0; i < n - 1; i++) {
			int j = i + 1;
			if ((colors[i] >>> 24) == 0 && (colors[j] >>> 24) == 0) {
				continue;
			}
			double ax = xs[i] - cx, ay = ys[i] - cy, az = zs[i] - cz;
			double bx = xs[j] - cx, by = ys[j] - cy, bz = zs[j] - cz;
			double wa = halfWidths[i], wb = halfWidths[j];
			bands.vertex(ax - side[i * 3] * wa, ay - side[i * 3 + 1] * wa, az - side[i * 3 + 2] * wa, -1f, 0f, colors[i]);
			bands.vertex(ax + side[i * 3] * wa, ay + side[i * 3 + 1] * wa, az + side[i * 3 + 2] * wa, 1f, 0f, colors[i]);
			bands.vertex(bx + side[j * 3] * wb, by + side[j * 3 + 1] * wb, bz + side[j * 3 + 2] * wb, 1f, 0f, colors[j]);
			bands.vertex(bx - side[j * 3] * wb, by - side[j * 3 + 1] * wb, bz - side[j * 3 + 2] * wb, -1f, 0f, colors[j]);
		}
	}

	/** A spark streak: a tapered stroke of light whose hot head is at (hx, hy, hz). */
	public void streak(double tx, double ty, double tz, double hx, double hy, double hz, double width, int argb) {
		double ax = tx - cx, ay = ty - cy, az = tz - cz;
		double bx = hx - cx, by = hy - cy, bz = hz - cz;
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dl < 1.0e-6 || bx * bx + by * by + bz * bz > MAX_DISTANCE_SQ) {
			return;
		}
		dx /= dl;
		dy /= dl;
		dz /= dl;
		double vl = Math.sqrt(bx * bx + by * by + bz * bz);
		double vx = bx / vl, vy = by / vl, vz = bz / vl;
		double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
		double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (sl < 1.0e-6) {
			return;
		}
		sx = sx / sl * width;
		sy = sy / sl * width;
		sz = sz / sl * width;
		double ex = bx + dx * width * 2.0, ey = by + dy * width * 2.0, ez = bz + dz * width * 2.0;
		streaks.vertex(ax - sx, ay - sy, az - sz, -1f, -1f, argb);
		streaks.vertex(ax + sx, ay + sy, az + sz, 1f, -1f, argb);
		streaks.vertex(ex + sx, ey + sy, ez + sz, 1f, 1f, argb);
		streaks.vertex(ex - sx, ey - sy, ez - sz, -1f, 1f, argb);
	}

	private void billboard(Batch batch, double x, double y, double z, double radius, int argb) {
		if ((argb >>> 24) == 0 || radius <= 0.0) {
			return;
		}
		double px = x - cx, py = y - cy, pz = z - cz;
		double len2 = px * px + py * py + pz * pz;
		if (len2 > MAX_DISTANCE_SQ || len2 < 1.0e-8) {
			return;
		}
		double len = Math.sqrt(len2);
		double nx = px / len, ny = py / len, nz = pz / len;
		// right = view x up, up = right x view
		double rx = -nz, rz = nx;
		double rl = Math.sqrt(rx * rx + rz * rz);
		if (rl < 1.0e-4) {
			rx = 1.0;
			rz = 0.0;
			rl = 1.0;
		}
		rx /= rl;
		rz /= rl;
		double ux = -rz * ny, uy = rz * nx - rx * nz, uz = rx * ny;
		rx *= radius;
		rz *= radius;
		ux *= radius;
		uy *= radius;
		uz *= radius;
		batch.vertex(px - rx - ux, py - uy, pz - rz - uz, -1f, -1f, argb);
		batch.vertex(px + rx - ux, py - uy, pz + rz - uz, 1f, -1f, argb);
		batch.vertex(px + rx + ux, py + uy, pz + rz + uz, 1f, 1f, argb);
		batch.vertex(px - rx + ux, py + uy, pz - rz + uz, -1f, 1f, argb);
	}

	/** Draws everything collected this frame and clears it. */
	public void flush(Matrix4f pose, MultiBufferSource consumers) {
		bands.flush(pose, consumers);
		halos.flush(pose, consumers);
		orbs.flush(pose, consumers);
		streaks.flush(pose, consumers);
	}

	public static int lerpColor(int a, int b, float t) {
		t = t < 0f ? 0f : Math.min(t, 1f);
		int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
				| Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}

	/** {@code argb} with its alpha multiplied by {@code f}. */
	public static int fade(int argb, double f) {
		if (f <= 0.0) {
			return argb & 0x00FFFFFF;
		}
		int a = (int) Math.round((argb >>> 24) * Math.min(1.0, f));
		return a << 24 | (argb & 0x00FFFFFF);
	}

	/** Camera-relative vertices of one shape, emitted in one go. */
	private static final class Batch {
		private final RenderType type;
		private float[] pos = new float[4 * 5 * 64];
		private int[] colors = new int[4 * 64];
		private int count;

		Batch(RenderType type) {
			this.type = type;
		}

		void vertex(double x, double y, double z, float u, float v, int argb) {
			if (count == colors.length) {
				colors = Arrays.copyOf(colors, colors.length * 2);
				pos = Arrays.copyOf(pos, pos.length * 2);
			}
			int o = count * 5;
			pos[o] = (float) x;
			pos[o + 1] = (float) y;
			pos[o + 2] = (float) z;
			pos[o + 3] = u;
			pos[o + 4] = v;
			colors[count] = argb;
			count++;
		}

		void flush(Matrix4f pose, MultiBufferSource consumers) {
			if (count == 0) {
				return;
			}
			VertexConsumer vc = consumers.getBuffer(type);
			for (int i = 0; i < count; i++) {
				int o = i * 5;
				vc.addVertex(pose, pos[o], pos[o + 1], pos[o + 2]).setUv(pos[o + 3], pos[o + 4]).setColor(colors[i]);
			}
			if (consumers instanceof MultiBufferSource.BufferSource bs) {
				bs.endBatch(type);
			}
			count = 0;
		}
	}
}
