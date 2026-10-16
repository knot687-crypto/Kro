package dev.kro.preview;

import dev.kro.ui.render.ClipRect;
import dev.kro.ui.render.GlyphAtlas;
import dev.kro.ui.render.GlyphRun;
import dev.kro.ui.render.RenderBackend;
import dev.kro.ui.render.ShapeCmd;
import dev.kro.ui.render.ShapeEncoding;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws Kro's command stream through a real OpenGL 3.3 core context the way Minecraft's GUI
 * renderer does: the shipped shape shaders, the exact 36-byte vertex layout written by
 * {@link ShapeEncoding}, attribute locations bound by element name, std140 uniform blocks,
 * quad index buffers, translucent blending and scissor rectangles.
 */
final class GlBackend implements RenderBackend {
	static final int SHAPE_STRIDE = 36;
	static final int GLYPH_STRIDE = 24;

	private final Path shaderDir;
	private final int width;
	private final int height;
	private final float guiScale;
	final int shape;
	final int shapeTextured;
	final int glyph;
	private final int shapeVao;
	private final int shapeVbo;
	private final int glyphVao;
	private final int glyphVbo;
	private final int ebo;
	private final int dynamicTransforms;
	private final int projection;
	private final Map<Object, Integer> textures = new IdentityHashMap<>();
	private final Map<GlyphAtlas.Page, int[]> pages = new IdentityHashMap<>();
	private ByteBuffer data = MemoryUtil.memAlloc(SHAPE_STRIDE * 4 * 4096);
	private int quads;
	private int batchProgram = -1;
	private int batchTexture = -1;
	private ClipRect batchClip;
	int drawCalls;

