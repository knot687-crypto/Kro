package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.AimAssist;
import dev.kro.core.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Gentle aim assistance, applied per frame like mouse input. It only pulls the crosshair to
 * the nearest edge of a target's hitbox and stops as soon as you are on it, never past it,
 * with speed capped and eased so it feels like a steadier hand rather than a lock.
 */
public final class AimAssistController {
	private static final float INSET = 0.18f;

	private final Kro kro;
	private LivingEntity target;
	private float engagementSpeed = 1f;
	private double noisePhase;

	public AimAssistController(Kro kro) {
		this.kro = kro;
	}

	/** Called from the mouse handler every frame with the seconds since the previous frame. */
	public void onFrame(double frameSeconds) {
		AimAssist m = kro.modules.aimAssist;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!m.enabled() || player == null || mc.level == null || mc.screen != null || player.isSpectator()) {
			target = null;
			return;
		}
		boolean clicking = mc.options.keyAttack.isDown() || kro.keys.sinceAttackClick() < 450L;
		if (m.clickOnly.on() && !clicking) {
			return;
		}
		if (m.weaponOnly.on() && !Targets.isWeapon(player.getMainHandItem())) {
			return;
		}
		if (player.isUsingItem()) {
			return;
		}
		float dt = (float) Math.min(Math.max(frameSeconds, 0.0), 0.05);
		if (dt <= 0f) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		LivingEntity next = pick(mc, player, m, eye, look);
		if (next != target) {
			target = next;
			engagementSpeed = m.humanize.on() ? 0.82f + ThreadLocalRandom.current().nextFloat() * 0.36f : 1f;
		}
		if (target == null) {
			return;
		}

		AABB box = target.getBoundingBox().deflate(target.getBbWidth() * INSET / 2.0, target.getBbHeight() * INSET / 2.0, target.getBbWidth() * INSET / 2.0);
		Vec3 aim = Targets.nearestOnBox(box, eye, look);
		float[] want = Targets.angles(eye, aim);
		float yawErr = MathUtil.wrapDegrees(want[0] - player.getYRot());
		float pitchErr = want[1] - player.getXRot();

		float strength = m.strength.floatValue();
		float rate = 1.6f * strength * engagementSpeed;
		float maxSpeed = (28f + 20f * strength) * engagementSpeed;
		if (m.humanize.on()) {
			noisePhase += dt * 3.1;
			rate *= 0.9f + 0.1f * (float) Math.sin(noisePhase);
		}
		float k = 1f - (float) Math.exp(-rate * dt);
		float yawStep = clampStep(yawErr * k, maxSpeed * dt);
		float pitchStep = 0f;
		if (m.vertical.on()) {
			pitchStep = clampStep(pitchErr * k * 0.55f, maxSpeed * 0.5f * dt);
		}
		if (Math.abs(yawStep) < 1.0e-3f && Math.abs(pitchStep) < 1.0e-3f) {
			return;
		}
		// Entity.turn expects mouse units and applies a 0.15 factor itself.
		player.turn(yawStep / 0.15, pitchStep / 0.15);
	}

	private static float clampStep(float step, float max) {
		return Math.max(-max, Math.min(max, step));
	}

	private LivingEntity pick(Minecraft mc, LocalPlayer player, AimAssist m, Vec3 eye, Vec3 look) {
		double range = m.range.value();
		float halfFov = m.fov.floatValue() / 2f;
		LivingEntity best = null;
		double bestAngle = Double.MAX_VALUE;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!Targets.accepts(player, e, m.targets.get(), m.ignoreTeam.on(), m.ignoreInvisible.on())) {
				continue;
			}
			LivingEntity living = (LivingEntity) e;
			AABB box = living.getBoundingBox();
			Vec3 nearest = Targets.nearestOnBox(box, eye, look);
			if (nearest.distanceTo(eye) > range) {
				continue;
			}
			Vec3 dir = box.getCenter().subtract(eye).normalize();
			double angle = Math.toDegrees(Math.acos(MathUtil.clamp(dir.dot(look), -1.0, 1.0)));
			// keep the current target a little longer to avoid flicking between two players
			double limit = living == target ? halfFov * 1.2 : halfFov;
			if (angle > limit || !player.hasLineOfSight(living)) {
				continue;
			}
			double score = living == target ? angle * 0.75 : angle;
			if (score < bestAngle) {
				bestAngle = score;
				best = living;
			}
		}
		return best;
	}
}
