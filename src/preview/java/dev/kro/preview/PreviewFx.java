package dev.kro.preview;

import dev.kro.core.fx.Anchor;
import dev.kro.core.fx.Effects;
import dev.kro.core.fx.Fx;
import dev.kro.ui.render.Theme;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Renders Kro's world effects offline through a real OpenGL 3.3 driver with the shipped spark
 * shaders: a small night scene (ground tiles, a player-sized figure), the camera-relative
 * geometry from {@link Fx}, additive blending and depth testing as in game. Each effect is
 * simulated at 60 fps and captured at four moments into a 2x2 sheet.
 * <p>
 * Usage (under a display, e.g. xvfb-run): PreviewFx outDir [effect...]
 */
public final class PreviewFx {
	private static final int W = 800;
	private static final int H = 450;
	private static final int SOLID_STRIDE = 16;

	private int solid;
	private final int[] programs = new int[4];
	private int vao;
	private int vbo;
	private int solidVao;
	private int solidVbo;
	private int ubo0;
	private int ubo1;

	/** A stand-in for a player: fixed or moving along a path. */
	private static final class Dummy implements Anchor {
		final DoubleFunction<double[]> path;
		double time;
		float hurt;

		Dummy(DoubleFunction<double[]> path) {
			this.path = path;
		}

		@Override
		public boolean present() {
			return true;
		}

		@Override
		public double x(float pt) {
			return path.apply(time)[0];
		}

		@Override
		public double y(float pt) {
			return path.apply(time)[1];
		}

		@Override
		public double z(float pt) {
			return path.apply(time)[2];
		}

		@Override
		public double height() {
			return 1.8;
		}

		@Override
		public double width() {
			return 0.6;
		}

		@Override
		public float hurt() {
			return hurt;
		}
	}

	/** One preview: how to start it, how to step it, where to look, and when to capture. */
	private record Scene(String name, double[] eye, double[] look, double[] times, Stepper stepper) {
	}

	private interface Stepper {
		/** Advances the scene by dt at time t and draws its light. */
		void step(Fx fx, double t, float dt);
	}

	public static void main(String[] args) throws Exception {
		File outDir = new File(args[0]);
		outDir.mkdirs();
		Theme.setAccent("Iris");
		PreviewFx p = new PreviewFx();
		p.init();
		List<Scene> scenes = scenes();
		for (Scene s : scenes) {
			boolean wanted = args.length == 1;
			for (int i = 1; i < args.length; i++) {
				wanted |= args[i].equals(s.name());
			}
			if (wanted) {
				File f = new File(outDir, "fx_" + s.name() + ".png");
				p.render(s, f);
				System.out.println("wrote " + f);
			}
		}
		glfwTerminate();
	}

	private static List<Scene> scenes() {
		List<Scene> list = new ArrayList<>();
		double[] near = {2.6, 2.3, -4.6};
		double[] chest = {0.0, 1.0, 0.0};
		list.add(new Scene("pop", near, chest, new double[] {0.12, 0.38, 0.75, 1.25}, single(() -> {
			Dummy d = new Dummy(t -> new double[] {0, 0, 0});
			int[] c = Effects.popColors("Totem", 0);
			return Effects.pop(d, c[0], c[1], 1.0);
		})));
		list.add(new Scene("lightning", new double[] {5.0, 3.0, -12.0}, new double[] {0.0, 7.0, 0.0}, new double[] {0.04, 0.2, 0.32, 0.6},
				single(() -> Effects.lightning(0, 0, 0, Theme.accent()))));
		list.add(new Scene("soul", near, new double[] {0.0, 1.6, 0.0}, new double[] {0.35, 0.8, 1.35, 1.9},
				single(() -> Effects.soul(0, 0, 0, 0.6, 1.8, Theme.accent()))));
		list.add(new Scene("shockwave", near, chest, new double[] {0.08, 0.26, 0.5, 0.8},
				single(() -> Effects.shockwave(0, 0, 0, 1.8, Theme.accent()))));
		list.add(new Scene("ripple", new double[] {1.8, 1.9, -3.2}, new double[] {0.0, 0.2, 0.0}, new double[] {0.08, 0.22, 0.4, 0.62},
				single(() -> Effects.ripple(0, 0, 0, "Accent", 0, 1.0, false))));
		list.add(new Scene("blast", new double[] {4.0, 3.2, -7.5}, new double[] {0.0, 0.6, 0.0}, new double[] {0.05, 0.2, 0.42, 0.7},
				single(() -> Effects.blast(0, 1, 0, 6.0f))));
		list.add(new Scene("halo", near, chest, new double[] {1.0, 1.35, 1.7, 2.05}, halo("Halo")));
		list.add(new Scene("orbit", near, chest, new double[] {1.0, 1.2, 1.4, 1.6}, halo("Orbit")));
		list.add(new Scene("trail", new double[] {0.0, 4.2, -6.5}, new double[] {0.0, 0.0, 1.0}, new double[] {1.4, 1.9, 2.4, 2.9}, trail()));
		return list;
	}

