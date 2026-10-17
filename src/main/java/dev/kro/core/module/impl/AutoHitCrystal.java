package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class AutoHitCrystal extends Module {
	public final RangeSetting delay = range("Step delay", "Extra ticks between each swap, place and hit, randomised.", 0, 1, 0, 6, 1, "t");
	public final BoolSetting hit = bool("Break", "Pop the crystal right after placing it.", true);
	public final BoolSetting swapBack = bool("Swap back", "Return to your weapon when the hit is done.", true);
	public final BoolSetting weaponOnly = bool("Start from weapon", "Only begin while holding a sword, axe or mace.", true);

	public AutoHitCrystal() {
		super("Auto Hit Crystal", "Right click a block with your weapon to place obsidian and a crystal, pop it, and swap back.", Category.CRYSTAL, Icons.ZAP);
	}
}
