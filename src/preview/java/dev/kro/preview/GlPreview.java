package dev.kro.preview;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Renders a menu scenario through a real OpenGL 3.3 core driver with Kro's shipped shaders and
 * the game's exact vertex encoding, renders the same frame in software, and compares them.
 * Also compiles and draws the hit-spark shaders.
 * <p>
 * Usage (under a display, e.g. xvfb-run): GlPreview scenario guiScale guiW guiH backdrop|none wallpaper outPrefix
 */
public final class GlPreview {
	public static void main(String[] args) throws Exception {
		String scenario = args[0];
		int gs = Integer.parseInt(args[1]);
		int gw = Integer.parseInt(args[2]);
		int gh = Integer.parseInt(args[3]);
		String backdrop = args[4];
		String wallpaper = args[5];
		String out = args[6];
		Path shaders = Path.of("/home/user/Kro/src/main/resources/assets/kro/shaders/core");
		int w = gw * gs, h = gh * gs;

		GLFWErrorCallback.createPrint(System.err).set();
		if (!glfwInit()) {
			throw new IllegalStateException("glfwInit failed");
		}
		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
		long window = glfwCreateWindow(64, 64, "kro", MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL) {
			throw new IllegalStateException("no GL 3.3 core context");
		}
		glfwMakeContextCurrent(window);
		GL.createCapabilities();
		System.out.println("GL " + glGetString(GL_VERSION) + " on " + glGetString(GL_RENDERER));

		int fbo = target(w, h);
		glViewport(0, 0, w, h);
		glClearColor(0f, 0f, 0f, 1f);
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glEnable(GL_BLEND);
		// BlendFunction.TRANSLUCENT
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

		PreviewMenu.Scene scene = PreviewMenu.scene(scenario, gs, gw, gh, "Iris", wallpaper.equals("none") ? null : wallpaper);
		BufferedImage back = backdrop.equals("none") ? null : Backdrop.make(new File(backdrop), w, h, 5 * gs, 0f);

		// software reference first (it also fills the glyph atlas the GPU then uploads)
		SoftwareBackend sw = new SoftwareBackend(w, h, gs);
		if (back != null) {
			sw.background(back);
		}
		scene.render(sw);
		sw.save(new File(out + "_sw.png"));

		GlBackend gl = new GlBackend(shaders, w, h, gs);
		if (back != null) {
			gl.backdrop(back);
		}
		scene.render(gl);
		gl.flush();
		glFinish();
		check("menu");
		BufferedImage gpu = read(w, h);
		ImageIO.write(gpu, "png", new File(out + "_gl.png"));
		System.out.println("GPU frame: " + gl.drawCalls + " draw calls");
		compare(ImageIO.read(new File(out + "_sw.png")), gpu, new File(out + "_diff.png"));

		sparks(shaders, out + "_sparks.png");
		glDeleteFramebuffers(fbo);
		glfwDestroyWindow(window);
		glfwTerminate();
	}

