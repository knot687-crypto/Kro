package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.KeySetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.core.util.Keys;
import dev.kro.ui.render.Icons;

/** Client-wide preferences. Always enabled; opened from the centre of the hub. */
public final class Interface extends Module {
	public final ModeSetting accent = mode("Accent", "Signature colour of the whole client.", "Iris", "Iris", "Rose", "Mint", "Ember", "Frost", "Mono");
	public final ModeSetting window = mode("Window", "Fill the menu with your picture under frosted glass, or keep it plain black. Swap the picture: config/kro/wallpaper.png", "Picture", "Picture", "Black");
	public final NumberSetting blur = number("Game blur", "How much the game behind the menu is blurred.", 5, 0, 10, 1, "");
	public final NumberSetting dim = number("Game dim", "How much the game behind the menu is darkened.", 25, 0, 80, 1, "%");
	public final NumberSetting animationSpeed = number("Animation speed", "Speed of every transition.", 1.0, 0.5, 2.0, 0.1, "×");
	public final BoolSetting notifications = bool("Notifications", "Show a toast when a module is toggled by key.", true);
	public final BoolSetting watermark = bool("Watermark", "A small Kro mark in the corner of your HUD.", false);
	public final BoolSetting sounds = bool("UI sounds", "Soft clicks when you interact with the menu.", true);
	public final KeySetting menuKey = key("Menu key", "Opens and closes this menu.", Keys.RIGHT_SHIFT);

	public Interface() {
		super("Interface", "Personalise Kro: accent colour, background picture, motion, sound and HUD extras.", Category.CLIENT, Icons.SETTINGS_2);
		setEnabled(true);
	}

	@Override
	public boolean alwaysOn() {
		return true;
	}
}
