package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class LowOnFire extends Module {
	public final NumberSetting height = number("Flame height", "How high flames reach on burning players and mobs. 0% hides them.", 40, 0, 100, 5, "%");

	public LowOnFire() {
		super("Low On Fire", "Keeps the flames on burning players and mobs low, so they never hide your target.", Category.VISUAL, Icons.FLAME);
	}
}
