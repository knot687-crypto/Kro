package dev.kro.ui.menu;

import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.ImageRef;

/**
 * Where the window's picture lies, so a surface can show a frosted copy of exactly the part of
 * the picture behind it: real glass over a real backdrop rather than a flat tint.
 */
final class Glass {
	private ImageRef image;
	private float x;
	private float y;
	private float w;
	private float h;

	void set(ImageRef image, float x, float y, float w, float h) {
		this.image = image;
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	void clear() {
		image = null;
	}

	boolean active() {
		return image != null;
	}

	/** Frosted fill of a rounded rect that is not transformed relative to the picture. */
	void fill(Canvas c, float rx, float ry, float rw, float rh, float r, int tint) {
		fill(c, rx, ry, rw, rh, r, tint, rx, ry, rw, rh);
	}

	/**
	 * Frosted fill of a rounded rect drawn at (rx, ry, rw, rh) in local coordinates whose
	 * on-screen footprint, in the picture's coordinates, is (sx, sy, sw, sh). Keeps the backdrop
	 * still while the surface itself lifts or scales.
	 */
	void fill(Canvas c, float rx, float ry, float rw, float rh, float r, int tint, float sx, float sy, float sw, float sh) {
		if (image == null) {
			return;
		}
		c.image(image, rx, ry, rw, rh, r, tint, (sx - x) / w, (sy - y) / h, (sx + sw - x) / w, (sy + sh - y) / h);
	}
}
