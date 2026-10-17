package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.ColorSetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.setting.NumberSetting;
import dev.kro.ui.render.Icons;

public final class WorldTheme extends Module {
	public final ModeSetting preset = mode("Preset", "A curated mood for sky, fog, light and ambience.", "Aurora", "Aurora", "Sakura", "Midnight", "Ember", "Frost", "Custom");
	public final BoolSetting customTime = bool("Custom time", "Override the preset's time of day.", false);
	public final NumberSetting time = number("Time of day", "Client-side clock, 0h is sunrise.", 13.0, 0, 24, 0.25, "h");
	public final NumberSetting skyTint = number("Sky tint", "How strongly the sky takes on the theme.", 70, 0, 100, 1, "%");
	public final NumberSetting fogTint = number("Fog tint", "How strongly the fog takes on the theme.", 60, 0, 100, 1, "%");
	public final ColorSetting skyColor = color("Sky color", "Sky colour for the Custom preset.", 0x5B6CFF);
	public final ColorSetting fogColor = color("Fog color", "Fog colour for the Custom preset.", 0x9D7BFF);
	public final BoolSetting particles = bool("Ambient particles", "Petals, fireflies, snow or embers drifting around you.", true);
	public final NumberSetting density = number("Density", "Ambient particle density.", 1.0, 0.2, 3.0, 0.1, "×");
	public final BoolSetting clearWeather = bool("Clear weather", "Hide rain and thunder on your screen.", true);

	/** A preset's look: sky, fog, particle colour and time of day in hours (0h = sunrise). */
	public record Look(int sky, int fog, int particle, float hours) {
	}

	public static Look look(String preset) {
		return switch (preset) {
			case "Sakura" -> new Look(0xFFFF9CC5, 0xFFFFD1E1, 0xFFFFB7D0, 23.5f);
			case "Midnight" -> new Look(0xFF2A3D9E, 0xFF141E4F, 0xFFC8FF6E, 18.0f);
			case "Ember" -> new Look(0xFFFF7A3D, 0xFFB8452A, 0xFFFFA14A, 12.6f);
			case "Frost" -> new Look(0xFFA9D8FF, 0xFFE6F5FF, 0xFFFFFFFF, 5.0f);
			case "Custom" -> new Look(0xFF5B6CFF, 0xFF9D7BFF, 0xFFFFFFFF, 6.0f);
			default -> new Look(0xFF6D5BFF, 0xFF34C6D3, 0xFF9DF5FF, 12.9f);
		};
	}

	/** The effective look, honouring the Custom preset and custom time. */
	public Look currentLook() {
		Look base = look(preset.get());
		int sky = preset.is("Custom") ? skyColor.color() : base.sky();
		int fog = preset.is("Custom") ? fogColor.color() : base.fog();
		float hours = customTime.on() ? time.floatValue() : base.hours();
		return new Look(sky, fog, base.particle(), hours);
	}

	public WorldTheme() {
		super("World Theme", "Restyles the sky, fog, time and ambience of the world. Visual only, just for you.", Category.WORLD, Icons.SUN_MOON);
		time.visibleWhen(customTime::on);
		skyColor.visibleWhen(() -> preset.is("Custom"));
		fogColor.visibleWhen(() -> preset.is("Custom"));
		density.visibleWhen(particles::on);
	}
}
