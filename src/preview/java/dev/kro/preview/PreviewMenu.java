package dev.kro.preview;

import dev.kro.core.anim.Clock;
import dev.kro.core.module.ModuleRegistry;
import dev.kro.ui.UiHost;
import dev.kro.ui.menu.KroMenu;
import dev.kro.ui.menu.Wallpaper;
import dev.kro.ui.render.Canvas;
import dev.kro.ui.render.Picture;
import dev.kro.ui.render.Theme;

import java.io.File;
import java.io.FileInputStream;

/**
 * Renders the menu offline. Usage:
 * PreviewMenu scenario guiScale guiW guiH backdrop|none out.png [accent] [wallpaper]
 * Scenarios: grid, hover, tab:N, page:N (card index), search:text
 */
public final class PreviewMenu {
	public static void main(String[] args) throws Exception {
		String scenario = args[0];
		int gs = Integer.parseInt(args[1]);
		int gw = Integer.parseInt(args[2]);
		int gh = Integer.parseInt(args[3]);
		String backdrop = args[4];
		File out = new File(args[5]);
		String accent = args.length > 6 ? args[6] : "Iris";
		String wallpaperPath = args.length > 7 ? args[7] : null;

		Clock.setFixed(System.nanoTime());
		ModuleRegistry modules = new ModuleRegistry();
		modules.ui.accent.set(accent);
		Theme.setAccent(accent);
		modules.aimAssist.setEnabled(true);
		modules.sprint.setEnabled(true);
		modules.hitSparks.setEnabled(true);
		modules.targetHud.setEnabled(true);
		modules.triggerbot.bind().set(82);

		KroMenu menu = new KroMenu(modules, UiHost.NONE);
		menu.setDim(modules.ui.dim.floatValue() / 100f);
		if (wallpaperPath != null) {
			// mirrors WallpaperTexture.build
			long t0 = System.nanoTime();
			Picture source = Picture.read(new FileInputStream(wallpaperPath));
			int[] box = KroMenu.windowPixelSize(gw, gh, gs);
			float fit = Math.min(box[0] / (float) source.width, box[1] / (float) source.height);
			int w = Math.max(1, Math.round(source.width * fit)), h = Math.max(1, Math.round(source.height * fit));
			Picture sharp = source.resize(w, h).fadeEdges(0.22f, 0.04f);
			Picture tinted = sharp.tint(0.05f, 0.20f, 1.10f);
			System.out.printf("wallpaper %dx%d -> %dx%d in %.0f ms%n", source.width, source.height, w, h, (System.nanoTime() - t0) / 1e6);
			menu.setWallpaper(new Wallpaper(PreviewImage.of(sharp), PreviewImage.of(tinted), w, h));
		}
		Canvas canvas = new Canvas();
		SoftwareBackend scratch = new SoftwareBackend(4, 4, gs);
		float[] mouse = {gw / 2f, gh / 2f};
		Runnable frame = () -> {
			Clock.advance(16_666_667L);
			canvas.begin(scratch, gs, gw, gh);
			menu.render(canvas, gw, gh, mouse[0], mouse[1], 1f);
		};
		for (int i = 0; i < 80; i++) {
			frame.run();
		}

		if (scenario.equals("hover")) {
			float[] p = menu.cardCentre(4);
			mouse[0] = p[0];
			mouse[1] = p[1];
		} else if (scenario.startsWith("tab:")) {
			int t = Integer.parseInt(scenario.substring(4));
			// cycle with the Tab key
			for (int i = 0; i < t; i++) {
				menu.keyPressed(258, 0, 344);
			}
		} else if (scenario.startsWith("page:")) {
			int idx = Integer.parseInt(scenario.substring(5));
			float[] p = menu.cardCentre(idx);
			menu.mouseClicked(p[0], p[1] + 20f, 0, false);
			menu.mouseReleased(p[0], p[1] + 20f, 0);
			mouse[0] = gw * 0.3f;
			mouse[1] = gh * 0.45f;
		} else if (scenario.startsWith("search:")) {
			for (char ch : scenario.substring(7).toCharArray()) {
				menu.charTyped(ch);
			}
		}
		for (int i = 0; i < 75; i++) {
			frame.run();
		}
		SoftwareBackend be = new SoftwareBackend(gw * gs, gh * gs, gs);
		if (!backdrop.equals("none")) {
			be.background(Backdrop.make(new File(backdrop), gw * gs, gh * gs, 5 * gs, 0.0f));
		}
		canvas.begin(be, gs, gw, gh);
		Clock.advance(16_666_667L);
		menu.render(canvas, gw, gh, mouse[0], mouse[1], 1f);
		be.save(out);
		System.out.println("wrote " + out);
	}
}
