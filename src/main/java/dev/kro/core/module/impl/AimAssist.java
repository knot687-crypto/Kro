package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class AimAssist extends Module {
	public final NumberSetting strength = number("Strength", "How firmly your aim is eased toward the target.", 3.5, 1.0, 10.0, 0.1, "");
	public final NumberSetting fov = number("Field of view", "Only targets within this cone around your crosshair are assisted.", 70, 15, 180, 1, "°");
	public final NumberSetting range = number("Range", "Maximum distance to a target.", 4.2, 3.0, 6.0, 0.1, "m");
	public final BoolSetting vertical = bool("Vertical", "Also guide pitch, at a gentler rate.", false);
	public final BoolSetting clickOnly = bool("Only while clicking", "Assist only while you are actively attacking.", true);
	public final BoolSetting weaponOnly = bool("Weapon only", "Assist only while holding a sword, axe, mace, spear or trident.", false);
	public final ModeSetting targets = mode("Targets", "Which entities can be assisted onto.", "Players", "Players", "Mobs", "All");
	public final BoolSetting ignoreTeam = bool("Ignore teammates", "Skip players on your team.", true);
	public final BoolSetting ignoreInvisible = bool("Ignore invisible", "Skip invisible entities.", true);
	public final BoolSetting humanize = bool("Humanize", "Vary speed slightly per engagement for natural motion.", true);

	public AimAssist() {
		super("Aim Assist", "Gently eases your crosshair onto nearby targets while you fight. Never snaps, never locks.", Category.COMBAT, Icons.CROSSHAIR);
	}
}
