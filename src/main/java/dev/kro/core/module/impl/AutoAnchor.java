package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class AutoAnchor extends Module {
	public final ModeSetting activation = mode("Activation", "Hold use works while you hold right click on an anchor; Always as soon as you look at one.", "Hold use", "Hold use", "Always");
	public final NumberSetting charges = number("Charges", "Glowstone charges loaded before the blast.", 1, 1, 4, 1, "");
	public final ModeSetting detonate = mode("Detonate with", "What you hold when the anchor goes off. Totem leaves you ready for the blast.", "Totem", "Totem", "Previous item");
	public final BoolSetting swapBack = bool("Swap back", "Return to the item you were holding afterwards.", true);
	public final RangeSetting delay = range("Step delay", "Ticks between each swap and click, randomised.", 1, 2, 0, 6, 1, "t");
	public final BoolSetting onlyExplosive = bool("Only where it explodes", "Stay idle in the Nether, where anchors set your spawn instead.", true);

	public AutoAnchor() {
		super("Auto Anchor", "Charges a respawn anchor with glowstone and sets it off for you, in a few quick human steps.", Category.CRYSTAL, Icons.BOLT);
	}
}
