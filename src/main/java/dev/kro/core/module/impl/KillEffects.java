package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.ui.render.Icons;

public final class KillEffects extends Module {
	public final ModeSetting style = mode("Style", "Lightning strikes from the sky, Soul rises in light, Shockwave bursts outward.", "Lightning", "Lightning", "Soul", "Shockwave");
	public final ModeSetting targets = mode("Show for", "Your own kills, or every player who dies near you.", "My kills", "My kills", "All players");
	public final ModeSetting palette = mode("Color", "Your accent or a colour of your own.", "Accent", "Accent", "Custom");
	public final ColorSetting customColor = color("Custom color", "Colour used when Color is Custom.", 0x8FB8FF);
	public final BoolSetting sound = bool("Thunder", "A crack of thunder with every strike.", true);

	public KillEffects() {
		super("Kill Effects", "Marks every kill with a strike of lightning, a rising soul or a shockwave.", Category.WORLD, Icons.ZAP);
		customColor.visibleWhen(() -> palette.is("Custom"));
		sound.visibleWhen(() -> style.is("Lightning"));
	}
}
