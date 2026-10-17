package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class PopEffects extends Module {
	public final ModeSetting who = mode("Show for", "Whose totem pops get the burst.", "Everyone", "Everyone", "Others", "Me");
	public final ModeSetting palette = mode("Color", "Totem gold and emerald, your accent, or a colour of your own.", "Totem", "Totem", "Accent", "Custom");
	public final ColorSetting customColor = color("Custom color", "Colour used when Color is Custom.", 0xFFD36B);
	public final NumberSetting scale = number("Scale", "Size of the whole burst.", 1.0, 0.5, 2.0, 0.05, "×");
	public final BoolSetting replaceParticles = bool("Replace vanilla particles", "Hide the green and yellow totem particles; the burst takes their place.", true);

	public PopEffects() {
		super("Pop Effects", "A burst of light when a totem saves someone: a rising spiral, a shockwave and a pillar of light.", Category.WORLD, Icons.PARTY_POPPER);
		customColor.visibleWhen(() -> palette.is("Custom"));
	}
}
