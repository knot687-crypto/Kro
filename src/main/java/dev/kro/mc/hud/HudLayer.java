package dev.kro.mc.hud;

import dev.kro.Kro;
import dev.kro.core.module.impl.TargetHud;
import dev.kro.mc.render.McBackend;
import dev.kro.mc.screen.HudEditorScreen;
import dev.kro.ui.hud.TargetHudView;
import dev.kro.ui.hud.Watermark;
import dev.kro.ui.render.Canvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Kro's in-game overlay: target card, toasts and the optional watermark. */
public final class HudLayer {
	private final Kro kro;
	private final TargetHudView target = new TargetHudView();

	public HudLayer(Kro kro) {
		this.kro = kro;
	}

	public void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) {
			return;
		}
		Canvas c = McBackend.canvas(graphics);
		float w = c.width(), h = c.height();

		TargetHud th = kro.modules.targetHud;
		if (th.enabled() && !(mc.screen instanceof HudEditorScreen)) {
			target.update(kro.targets.info());
			if (target.visible()) {
				target.render(c, th.posX.floatValue() * w, th.posY.floatValue() * h, th);
			}
		}
		if (kro.modules.ui.notifications.on()) {
			kro.toasts.render(c, w);
		}
		boolean sprintChip = kro.modules.sprint.enabled() && kro.modules.sprint.indicator.on() && mc.player.isSprinting();
		if (kro.modules.ui.watermark.on() || sprintChip) {
			Watermark.render(c, mc.getFps(), kro.modules.ui.watermark.on(), sprintChip);
		}
	}
}
