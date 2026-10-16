package dev.kro.ui.render;

import dev.kro.core.util.Colors;

import java.util.ArrayDeque;

/**
 * Kro's immediate-mode drawing surface. Every primitive is an analytic signed distance field
 * evaluated per pixel, and text is laid out on the physical pixel grid, so the result is crisp
 * at any GUI scale. Coordinates are GUI units transformed by a 2D affine stack.
 */
public final class Canvas {
	public static final int LEFT = 0;
	public static final int CENTER = 1;
	public static final int RIGHT = 2;

	private RenderBackend backend;
	private float guiScale = 2f;
	private float width;
	private float height;

	/** Affine transform: x' = m0*x + m2*y + m4, y' = m1*x + m3*y + m5. */
	private float[] m = {1f, 0f, 0f, 1f, 0f, 0f};
	private float alpha = 1f;
	private final ArrayDeque<float[]> transforms = new ArrayDeque<>();
	private final ArrayDeque<Float> alphas = new ArrayDeque<>();
	private final ArrayDeque<ClipRect> clips = new ArrayDeque<>();
	private ClipRect clip;

	public void begin(RenderBackend backend, float guiScale, float width, float height) {
		this.backend = backend;
		this.guiScale = guiScale;
		this.width = width;
		this.height = height;
		this.m = new float[] {1f, 0f, 0f, 1f, 0f, 0f};
		this.alpha = 1f;
		this.clip = null;
		transforms.clear();
		alphas.clear();
		clips.clear();
	}

	public RenderBackend backend() {
		return backend;
	}

	public float guiScale() {
		return guiScale;
	}

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	// ------------------------------------------------------------------ state

	public void push() {
		transforms.push(m.clone());
		alphas.push(alpha);
	}

	public void pop() {
		m = transforms.pop();
		alpha = alphas.pop();
	}

	public void translate(float x, float y) {
		m[4] += m[0] * x + m[2] * y;
		m[5] += m[1] * x + m[3] * y;
	}

	public void scale(float s) {
		scale(s, s);
	}

	public void scale(float sx, float sy) {
		m[0] *= sx;
		m[1] *= sx;
		m[2] *= sy;
		m[3] *= sy;
	}

	/** Scales around a pivot point. */
	public void scale(float s, float px, float py) {
		translate(px, py);
		scale(s);
		translate(-px, -py);
	}

	public void rotate(float radians, float px, float py) {
		translate(px, py);
		float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
		float a = m[0], b = m[1], cc = m[2], d = m[3];
		m[0] = a * c + cc * s;
		m[1] = b * c + d * s;
		m[2] = -a * s + cc * c;
		m[3] = -b * s + d * c;
		translate(-px, -py);
	}

	/** Multiplies the opacity of everything drawn until the matching {@link #pop()}. */
	public void alpha(float a) {
		alpha *= Math.max(0f, Math.min(1f, a));
	}

	public float currentAlpha() {
		return alpha;
	}

	public float uniformScale() {
		return (float) Math.sqrt(Math.abs(m[0] * m[3] - m[1] * m[2]));
	}

	public float screenX(float x, float y) {
		return m[0] * x + m[2] * y + m[4];
	}

	public float screenY(float x, float y) {
		return m[1] * x + m[3] * y + m[5];
	}

	/** Current transform as {m0..m5}; for backends that draw foreign content in place. */
	public float[] transform() {
		return m.clone();
	}

	/** Clips subsequent drawing to a local-space rectangle (intersected with the current clip). */
	public void pushClip(float x, float y, float w, float h) {
		float x0 = screenX(x, y), y0 = screenY(x, y);
		float x1 = screenX(x + w, y + h), y1 = screenY(x + w, y + h);
		int ix0 = (int) Math.floor(Math.min(x0, x1));
		int iy0 = (int) Math.floor(Math.min(y0, y1));
		int ix1 = (int) Math.ceil(Math.max(x0, x1));
		int iy1 = (int) Math.ceil(Math.max(y0, y1));
		clips.push(clip == null ? new ClipRect(-1, -1, -1, -1) : clip);
		ClipRect next = new ClipRect(ix0, iy0, ix1 - ix0, iy1 - iy0);
		clip = clip == null ? next : clip.intersect(next);
	}

