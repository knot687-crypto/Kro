package dev.kro.mc.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.resources.Identifier;

/** Render pipelines backing Kro's SDF shapes. Shaders live in assets/kro/shaders/core. */
public final class KroPipelines {
	/** Position, Color, UV0, UV1, UV2, Normal (+1 byte padding): 36 bytes per vertex. */
	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
			.add("Position", VertexFormatElement.POSITION)
			.add("Color", VertexFormatElement.COLOR)
			.add("UV0", VertexFormatElement.UV0)
			.add("UV1", VertexFormatElement.UV1)
			.add("UV2", VertexFormatElement.UV2)
			.add("Normal", VertexFormatElement.NORMAL)
			.padding(1)
			.build();

	/** Position, UV0, Color: matches vanilla's position_tex_color shader inputs. */
	public static final VertexFormat GLYPH_FORMAT = VertexFormat.builder()
			.add("Position", VertexFormatElement.POSITION)
			.add("UV0", VertexFormatElement.UV0)
			.add("Color", VertexFormatElement.COLOR)
			.build();

	public static final RenderPipeline SHAPE = base("shape")
			.build();

	/** Glyph quads from Kro's atlas, drawn with vanilla's textured GUI shader. */
	public static final RenderPipeline GLYPH = RenderPipeline.builder()
			.withLocation(Identifier.fromNamespaceAndPath("kro", "pipeline/glyph"))
			.withVertexShader(Identifier.withDefaultNamespace("core/position_tex_color"))
			.withFragmentShader(Identifier.withDefaultNamespace("core/position_tex_color"))
			.withSampler("Sampler0")
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withBlend(BlendFunction.TRANSLUCENT)
			.withVertexFormat(GLYPH_FORMAT, VertexFormat.Mode.QUADS)
			.withCull(false)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build();

	public static final RenderPipeline SHAPE_TEXTURED = base("shape_textured")
			.withShaderDefine("TEXTURED")
			.withSampler("Sampler0")
			.build();

	private KroPipelines() {
	}

	private static RenderPipeline.Builder base(String name) {
		Identifier shader = Identifier.fromNamespaceAndPath("kro", "core/shape");
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath("kro", "pipeline/" + name))
				.withVertexShader(shader)
				.withFragmentShader(shader)
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
				.withCull(false)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
	}
}
