package dev.kro.mc.feature;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kro.Kro;
import dev.kro.core.module.impl.HitSparks;
import dev.kro.core.util.Colors;
import dev.kro.mc.render.SparkPipelines;
import dev.kro.ui.render.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hit sparks: luminous streaks that burst from the point of impact, arc under gravity and fade,
 * with a soft ring blooming where the blow landed. Simulated per frame for perfectly smooth
 * motion and drawn additively so they glow against any background.
 */
public final class HitSparksController {
	private static final double GRAVITY = 14.0;
	private static final double DRAG = 2.6;

	private final Kro kro;
	private final List<Spark> sparks = new ArrayList<>();
	private final List<Flash> flashes = new ArrayList<>();
	private long last = System.nanoTime();

	private static final class Spark {
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		float age;
		float life;
		float width;
		int color;
		boolean gravity;
	}

	private static final class Flash {
		double x;
		double y;
		double z;
		float age;
		float life;
		float size;
		int color;
	}

	public HitSparksController(Kro kro) {
		this.kro = kro;
	}

	public void onAttack(Entity target) {
		HitSparks m = kro.modules.hitSparks;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!m.enabled() || player == null || mc.level == null) {
			return;
		}
		if (m.critsOnly.on() && !isCrit(player)) {
			return;
		}
		Vec3 hit = mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() == target
				? ehr.getLocation()
				: target.getBoundingBox().getCenter();
		Vec3 dir = hit.subtract(player.getEyePosition()).normalize();
		if (m.style.is("Glow")) {
			spawnGlow(hit, dir, m);
		} else {
			spawnVanilla(mc, hit, dir, m);
		}
	}

	private static boolean isCrit(LocalPlayer p) {
		return p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger()
				&& p.getAttackStrengthScale(0.5f) > 0.9f;
	}

	private int pickColor(HitSparks m, ThreadLocalRandom r) {
		return switch (m.palette.get()) {
			case "Custom" -> Colors.lerp(m.customColor.color(), 0xFFFFFFFF, r.nextFloat() * 0.25f);
			case "Spectrum" -> Colors.hsv(r.nextFloat(), 0.75f, 1f);
			default -> Colors.lerp(Theme.accent(), Theme.accentLight(), r.nextFloat());
		};
	}

	private void spawnGlow(Vec3 hit, Vec3 dir, HitSparks m) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int count = m.amount.intValue();
		float size = m.size.floatValue();
		double spread = m.spread.value();
		for (int i = 0; i < count; i++) {
			// a hemisphere burst biased away from the attacker
			double ux = r.nextGaussian(), uy = r.nextGaussian() * 0.8 + 0.35, uz = r.nextGaussian();
			double len = Math.sqrt(ux * ux + uy * uy + uz * uz);
			ux /= len;
			uy /= len;
			uz /= len;
			double bias = 0.55;
			double vx = ux + dir.x * bias, vy = uy + dir.y * bias * 0.4, vz = uz + dir.z * bias;
			double speed = (2.6 + r.nextDouble() * 4.2) * spread;
			double vlen = Math.sqrt(vx * vx + vy * vy + vz * vz);
			Spark s = new Spark();
			s.x = hit.x;
			s.y = hit.y;
			s.z = hit.z;
			s.vx = vx / vlen * speed;
			s.vy = vy / vlen * speed;
			s.vz = vz / vlen * speed;
			s.life = 0.28f + r.nextFloat() * 0.32f;
			s.width = (0.022f + r.nextFloat() * 0.022f) * size;
			s.color = pickColor(m, r);
			s.gravity = m.gravity.on();
			sparks.add(s);
		}
		if (m.flash.on()) {
			Flash f = new Flash();
			f.x = hit.x;
			f.y = hit.y;
			f.z = hit.z;
			f.life = 0.3f;
			f.size = 0.62f * size;
			f.color = pickColor(m, r);
			flashes.add(f);
		}
		while (sparks.size() > 600) {
			sparks.remove(0);
		}
	}

	private void spawnVanilla(Minecraft mc, Vec3 hit, Vec3 dir, HitSparks m) {
		ParticleOptions type = switch (m.style.get()) {
			case "Magic" -> ParticleTypes.ENCHANTED_HIT;
			case "Electric" -> ParticleTypes.ELECTRIC_SPARK;
			default -> ParticleTypes.CRIT;
		};
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double spread = m.spread.value();
		for (int i = 0; i < m.amount.intValue(); i++) {
			double vx = (r.nextDouble() - 0.5) * 0.9 * spread + dir.x * 0.25;
			double vy = r.nextDouble() * 0.5 * spread;
			double vz = (r.nextDouble() - 0.5) * 0.9 * spread + dir.z * 0.25;
			mc.level.addParticle(type, hit.x, hit.y, hit.z, vx, vy, vz);
		}
	}

	/** World render hook: integrates and draws every live spark. */
	public void renderWorld(WorldRenderContext ctx) {
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - last) / 1.0e9f);
		last = now;
		if (sparks.isEmpty() && flashes.isEmpty()) {
			return;
		}
		step(dt);
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		Matrix4f pose = ctx.matrices().last().pose();
		MultiBufferSource consumers = ctx.consumers();

		if (!sparks.isEmpty()) {
			VertexConsumer vc = consumers.getBuffer(SparkPipelines.SPARK_TYPE);
			for (Spark s : sparks) {
				emitSpark(vc, pose, s, cam);
			}
			if (consumers instanceof MultiBufferSource.BufferSource bs) {
				bs.endBatch(SparkPipelines.SPARK_TYPE);
			}
		}
		if (!flashes.isEmpty()) {
			VertexConsumer vc = consumers.getBuffer(SparkPipelines.RING_TYPE);
			for (Flash f : flashes) {
				emitFlash(vc, pose, f, cam);
			}
			if (consumers instanceof MultiBufferSource.BufferSource bs) {
				bs.endBatch(SparkPipelines.RING_TYPE);
			}
		}
	}

	private void step(float dt) {
		double drag = Math.exp(-DRAG * dt);
		Iterator<Spark> it = sparks.iterator();
		while (it.hasNext()) {
			Spark s = it.next();
			s.age += dt;
			if (s.age >= s.life) {
				it.remove();
				continue;
			}
			if (s.gravity) {
				s.vy -= GRAVITY * dt;
			}
			s.vx *= drag;
			s.vy *= drag;
			s.vz *= drag;
			s.x += s.vx * dt;
			s.y += s.vy * dt;
			s.z += s.vz * dt;
		}
		flashes.removeIf(f -> (f.age += dt) >= f.life);
	}

	private static void emitSpark(VertexConsumer vc, Matrix4f pose, Spark s, Vec3 cam) {
		float t = s.age / s.life;
		double hx = s.x - cam.x, hy = s.y - cam.y, hz = s.z - cam.z;
		double speed = Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
		double dx, dy, dz;
		if (speed < 1.0e-4) {
			dx = 0;
			dy = 1;
			dz = 0;
		} else {
			dx = s.vx / speed;
			dy = s.vy / speed;
			dz = s.vz / speed;
		}
		double length = Math.max(0.04, Math.min(0.55, speed * 0.05)) * Math.sqrt(1.0 - t);
		double tx = hx - dx * length, ty = hy - dy * length, tz = hz - dz * length;
		// side vector: perpendicular to both the streak and the view ray
		double vlen = Math.sqrt(hx * hx + hy * hy + hz * hz);
		double vx = hx / vlen, vy = hy / vlen, vz = hz / vlen;
		double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
		double slen = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (slen < 1.0e-6) {
			return;
		}
		double w = s.width * (1.0 - 0.4 * t);
		sx = sx / slen * w;
		sy = sy / slen * w;
		sz = sz / slen * w;
		// let the rounded head extend past the particle position
		double ex = hx + dx * w * 2, ey = hy + dy * w * 2, ez = hz + dz * w * 2;
		int color = Colors.withAlpha(s.color, (float) Math.pow(1.0 - t, 1.3) * 0.95f);
		vc.addVertex(pose, (float) (tx - sx), (float) (ty - sy), (float) (tz - sz)).setUv(-1f, -1f).setColor(color);
		vc.addVertex(pose, (float) (tx + sx), (float) (ty + sy), (float) (tz + sz)).setUv(1f, -1f).setColor(color);
		vc.addVertex(pose, (float) (ex + sx), (float) (ey + sy), (float) (ez + sz)).setUv(1f, 1f).setColor(color);
		vc.addVertex(pose, (float) (ex - sx), (float) (ey - sy), (float) (ez - sz)).setUv(-1f, 1f).setColor(color);
	}

	private static void emitFlash(VertexConsumer vc, Matrix4f pose, Flash f, Vec3 cam) {
		float t = f.age / f.life;
		double px = f.x - cam.x, py = f.y - cam.y, pz = f.z - cam.z;
		double len = Math.sqrt(px * px + py * py + pz * pz);
		if (len < 1.0e-4) {
			return;
		}
		double nx = px / len, ny = py / len, nz = pz / len;
		// right = normal x up(0,1,0), up = right x normal
		double rx = -nz, ry = 0, rz = nx;
		double rl = Math.sqrt(rx * rx + rz * rz);
		if (rl < 1.0e-4) {
			rx = 1;
			rz = 0;
			rl = 1;
		}
		rx /= rl;
		rz /= rl;
		double ux = ry * nz - rz * ny, uy = rz * nx - rx * nz, uz = rx * ny - ry * nx;
		double e = 1.0 - Math.pow(1.0 - t, 3.0);
		double size = f.size * (0.35 + 0.65 * e);
		rx *= size;
		ry *= size;
		rz *= size;
		ux *= size;
		uy *= size;
		uz *= size;
		int color = Colors.withAlpha(f.color, (float) Math.pow(1.0 - t, 2.0) * 0.9f);
		vc.addVertex(pose, (float) (px - rx - ux), (float) (py - ry - uy), (float) (pz - rz - uz)).setUv(-1f, -1f).setColor(color);
		vc.addVertex(pose, (float) (px + rx - ux), (float) (py + ry - uy), (float) (pz + rz - uz)).setUv(1f, -1f).setColor(color);
		vc.addVertex(pose, (float) (px + rx + ux), (float) (py + ry + uy), (float) (pz + rz + uz)).setUv(1f, 1f).setColor(color);
		vc.addVertex(pose, (float) (px - rx + ux), (float) (py - ry + uy), (float) (pz - rz + uz)).setUv(-1f, 1f).setColor(color);
	}
}
