package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class SmallerTotem extends Module {
	public final NumberSetting popSize = number("Pop size", "Size of the totem that flies up when one saves you. 0% hides it.", 45, 0, 100, 5, "%");
	public final NumberSetting heldSize = number("Held size", "Size of a totem held in first person.", 70, 30, 100, 5, "%");

	public SmallerTotem() {
		super("Smaller Totem", "Shrinks the totem pop animation and the totem in your hand, so neither covers the fight.", Category.VISUAL, Icons.SHAPES);
	}
}