	private static int target(int w, int h) {
		int fbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		int color = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, color);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
		if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
			throw new IllegalStateException("framebuffer incomplete");
		}
		return fbo;
	}

	private static void check(String what) {
		int err = glGetError();
		if (err != GL_NO_ERROR) {
			throw new IllegalStateException(what + ": GL error 0x" + Integer.toHexString(err));
		}
	}

	private static BufferedImage read(int w, int h) {
		ByteBuffer px = MemoryUtil.memAlloc(w * h * 4);
		glPixelStorei(GL_PACK_ALIGNMENT, 4);
		glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = ((h - 1 - y) * w + x) * 4;
				int r = px.get(i) & 255, g = px.get(i + 1) & 255, b = px.get(i + 2) & 255;
				img.setRGB(x, y, r << 16 | g << 8 | b);
			}
		}
		MemoryUtil.memFree(px);
		return img;
	}

	/** Per-channel differences, with a heat map scaled so 16 levels are full red. */
	private static void compare(BufferedImage a, BufferedImage b, File heat) throws Exception {
		int w = a.getWidth(), h = a.getHeight();
		int[] hist = new int[256];
		long sum = 0;
		int max = 0;
		BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int ca = a.getRGB(x, y), cb = b.getRGB(x, y);
				int d = 0;
				for (int s = 0; s <= 16; s += 8) {
					d = Math.max(d, Math.abs(((ca >> s) & 255) - ((cb >> s) & 255)));
				}
				hist[d]++;
				sum += d;
				max = Math.max(max, d);
				int v = Math.min(255, d * 16);
				int grey = ((ca >> 16 & 255) + (ca >> 8 & 255) + (ca & 255)) / 12;
				map.setRGB(x, y, d > 2 ? v << 16 | (v / 4) << 8 : grey << 16 | grey << 8 | grey);
			}
		}
		ImageIO.write(map, "png", heat);
		int n = w * h;
		int acc = 0, p99 = 0, p999 = 0;
		for (int i = 0; i < 256; i++) {
			acc += hist[i];
			if (p99 == 0 && acc >= n * 0.99) {
				p99 = i;
			}
			if (p999 == 0 && acc >= n * 0.999) {
				p999 = i;
				break;
			}
		}
		int over8 = 0;
		for (int i = 9; i < 256; i++) {
			over8 += hist[i];
		}
		System.out.printf("GPU vs software: mean %.3f, p99 %d, p99.9 %d, max %d levels; %.4f%% of pixels differ by more than 8%n",
				sum / (double) n, p99, p999, max, 100.0 * over8 / n);
	}

	/** Compiles both spark programs as the game would and draws a few sparks and a ring. */
	private static void sparks(Path shaders, String file) throws Exception {
		String vsh = Files.readString(shaders.resolve("spark.vsh"));
		String fsh = Files.readString(shaders.resolve("spark.fsh"));
		String[] attribs = {"Position", "UV0", "Color"};
		int spark = GlBackend.program(vsh, fsh, null, attribs);
		int ring = GlBackend.program(vsh, fsh, "RING", attribs);
		int w = 480, h = 240;
		int fbo = target(w, h);
		glViewport(0, 0, w, h);
		glClearColor(0.02f, 0.03f, 0.06f, 1f);
		glClear(GL_COLOR_BUFFER_BIT);
		// additive, as SparkPipelines
		glBlendFunc(GL_ONE, GL_ONE);

		// reuse the GUI uniform blocks: identity model-view, pixel ortho projection
		GlBackend ui = new GlBackend(shaders, w, h, 1f);
		GlBackend.bindBlocks(spark);
		GlBackend.bindBlocks(ring);
		int vao = glGenVertexArrays();
		glBindVertexArray(vao);
		int vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, GlBackend.GLYPH_STRIDE, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 2, GL_FLOAT, false, GlBackend.GLYPH_STRIDE, 12);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, GlBackend.GLYPH_STRIDE, 20);

		drawQuads(spark, vbo, new float[][] {
				// x0, y0 (tail) .. head, half width, colour
				{60, 200, 160, 60, 7, 0xFF8C7BFF},
				{120, 210, 300, 120, 6, 0xFFB49CFF},
				{240, 220, 420, 150, 8, 0xFF7C6CFF}});
		drawRing(ring, vbo, 360, 120, 70, 0xFF9B8FFF);
		glFinish();
		check("sparks");
		ImageIO.write(read(w, h), "png", new File(file));
		System.out.println("spark shaders compiled, linked and drew -> " + file);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		glDeleteFramebuffers(fbo);
		ui.flush();
	}

	private static void drawQuads(int program, int vbo, float[][] streaks) {
		ByteBuffer b = MemoryUtil.memAlloc(streaks.length * 4 * GlBackend.GLYPH_STRIDE);
		for (float[] s : streaks) {
			float dx = s[2] - s[0], dy = s[3] - s[1];
			float len = (float) Math.hypot(dx, dy);
			float nx = -dy / len * s[4], ny = dx / len * s[4];
			int c = (int) s[5];
			vertex(b, s[0] - nx, s[1] - ny, -1, -1, c);
			vertex(b, s[0] + nx, s[1] + ny, 1, -1, c);
			vertex(b, s[2] + nx, s[3] + ny, 1, 1, c);
			vertex(b, s[2] - nx, s[3] - ny, -1, 1, c);
		}
		b.flip();
		draw(program, vbo, b, streaks.length);
		MemoryUtil.memFree(b);
	}

	private static void drawRing(int program, int vbo, float cx, float cy, float r, int c) {
		ByteBuffer b = MemoryUtil.memAlloc(4 * GlBackend.GLYPH_STRIDE);
		vertex(b, cx - r, cy - r, -1, -1, c);
		vertex(b, cx + r, cy - r, 1, -1, c);
		vertex(b, cx + r, cy + r, 1, 1, c);
		vertex(b, cx - r, cy + r, -1, 1, c);
		b.flip();
		draw(program, vbo, b, 1);
		MemoryUtil.memFree(b);
	}

	private static void vertex(ByteBuffer b, float x, float y, float u, float v, int argb) {
		b.putFloat(x).putFloat(y).putFloat(0f).putFloat(u).putFloat(v);
		b.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
	}

	private static void draw(int program, int vbo, ByteBuffer data, int quads) {
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
		glUseProgram(program);
		int[] idx = new int[quads * 6];
		for (int q = 0; q < quads; q++) {
			int o = q * 4;
			System.arraycopy(new int[] {o, o + 1, o + 2, o + 2, o + 3, o}, 0, idx, q * 6, 6);
		}
		int ebo = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STREAM_DRAW);
		glDrawElements(GL_TRIANGLES, idx.length, GL_UNSIGNED_INT, 0L);
		glDeleteBuffers(ebo);
		Arrays.fill(idx, 0);
	}
}
