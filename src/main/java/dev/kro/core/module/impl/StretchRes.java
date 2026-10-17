package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class StretchRes extends Module {
	public final ModeSetting ratio = mode("Ratio", "4:3 is the classic 1440×1080 stretch; 5:4 stretches further, 16:10 just a touch.", "4:3", "4:3", "5:4", "3:2", "16:10", "Custom");
	public final NumberSetting width = number("Width", "Width of the custom resolution.", 1440, 640, 3840, 10, "px");
	public final NumberSetting height = number("Height", "Height of the custom resolution.", 1080, 480, 2160, 10, "px");

	public StretchRes() {
		super("Stretch Res", "Plays like a stretched resolution: the world is drawn at a narrower ratio and stretched across your screen, so players look wider. Your HUD stays sharp.", Category.VISUAL, Icons.MAXIMIZE_2);
		width.visibleWhen(() -> ratio.is("Custom"));
		height.visibleWhen(() -> ratio.is("Custom"));
	}

	/** Width over height of the resolution being played at. */
	public float aspect() {
		return switch (ratio.get()) {
			case "5:4" -> 5f / 4f;
			case "3:2" -> 3f / 2f;
			case "16:10" -> 16f / 10f;
			case "Custom" -> (float) (width.value() / height.value());
			default -> 4f / 3f;
		};
	}
}
