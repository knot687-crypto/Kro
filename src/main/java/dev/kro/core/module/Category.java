package dev.kro.core.module;

import dev.kro.ui.render.Icons;

public enum Category {
	COMBAT("Combat", 0xFFF2768B, Icons.SWORDS),
	MOVEMENT("Movement", 0xFF5CC2F2, Icons.WIND),
	VISUAL("Visual", 0xFFB49CFF, Icons.SPARKLES),
	CLIENT("Client", 0xFFF2C46B, Icons.SETTINGS);

	private final String title;
	private final int color;
	private final char icon;

	Category(String title, int color, char icon) {
		this.title = title;
		this.color = color;
		this.icon = icon;
	}

	public String title() {
		return title;
	}

	/** Muted signature hue of the category, opaque ARGB. */
	public int color() {
		return color;
	}

	public char icon() {
		return icon;
	}
}
