package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class BreachSwap extends Module {
	public final BoolSetting breachOnly = bool("Breach only", "Swap only to a mace enchanted with Breach.", true);
	public final BoolSetting playersOnly = bool("Players only", "Swap only when hitting players.", true);
	public final NumberSetting chance = number("Chance", "How often a hit is swapped.", 100, 0, 100, 1, "%");

	public BreachSwap() {
		super("Breach Swap", "Hits with your sword while swapping to a Breach mace in the same instant, so the blow cuts through armor.", Category.COMBAT, Icons.SHIELD_HALF);
	}
}
