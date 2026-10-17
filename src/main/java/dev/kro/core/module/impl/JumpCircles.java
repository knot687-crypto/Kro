package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class JumpCircles extends Module {
	public final ModeSetting palette = mode("Color", "Accent glides between your accent shades; Rainbow sweeps the spectrum.", "Accent", "Accent", "Rainbow", "Custom");
	public final ColorSetting customColor = color("Custom color", "Colour used when Color is Custom.", 0x7AD7FF);
	public final NumberSetting size = number("Size", "How wide each circle grows.", 1.0, 0.5, 2.0, 0.05, "×");
	public final BoolSetting onLand = bool("On landing", "Also ripple when you land.", false);
	public final BoolSetting everyone = bool("Everyone", "Circles for other players too.", false);

	public JumpCircles() {
		super("Jump Circles", "Rings of light ripple out under your feet every time you jump.", Category.WORLD, Icons.CIRCLE_DOT);
		customColor.visibleWhen(() -> palette.is("Custom"));
	}
}