	/** A single effect started at time 0. */
	private static Stepper single(java.util.function.Supplier<Effects.Effect> make) {
		Effects.Effect[] e = {null};
		return (fx, t, dt) -> {
			if (e[0] == null) {
				e[0] = make.get();
			}
			e[0].draw(fx, t, dt, 1f);
		};
	}

	private static Stepper halo(String style) {
		Effects.Halo halo = new Effects.Halo();
		Dummy d = new Dummy(t -> new double[] {0, 0, 0});
		return (fx, t, dt) -> {
			d.time = t;
			d.hurt = t > 1.3 && t < 1.8 ? (float) Math.max(0.0, 1.0 - (t - 1.3) / 0.5) : 0f;
			halo.draw(fx, d, style, "Accent", 0, 1.0, true, dt, 1f, t);
		};
	}

	private static Stepper trail() {
		Effects.Trail trail = new Effects.Trail();
		DoubleFunction<double[]> path = t -> new double[] {Math.sin(t * 1.6) * 2.2, 0.0, Math.cos(t * 1.1) * 1.4 + 1.0};
		return (fx, t, dt) -> {
			double[] p = path.apply(t);
			trail.sample(p[0], p[1], p[2], t, 1.2);
			trail.draw(fx, "Accent", 0, 1.0, t, 1.2, 0.0, 0.18, p[0], p[1], p[2]);
		};
	}

	// ------------------------------------------------------------------ GL

