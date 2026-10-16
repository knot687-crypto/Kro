package dev.kro.mc.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kro.ui.render.ShapeCmd;
import dev.kro.ui.render.ShapeEncoding;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;

/** A Kro SDF shape submitted to Minecraft's GUI render state. See {@link ShapeEncoding}. */
record ShapeElement(
		ShapeCmd cmd,
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer vc) {
		ShapeEncoding.encode(cmd, (x, y, argb, u, v, ax, ay, bx, by, mode) -> vc.addVertex(x, y, 0f)
				.setColor(argb)
				.setUv(u, v)
				.setUv1(ax, ay)
				.setUv2(bx, by)
				.setNormal(0f, 0f, mode));
	}
}
