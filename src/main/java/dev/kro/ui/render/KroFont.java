package dev.kro.ui.render;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A TrueType face rasterised with stb_truetype directly into Kro's glyph atlas at the exact
 * physical pixel size it is shown at, with three horizontal sub-pixel phases. This is what makes
 * Kro's text crisp at every GUI scale instead of resampled.
 */
public final class KroFont {
	/** Horizontal sub-pixel phases per glyph. */
	public static final int SUBPIXEL_BINS = 3;

	private final String name;
	private final GlyphAtlas atlas;
	@SuppressWarnings("unused") // must stay reachable: stb keeps a raw pointer into it
	private final ByteBuffer data;
	private final STBTTFontinfo info;
	private final float emScale;
	private final float ascent;
	private final float descent;
	private final float lineGap;
	private final float capHeight;
	private final byte[] coverageLut = new byte[256];
	private final Int2IntOpenHashMap glyphIndex = new Int2IntOpenHashMap();
	private final Int2IntOpenHashMap advanceUnits = new Int2IntOpenHashMap();
	private final Long2ObjectOpenHashMap<Glyph> glyphs = new Long2ObjectOpenHashMap<>();
	private final Int2ObjectOpenHashMap<float[]> boxes = new Int2ObjectOpenHashMap<>();

	private KroFont(String name, GlyphAtlas atlas, ByteBuffer data, float gamma) {
		this.name = name;
		this.atlas = atlas;
		this.data = data;
		this.info = STBTTFontinfo.create();
		if (!STBTruetype.stbtt_InitFont(info, data)) {
			throw new IllegalStateException("stb_truetype rejected font " + name);
		}
		this.emScale = STBTruetype.stbtt_ScaleForMappingEmToPixels(info, 1f);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer a = stack.mallocInt(1), d = stack.mallocInt(1), g = stack.mallocInt(1);
			STBTruetype.stbtt_GetFontVMetrics(info, a, d, g);
			ascent = a.get(0) * emScale;
			descent = -d.get(0) * emScale;
			lineGap = g.get(0) * emScale;

			IntBuffer x0 = stack.mallocInt(1), y0 = stack.mallocInt(1), x1 = stack.mallocInt(1), y1 = stack.mallocInt(1);
			int h = STBTruetype.stbtt_FindGlyphIndex(info, 'H');
			if (h != 0 && STBTruetype.stbtt_GetGlyphBox(info, h, x0, y0, x1, y1)) {
				capHeight = y1.get(0) * emScale;
			} else {
				capHeight = ascent * 0.72f;
			}
		}
		glyphIndex.defaultReturnValue(-1);
		advanceUnits.defaultReturnValue(Integer.MIN_VALUE);
		for (int i = 0; i < 256; i++) {
			coverageLut[i] = (byte) Math.round(255.0 * Math.pow(i / 255.0, 1.0 / gamma));
		}
	}

	/** Loads a TTF from the mod's own resources (not the game resource manager). */
	public static KroFont load(String name, String resource, GlyphAtlas atlas, float gamma) {
		try (InputStream in = KroFont.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Missing font resource " + resource);
			}
			byte[] bytes = in.readAllBytes();
			ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
			buf.put(bytes).flip();
			return new KroFont(name, atlas, buf, gamma);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read font " + resource, e);
		}
	}

	public String name() {
		return name;
	}

	public GlyphAtlas atlas() {
		return atlas;
	}

	/** Cap height as a fraction of the em size. */
	public float capHeight() {
		return capHeight;
	}

	public float ascent() {
		return ascent;
	}

	public float descent() {
		return descent;
	}

	public float lineHeight() {
		return ascent + descent + lineGap;
	}

	public int glyphIndex(int codepoint) {
		int g = glyphIndex.get(codepoint);
		if (g == -1) {
			g = STBTruetype.stbtt_FindGlyphIndex(info, codepoint);
			glyphIndex.put(codepoint, g);
		}
		return g;
	}

	public boolean has(int codepoint) {
		return glyphIndex(codepoint) != 0;
	}

	/** Horizontal advance in em units. */
	public float advance(int glyph) {
		int adv = advanceUnits.get(glyph);
		if (adv == Integer.MIN_VALUE) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer a = stack.mallocInt(1), l = stack.mallocInt(1);
				STBTruetype.stbtt_GetGlyphHMetrics(info, glyph, a, l);
				adv = a.get(0);
			}
			advanceUnits.put(glyph, adv);
		}
		return adv * emScale;
	}

	/** Ink bounding box of a glyph in em units, y up: {x0, y0, x1, y1}. */
	public float[] box(int glyph) {
		float[] cached = boxes.get(glyph);
		if (cached != null) {
			return cached;
		}
		float[] b;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x0 = stack.mallocInt(1), y0 = stack.mallocInt(1), x1 = stack.mallocInt(1), y1 = stack.mallocInt(1);
			if (STBTruetype.stbtt_GetGlyphBox(info, glyph, x0, y0, x1, y1)) {
				b = new float[] {x0.get(0) * emScale, y0.get(0) * emScale, x1.get(0) * emScale, y1.get(0) * emScale};
			} else {
				b = new float[] {0f, 0f, 0f, 0f};
			}
		}
		boxes.put(glyph, b);
		return b;
	}

	/** Kerning between two glyphs in em units. */
	public float kern(int left, int right) {
		if (left == 0 || right == 0) {
			return 0f;
		}
		return STBTruetype.stbtt_GetGlyphKernAdvance(info, left, right) * emScale;
	}

	/** Width of a string in em units, including tracking (em per gap). */
	public float width(String s, float tracking) {
		float w = 0f;
		int prev = 0;
		int n = 0;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			int g = glyphIndex(cp);
			w += kern(prev, g) + advance(g);
			prev = g;
			n++;
		}
		if (n > 1) {
			w += tracking * (n - 1);
		}
		return w;
	}

	/**
	 * Rasterises (or fetches) a glyph at a physical pixel size and sub-pixel phase.
	 *
	 * @param px  size in physical pixels, quantised by the caller
	 * @param bin sub-pixel phase in [0, SUBPIXEL_BINS)
	 */
	public Glyph glyph(int glyph, float px, int bin) {
		int pxKey = Math.round(px * 4f);
		long key = ((long) pxKey << 40) | ((long) glyph << 4) | bin;
		Glyph cached = glyphs.get(key);
		if (cached != null) {
			return cached;
		}
		Glyph g = rasterise(glyph, pxKey / 4f, bin);
		glyphs.put(key, g);
		return g;
	}

	private Glyph rasterise(int glyph, float px, int bin) {
		float scale = STBTruetype.stbtt_ScaleForMappingEmToPixels(info, px);
		float shift = bin / (float) SUBPIXEL_BINS;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer x0 = stack.mallocInt(1), y0 = stack.mallocInt(1), x1 = stack.mallocInt(1), y1 = stack.mallocInt(1);
			STBTruetype.stbtt_GetGlyphBitmapBoxSubpixel(info, glyph, scale, scale, shift, 0f, x0, y0, x1, y1);
			int w = x1.get(0) - x0.get(0);
			int h = y1.get(0) - y0.get(0);
			if (w <= 0 || h <= 0) {
				return Glyph.EMPTY;
			}
			int[] slot = atlas.allocate(w, h);
			if (slot == null) {
				return Glyph.EMPTY;
			}
			ByteBuffer bitmap = MemoryUtil.memAlloc(w * h);
			try {
				STBTruetype.stbtt_MakeGlyphBitmapSubpixel(info, bitmap, w, h, w, scale, scale, shift, 0f, glyph);
				byte[] out = new byte[w * h];
				for (int i = 0; i < out.length; i++) {
					out[i] = coverageLut[bitmap.get(i) & 0xFF];
				}
				atlas.write(slot[0], slot[1], slot[2], w, h, out);
			} finally {
				MemoryUtil.memFree(bitmap);
			}
			return new Glyph(slot[0], slot[1], slot[2], w, h, x0.get(0), y0.get(0));
		}
	}
}