	public void popClip() {
		ClipRect prev = clips.pop();
		clip = prev.w() < 0 ? null : prev;
	}

	public ClipRect clip() {
		return clip;
	}

	private boolean clippedAway() {
		return clip != null && clip.empty();
	}

	// ------------------------------------------------------------------ shapes

	private float pad() {
		return Math.max(1f, 1.5f / (guiScale * Math.max(0.05f, uniformScale())));
	}

	private ShapeCmd quad(int mode, float cx, float cy, float qx, float qy, float rotation) {
		ShapeCmd s = new ShapeCmd();
		s.mode = mode;
		s.qx = qx;
		s.qy = qy;
		float cos = 1f, sin = 0f;
		if (rotation != 0f) {
			cos = (float) Math.cos(rotation);
			sin = (float) Math.sin(rotation);
		}
		for (int i = 0; i < 4; i++) {
			float lx = ShapeCmd.SIGN_X[i] * qx;
			float ly = ShapeCmd.SIGN_Y[i] * qy;
			float px = cx + lx * cos - ly * sin;
			float py = cy + lx * sin + ly * cos;
			s.x[i] = m[0] * px + m[2] * py + m[4];
			s.y[i] = m[1] * px + m[3] * py + m[5];
		}
		s.clip = clip;
		return s;
	}

	private int a(int color) {
		return alpha >= 1f ? color : Colors.fade(color, alpha);
	}

	private void colors(ShapeCmd s, int tl, int bl, int br, int tr) {
		s.color[0] = a(tl);
		s.color[1] = a(bl);
		s.color[2] = a(br);
		s.color[3] = a(tr);
	}

	private static boolean invisible(int c) {
		return (c >>> 24) == 0;
	}

	/** Filled rounded rectangle. */
	public void rect(float x, float y, float w, float h, float r, int color) {
		if (invisible(color)) {
			return;
		}
		rect(x, y, w, h, r, color, color, color, color);
	}

	/** Filled rounded rectangle with per-corner colours (top-left, top-right, bottom-right, bottom-left). */
	public void rect(float x, float y, float w, float h, float r, int tl, int tr, int br, int bl) {
		if (w <= 0f || h <= 0f || clippedAway()) {
			return;
		}
		float p = pad();
		ShapeCmd s = quad(ShapeCmd.RECT, x + w / 2f, y + h / 2f, w / 2f + p, h / 2f + p, 0f);
		s.a = w / 2f;
		s.b = h / 2f;
		s.c = Math.min(r, Math.min(w, h) / 2f);
		s.d = 0f;
		colors(s, tl, bl, br, tr);
		backend.shape(s);
	}

	/** Vertical gradient fill. */
	public void rectV(float x, float y, float w, float h, float r, int top, int bottom) {
		rect(x, y, w, h, r, top, top, bottom, bottom);
	}

	/** Horizontal gradient fill. */
	public void rectH(float x, float y, float w, float h, float r, int left, int right) {
		rect(x, y, w, h, r, left, right, right, left);
	}

	/** Rounded rectangle outline drawn inside the bounds. */
	public void outline(float x, float y, float w, float h, float r, float thickness, int color) {
		outline(x, y, w, h, r, thickness, color, color, color, color);
	}

	public void outline(float x, float y, float w, float h, float r, float thickness, int tl, int tr, int br, int bl) {
		if (w <= 0f || h <= 0f || thickness <= 0f || clippedAway()) {
			return;
		}
		float p = pad();
		ShapeCmd s = quad(ShapeCmd.RECT, x + w / 2f, y + h / 2f, w / 2f + p, h / 2f + p, 0f);
		s.a = w / 2f;
		s.b = h / 2f;
		s.c = Math.min(r, Math.min(w, h) / 2f);
		s.d = thickness;
		colors(s, tl, bl, br, tr);
		backend.shape(s);
	}

