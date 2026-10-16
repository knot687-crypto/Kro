package dev.kro.ui.render;

/** Axis aligned scissor rectangle in GUI units. */
public record ClipRect(int x, int y, int w, int h) {
	public ClipRect intersect(ClipRect o) {
		if (o == null) {
			return this;
		}
		int x0 = Math.max(x, o.x);
		int y0 = Math.max(y, o.y);
		int x1 = Math.min(x + w, o.x + o.w);
		int y1 = Math.min(y + h, o.y + o.h);
		return new ClipRect(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
	}

	public boolean empty() {
		return w <= 0 || h <= 0;
	}

	public boolean contains(double px, double py) {
		return px >= x && py >= y && px < x + w && py < y + h;
	}
}
