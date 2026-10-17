package dev.kro.mc.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kro.Kro;
import dev.kro.core.fx.Anchor;
import dev.kro.core.fx.Effects;
import dev.kro.core.fx.Fx;
import dev.kro.core.module.impl.JumpCircles;
import dev.kro.core.module.impl.KillEffects;
import dev.kro.core.module.impl.NoExplosions;
import dev.kro.core.module.impl.PopEffects;
import dev.kro.core.module.impl.TargetRing;
import dev.kro.core.module.impl.Trails;
import dev.kro.mc.render.SparkPipelines;
import dev.kro.ui.render.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Connects Kro's world effects ({@link Effects}) to the game: totem pops, deaths, jumps and
 * explosions start effects, your opponent gets a halo, players leave trails, your shot shows its
 * arc, and every frame the light is drawn after the opaque world. Purely visual; it exists only
 * on your screen.
 */
public final class WorldEffects {
	private static final long KILL_CREDIT_MS = 5000L;
	private static final long DEATH_DEDUPE_MS = 3000L;
	private static final double PLAYER_RANGE_SQ = 64.0 * 64.0;

	private final Kro kro;
	private final Fx fx = new Fx();
	private final List<Effects.Effect> effects = new ArrayList<>();
	private final Map<Integer, Long> attacked = new HashMap<>();
	private final Map<Integer, Long> deathShown = new HashMap<>();
	private final Set<Integer> dead = new HashSet<>();
	private final Map<Integer, Boolean> grounded = new HashMap<>();
	private final Map<Integer, Double> lastY = new HashMap<>();
	private final Map<Integer, Effects.Trail> trails = new HashMap<>();
	private final Effects.Halo halo = new Effects.Halo();
	private final Trajectory trajectory = new Trajectory();
	private EntityAnchor haloAnchor;
	private ClientLevel level;
	private float partialTick = 1f;
	private long lastFrame = System.nanoTime();
	private double clock;
	private Matrix4f pose;
	private MultiBufferSource consumers;
	private final Fx.Sink sink = this::emit;

	/** An entity seen as an effect anchor. */
	private record EntityAnchor(Entity entity) implements Anchor {
		@Override
		public boolean present() {
			return !entity.isRemoved() && entity.isAlive();
		}

		@Override
		public double x(float pt) {
			return entity.getPosition(pt).x;
		}

		@Override
		public double y(float pt) {
			return entity.getPosition(pt).y;
		}

		@Override
		public double z(float pt) {
			return entity.getPosition(pt).z;
		}

		@Override
		public double height() {
			return entity.getBbHeight();
		}

		@Override
		public double width() {
			return entity.getBbWidth();
		}

