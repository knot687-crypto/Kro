package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class LowFire extends Module {
	public final NumberSetting lower = number("Lower by", "How far the fire on your screen sinks.", 50, 0, 100, 5, "%");
	public final NumberSetting opacity = number("Opacity", "How solid the fire overlay looks.", 80, 10, 100, 5, "%");
	public final BoolSetting hideResistant = bool("Hide with Fire Resistance", "Remove the overlay entirely while fire can't hurt you.", true);

	public LowFire() {
		super("Low Fire", "Lowers the fire on your screen while you burn, so it never blocks your view.", Category.VISUAL, Icons.FLAME);
	}
}