	GlBackend(Path shaderDir, int width, int height, float guiScale) throws IOException {
		this.shaderDir = shaderDir;
		this.width = width;
		this.height = height;
		this.guiScale = guiScale;
		String[] shapeAttribs = {"Position", "Color", "UV0", "UV1", "UV2", "Normal"};
		String[] glyphAttribs = {"Position", "UV0", "Color"};
		shape = program(read("shape.vsh"), read("shape.fsh"), null, shapeAttribs);
		shapeTextured = program(read("shape.vsh"), read("shape.fsh"), "TEXTURED", shapeAttribs);
		glyph = program(GLYPH_VSH, GLYPH_FSH, null, glyphAttribs);

		ebo = glGenBuffers();
		IntBuffer idx = MemoryUtil.memAllocInt(6 * 65536);
		for (int q = 0; q < 65536; q++) {
			int b = q * 4;
			idx.put(b).put(b + 1).put(b + 2).put(b + 2).put(b + 3).put(b);
		}
		idx.flip();
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		shapeVao = glGenVertexArrays();
		glBindVertexArray(shapeVao);
		shapeVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, shapeVbo);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
		MemoryUtil.memFree(idx);
		// the layout of KroPipelines.SHAPE_FORMAT, set up the way Mojang's GL backend does
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, SHAPE_STRIDE, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, SHAPE_STRIDE, 12);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 2, GL_FLOAT, false, SHAPE_STRIDE, 16);
		glEnableVertexAttribArray(3);
		glVertexAttribIPointer(3, 2, GL_SHORT, SHAPE_STRIDE, 24);
		glEnableVertexAttribArray(4);
		glVertexAttribIPointer(4, 2, GL_SHORT, SHAPE_STRIDE, 28);
		glEnableVertexAttribArray(5);
		glVertexAttribPointer(5, 3, GL_BYTE, true, SHAPE_STRIDE, 32);

		glyphVao = glGenVertexArrays();
		glBindVertexArray(glyphVao);
		glyphVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, glyphVbo);
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, GLYPH_STRIDE, 0);
		glEnableVertexAttribArray(1);
		glVertexAttribPointer(1, 2, GL_FLOAT, false, GLYPH_STRIDE, 12);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, GLYPH_STRIDE, 20);
		glBindVertexArray(0);

		// std140 DynamicTransforms {mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat;}
		dynamicTransforms = glGenBuffers();
		FloatBuffer dt = MemoryUtil.memAllocFloat(40);
		putIdentity(dt);
		dt.put(1f).put(1f).put(1f).put(1f);
		dt.put(0f).put(0f).put(0f).put(0f);
		putIdentity(dt);
		dt.flip();
		glBindBuffer(GL_UNIFORM_BUFFER, dynamicTransforms);
		glBufferData(GL_UNIFORM_BUFFER, dt, GL_STATIC_DRAW);
		MemoryUtil.memFree(dt);
		projection = glGenBuffers();
		float guiW = width / guiScale, guiH = height / guiScale;
		FloatBuffer pm = MemoryUtil.memAllocFloat(16);
		pm.put(new float[] {
				2f / guiW, 0, 0, 0,
				0, -2f / guiH, 0, 0,
				0, 0, -1f, 0,
				-1f, 1f, 0, 1f});
		pm.flip();
		glBindBuffer(GL_UNIFORM_BUFFER, projection);
		glBufferData(GL_UNIFORM_BUFFER, pm, GL_STATIC_DRAW);
		MemoryUtil.memFree(pm);
		glBindBufferBase(GL_UNIFORM_BUFFER, 0, dynamicTransforms);
		glBindBufferBase(GL_UNIFORM_BUFFER, 1, projection);
		for (int p : new int[] {shape, shapeTextured, glyph}) {
			bindBlocks(p);
		}
	}

	private static void putIdentity(FloatBuffer b) {
		b.put(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
	}

	private String read(String name) throws IOException {
		return Files.readString(shaderDir.resolve(name), StandardCharsets.UTF_8);
	}

	static void bindBlocks(int program) {
		int dt = glGetUniformBlockIndex(program, "DynamicTransforms");
		if (dt != GL_INVALID_INDEX) {
			glUniformBlockBinding(program, dt, 0);
		}
		int pr = glGetUniformBlockIndex(program, "Projection");
		if (pr != GL_INVALID_INDEX) {
			glUniformBlockBinding(program, pr, 1);
		}
		glUseProgram(program);
		int s = glGetUniformLocation(program, "Sampler0");
		if (s >= 0) {
			glUniform1i(s, 0);
		}
		glUseProgram(0);
	}

	/** Compiles and links like Minecraft: defines go right after #version, attributes bound by name. */
	static int program(String vsh, String fsh, String define, String[] attributes) {
		int vs = compile(GL_VERTEX_SHADER, inject(vsh, define));
		int fs = compile(GL_FRAGMENT_SHADER, inject(fsh, define));
		int p = glCreateProgram();
		glAttachShader(p, vs);
		glAttachShader(p, fs);
		for (int i = 0; i < attributes.length; i++) {
			glBindAttribLocation(p, i, attributes[i]);
		}
		glLinkProgram(p);
		if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) {
			throw new IllegalStateException("link failed (" + define + "):\n" + glGetProgramInfoLog(p));
		}
		String log = glGetProgramInfoLog(p).trim();
		if (!log.isEmpty()) {
			System.out.println("link log (" + define + "): " + log);
		}
		return p;
	}

	private static String inject(String src, String define) {
		if (define == null) {
			return src;
		}
		int eol = src.indexOf('\n');
		return src.substring(0, eol + 1) + "#define " + define + "\n" + src.substring(eol + 1);
	}

	private static int compile(int type, String src) {
		int s = glCreateShader(type);
		glShaderSource(s, src);
		glCompileShader(s);
		String log = glGetShaderInfoLog(s).trim();
		if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
			throw new IllegalStateException("compile failed:\n" + log);
		}
		if (!log.isEmpty()) {
			System.out.println("compile log: " + log);
		}
		return s;
	}

	// ------------------------------------------------------------------ batching

	private void use(int program, int texture, ClipRect clip, int stride) {
		boolean same = program == batchProgram && texture == batchTexture && java.util.Objects.equals(clip, batchClip);
		if (!same) {
			flush();
			batchProgram = program;
			batchTexture = texture;
			batchClip = clip;
		}
		int need = (quads + 1) * 4 * stride;
		if (need > data.capacity()) {
			ByteBuffer bigger = MemoryUtil.memAlloc(data.capacity() * 2);
			data.flip();
			bigger.put(data);
			MemoryUtil.memFree(data);
			data = bigger;
		}
	}

	void flush() {
		if (quads == 0 || batchProgram < 0) {
			quads = 0;
			data.clear();
			return;
		}
		data.flip();
		boolean glyphs = batchProgram == glyph;
		glBindVertexArray(glyphs ? glyphVao : shapeVao);
		glBindBuffer(GL_ARRAY_BUFFER, glyphs ? glyphVbo : shapeVbo);
		glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
		glUseProgram(batchProgram);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, Math.max(0, batchTexture));
		if (batchClip != null) {
			glEnable(GL_SCISSOR_TEST);
			int x0 = Math.round(batchClip.x() * guiScale);
			int y0 = Math.round(batchClip.y() * guiScale);
			int x1 = Math.round((batchClip.x() + batchClip.w()) * guiScale);
			int y1 = Math.round((batchClip.y() + batchClip.h()) * guiScale);
			glScissor(x0, height - y1, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
		} else {
			glDisable(GL_SCISSOR_TEST);
		}
		glDrawElements(GL_TRIANGLES, quads * 6, GL_UNSIGNED_INT, 0L);
		drawCalls++;
		quads = 0;
		data.clear();
	}

	@Override
	public void shape(ShapeCmd cmd) {
		int program = shape;
		int texture = -1;
		if (cmd.mode == ShapeCmd.IMAGE) {
			if (!(cmd.image instanceof PreviewImage img)) {
				return;
			}
			program = shapeTextured;
			texture = texture(img.image(), img.smooth());
		}
		use(program, texture, cmd.clip, SHAPE_STRIDE);
		ShapeEncoding.encode(cmd, (x, y, argb, u, v, ax, ay, bx, by, mode) -> {
			data.putFloat(x).putFloat(y).putFloat(0f);
			putColor(argb);
			data.putFloat(u).putFloat(v);
			data.putShort((short) ax).putShort((short) ay);
			data.putShort((short) bx).putShort((short) by);
			// BufferBuilder.normalIntValue: (byte) (int) (clamp(v) * 127)
			data.put((byte) 0).put((byte) 0).put((byte) (int) (Math.max(-1f, Math.min(1f, mode)) * 127f)).put((byte) 0);
		});
		quads++;
	}

	/** BufferBuilder.setColor: ARGB written as ABGR, i.e. R, G, B, A in memory. */
	private void putColor(int argb) {
		data.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
	}

	@Override
	public void glyphs(GlyphRun run) {
		int texture = page(run.atlas.page(run.page));
		float[] q = run.quads;
		int[] c = run.colors;
		for (int i = 0; i < run.count; i++) {
			use(glyph, texture, run.clip, GLYPH_STRIDE);
			int o = i * 8, k = i * 4;
			float x0 = q[o], y0 = q[o + 1], x1 = q[o + 2], y1 = q[o + 3];
			float u0 = q[o + 4], v0 = q[o + 5], u1 = q[o + 6], v1 = q[o + 7];
			// GlyphElement's corner order: TL, BL, BR, TR
			data.putFloat(x0).putFloat(y0).putFloat(0f).putFloat(u0).putFloat(v0);
			putColor(c[k]);
			data.putFloat(x0).putFloat(y1).putFloat(0f).putFloat(u0).putFloat(v1);
			putColor(c[k + 1]);
			data.putFloat(x1).putFloat(y1).putFloat(0f).putFloat(u1).putFloat(v1);
			putColor(c[k + 2]);
			data.putFloat(x1).putFloat(y0).putFloat(0f).putFloat(u1).putFloat(v0);
			putColor(c[k + 3]);
			quads++;
		}
	}

	/** Draws an image over the whole target, e.g. the game behind the menu. */
	void backdrop(BufferedImage image) {
		float w = width / guiScale, h = height / guiScale;
		use(glyph, texture(image, true), null, GLYPH_STRIDE);
		data.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f);
		putColor(0xFFFFFFFF);
		data.putFloat(0f).putFloat(h).putFloat(0f).putFloat(0f).putFloat(1f);
		putColor(0xFFFFFFFF);
		data.putFloat(w).putFloat(h).putFloat(0f).putFloat(1f).putFloat(1f);
		putColor(0xFFFFFFFF);
		data.putFloat(w).putFloat(0f).putFloat(0f).putFloat(1f).putFloat(0f);
		putColor(0xFFFFFFFF);
		quads++;
		flush();
	}

	// ------------------------------------------------------------------ textures

	private int texture(BufferedImage image, boolean smooth) {
		Integer id = textures.get(image);
		if (id != null) {
			return id;
		}
		int w = image.getWidth(), h = image.getHeight();
		ByteBuffer px = MemoryUtil.memAlloc(w * h * 4);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = image.getRGB(x, y);
				px.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
			}
		}
		px.flip();
		int t = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, t);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
		MemoryUtil.memFree(px);
		int filter = smooth ? GL_LINEAR : GL_NEAREST;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		textures.put(image, t);
		return t;
	}

	/** Mirrors GlyphTextures: white RGB, coverage in alpha, linear filtering. */
	private int page(GlyphAtlas.Page page) {
		int[] state = pages.get(page);
		if (state == null) {
			state = new int[] {glGenTextures(), -1};
			pages.put(page, state);
		}
		if (state[1] != page.version) {
			flush();
			int n = GlyphAtlas.SIZE;
			ByteBuffer px = MemoryUtil.memAlloc(n * n * 4);
			for (int i = 0; i < n * n; i++) {
				px.put((byte) 255).put((byte) 255).put((byte) 255).put(page.alpha[i]);
			}
			px.flip();
			glBindTexture(GL_TEXTURE_2D, state[0]);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, n, n, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
			MemoryUtil.memFree(px);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			state[1] = page.version;
		}
		return state[0];
	}

	// Stand-in for vanilla core/position_tex_color, which KroPipelines.GLYPH uses.
	private static final String GLYPH_VSH = """
			#version 330
			layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };
			layout(std140) uniform Projection { mat4 ProjMat; };
			in vec3 Position;
			in vec2 UV0;
			in vec4 Color;
			out vec2 texCoord0;
			out vec4 vertexColor;
			void main() {
			    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
			    texCoord0 = UV0;
			    vertexColor = Color;
			}
			""";
	private static final String GLYPH_FSH = """
			#version 330
			layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat; };
			uniform sampler2D Sampler0;
			in vec2 texCoord0;
			in vec4 vertexColor;
			out vec4 fragColor;
			void main() {
			    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
			    if (color.a == 0.0) {
			        discard;
			    }
			    fragColor = color * ColorModulator;
			}
			""";
}
