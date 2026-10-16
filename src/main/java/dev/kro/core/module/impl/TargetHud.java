package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class TargetHud extends Module {
	public final ModeSetting style = mode("Style", "Card shows the full breakdown; Compact is a slim bar.", "Card", "Card", "Compact");
	public final ModeSetting avatar = mode("Avatar", "Player face or a live 3D model of your opponent.", "Face", "Face", "Model");
	public final BoolSetting armor = bool("Armor", "Show the target's armor points.", true);
	public final BoolSetting distance = bool("Distance", "Show the distance to the target.", true);
	public final BoolSetting duel = bool("Duel status", "Compare health and show who is ahead.", true);
	public final ModeSetting healthColor = mode("Health color", "Health bar colouring.", "Dynamic", "Dynamic", "Accent");
	public final NumberSetting scale = number("Scale", "Size of the card.", 1.0, 0.6, 1.6, 0.05, "×");
	/** Card centre, as a fraction of the screen. Edited by dragging, not shown as a row. */
	public final NumberSetting posX = number("X", "", 0.5, 0, 1, 0.0001, "");
	public final NumberSetting posY = number("Y", "", 0.64, 0, 1, 0.0001, "");

	public TargetHud() {
		super("Target HUD", "A clean card with your opponent's health, armor and the state of the duel.", Category.VISUAL, Icons.SCAN_FACE);
		avatar.visibleWhen(() -> style.is("Card"));
		armor.visibleWhen(() -> style.is("Card"));
		duel.visibleWhen(() -> style.is("Card"));
		posX.visibleWhen(() -> false);
		posY.visibleWhen(() -> false);
	}
}
