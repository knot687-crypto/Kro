package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class Trails extends Module {
	public final NumberSetting length = number("Length", "How long the trail lingers behind you.", 1.2, 0.3, 4.0, 0.1, "s");
	public final NumberSetting width = number("Width", "Thickness of the ribbon.", 1.0, 0.4, 2.5, 0.05, "×");
	public final ModeSetting palette = mode("Color", "Accent glides between your accent shades; Rainbow sweeps the spectrum.", "Accent", "Accent", "Rainbow", "Custom");
	public final ColorSetting customColor = color("Custom color", "Colour used when Color is Custom.", 0xB49CFF);
	public final BoolSetting everyone = bool("Everyone", "Trails for other players too.", false);

	public Trails() {
		super("Trails", "A ribbon of light follows your steps and slowly fades away.", Category.WORLD, Icons.WAVES);
		customColor.visibleWhen(() -> palette.is("Custom"));
	}
}
