package dev.kro.ui.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Shelf-packed 8-bit coverage atlas shared by every Kro font. Backends mirror each page into a
 * texture and re-upload whenever {@link Page#version} changes.
 */
public final class GlyphAtlas {
	public static final int SIZE = 1024;
	private static final int PADDING = 2;
	private static final int MAX_PAGES = 8;

	private final List<Page> pages = new ArrayList<>();

	public static final class Page {
		public final byte[] alpha = new byte[SIZE * SIZE];
		/** Bumped on every write; backends compare against their uploaded copy. */
		public int version;
		/** Backend-owned texture for this page. */
		public Object backendTexture;
		public int uploadedVersion = -1;
		/** Region written since the last {@link #clearDirty()}: x0, y0, x1, y1 (exclusive). */
		public int dirtyX0 = SIZE;
		public int dirtyY0 = SIZE;
		public int dirtyX1;
		public int dirtyY1;
		private final List<int[]> shelves = new ArrayList<>(); // {y, height, cursorX}

		public void clearDirty() {
			dirtyX0 = SIZE;
			dirtyY0 = SIZE;
			dirtyX1 = 0;
			dirtyY1 = 0;
		}
		private int nextShelfY = PADDING;

		private int[] allocate(int w, int h) {
			for (int[] shelf : shelves) {
				if (h <= shelf[1] && h >= shelf[1] * 0.6f && shelf[2] + w + PADDING <= SIZE) {
					int x = shelf[2];
					shelf[2] += w + PADDING;
					return new int[] {x, shelf[0]};
				}
			}
			if (nextShelfY + h + PADDING > SIZE) {
				return null;
			}
			int[] shelf = {nextShelfY, h, PADDING};
			shelves.add(shelf);
			nextShelfY += h + PADDING;
			int x = shelf[2];
			shelf[2] += w + PADDING;
			return new int[] {x, shelf[0]};
		}
	}

	/** Reserves room for a w*h bitmap; returns {page, x, y} or null when the atlas is full. */
	public int[] allocate(int w, int h) {
		if (w > SIZE - 2 * PADDING || h > SIZE - 2 * PADDING) {
			return null;
		}
		for (int i = 0; i < pages.size(); i++) {
			int[] xy = pages.get(i).allocate(w, h);
			if (xy != null) {
				return new int[] {i, xy[0], xy[1]};
			}
		}
		if (pages.size() >= MAX_PAGES) {
			return null;
		}
		pages.add(new Page());
		int[] xy = pages.get(pages.size() - 1).allocate(w, h);
		return xy == null ? null : new int[] {pages.size() - 1, xy[0], xy[1]};
	}

	public void write(int page, int x, int y, int w, int h, byte[] data) {
		Page p = pages.get(page);
		for (int row = 0; row < h; row++) {
			System.arraycopy(data, row * w, p.alpha, (y + row) * SIZE + x, w);
		}
		p.dirtyX0 = Math.min(p.dirtyX0, x);
		p.dirtyY0 = Math.min(p.dirtyY0, y);
		p.dirtyX1 = Math.max(p.dirtyX1, x + w);
		p.dirtyY1 = Math.max(p.dirtyY1, y + h);
		p.version++;
	}

	public Page page(int i) {
		return pages.get(i);
	}

	public int pageCount() {
		return pages.size();
	}
}
