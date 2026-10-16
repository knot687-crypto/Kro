package dev.kro.preview;

import dev.kro.ui.render.ClipRect;
import dev.kro.ui.render.GlyphAtlas;
import dev.kro.ui.render.GlyphRun;
import dev.kro.ui.render.RenderBackend;
import dev.kro.ui.render.ShapeCmd;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Offline rasteriser that evaluates exactly the same distance functions as shape.fsh, so UI
 * layouts can be reviewed without a GPU. Includes the same 1/16 unit parameter quantisation.
 */
public final class SoftwareBackend implements RenderBackend {
	private static final float TAU = (float) (Math.PI * 2);
	private static final float ANGLE_UNIT = (float) (Math.PI / 4096.0);

	final int w;
	final int h;
	final float scale;
	final float[] r;
	final float[] g;
	final float[] b;

	public SoftwareBackend(int physicalW, int physicalH, float guiScale) {
		this.w = physicalW;
		this.h = physicalH;
		this.scale = guiScale;
		r = new float[w * h];
		g = new float[w * h];
		b = new float[w * h];
	}

	public void background(BufferedImage img) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = img.getRGB(x * img.getWidth() / w, y * img.getHeight() / h);
				int i = y * w + x;
				r[i] = ((c >> 16) & 255) / 255f;
				g[i] = ((c >> 8) & 255) / 255f;
				b[i] = (c & 255) / 255f;
			}
		}
	}

	public void save(File f) throws IOException {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < w * h; i++) {
			int rr = Math.round(clamp01(r[i]) * 255f), gg = Math.round(clamp01(g[i]) * 255f), bb = Math.round(clamp01(b[i]) * 255f);
			out.setRGB(i % w, i / w, (rr << 16) | (gg << 8) | bb);
		}
		ImageIO.write(out, "png", f);
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	private static float q16(float v) {
		return Math.max(0, Math.min(32766, Math.round(v * 16f))) / 16f;
	}

	private void blend(int i, float cr, float cg, float cb, float ca) {
		if (ca <= 0f) {
			return;
		}
		float ia = 1f - ca;
		r[i] = cr * ca + r[i] * ia;
		g[i] = cg * ca + g[i] * ia;
		b[i] = cb * ca + b[i] * ia;
	}

	private int[] pixelBounds(float[] bb, ClipRect clip) {
		float minX = bb[0], minY = bb[1], maxX = bb[2], maxY = bb[3];
		if (clip != null) {
			minX = Math.max(minX, clip.x());
			minY = Math.max(minY, clip.y());
			maxX = Math.min(maxX, clip.x() + clip.w());
			maxY = Math.min(maxY, clip.y() + clip.h());
		}
		int x0 = Math.max(0, (int) Math.floor(minX * scale));
		int y0 = Math.max(0, (int) Math.floor(minY * scale));
		int x1 = Math.min(w, (int) Math.ceil(maxX * scale));
		int y1 = Math.min(h, (int) Math.ceil(maxY * scale));
		return new int[] {x0, y0, x1, y1};
	}

	private static float sdRoundBox(float px, float py, float bx, float by, float rad) {
		float qx = Math.abs(px) - bx + rad;
		float qy = Math.abs(py) - by + rad;
		float mx = Math.max(qx, 0f), my = Math.max(qy, 0f);
		return Math.min(Math.max(qx, qy), 0f) + (float) Math.sqrt(mx * mx + my * my) - rad;
	}

	private static float erf(float x) {
		float s = Math.signum(x);
		float a = Math.abs(x);
		float t = 1f + (0.278393f + (0.230389f + 0.078108f * (a * a)) * a) * a;
		t *= t;
		return s - s / (t * t);
	}

	private static float ch(int c, int shift) {
		return ((c >> shift) & 255) / 255f;
	}

	@Override
	public void shape(ShapeCmd s) {
		int[] pb = pixelBounds(s.bounds(), s.clip);
		if (pb[0] >= pb[2] || pb[1] >= pb[3]) {
			return;
		}
		// screen = P0 + U*(lx+qx) + V*(ly+qy)
		float ux = (s.x[3] - s.x[0]) / (2 * s.qx), uy = (s.y[3] - s.y[0]) / (2 * s.qx);
		float vx = (s.x[1] - s.x[0]) / (2 * s.qy), vy = (s.y[1] - s.y[0]) / (2 * s.qy);
		float det = ux * vy - vx * uy;
		if (Math.abs(det) < 1e-9f) {
			return;
		}
		float i00 = vy / det, i01 = -vx / det, i10 = -uy / det, i11 = ux / det;
		float pxUnit = (float) Math.sqrt(i00 * i00 + i01 * i01) / scale;
		pxUnit = Math.max(pxUnit, 1e-4f);

		float a = q16(s.a), bb = q16(s.b);
		float c = s.c, d = s.d;
		if (s.mode == ShapeCmd.ARC) {
			float start = s.c % TAU;
			if (start < 0) {
				start += TAU;
			}
			c = Math.round(start / ANGLE_UNIT) * ANGLE_UNIT;
			d = Math.min(32767, Math.round(s.d / ANGLE_UNIT)) * ANGLE_UNIT;
		} else {
			c = q16(s.c);
			d = q16(s.d);
		}

		for (int py = pb[1]; py < pb[3]; py++) {
			for (int px = pb[0]; px < pb[2]; px++) {
				float sx = (px + 0.5f) / scale - s.x[0];
				float sy = (py + 0.5f) / scale - s.y[0];
				float lx = i00 * sx + i01 * sy - s.qx;
				float ly = i10 * sx + i11 * sy - s.qy;
				float tu = (lx + s.qx) / (2 * s.qx), tv = (ly + s.qy) / (2 * s.qy);
				if (tu < -0.001f || tu > 1.001f || tv < -0.001f || tv > 1.001f) {
					continue;
				}
				float[] col = bilinear(s.color, tu, tv);
				float cov;
				switch (s.mode) {
					case ShapeCmd.RECT -> {
						float dist = sdRoundBox(lx, ly, a, bb, c);
						cov = clamp01(0.5f - dist / pxUnit);
						if (d > 0f) {
							cov -= clamp01(0.5f - (dist + d) / pxUnit);
						}
					}
					case ShapeCmd.SHADOW -> {
						float dist = sdRoundBox(lx, ly, a, bb, c);
						float sigma = Math.max(d * 0.5f, 1e-3f);
						cov = 0.5f - 0.5f * erf(dist / (sigma * 1.41421356f));
					}
					case ShapeCmd.ARC -> {
						float mid = a, halfT = bb, startA = c, sweep = d;
						float ang = (float) Math.atan2(ly, lx);
						float rel = (ang - startA) % TAU;
						if (rel < 0) {
							rel += TAU;
						}
						float dist;
						float t;
						if (sweep >= TAU - 1e-3f) {
							dist = Math.abs((float) Math.sqrt(lx * lx + ly * ly) - mid) - halfT;
							t = rel / TAU;
						} else {
							float rot = (float) (Math.PI * 0.5) - (startA + sweep * 0.5f);
							float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
							float rx = Math.abs(lx * cr - ly * sr), ry = lx * sr + ly * cr;
							float hap = sweep * 0.5f;
							float scx = (float) Math.sin(hap), scy = (float) Math.cos(hap);
							if (scy * rx > scx * ry) {
								float ddx = rx - scx * mid, ddy = ry - scy * mid;
								dist = (float) Math.sqrt(ddx * ddx + ddy * ddy) - halfT;
							} else {
								dist = Math.abs((float) Math.sqrt(rx * rx + ry * ry) - mid) - halfT;
							}
							if (rel <= sweep) {
								t = rel / Math.max(sweep, 1e-4f);
							} else {
								t = (rel - sweep) < (TAU - rel) ? 1f : 0f;
							}
						}
						cov = clamp01(0.5f - dist / pxUnit);
						int c2 = s.color2;
						col[0] = col[0] + (ch(c2, 16) - col[0]) * t;
						col[1] = col[1] + (ch(c2, 8) - col[1]) * t;
						col[2] = col[2] + (ch(c2, 0) - col[2]) * t;
						col[3] = col[3] + (ch(c2, 24) - col[3]) * t;
					}
					default -> {
						if (s.image instanceof PreviewImage img) {
							float u = s.u0 + (s.u1 - s.u0) * tu;
							float v = s.v0 + (s.v1 - s.v0) * tv;
							float[] tex = img.sample(u, v);
							col[0] *= tex[0];
							col[1] *= tex[1];
							col[2] *= tex[2];
							col[3] *= tex[3];
						}
						float dist = sdRoundBox(lx, ly, a, bb, c);
						cov = clamp01(0.5f - dist / pxUnit);
					}
				}
				blend(py * w + px, col[0], col[1], col[2], col[3] * cov);
			}
		}
	}

	private static float[] bilinear(int[] c, float u, float v) {
		u = clamp01(u);
		v = clamp01(v);
		float[] out = new float[4];
		int[] shifts = {16, 8, 0, 24};
		for (int k = 0; k < 4; k++) {
			float tl = ch(c[0], shifts[k]), bl = ch(c[1], shifts[k]), br = ch(c[2], shifts[k]), tr = ch(c[3], shifts[k]);
			float top = tl + (tr - tl) * u;
			float bot = bl + (br - bl) * u;
			out[k] = top + (bot - top) * v;
		}
		return out;
	}

	@Override
	public void glyphs(GlyphRun run) {
		GlyphAtlas.Page page = run.atlas.page(run.page);
		for (int q = 0; q < run.count; q++) {
			int o = q * 8;
			float x0 = run.quads[o], y0 = run.quads[o + 1], x1 = run.quads[o + 2], y1 = run.quads[o + 3];
			float u0 = run.quads[o + 4], v0 = run.quads[o + 5], u1 = run.quads[o + 6], v1 = run.quads[o + 7];
			int[] cols = {run.colors[q * 4], run.colors[q * 4 + 1], run.colors[q * 4 + 2], run.colors[q * 4 + 3]};
			int[] pb = pixelBounds(new float[] {x0, y0, x1, y1}, run.clip);
			float X0 = x0 * scale, Y0 = y0 * scale, X1 = x1 * scale, Y1 = y1 * scale;
			for (int py = pb[1]; py < pb[3]; py++) {
				for (int px = pb[0]; px < pb[2]; px++) {
					float fu = ((px + 0.5f) - X0) / (X1 - X0);
					float fv = ((py + 0.5f) - Y0) / (Y1 - Y0);
					if (fu < 0 || fu > 1 || fv < 0 || fv > 1) {
						continue;
					}
					float u = u0 + (u1 - u0) * fu, v = v0 + (v1 - v0) * fv;
					float cov = sampleAtlas(page, u, v);
					float[] col = bilinear(cols, fu, fv);
					blend(py * w + px, col[0], col[1], col[2], col[3] * cov);
				}
			}
		}
	}

	private static float sampleAtlas(GlyphAtlas.Page page, float u, float v) {
		float tx = u * GlyphAtlas.SIZE - 0.5f, ty = v * GlyphAtlas.SIZE - 0.5f;
		int ix = (int) Math.floor(tx), iy = (int) Math.floor(ty);
		float fx = tx - ix, fy = ty - iy;
		float a00 = at(page, ix, iy), a10 = at(page, ix + 1, iy), a01 = at(page, ix, iy + 1), a11 = at(page, ix + 1, iy + 1);
		float top = a00 + (a10 - a00) * fx, bot = a01 + (a11 - a01) * fx;
		return top + (bot - top) * fy;
	}

	private static float at(GlyphAtlas.Page page, int x, int y) {
		if (x < 0 || y < 0 || x >= GlyphAtlas.SIZE || y >= GlyphAtlas.SIZE) {
			return 0f;
		}
		return (page.alpha[y * GlyphAtlas.SIZE + x] & 255) / 255f;
	}
}
