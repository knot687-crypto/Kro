package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.Reach;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Reach: your crosshair picks out bodies a little further away than vanilla's entity reach. Only
 * that reach grows, so blocks still stop it exactly as they stop a normal hit, and the distance is
 * picked fresh after every swing so no two hits reach quite the same.
 */
public final class ReachController {
	private final Kro kro;
	/** Reach for the coming swing; 0 keeps vanilla's, below 0 means not picked yet. */
	private double next = -1.0;

	public ReachController(Kro kro) {
		this.kro = kro;
	}

	/** Your entity reach as the crosshair sees it, given vanilla's. */
	public double range(double vanilla) {
		Reach m = kro.modules.reach;
		if (!m.enabled()) {
			return vanilla;
		}
		if (next < 0.0) {
			roll(m);
		}
		return Math.max(vanilla, next);
	}

	/** A swing just left: the next one gets its own distance. */
	public void onAttack() {
		roll(kro.modules.reach);
	}

	private void roll(Reach m) {
		next = ThreadLocalRandom.current().nextDouble(100.0) < m.chance.value() ? m.distance.random() : 0.0;
	}
}
