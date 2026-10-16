package dev.kro.mc.screen;

import dev.kro.Kro;
import dev.kro.core.module.impl.Interface;
import dev.kro.mc.McHost;
import dev.kro.mc.render.McBackend;
import dev.kro.ui.menu.KroMenu;
import dev.kro.ui.menu.Wallpaper;
import dev.kro.ui.render.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Hosts {@link KroMenu}; everything visual happens in the UI layer. */
public final class KroScreen extends Screen {
	private static boolean open;

	private final KroMenu menu;
	private boolean released;
	private Wallpaper wallpaper;

	public KroScreen() {
		super(Component.literal("Kro"));
		Kro kro = Kro.get();
		this.menu = new KroMenu(kro.modules, new McHost(() -> Minecraft.getInstance().setScreen(null)));
		this.menu.onEditHud(() -> Minecraft.getInstance().setScreen(new HudEditorScreen()));
		kro.wallpaper.refresh();
	}

	/** Centre of a module card in GUI coordinates (used by the automated screenshot test). */
	public float[] cardCentre(int index) {
		return menu.cardCentre(index);
	}

	/** Physical pixel height of the GUI, the size the wallpaper is resampled to. */
	public static int pixelHeight(Minecraft mc) {
		return Math.round(mc.getWindow().getGuiScaledHeight() * (float) mc.getWindow().getGuiScale());
	}

	/** Whether the Kro menu is on screen (used to drive the background blur). */
	public static boolean isOpen() {
		return open;
	}

	@Override
	protected void init() {
		open = true;
	}

	@Override
	public void removed() {
		open = false;
		Kro.get().save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		menu.requestClose();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Kro kro = Kro.get();
		Interface ui = kro.modules.ui;
		wallpaper = ui.background.is("Picture") ? kro.wallpaper.get(pixelHeight(Minecraft.getInstance())) : null;
		if (wallpaper == null && ui.blur.intValue() > 0) {
			this.renderBlurredBackground(graphics);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Kro kro = Kro.get();
		kro.applyInterface();
		menu.setDim(kro.modules.ui.dim.floatValue() / 100f);
		menu.setWallpaper(wallpaper);
		Minecraft mc = Minecraft.getInstance();
		float mx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
		float my = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
		Canvas canvas = McBackend.canvas(graphics);
		menu.render(canvas, this.width, this.height, mx, my, 1f);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return menu.mouseClicked(event.x(), event.y(), event.button(), doubleClick) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		menu.mouseReleased(event.x(), event.y(), event.button());
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		menu.mouseDragged(event.x(), event.y(), event.button());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return menu.mouseScrolled(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int menuKey = Kro.get().modules.ui.menuKey.key();
		if (event.key() == menuKey && !released) {
			// ignore auto-repeat of the key that opened the menu
			return true;
		}
		return menu.keyPressed(event.key(), event.modifiers(), menuKey) || menu.wantsKeyboard();
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.key() == Kro.get().modules.ui.menuKey.key()) {
			released = true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return menu.charTyped(event.codepoint());
	}
}
