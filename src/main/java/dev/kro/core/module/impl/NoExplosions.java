package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.ui.render.Icons;

public final class NoExplosions extends Module {
	public final BoolSetting cloud = bool("Blast cloud", "Hide the big puff at the centre of an explosion.", true);
	public final BoolSetting debris = bool("Debris", "Hide the smoke and dust thrown out around it.", true);
	public final BoolSetting shockwave = bool("Shockwave", "In their place, a clean ring of light sweeps out from the blast.", true);

	public NoExplosions() {
		super("No Explosions", "Clears explosion clouds and debris from your view. Sound and knockback stay exactly as they are.", Category.VISUAL, Icons.EYE_OFF);
	}
}
