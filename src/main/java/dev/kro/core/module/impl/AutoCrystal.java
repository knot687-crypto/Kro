package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class AutoCrystal extends Module {
	public final ModeSetting activation = mode("Activation", "Hold use works while you hold right click with crystals; Always whenever crystals are in your hand.", "Hold use", "Hold use", "Always");
	public final BoolSetting place = bool("Place", "Set a crystal on the obsidian or bedrock under your crosshair.", true);
	public final RangeSetting placeDelay = range("Place delay", "Ticks from a free spot appearing to the placement, randomised.", 1, 2, 0, 8, 1, "t");
	public final BoolSetting hit = bool("Break", "Hit the crystal under your crosshair the moment it lands.", true);
	public final RangeSetting breakDelay = range("Break delay", "Ticks from a crystal appearing to the hit, randomised.", 1, 2, 0, 8, 1, "t");
	public final BoolSetting pauseOnEat = bool("Pause while eating", "Hold off while you eat, drink or block.", true);

	public AutoCrystal() {
		super("Auto Crystal", "Places and pops end crystals the instant your crosshair lines up, with human timing.", Category.CRYSTAL, Icons.ATOM);
		placeDelay.visibleWhen(place::on);
		breakDelay.visibleWhen(hit::on);
	}
}
