package dev.kro.ui.render;

import dev.kro.core.util.Colors;

/**
 * Kro's design tokens. True black surfaces with neutral greys (no colour cast), lifted by
 * hairline rims rather than lighter fills; a single accent hue reserved for things that are
 * on, selected or being dragged.
 */
public final class Theme {
	// surfaces
	public static final int WINDOW_TOP = 0xF50B0B0C;
	public static final int WINDOW_BOTTOM = 0xF7060607;
	public static final int PANEL_TOP = 0xEB0B0B0C;
	public static final int PANEL_BOTTOM = 0xEE060607;
	public static final int POPOVER = 0xF80F0F10;
	public static final int SURFACE = 0x0AFFFFFF;
	public static final int SURFACE_HOVER = 0x12FFFFFF;
	public static final int SURFACE_ACTIVE = 0x1AFFFFFF;
	public static final int FIELD = 0x0EFFFFFF;
	public static final int BORDER = 0x16FFFFFF;
	public static final int BORDER_STRONG = 0x29FFFFFF;
	public static final int DIVIDER = 0x10FFFFFF;
	public static final int SHADOW = 0xB3000000;

	// text
	public static final int TEXT = 0xFFF2F2F3;
	public static final int TEXT_DIM = 0xFF9C9CA2;
	public static final int TEXT_FAINT = 0xFF606066;

	// signals
	public static final int GOOD = 0xFF3DD68C;
	public static final int WARN = 0xFFF5B841;
	public static final int BAD = 0xFFF2555A;

	private static int accent = 0xFF7C6CFF;
	private static int accentLight = 0xFF9B8FFF;
	private static String accentName = "Iris";

	private Theme() {
	}

	public static void setAccent(String name) {
		if (name.equals(accentName)) {
			return;
		}
		int[] p = palette(name);
		accent = p[0];
		accentLight = p[1];
		accentName = name;
	}

	public static int[] palette(String name) {
		return switch (name) {
			case "Rose" -> new int[] {0xFFFF5C8A, 0xFFFF86A7};
			case "Mint" -> new int[] {0xFF22C997, 0xFF55DDB4};
			case "Ember" -> new int[] {0xFFFF7442, 0xFFFF9A6E};
			case "Frost" -> new int[] {0xFF4F9DFF, 0xFF85BCFF};
			case "Mono" -> new int[] {0xFFE6E8EE, 0xFFFFFFFF};
			default -> new int[] {0xFF7C6CFF, 0xFF9B8FFF};
		};
	}

	public static int accent() {
		return accent;
	}

	/** A lighter companion of the accent for highlights and gentle gradients. */
	public static int accentLight() {
		return accentLight;
	}

	public static int accent(float alpha) {
		return Colors.withAlpha(accent, alpha);
	}

	/** Readable foreground for content drawn on the accent. */
	public static int onAccent() {
		return Colors.luminance(accent) > 0.7f ? 0xFF0B0B0C : 0xFFFFFFFF;
	}

	/** Muted category hues, used for small labels only. */
	public static int category(dev.kro.core.module.Category c) {
		return c.color();
	}
}
