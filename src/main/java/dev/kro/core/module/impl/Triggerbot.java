package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class Triggerbot extends Module {
	public final ModeSetting timing = mode("Timing", "Cooldown waits for a charged swing; CPS clicks at a human rate.", "Cooldown", "Cooldown", "CPS");
	public final NumberSetting charge = number("Charge", "Attack once the swing is this charged.", 94, 70, 100, 1, "%");
	public final RangeSetting cps = range("Clicks per second", "Random click rate window.", 8, 11, 1, 20, 0.5, "");
	public final RangeSetting reaction = range("Reaction", "Human-like delay before the first hit on a new target.", 60, 140, 0, 400, 5, "ms");
	public final BoolSetting weaponOnly = bool("Weapon only", "Only fire while holding a weapon.", true);
	public final ModeSetting targets = mode("Targets", "Which entities can trigger an attack.", "Players", "Players", "Mobs", "All");
	public final BoolSetting ignoreTeam = bool("Ignore teammates", "Never fire on players of your team.", true);
	public final BoolSetting ignoreInvisible = bool("Ignore invisible", "Never fire on invisible entities.", true);

	public Triggerbot() {
		super("Triggerbot", "Attacks when your crosshair rests on a target, with human reaction timing.", Category.COMBAT, Icons.MOUSE_POINTER_CLICK);
		charge.visibleWhen(() -> timing.is("Cooldown"));
		cps.visibleWhen(() -> timing.is("CPS"));
	}
}
