package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class WTap extends Module {
	public final NumberSetting chance = number("Chance", "How often a sprint hit is followed by a tap.", 90, 0, 100, 1, "%");
	public final RangeSetting delay = range("Delay", "Ticks after the hit before letting go of forward.", 0, 1, 0, 4, 1, "t");
	public final RangeSetting release = range("Release", "Ticks forward stays released.", 1, 2, 1, 5, 1, "t");
	public final BoolSetting playersOnly = bool("Players only", "Tap only after hitting players.", true);

	public WTap() {
		super("W Tap", "Lets go of forward for a moment after each sprint hit, so the next one lands with full sprint knockback.", Category.COMBAT, Icons.CHEVRONS_RIGHT);
	}
}
