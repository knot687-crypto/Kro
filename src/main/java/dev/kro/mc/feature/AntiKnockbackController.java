package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.AntiKnockback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scales the knockback the server hands you as it arrives: the motion set by a hit, arrow or rod,
 * and the push of an explosion. Wind charges and wind bursts are left whole, so your own
 * launches keep their full height.
 */
public final class AntiKnockbackController {
	private final Kro kro;

	public AntiKnockbackController(Kro kro) {
		this.kro = kro;
	}

	/** The motion the server just gave {@code entity}; yours comes back with knockback reduced. */
	public Vec3 motion(Entity entity, Vec3 motion) {
		AntiKnockback m = kro.modules.antiKnockback;
		if (!m.enabled() || entity != Minecraft.getInstance().player || !roll(m)) {
			return motion;
		}
		return scale(m, motion);
	}

	/** An explosion's push on you, given the particle the blast shows. */
	public Optional<Vec3> explosion(Optional<Vec3> push, ParticleOptions particle) {
		AntiKnockback m = kro.modules.antiKnockback;
		if (!m.enabled() || !m.explosions.on() || push.isEmpty() || isWind(particle) || !roll(m)) {
			return push;
		}
		return Optional.of(scale(m, push.get()));
	}

	private static boolean roll(AntiKnockback m) {
		return ThreadLocalRandom.current().nextDouble(100.0) < m.chance.value();
	}

	private static Vec3 scale(AntiKnockback m, Vec3 v) {
		double h = m.horizontal.value() / 100.0;
		return new Vec3(v.x * h, v.y * (m.vertical.value() / 100.0), v.z * h);
	}

	private static boolean isWind(ParticleOptions particle) {
		ParticleType<?> type = particle.getType();
		return type == ParticleTypes.GUST_EMITTER_SMALL || type == ParticleTypes.GUST_EMITTER_LARGE;
	}
}
