package dev.kro.ui.render;

import java.util.Arrays;

/**
 * A batch of textured glyph quads that share one atlas page. Positions are screen-space GUI
 * units; colours are per vertex so gradients across a word are possible.
 */
public final class GlyphRun {
	public final GlyphAtlas atlas;
	public final int page;
	public ClipRect clip;
	/** Per quad: x0, y0, x1, y1, u0, v0, u1, v1. */
	public float[] quads = new float[8 * 16];
	/** Per quad: top-left, bottom-left, bottom-right, top-right colours. */
	public int[] colors = new int[4 * 16];
	public int count;

	public GlyphRun(GlyphAtlas atlas, int page) {
		this.atlas = atlas;
		this.page = page;
	}

	public void add(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int cTL, int cBL, int cBR, int cTR) {
		if ((count + 1) * 8 > quads.length) {
			quads = Arrays.copyOf(quads, quads.length * 2);
			colors = Arrays.copyOf(colors, colors.length * 2);
		}
		int q = count * 8;
		quads[q] = x0;
		quads[q + 1] = y0;
		quads[q + 2] = x1;
		quads[q + 3] = y1;
		quads[q + 4] = u0;
		quads[q + 5] = v0;
		quads[q + 6] = u1;
		quads[q + 7] = v1;
		int c = count * 4;
		colors[c] = cTL;
		colors[c + 1] = cBL;
		colors[c + 2] = cBR;
		colors[c + 3] = cTR;
		count++;
	}

	/** Screen-space bounding box: {minX, minY, maxX, maxY}. */
	public float[] bounds() {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			int q = i * 8;
			minX = Math.min(minX, quads[q]);
			minY = Math.min(minY, quads[q + 1]);
			maxX = Math.max(maxX, quads[q + 2]);
			maxY = Math.max(maxY, quads[q + 3]);
		}
		return new float[] {minX, minY, maxX, maxY};
	}
}