		@Override
		public float hurt() {
			return entity instanceof LivingEntity living && living.hurtTime > 0 ? living.hurtTime / 10f : 0f;
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

	private static boolean popShownFor(PopEffects m, Entity entity) {
		boolean self = entity == Minecraft.getInstance().player;
		return m.who.is("Everyone") || (m.who.is("Me") == self);
	}

	public void onTotemPop(Entity entity) {
		PopEffects m = kro.modules.popEffects;
		if (m.enabled() && popShownFor(m, entity) && entity instanceof LivingEntity) {
			int[] colors = Effects.popColors(m.palette.get(), m.customColor.color());
			Minecraft mc = Minecraft.getInstance();
			boolean viewer = entity == mc.player && mc.options.getCameraType().isFirstPerson();
			effects.add(Effects.pop(new EntityAnchor(entity), colors[0], colors[1], m.scale.value(), viewer));
		}
	}

	public void onDeath(Entity entity) {
		KillEffects m = kro.modules.killEffects;
		if (!m.enabled() || !(entity instanceof LivingEntity living) || entity == Minecraft.getInstance().player) {
			return;
		}
		long now = System.currentTimeMillis();
		Long shown = deathShown.get(entity.getId());
		if (shown != null && now - shown < DEATH_DEDUPE_MS) {
			return;
		}
		Long hit = attacked.get(entity.getId());
		boolean mine = hit != null && now - hit < KILL_CREDIT_MS;
		if (!(mine || (m.targets.is("All players") && entity instanceof Player))) {
			return;
		}
		deathShown.put(entity.getId(), now);
		int color = m.palette.is("Custom") ? 0xFF000000 | m.customColor.color() : Theme.accent();
		Vec3 feet = living.position();
		switch (m.style.get()) {
			case "Soul" -> effects.add(Effects.soul(feet.x, feet.y, feet.z, living.getBbWidth(), living.getBbHeight(), color));
			case "Shockwave" -> effects.add(Effects.shockwave(feet.x, feet.y, feet.z, living.getBbHeight(), color));
			default -> {
				effects.add(Effects.lightning(feet.x, feet.y, feet.z, color));
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
			effects.add(Effects.blast(center.x, center.y, center.z, radius));
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
			haloAnchor = null;
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
			Vec3 feet = p.position();
			if (was && !on && (p.getDeltaMovement().y > 0.2 || rise > 0.08)) {
				effects.add(Effects.ripple(feet.x, feet.y, feet.z, jc.palette.get(), jc.customColor.color(), jc.size.value(), false));
			} else if (!was && on && jc.onLand.on()) {
				effects.add(Effects.ripple(feet.x, feet.y, feet.z, jc.palette.get(), jc.customColor.color(), jc.size.value(), true));
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
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		fx.begin(cam.x, cam.y, cam.z);
		float pt = partialTick;

		TargetRing ring = kro.modules.targetRing;
		LivingEntity target = ring.enabled() ? kro.targets.current() : null;
		if (target == null) {
			haloAnchor = null;
		} else if (haloAnchor == null || haloAnchor.entity() != target) {
			haloAnchor = new EntityAnchor(target);
		}
		halo.draw(fx, haloAnchor, ring.style.get(), ring.palette.get(), ring.customColor.color(), ring.speed.value(), ring.hurtFlash.on(), dt, pt, clock);
		drawTrails(mc, pt);
		trajectory.draw(fx, kro.modules.bowTrajectory, mc, pt, clock);

		Iterator<Effects.Effect> it = effects.iterator();
		while (it.hasNext()) {
			if (!it.next().draw(fx, clock, dt, pt)) {
				it.remove();
			}
		}
		while (effects.size() > 64) {
			effects.remove(0);
		}
		pose = ctx.matrices().last().pose();
		consumers = ctx.consumers();
		fx.flush(sink);
		pose = null;
		consumers = null;
	}

	private void drawTrails(Minecraft mc, float pt) {
		Trails m = kro.modules.trails;
		if (!m.enabled()) {
			trails.clear();
			return;
		}
		double life = m.length.value();
		Set<Integer> seen = new HashSet<>();
		for (Player p : mc.level.players()) {
			boolean self = p == mc.player;
			if ((!self && !m.everyone.on()) || p.isSpectator() || p.isInvisible() || p.distanceToSqr(mc.player) > PLAYER_RANGE_SQ) {
				continue;
			}
			seen.add(p.getId());
			Effects.Trail trail = trails.computeIfAbsent(p.getId(), id -> new Effects.Trail());
			Vec3 pos = p.getPosition(pt);
			trail.sample(pos.x, pos.y, pos.z, clock, life);
			// your own trail starts a moment behind you, so it never floats under the camera
			boolean firstPerson = self && mc.options.getCameraType().isFirstPerson();
			trail.draw(fx, m.palette.get(), m.customColor.color(), m.width.value(), clock, life, firstPerson ? 0.12 : 0.0, 0.18, pos.x, pos.y, pos.z);
		}
		trails.keySet().retainAll(seen);
	}

	/** Draws one shape's batch with its glow pipeline. */
	private void emit(int shape, float[] data, int[] colors, int vertices) {
		RenderType type = switch (shape) {
			case Fx.BAND -> SparkPipelines.BAND_TYPE;
			case Fx.HALO -> SparkPipelines.RING_TYPE;
			case Fx.ORB -> SparkPipelines.ORB_TYPE;
			default -> SparkPipelines.SPARK_TYPE;
		};
		VertexConsumer vc = consumers.getBuffer(type);
		for (int i = 0; i < vertices; i++) {
			int o = i * 5;
			vc.addVertex(pose, data[o], data[o + 1], data[o + 2]).setUv(data[o + 3], data[o + 4]).setColor(colors[i]);
		}
		if (consumers instanceof MultiBufferSource.BufferSource bs) {
			bs.endBatch(type);
		}
	}
}
