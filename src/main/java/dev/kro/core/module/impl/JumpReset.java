package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class JumpReset extends Module {
	public final NumberSetting chance = number("Chance", "How often a hit is answered with a timed jump.", 80, 0, 100, 1, "%");
	public final RangeSetting delay = range("Delay", "Ticks to wait after the hit before jumping.", 0, 0, 0, 3, 1, "t");
	public final BoolSetting onlyMoving = bool("Only while moving", "Skip jump resets while you stand still.", false);
	public final BoolSetting onlyAttacker = bool("Only from players", "React only to hits from other players.", true);

	public JumpReset() {
		super("Jump Reset", "Times a jump the moment you are hit to soften knockback, like a practiced player.", Category.COMBAT, Icons.CHEVRONS_UP);
	}
}
