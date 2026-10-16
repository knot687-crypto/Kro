package dev.kro.mc.screen;

import dev.kro.Kro;
import dev.kro.mc.McHost;
import dev.kro.mc.render.McBackend;
import dev.kro.ui.hud.HudEditor;
import dev.kro.ui.hud.TargetInfo;
import dev.kro.ui.render.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag-to-place editor for the Target HUD, previewed with your own player as the target. */
public final class HudEditorScreen extends Screen {
	private final HudEditor editor;

	public HudEditorScreen() {
		super(Component.literal("Kro HUD"));
		Kro kro = Kro.get();
		TargetInfo sample = kro.targets.sample();
		this.editor = new HudEditor(kro.modules.targetHud, sample, new McHost(() -> Minecraft.getInstance().setScreen(new KroScreen())),
				() -> Minecraft.getInstance().setScreen(new KroScreen()));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		editor.keyPressed(256);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Kro.get().applyInterface();
		Minecraft mc = Minecraft.getInstance();
		float mx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
		float my = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
		Canvas canvas = McBackend.canvas(graphics);
		editor.render(canvas, this.width, this.height, mx, my);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return editor.mouseClicked(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		editor.mouseDragged(event.x(), event.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		editor.mouseReleased();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return editor.keyPressed(event.key()) || super.keyPressed(event);
	}
}
