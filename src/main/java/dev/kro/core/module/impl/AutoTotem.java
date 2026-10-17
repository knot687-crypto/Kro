package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class AutoTotem extends Module {
	public final ModeSetting mode = mode("Mode", "Always refills your offhand the moment it empties; Inventory swaps only while your inventory is open.", "Always", "Always", "Inventory");
	public final RangeSetting delay = range("Delay", "Wait before each swap, randomised.", 60, 140, 0, 600, 10, "ms");
	public final BoolSetting replace = bool("Replace offhand", "Swap out whatever else is in your offhand.", true);
	public final ModeSetting hotbar = mode("Hotbar totem", "Also keep a totem in this hotbar slot while your inventory is open.", "Off", "Off", "1", "2", "3", "4", "5", "6", "7", "8", "9");

	public AutoTotem() {
		super("Auto Totem", "Keeps a Totem of Undying in your offhand, refilled right after every pop.", Category.CRYSTAL, Icons.HEART_PULSE);
	}

	/** Hotbar slot index 0..8 to keep stocked, or -1. */
	public int hotbarSlot() {
		return hotbar.index() - 1;
	}
}