	/** Vertical gradient outline, e.g. a light top edge fading down. */
	public void outlineV(float x, float y, float w, float h, float r, float thickness, int top, int bottom) {
		outline(x, y, w, h, r, thickness, top, top, bottom, bottom);
	}

	/** Soft shadow / glow of a rounded rectangle. */
	public void shadow(float x, float y, float w, float h, float r, float softness, int color) {
		if (invisible(color) || softness <= 0f || clippedAway()) {
			return;
		}
		float ext = softness * 1.6f + pad();
		ShapeCmd s = quad(ShapeCmd.SHADOW, x + w / 2f, y + h / 2f, w / 2f + ext, h / 2f + ext, 0f);
		s.a = Math.max(0f, w / 2f);
		s.b = Math.max(0f, h / 2f);
		s.c = Math.min(r, Math.min(w, h) / 2f);
		s.d = softness;
		colors(s, color, color, color, color);
		backend.shape(s);
	}

	public void circle(float cx, float cy, float radius, int color) {
		rect(cx - radius, cy - radius, radius * 2f, radius * 2f, radius, color);
	}

	public void circle(float cx, float cy, float radius, int tl, int tr, int br, int bl) {
		rect(cx - radius, cy - radius, radius * 2f, radius * 2f, radius, tl, tr, br, bl);
	}

	public void ring(float cx, float cy, float radius, float thickness, int color) {
		outline(cx - radius, cy - radius, radius * 2f, radius * 2f, radius, thickness, color);
	}

	/** Radial glow: a blurred disc. */
	public void glow(float cx, float cy, float radius, float softness, int color) {
		shadow(cx - radius, cy - radius, radius * 2f, radius * 2f, radius, softness, color);
	}

	/**
	 * Arc of a ring with round caps. Angles in radians, screen space: 0 points right, positive
	 * sweeps clockwise. Colours blend along the arc from start to end.
	 */
	public void arc(float cx, float cy, float radius, float thickness, float start, float sweep, int startColor, int endColor) {
		if (thickness <= 0f || Math.abs(sweep) < 1.0e-4f || clippedAway()) {
			return;
		}
		if (sweep < 0f) {
			start += sweep;
			sweep = -sweep;
			int t = startColor;
			startColor = endColor;
			endColor = t;
		}
		float q = radius + ShapeCmd.ARC_PAD;
		ShapeCmd s = quad(ShapeCmd.ARC, cx, cy, q, q, 0f);
		s.a = radius - thickness / 2f;
		s.b = thickness / 2f;
		s.c = start;
		s.d = Math.min(sweep, (float) (Math.PI * 2.0));
		int c = a(startColor);
		s.color[0] = c;
		s.color[1] = c;
		s.color[2] = c;
		s.color[3] = c;
		s.color2 = a(endColor);
		backend.shape(s);
	}

	public void arc(float cx, float cy, float radius, float thickness, float start, float sweep, int color) {
		arc(cx, cy, radius, thickness, start, sweep, color, color);
	}

