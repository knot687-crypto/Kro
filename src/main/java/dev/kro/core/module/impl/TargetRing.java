package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class TargetRing extends Module {
	public final ModeSetting style = mode("Style", "Halo glides up and down your target; Orbit circles it with three lights.", "Halo", "Halo", "Orbit");
	public final ModeSetting palette = mode("Color", "Accent glides between your accent shades; Rainbow sweeps the spectrum.", "Accent", "Accent", "Rainbow", "Custom");
	public final ColorSetting customColor = color("Custom color", "Colour used when Color is Custom.", 0xFF6FA8);
	public final NumberSetting speed = number("Speed", "How fast it moves.", 1.0, 0.3, 3.0, 0.1, "×");
	public final BoolSetting hurtFlash = bool("Hurt flash", "Flash red each time your hit lands.", true);

	public TargetRing() {
		super("Target Ring", "A ring of light that follows the opponent you are fighting.", Category.WORLD, Icons.TARGET);
		customColor.visibleWhen(() -> palette.is("Custom"));
	}
}
