package dev.kro.preview;

import dev.kro.core.anim.Clock;
import dev.kro.core.module.ModuleRegistry;
import dev.kro.ui.hud.TargetHudView;
import dev.kro.ui.hud.TargetInfo;
import dev.kro.ui.hud.Toasts;
import dev.kro.ui.hud.Watermark;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Theme;

import javax.imageio.ImageIO;
import java.io.File;

/** Renders HUD elements over an unblurred backdrop. Usage: PreviewHud gs w h backdrop skin out [style] */
public final class PreviewHud {
	public static void main(String[] args) throws Exception {
		int gs = Integer.parseInt(args[0]);
		int gw = Integer.parseInt(args[1]);
		int gh = Integer.parseInt(args[2]);
		File backdrop = new File(args[3]);
		PreviewImage skin = new PreviewImage(ImageIO.read(new File(args[4])));
		File out = new File(args[5]);
		String style = args.length > 6 ? args[6] : "Card";

		Clock.setFixed(System.nanoTime());
		ModuleRegistry modules = new ModuleRegistry();
		Theme.setAccent("Iris");
		modules.targetHud.style.set(style);
		TargetHudView view = new TargetHudView();
		Toasts toasts = new Toasts();

		TargetInfo t = new TargetInfo();
		t.name = "Technoblade";
		t.player = true;
		t.health = 20f;
		t.maxHealth = 20f;
		t.armor = 16;
		t.distance = 3.2f;
		t.face = skin;
		t.faceU0 = 8 / 64f;
		t.faceV0 = 8 / 64f;
		t.faceU1 = 16 / 64f;
		t.faceV1 = 16 / 64f;
		t.hatU0 = 40 / 64f;
		t.hatV0 = 8 / 64f;
		t.hatU1 = 48 / 64f;
		t.hatV1 = 16 / 64f;
		t.selfHealth = 17f;
		t.selfMax = 20f;
		t.id = 7;

		Canvas canvas = new Canvas();
		SoftwareBackend scratch = new SoftwareBackend(4, 4, gs);
		for (int i = 0; i < 40; i++) {
			Clock.advance(16_666_667L);
			view.update(t);
			canvas.begin(scratch, gs, gw, gh);
			view.render(canvas, gw * 0.5f, gh * 0.64f, modules.targetHud);
		}
		// take a hit: health drops, ghost bar lingers
		t.health = 11.5f;
		t.hurt = 0.6f;
		toasts.push(modules.aimAssist, true);
		toasts.push(modules.triggerbot, false);
		for (int i = 0; i < 12; i++) {
			Clock.advance(16_666_667L);
			view.update(t);
			canvas.begin(scratch, gs, gw, gh);
			view.render(canvas, gw * 0.5f, gh * 0.64f, modules.targetHud);
			toasts.render(canvas, gw);
		}
		SoftwareBackend be = new SoftwareBackend(gw * gs, gh * gs, gs);
		be.background(Backdrop.make(backdrop, gw * gs, gh * gs, 0, 0.0f));
		canvas.begin(be, gs, gw, gh);
		Clock.advance(16_666_667L);
		view.update(t);
		view.render(canvas, gw * 0.5f, gh * 0.64f, modules.targetHud);
		toasts.render(canvas, gw);
		Watermark.render(canvas, 144, true, true);
		be.save(out);
		System.out.println("wrote " + out);
	}
}
