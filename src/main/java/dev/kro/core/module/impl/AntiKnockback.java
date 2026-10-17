package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class AntiKnockback extends Module {
	public final NumberSetting horizontal = number("Horizontal", "Share of sideways knockback you still take.", 90, 0, 100, 1, "%");
	public final NumberSetting vertical = number("Vertical", "Share of upward knockback you still take.", 100, 0, 100, 1, "%");
	public final NumberSetting chance = number("Chance", "How often knockback is reduced.", 100, 0, 100, 1, "%");
	public final BoolSetting explosions = bool("Explosions", "Also reduce the push from explosions.", true);

	public AntiKnockback() {
		super("Anti Knockback", "Reduces the knockback you take from hits and explosions.", Category.COMBAT, Icons.SHIELD);
	}
}