	/** A capsule line segment with round ends. */
	public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
		line(x1, y1, x2, y2, thickness, color, color);
	}

	public void line(float x1, float y1, float x2, float y2, float thickness, int startColor, int endColor) {
		if (thickness <= 0f || clippedAway()) {
			return;
		}
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		float ang = (float) Math.atan2(dy, dx);
		float p = pad();
		float hw = len / 2f + thickness / 2f;
		float hh = thickness / 2f;
		ShapeCmd s = quad(ShapeCmd.RECT, (x1 + x2) / 2f, (y1 + y2) / 2f, hw + p, hh + p, ang);
		s.a = hw;
		s.b = hh;
		s.c = hh;
		s.d = 0f;
		colors(s, startColor, startColor, endColor, endColor);
		backend.shape(s);
	}

	/** Texture in a rounded rectangle; uv in 0..1. */
	public void image(ImageRef image, float x, float y, float w, float h, float r, int tint, float u0, float v0, float u1, float v1) {
		if (image == null || w <= 0f || h <= 0f || clippedAway()) {
			return;
		}
		ShapeCmd s = quad(ShapeCmd.IMAGE, x + w / 2f, y + h / 2f, w / 2f, h / 2f, 0f);
		s.a = w / 2f;
		s.b = h / 2f;
		s.c = Math.min(r, Math.min(w, h) / 2f);
		s.d = 0f;
		s.image = image;
		s.u0 = u0;
		s.v0 = v0;
		s.u1 = u1;
		s.v1 = v1;
		colors(s, tint, tint, tint, tint);
		backend.shape(s);
	}

	/** Forwards a backend specific payload in paint order. */
	public void custom(Object payload) {
		backend.custom(payload);
	}

	// ------------------------------------------------------------------ text

	/** Width of a string in GUI units at transform scale 1. */
	public float textWidth(KroFont font, float size, String s) {
		return font.width(s, 0f) * size;
	}

	public float textWidth(KroFont font, float size, String s, float tracking) {
		return font.width(s, tracking) * size;
	}

	/**
	 * Draws text whose cap height is vertically centred on {@code cy}.
	 *
	 * @return the advance width in GUI units
	 */
	public float text(KroFont font, float size, String s, float x, float cy, int color) {
		return text(font, size, s, x, cy, color, LEFT, 0f);
	}

	public float text(KroFont font, float size, String s, float x, float cy, int color, int align) {
		return text(font, size, s, x, cy, color, align, 0f);
	}

	public float text(KroFont font, float size, String s, float x, float cy, int color, int align, float tracking) {
		return drawText(font, size, s, x, cy, color, color, align, tracking);
	}

	/** Text with a horizontal colour gradient across the whole string. */
	public float textGradient(KroFont font, float size, String s, float x, float cy, int left, int right, int align, float tracking) {
		return drawText(font, size, s, x, cy, left, right, align, tracking);
	}

	/** Draws an icon glyph centred on (cx, cy). {@code size} is the icon's em size. */
	public void icon(char icon, float cx, float cy, float size, int color) {
		KroFont font = Fonts.icons();
		int g = font.glyphIndex(icon);
		if (g == 0 || invisible(color)) {
			return;
		}
		float[] box = font.box(g);
		float centreX = (box[0] + box[2]) / 2f;
		float x = cx - centreX * size;
		float baseline = cy + 0.5f * size;
		drawGlyphs(font, size, new int[] {g}, x, baseline, color, color, 0f, true);
	}

	private float drawText(KroFont font, float size, String s, float x, float cy, int c1, int c2, int align, float tracking) {
		if (s == null || s.isEmpty() || (invisible(c1) && invisible(c2))) {
			return s == null ? 0f : textWidth(font, size, s, tracking);
		}
		int[] glyphs = new int[s.codePointCount(0, s.length())];
		int n = 0;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			glyphs[n++] = font.glyphIndex(cp);
		}
		float w = font.width(s, tracking) * size;
		float left = align == CENTER ? x - w / 2f : align == RIGHT ? x - w : x;
		float baseline = cy + font.capHeight() * size / 2f;
		drawGlyphs(font, size, glyphs, left, baseline, c1, c2, tracking, false);
		return w;
	}

	private void drawGlyphs(KroFont font, float size, int[] glyphs, float left, float baseline, int c1, int c2, float tracking, boolean snapX) {
		if (clippedAway()) {
			return;
		}
		float us = uniformScale();
		boolean axisAligned = Math.abs(m[1]) < 1.0e-4f && Math.abs(m[2]) < 1.0e-4f;
		float pxIdeal = size * guiScale * us;
		if (pxIdeal < 1f) {
			return;
		}
		boolean exact = axisAligned && Math.abs(us - 1f) < 0.002f;
		float pxBake = exact ? Math.round(pxIdeal * 4f) / 4f : Math.max(1f, Math.round(pxIdeal));
		float residual = pxIdeal / pxBake;

		float ox = (m[0] * left + m[2] * baseline + m[4]) * guiScale;
		float oy = (m[1] * left + m[3] * baseline + m[5]) * guiScale;
		if (axisAligned) {
			oy = Math.round(oy);
		}
		if (snapX) {
			ox = Math.round(ox);
		}
		float totalPx = 0f;
		if (c1 != c2) {
			int prev = 0;
			for (int i = 0; i < glyphs.length; i++) {
				totalPx += (font.kern(prev, glyphs[i]) + font.advance(glyphs[i]) + (i > 0 ? tracking : 0f)) * pxIdeal;
				prev = glyphs[i];
			}
		}

		int ca1 = a(c1), ca2 = a(c2);
		GlyphRun run = null;
		float pen = 0f;
		int prev = 0;
		for (int i = 0; i < glyphs.length; i++) {
			int g = glyphs[i];
			if (i > 0) {
				pen += tracking * pxIdeal;
			}
			pen += font.kern(prev, g) * pxIdeal;
			float penAbs = ox + pen;
			float floor = (float) Math.floor(penAbs);
			int bin = exact && !snapX ? Math.min(KroFont.SUBPIXEL_BINS - 1, (int) ((penAbs - floor) * KroFont.SUBPIXEL_BINS)) : 0;
			Glyph gl = font.glyph(g, pxBake, bin);
			if (!gl.empty()) {
				float gx0, gy0, gx1, gy1;
				if (exact) {
					gx0 = floor + gl.xoff();
					gy0 = oy + gl.yoff();
					gx1 = gx0 + gl.w();
					gy1 = gy0 + gl.h();
				} else {
					gx0 = penAbs + gl.xoff() * residual;
					gy0 = oy + gl.yoff() * residual;
					gx1 = gx0 + gl.w() * residual;
					gy1 = gy0 + gl.h() * residual;
				}
				if (run == null || run.page != gl.page()) {
					if (run != null) {
						backend.glyphs(run);
					}
					run = new GlyphRun(font.atlas(), gl.page());
					run.clip = clip;
				}
				int cl = ca1, cr = ca1;
				if (ca1 != ca2 && totalPx > 0f) {
					cl = Colors.lerp(ca1, ca2, (gx0 - ox) / totalPx);
					cr = Colors.lerp(ca1, ca2, (gx1 - ox) / totalPx);
				}
				float inv = 1f / guiScale;
				run.add(gx0 * inv, gy0 * inv, gx1 * inv, gy1 * inv, gl.u0(), gl.v0(), gl.u1(), gl.v1(), cl, cl, cr, cr);
			}
			pen += font.advance(g) * pxIdeal;
			prev = g;
		}
		if (run != null && run.count > 0) {
			backend.glyphs(run);
		}
	}

	/** Splits text into lines no wider than {@code maxWidth} GUI units. */
	public java.util.List<String> wrap(KroFont font, float size, String s, float maxWidth) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (textWidth(font, size, candidate) <= maxWidth || line.isEmpty()) {
				line.setLength(0);
				line.append(candidate);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Truncates with an ellipsis to fit {@code maxWidth}. */
	public String ellipsize(KroFont font, float size, String s, float maxWidth) {
		if (textWidth(font, size, s) <= maxWidth) {
			return s;
		}
		String e = "…";
		for (int n = s.length() - 1; n > 0; n--) {
			String t = s.substring(0, n).stripTrailing() + e;
			if (textWidth(font, size, t) <= maxWidth) {
				return t;
			}
		}
		return e;
	}
}
