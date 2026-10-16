package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class HitSparks extends Module {
	public final ModeSetting style = mode("Style", "Glow draws Kro's luminous sparks; the others use vanilla particles.", "Glow", "Glow", "Crit", "Magic", "Electric");
	public final ModeSetting palette = mode("Color", "Spark colour source.", "Accent", "Accent", "Custom", "Spectrum");
	public final ColorSetting customColor = color("Custom color", "Colour used when Color is Custom.", 0xFF7AA2);
	public final NumberSetting amount = number("Amount", "Sparks per hit.", 14, 4, 40, 1, "");
	public final NumberSetting size = number("Size", "Spark scale.", 1.0, 0.5, 2.0, 0.05, "×");
	public final NumberSetting spread = number("Spread", "How far sparks travel.", 1.0, 0.5, 2.0, 0.05, "×");
	public final BoolSetting flash = bool("Impact flash", "A soft ring blooms at the point of impact.", true);
	public final BoolSetting gravity = bool("Gravity", "Sparks arc and fall naturally.", true);
	public final BoolSetting critsOnly = bool("Crits only", "Only spark on critical hits.", false);

	public HitSparks() {
		super("Hit Sparks", "Beautiful glowing sparks burst from every hit you land.", Category.VISUAL, Icons.SPARKLES);
		palette.visibleWhen(() -> style.is("Glow"));
		customColor.visibleWhen(() -> style.is("Glow") && palette.is("Custom"));
		size.visibleWhen(() -> style.is("Glow"));
		flash.visibleWhen(() -> style.is("Glow"));
		gravity.visibleWhen(() -> style.is("Glow"));
	}
}
