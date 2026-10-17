package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;

/**
 * No Click Delay: a swing at the air makes vanilla ignore attacks for the next ten ticks. That
 * lockout is cleared at the start of every tick and before every attack, so a miss never eats
 * your next click. The attack cooldown itself is the server's and is left alone.
 */
public final class NoClickDelayController {
	private final Kro kro;

	public NoClickDelayController(Kro kro) {
		this.kro = kro;
	}

	/** Start of a tick, and the moment before an attack is handled. */
	public void clear(Minecraft mc) {
		if (kro.modules.noClickDelay.enabled()) {
			((MinecraftAccessor) mc).kro$setMissTime(0);
		}
	}
}
