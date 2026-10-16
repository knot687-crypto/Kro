package dev.kro.ui;

/** Pointer state in the UI's design space. */
public final class Mouse {
	public float x = -1000f;
	public float y = -1000f;
	public boolean left;
	public boolean right;

	public boolean in(float rx, float ry, float rw, float rh) {
		return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
	}

	public boolean inCircle(float cx, float cy, float r) {
		float dx = x - cx, dy = y - cy;
		return dx * dx + dy * dy <= r * r;
	}
}