	private void init() throws Exception {
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
		long window = glfwCreateWindow(64, 64, "kro-fx", MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL) {
			throw new IllegalStateException("no GL 3.3 core context");
		}
		glfwMakeContextCurrent(window);
		GL.createCapabilities();

		Path shaders = Path.of("/home/user/Kro/src/main/resources/assets/kro/shaders/core");
		String vsh = Files.readString(shaders.resolve("spark.vsh"));
		String fsh = Files.readString(shaders.resolve("spark.fsh"));
		String[] attribs = {"Position", "UV0", "Color"};
		programs[Fx.BAND] = GlBackend.program(vsh, fsh, "BAND", attribs);
		programs[Fx.HALO] = GlBackend.program(vsh, fsh, "RING", attribs);
		programs[Fx.ORB] = GlBackend.program(vsh, fsh, "ORB", attribs);
		programs[Fx.STREAK] = GlBackend.program(vsh, fsh, null, attribs);
		solid = GlBackend.program(SOLID_VSH, SOLID_FSH, null, new String[] {"Position", "Color"});
		for (int prog : programs) {
			GlBackend.bindBlocks(prog);
		}
		GlBackend.bindBlocks(solid);

		vao = glGenVertexArrays();
		glBindVertexArray(vao);
		vbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, GlBackend.GLYPH_STRIDE, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 2, GL_FLOAT, false, GlBackend.GLYPH_STRIDE, 12);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, GlBackend.GLYPH_STRIDE, 20);

		solidVao = glGenVertexArrays();
		glBindVertexArray(solidVao);
		solidVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, solidVbo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, SOLID_STRIDE, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, SOLID_STRIDE, 12);
		glBindVertexArray(0);

		ubo0 = glGenBuffers();
		ubo1 = glGenBuffers();
		glBindBufferBase(GL_UNIFORM_BUFFER, 0, ubo0);
		glBindBufferBase(GL_UNIFORM_BUFFER, 1, ubo1);

		int fbo = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo);
		int color = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, color);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
		int depth = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, depth);
		glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, W, H);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
		if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
			throw new IllegalStateException("framebuffer incomplete");
		}
		glViewport(0, 0, W, H);
	}

	private void render(Scene s, File out) throws Exception {
		BufferedImage sheet = new BufferedImage(W * 2, H * 2, BufferedImage.TYPE_INT_RGB);
		Fx fx = new Fx();
		double t = 0.0;
		float dt = 1f / 60f;
		int shot = 0;
		while (shot < s.times().length) {
			t += dt;
			fx.begin(s.eye()[0], s.eye()[1], s.eye()[2]);
			s.stepper().step(fx, t, dt);
			if (t + 1.0e-9 >= s.times()[shot]) {
				drawFrame(s, fx);
				BufferedImage img = read();
				sheet.getGraphics().drawImage(img, (shot % 2) * W, (shot / 2) * H, null);
				shot++;
			} else {
				fx.flush((shape, data, colors, n) -> {
				});
			}
		}
		ImageIO.write(sheet, "png", out);
	}

	private void drawFrame(Scene s, Fx fx) {
		uploadCamera(s.eye(), s.look());
		glClearColor(0.035f, 0.045f, 0.085f, 1f);
		glClearDepth(1.0);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LEQUAL);
		glDepthMask(true);
		glDisable(GL_BLEND);
		drawScene(s.eye());
		// as SparkPipelines: additive, depth tested, no depth writes
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE);
		glDepthMask(false);
		fx.flush((shape, data, colors, n) -> drawQuads(programs[shape], data, colors, n));
		glDepthMask(true);
		glFinish();
	}

	private void uploadCamera(double[] eye, double[] look) {
		double fx = look[0] - eye[0], fy = look[1] - eye[1], fz = look[2] - eye[2];
		double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
		fx /= fl;
		fy /= fl;
		fz /= fl;
		// right = forward x up(0,1,0)
		double rx = -fz, ry = 0.0, rz = fx;
		double rl = Math.sqrt(rx * rx + rz * rz);
		rx /= rl;
		rz /= rl;
		double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
		FloatBuffer dt = MemoryUtil.memAllocFloat(40);
		dt.put(new float[] {
				(float) rx, (float) ux, (float) -fx, 0,
				(float) ry, (float) uy, (float) -fy, 0,
				(float) rz, (float) uz, (float) -fz, 0,
				0, 0, 0, 1});
		dt.put(new float[] {1, 1, 1, 1, 0, 0, 0, 0});
		dt.put(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
		dt.flip();
		glBindBuffer(GL_UNIFORM_BUFFER, ubo0);
		glBufferData(GL_UNIFORM_BUFFER, dt, GL_STREAM_DRAW);
		MemoryUtil.memFree(dt);
		double f = 1.0 / Math.tan(Math.toRadians(70.0) / 2.0), aspect = (double) W / H, n = 0.05, far = 500.0;
		FloatBuffer pm = MemoryUtil.memAllocFloat(16);
		pm.put(new float[] {
				(float) (f / aspect), 0, 0, 0,
				0, (float) f, 0, 0,
				0, 0, (float) ((far + n) / (n - far)), -1,
				0, 0, (float) (2 * far * n / (n - far)), 0});
		pm.flip();
		glBindBuffer(GL_UNIFORM_BUFFER, ubo1);
		glBufferData(GL_UNIFORM_BUFFER, pm, GL_STREAM_DRAW);
		MemoryUtil.memFree(pm);
	}

	/** Ground tiles fading into the night, and a dark figure the size of a player at the origin. */
	private void drawScene(double[] eye) {
		List<float[]> quads = new ArrayList<>();
		for (int x = -14; x < 14; x++) {
			for (int z = -14; z < 14; z++) {
				double d = Math.sqrt((x + 0.5) * (x + 0.5) + (z + 0.5) * (z + 0.5));
				float fade = (float) Math.max(0.0, 1.0 - d / 14.0);
				int base = ((x + z) & 1) == 0 ? 0x2B3B2A : 0x283727;
				int c = mix(0x0E1424, base, fade);
				quads.add(new float[] {x, 0, z, x + 1, 0, z, x + 1, 0, z + 1, x, 0, z + 1, Float.intBitsToFloat(c)});
			}
		}
		// the figure: a slim box, lit from above
		box(quads, -0.3, 0.0, -0.3, 0.3, 1.8, 0.3, 0x2A2F3A, 0x1C2029, 0x3A4150);
		ByteBuffer b = MemoryUtil.memAlloc(quads.size() * 4 * SOLID_STRIDE);
		for (float[] q : quads) {
			int c = Float.floatToRawIntBits(q[12]);
			for (int v = 0; v < 4; v++) {
				b.putFloat((float) (q[v * 3] - eye[0])).putFloat((float) (q[v * 3 + 1] - eye[1])).putFloat((float) (q[v * 3 + 2] - eye[2]));
				b.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 0xFF);
			}
		}
		b.flip();
		glBindVertexArray(solidVao);
		glBindBuffer(GL_ARRAY_BUFFER, solidVbo);
		glBufferData(GL_ARRAY_BUFFER, b, GL_STREAM_DRAW);
		glUseProgram(solid);
		drawIndexed(quads.size());
		MemoryUtil.memFree(b);
	}

	private static void box(List<float[]> quads, double x0, double y0, double z0, double x1, double y1, double z1, int side, int dark, int top) {
		float a = (float) x0, b = (float) y0, c = (float) z0, d = (float) x1, e = (float) y1, f = (float) z1;
		quads.add(new float[] {a, e, c, d, e, c, d, e, f, a, e, f, Float.intBitsToFloat(top)});
		quads.add(new float[] {a, b, c, d, b, c, d, e, c, a, e, c, Float.intBitsToFloat(side)});
		quads.add(new float[] {a, b, f, d, b, f, d, e, f, a, e, f, Float.intBitsToFloat(dark)});
		quads.add(new float[] {a, b, c, a, b, f, a, e, f, a, e, c, Float.intBitsToFloat(dark)});
		quads.add(new float[] {d, b, c, d, b, f, d, e, f, d, e, c, Float.intBitsToFloat(side)});
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
		return r << 16 | g << 8 | bl;
	}

	private void drawQuads(int program, float[] data, int[] colors, int vertices) {
		ByteBuffer b = MemoryUtil.memAlloc(vertices * GlBackend.GLYPH_STRIDE);
		for (int i = 0; i < vertices; i++) {
			int o = i * 5;
			int c = colors[i];
			b.putFloat(data[o]).putFloat(data[o + 1]).putFloat(data[o + 2]).putFloat(data[o + 3]).putFloat(data[o + 4]);
			b.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
		}
		b.flip();
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		glBufferData(GL_ARRAY_BUFFER, b, GL_STREAM_DRAW);
		glUseProgram(program);
		drawIndexed(vertices / 4);
		MemoryUtil.memFree(b);
	}

	private static void drawIndexed(int quads) {
		int[] idx = new int[quads * 6];
		for (int q = 0; q < quads; q++) {
			int o = q * 4;
			idx[q * 6] = o;
			idx[q * 6 + 1] = o + 1;
			idx[q * 6 + 2] = o + 2;
			idx[q * 6 + 3] = o + 2;
			idx[q * 6 + 4] = o + 3;
			idx[q * 6 + 5] = o;
		}
		int ebo = glGenBuffers();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STREAM_DRAW);
		glDrawElements(GL_TRIANGLES, idx.length, GL_UNSIGNED_INT, 0L);
		glDeleteBuffers(ebo);
	}

	private static BufferedImage read() {
		ByteBuffer px = MemoryUtil.memAlloc(W * H * 4);
		glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, px);
		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int i = ((H - 1 - y) * W + x) * 4;
				img.setRGB(x, y, (px.get(i) & 255) << 16 | (px.get(i + 1) & 255) << 8 | (px.get(i + 2) & 255));
			}
		}
		MemoryUtil.memFree(px);
		return img;
	}

	private static final String SOLID_VSH = """
			#version 330
			layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };
			layout(std140) uniform Projection { mat4 ProjMat; };
			in vec3 Position;
			in vec4 Color;
			out vec4 vColor;
			void main() {
			    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
			    vColor = Color;
			}
			""";

	private static final String SOLID_FSH = """
			#version 330
			in vec4 vColor;
			out vec4 fragColor;
			void main() {
			    fragColor = vColor;
			}
			""";
}
