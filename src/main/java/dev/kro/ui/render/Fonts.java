package dev.kro.ui.render;

/** Kro's typefaces, loaded lazily on first use from the mod jar. */
public final class Fonts {
	public static final GlyphAtlas ATLAS = new GlyphAtlas();

	private static KroFont medium;
	private static KroFont semibold;
	private static KroFont bold;
	private static KroFont display;
	private static KroFont icons;

	private Fonts() {
	}

	/** Body text: Inter Medium. */
	public static KroFont medium() {
		if (medium == null) {
			medium = KroFont.load("Inter Medium", "/assets/kro/fonts/inter-medium.ttf", ATLAS, 1.08f);
		}
		return medium;
	}

	/** Labels and titles: Inter SemiBold. */
	public static KroFont semibold() {
		if (semibold == null) {
			semibold = KroFont.load("Inter SemiBold", "/assets/kro/fonts/inter-semibold.ttf", ATLAS, 1.05f);
		}
		return semibold;
	}

	/** Emphasis: Inter Bold. */
	public static KroFont bold() {
		if (bold == null) {
			bold = KroFont.load("Inter Bold", "/assets/kro/fonts/inter-bold.ttf", ATLAS, 1.0f);
		}
		return bold;
	}

	/** Wordmark and big numerals: Outfit Bold. */
	public static KroFont display() {
		if (display == null) {
			display = KroFont.load("Outfit Bold", "/assets/kro/fonts/outfit-bold.ttf", ATLAS, 1.0f);
		}
		return display;
	}

	/** Lucide icon font. */
	public static KroFont icons() {
		if (icons == null) {
			icons = KroFont.load("Lucide", "/assets/kro/fonts/lucide.ttf", ATLAS, 1.12f);
		}
		return icons;
	}
}
