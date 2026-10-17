package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class AutoMace extends Module {
	public final NumberSetting minFall = number("Min fall", "Swap only after falling at least this far; a mace smashes from 1.5 blocks.", 1.5, 1.5, 10.0, 0.1, "m");
	public final ModeSetting swapBack = mode("Swap back", "Instantly keeps your weapon in hand between hits; On landing keeps the mace until you touch down.", "Instantly", "Instantly", "On landing", "Never");
	public final BoolSetting playersOnly = bool("Players only", "Swap only when hitting players.", false);

	public AutoMace() {
		super("Auto Mace", "Swaps to your mace the instant a falling hit lands, so it smashes with all the fall behind it.", Category.COMBAT, Icons.ARROW_BIG_UP_DASH);
	}
}
