package dev.kro.mc.render;

import net.minecraft.client.gui.GuiGraphics;

/** Vanilla drawing interleaved with Kro commands, see {@link dev.kro.ui.render.Canvas#custom}. */
@FunctionalInterface
public interface McDraw {
	void draw(GuiGraphics graphics);
}
