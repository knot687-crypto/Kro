package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;

/**
 * No Click Delay: a swing at the air makes vanilla ignore attacks for the next ten ticks. That
 * lockout is cleared at the start of every tick and before every attack, so a miss never eats
 * your next click. Vanilla parks the same timer far higher while a screen is open or the mouse is
 * being grabbed, so a click into the game never swings; that guard is left alone, and so is the
 * attack cooldown, which is the server's.
 */
public final class NoClickDelayController {
	/** The lockout vanilla sets after a missed swing. */
	private static final int MISS_LOCKOUT = 10;

	private final Kro kro;

	public NoClickDelayController(Kro kro) {
		this.kro = kro;
	}

	/** Start of a tick, and the moment before an attack is handled. */
	public void clear(Minecraft mc) {
		if (!kro.modules.noClickDelay.enabled()) {
			return;
		}
		MinecraftAccessor timers = (MinecraftAccessor) mc;
		int miss = timers.kro$missTime();
		if (miss > 0 && miss <= MISS_LOCKOUT) {
			timers.kro$setMissTime(0);
		}
	}
}
