package dev.kro.mc.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kro.ui.render.GlyphRun;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;

/** A run of Kro glyph quads sampling one atlas page with linear filtering. */
record GlyphElement(
		GlyphRun run,
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer vc) {
		float[] q = run.quads;
		int[] c = run.colors;
		for (int i = 0; i < run.count; i++) {
			int o = i * 8;
			int k = i * 4;
			float x0 = q[o], y0 = q[o + 1], x1 = q[o + 2], y1 = q[o + 3];
			float u0 = q[o + 4], v0 = q[o + 5], u1 = q[o + 6], v1 = q[o + 7];
			vc.addVertex(x0, y0, 0f).setUv(u0, v0).setColor(c[k]);
			vc.addVertex(x0, y1, 0f).setUv(u0, v1).setColor(c[k + 1]);
			vc.addVertex(x1, y1, 0f).setUv(u1, v1).setColor(c[k + 2]);
			vc.addVertex(x1, y0, 0f).setUv(u1, v0).setColor(c[k + 3]);
		}
	}
}
