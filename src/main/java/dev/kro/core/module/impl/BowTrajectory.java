package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.ui.render.Icons;

public final class BowTrajectory extends Module {
	public final ModeSetting palette = mode("Color", "Accent glides between your accent shades; Rainbow sweeps the spectrum.", "Accent", "Accent", "Rainbow", "Custom");
	public final ColorSetting customColor = color("Custom color", "Colour used when Color is Custom.", 0x7AD7FF);
	public final BoolSetting throwables = bool("Throwables", "Also show arcs for tridents, pearls, snowballs, eggs, potions and wind charges.", true);
	public final BoolSetting beforeDraw = bool("Before drawing", "Show the full-power arc while just holding a bow, before you pull it back.", true);
	public final BoolSetting highlight = bool("Highlight target", "Mark the player or mob the shot would hit.", true);

	public BowTrajectory() {
		super("Bow Trajectory", "Draws the arc your arrow, trident or throw will fly and marks exactly where it lands.", Category.VISUAL, Icons.CROSSHAIR);
		customColor.visibleWhen(() -> palette.is("Custom"));
	}
}
