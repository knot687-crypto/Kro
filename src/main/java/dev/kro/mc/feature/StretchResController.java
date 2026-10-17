package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.StretchRes;

/**
 * Stretch Res: the world's projection is built for the chosen resolution's width-to-height ratio
 * instead of the window's, then drawn across the whole window, exactly how a stretched resolution
 * looks. The vertical field of view is kept, the horizontal one narrows, and everything in the
 * world (your hand included) is widened. The HUD and menus are drawn apart and stay sharp.
 */
public final class StretchResController {
	private final Kro kro;

	public StretchResController(Kro kro) {
		this.kro = kro;
	}

	/** The width-to-height ratio the world is projected with, given the window's. */
	public float aspect(float window) {
		StretchRes m = kro.modules.stretchRes;
		return m.enabled() ? m.aspect() : window;
	}

	/** The width the first-person hand is projected for, given the window's size. */
	public int width(int width, int height) {
		StretchRes m = kro.modules.stretchRes;
		return m.enabled() && height > 0 ? Math.max(1, Math.round(height * m.aspect())) : width;
	}
}
