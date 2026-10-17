package dev.kro.mc.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Additive, depth-tested light for Kro's world effects; shapes are procedural in spark.fsh. */
public final class SparkPipelines {
	private static final BlendFunction ADDITIVE = new BlendFunction(SourceFactor.ONE, DestFactor.ONE);

	public static final RenderPipeline SPARK = base("spark").build();
	public static final RenderPipeline RING = base("spark_ring").withShaderDefine("RING").build();
	public static final RenderPipeline ORB = base("spark_orb").withShaderDefine("ORB").build();
	public static final RenderPipeline BAND = base("spark_band").withShaderDefine("BAND").build();

	public static final RenderType SPARK_TYPE = RenderType.create("kro_spark", RenderSetup.builder(SPARK).createRenderSetup());
	public static final RenderType RING_TYPE = RenderType.create("kro_spark_ring", RenderSetup.builder(RING).createRenderSetup());
	public static final RenderType ORB_TYPE = RenderType.create("kro_spark_orb", RenderSetup.builder(ORB).createRenderSetup());
	public static final RenderType BAND_TYPE = RenderType.create("kro_spark_band", RenderSetup.builder(BAND).createRenderSetup());

	private SparkPipelines() {
	}

	private static RenderPipeline.Builder base(String name) {
		Identifier shader = Identifier.fromNamespaceAndPath("kro", "core/spark");
		return RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath("kro", "pipeline/" + name))
				.withVertexShader(shader)
				.withFragmentShader(shader)
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(ADDITIVE)
				.withVertexFormat(KroPipelines.GLYPH_FORMAT, VertexFormat.Mode.QUADS)
				.withCull(false)
				// depth tested with the builder's default (less-or-equal) so sparks hide behind blocks
				.withDepthWrite(false);
	}
}
