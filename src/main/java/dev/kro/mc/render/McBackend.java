package dev.kro.mc.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.kro.ui.hud.TargetHudView;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.GlyphRun;
import dev.kro.ui.render.RenderBackend;
import dev.kro.ui.render.ShapeCmd;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;

/** Submits Kro draw commands into Minecraft's GUI render state, preserving paint order. */
public final class McBackend implements RenderBackend {
	private static final McBackend INSTANCE = new McBackend();
	private static final Canvas CANVAS = new Canvas();
	private static final Field STATE_FIELD = findStateField();

	private GuiGraphics graphics;
	private GuiRenderState state;

	private McBackend() {
	}

	/** The render state is not exposed publicly; locate it by type so names never matter. */
	private static Field findStateField() {
		for (Field f : GuiGraphics.class.getDeclaredFields()) {
			if (f.getType() == GuiRenderState.class) {
				f.setAccessible(true);
				return f;
			}
		}
		throw new IllegalStateException("GuiGraphics has no GuiRenderState field");
	}

	/** Prepares the shared canvas for drawing into {@code graphics} for this frame. */
	public static Canvas canvas(GuiGraphics graphics) {
		INSTANCE.graphics = graphics;
		try {
			INSTANCE.state = (GuiRenderState) STATE_FIELD.get(graphics);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		Minecraft mc = Minecraft.getInstance();
		CANVAS.begin(INSTANCE, (float) mc.getWindow().getGuiScale(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
		return CANVAS;
	}

	private void submit(GuiElementRenderState element) {
		state.submitGuiElement(element);
	}

	@Override
	public void shape(ShapeCmd cmd) {
		ScreenRectangle bounds = Rects.bounds(cmd.bounds(), cmd.clip);
		if (bounds == null) {
			return;
		}
		RenderPipeline pipeline;
		TextureSetup setup;
		if (cmd.mode == ShapeCmd.IMAGE) {
			if (!(cmd.image instanceof GpuImage image)) {
				return;
			}
			pipeline = KroPipelines.SHAPE_TEXTURED;
			setup = TextureSetup.singleTexture(image.view(), image.sampler());
		} else {
			pipeline = KroPipelines.SHAPE;
			setup = TextureSetup.noTexture();
		}
		submit(new ShapeElement(cmd, pipeline, setup, Rects.scissor(cmd.clip), bounds));
	}

	@Override
	public void glyphs(GlyphRun run) {
		ScreenRectangle bounds = Rects.bounds(run.bounds(), run.clip);
		if (bounds == null) {
			return;
		}
		TextureSetup setup = GlyphTextures.setup(run.atlas, run.page);
		submit(new GlyphElement(run, KroPipelines.GLYPH, setup, Rects.scissor(run.clip), bounds));
	}

	@Override
	public void custom(Object payload) {
		if (payload instanceof McDraw draw) {
			draw.draw(graphics);
		} else if (payload instanceof TargetHudView.AvatarRequest req && req.model() instanceof LivingEntity entity && req.alpha() > 0.6f) {
			int x0 = Math.round(req.x0()), y0 = Math.round(req.y0()), x1 = Math.round(req.x1()), y1 = Math.round(req.y1());
			float box = Math.min(x1 - x0, y1 - y0);
			int scale = Math.max(4, Math.round(box * 0.86f / Math.max(0.5f, entity.getBbHeight())));
			float cx = (x0 + x1) / 2f, cy = y0 + (y1 - y0) * 0.35f;
			InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1, scale, 0.0625f, cx, cy, entity);
		}
	}
}
