package dev.kro.ui.render;

/**
 * One signed-distance-field quad. The GPU (or the preview rasteriser) evaluates the exact shape
 * per pixel, giving resolution independent, perfectly anti-aliased edges.
 *
 * <p>Corner order is top-left, bottom-left, bottom-right, top-right, matching local signs
 * (-,-) (-,+) (+,+) (+,-).
 */
public final class ShapeCmd {
	/** Rounded rectangle fill, or outline when {@link #param} &gt; 0. */
	public static final int RECT = 0;
	/** Gaussian-like soft shadow / glow of a rounded rectangle; {@link #param} is the softness. */
	public static final int SHADOW = 1;
	/** Ring arc with round caps and an optional conic gradient. */
	public static final int ARC = 2;
	/** Texture clipped to a rounded rectangle. */
	public static final int IMAGE = 3;

	/** Extra local margin around arcs so their anti-aliased rim is never clipped. */
	public static final float ARC_PAD = 1.0f;

	public static final float[] SIGN_X = {-1f, -1f, 1f, 1f};
	public static final float[] SIGN_Y = {-1f, 1f, 1f, -1f};

	public int mode;
	/** Screen-space corner positions, GUI units. */
	public final float[] x = new float[4];
	public final float[] y = new float[4];
	/** Per-corner colours (ARGB). */
	public final int[] color = new int[4];
	/** Quad half extents in local units. */
	public float qx;
	public float qy;
	/** RECT/SHADOW/IMAGE: shape half size. ARC: mid radius, half thickness. */
	public float a;
	public float b;
	/** RECT/IMAGE: corner radius, SHADOW: corner radius. ARC: start angle (rad). */
	public float c;
	/** RECT: border thickness (0 = fill). SHADOW: softness. ARC: sweep (rad). */
	public float d;
	/** ARC: end colour of the conic gradient. */
	public int color2;
	/** IMAGE: texture and uv rect. */
	public ImageRef image;
	public float u0;
	public float v0;
	public float u1;
	public float v1;
	public ClipRect clip;

	/** Screen-space bounding box: {minX, minY, maxX, maxY}. */
	public float[] bounds() {
		float minX = Math.min(Math.min(x[0], x[1]), Math.min(x[2], x[3]));
		float maxX = Math.max(Math.max(x[0], x[1]), Math.max(x[2], x[3]));
		float minY = Math.min(Math.min(y[0], y[1]), Math.min(y[2], y[3]));
		float maxY = Math.max(Math.max(y[0], y[1]), Math.max(y[2], y[3]));
		return new float[] {minX, minY, maxX, maxY};
	}

	/** Texture coordinate for corner {@code i}. */
	public float u(int i) {
		return SIGN_X[i] < 0 ? u0 : u1;
	}

	public float v(int i) {
		return SIGN_Y[i] < 0 ? v0 : v1;
	}
}
