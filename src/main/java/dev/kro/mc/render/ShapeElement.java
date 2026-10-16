package dev.kro.mc.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kro.ui.render.ShapeCmd;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;

/** A Kro SDF shape submitted to Minecraft's GUI render state. See shape.vsh for the encoding. */
record ShapeElement(
		ShapeCmd cmd,
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		ScreenRectangle scissorArea,
		ScreenRectangle bounds
) implements GuiElementRenderState {
	private static final float TAU = (float) (Math.PI * 2.0);
	private static final float ANGLE_UNIT = (float) (Math.PI / 4096.0);

	private static int fixed(float v) {
		int i = Math.round(v * 16f);
		return i < 0 ? 0 : Math.min(i, 32766);
	}

	@Override
	public void buildVertices(VertexConsumer vc) {
		ShapeCmd s = cmd;
		int ha = fixed(s.a) + 1;
		int hb = fixed(s.b) + 1;
		int p0;
		int p1;
		float e0 = 0f;
		float e1 = 0f;
		if (s.mode == ShapeCmd.ARC) {
			float start = s.c % TAU;
			if (start < 0f) {
				start += TAU;
			}
			p0 = Math.round(start / ANGLE_UNIT);
			p1 = Math.min(32767, Math.round(s.d / ANGLE_UNIT));
			int c2 = s.color2;
			e0 = (c2 >>> 24) * 256 + ((c2 >> 16) & 0xFF);
			e1 = ((c2 >> 8) & 0xFF) * 256 + (c2 & 0xFF);
		} else {
			p0 = fixed(s.c);
			p1 = fixed(s.d);
		}
		float mode = (s.mode + 0.5f) / 127f;
		for (int i = 0; i < 4; i++) {
			int sx = ShapeCmd.SIGN_X[i] < 0f ? -1 : 1;
			int sy = ShapeCmd.SIGN_Y[i] < 0f ? -1 : 1;
			float u;
			float v;
			switch (s.mode) {
				case ShapeCmd.ARC -> {
					u = e0;
					v = e1;
				}
				case ShapeCmd.IMAGE -> {
					u = s.u(i);
					v = s.v(i);
				}
				default -> {
					u = s.qx;
					v = s.qy;
				}
			}
			vc.addVertex(s.x[i], s.y[i], 0f)
					.setColor(s.color[i])
					.setUv(u, v)
					.setUv1(sx * ha, sy * hb)
					.setUv2(p0, p1)
					.setNormal(0f, 0f, mode);
		}
	}
}
