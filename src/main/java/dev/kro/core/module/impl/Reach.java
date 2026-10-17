package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class Reach extends Module {
	public final RangeSetting distance = range("Distance", "How far each swing reaches, picked fresh for every hit. Vanilla is 3.", 3.1, 3.3, 3.0, 6.0, 0.05, "m");
	public final NumberSetting chance = number("Chance", "How often a swing gets the longer reach.", 100, 0, 100, 1, "%");

	public Reach() {
		super("Reach", "Lets your hits land a little beyond vanilla's three blocks, never through walls.", Category.COMBAT, Icons.RULER);
	}
}
