package dev.kro.ui.render;

/**
 * A rasterised glyph at one pixel size and sub-pixel phase. Offsets are in physical pixels from
 * the integer pen position on the baseline (y grows downwards).
 */
public record Glyph(int page, int x, int y, int w, int h, int xoff, int yoff) {
	public static final Glyph EMPTY = new Glyph(-1, 0, 0, 0, 0, 0, 0);

	public boolean empty() {
		return page < 0 || w == 0 || h == 0;
	}

	public float u0() {
		return x / (float) GlyphAtlas.SIZE;
	}

	public float v0() {
		return y / (float) GlyphAtlas.SIZE;
	}

	public float u1() {
		return (x + w) / (float) GlyphAtlas.SIZE;
	}

	public float v1() {
		return (y + h) / (float) GlyphAtlas.SIZE;
	}
}
