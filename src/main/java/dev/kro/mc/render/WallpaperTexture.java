package dev.kro.mc.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.kro.Kro;
import dev.kro.ui.menu.Wallpaper;
import dev.kro.ui.render.Picture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * The picture shown whole inside the menu window. Decoded once, then for every window size
 * resampled on a worker thread to exactly the size it is shown at, so it is drawn one texel per
 * pixel and stays crisp at any resolution, together with an equally sharp tinted copy for the
 * glass surfaces on top of it. Only the GPU uploads happen on the render thread.
 * <p>
 * A {@code wallpaper.png} / {@code .jpg} in {@code config/kro} replaces the bundled picture.
 */
public final class WallpaperTexture {
	private static final String BUNDLED = "/assets/kro/wallpaper/default.png";
	private static final String[] CUSTOM = {"wallpaper.png", "wallpaper.jpg", "wallpaper.jpeg"};

	private final Path dir;
	private Path customPath;
	private long customStamp;
	private CompletableFuture<Picture> source;
	private CompletableFuture<Sized> job;
	private long builtFor = -1L;
	private DynamicTexture texture;
	private DynamicTexture tintedTexture;
	private Wallpaper current;

	private record Sized(NativeImage image, NativeImage tinted, int width, int height, Picture source) {
	}

	/** A texture the GUI can draw directly. */
	private record Image(DynamicTexture texture) implements GpuImage {
		@Override
		public Object handle() {
			return texture;
		}

		@Override
		public GpuTextureView view() {
			return texture.getTextureView();
		}

		@Override
		public GpuSampler sampler() {
			return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	public WallpaperTexture(Path configDir) {
		this.dir = configDir.resolve("kro");
	}

	/** Picks up a custom picture added, changed or removed since the last look. */
	public void refresh() {
		Path found = null;
		long stamp = 0L;
		for (String name : CUSTOM) {
			Path p = dir.resolve(name);
			try {
				if (Files.isRegularFile(p)) {
					found = p;
					stamp = Files.getLastModifiedTime(p).toMillis();
					break;
				}
			} catch (IOException ignored) {
				// unreadable: treat as absent
			}
		}
		boolean changed = found == null ? customPath != null : !found.equals(customPath) || stamp != customStamp;
		if (changed || source == null) {
			Path path = found;
			customPath = path;
			customStamp = stamp;
			source = CompletableFuture.supplyAsync(() -> load(path));
			builtFor = -1L;
		}
	}

	private static Picture load(Path custom) {
		Picture picture = null;
		if (custom != null) {
			try (InputStream in = Files.newInputStream(custom)) {
				picture = Picture.read(in);
			} catch (IOException | RuntimeException e) {
				Kro.LOGGER.warn("Could not read wallpaper {}, using the default", custom, e);
			}
		}
		if (picture == null) {
			try (InputStream in = WallpaperTexture.class.getResourceAsStream(BUNDLED)) {
				if (in == null) {
					throw new IOException("missing " + BUNDLED);
				}
				picture = Picture.read(in);
			} catch (IOException e) {
				throw new IllegalStateException("Kro's bundled wallpaper is unreadable", e);
			}
		}
		return picture;
	}

	/**
	 * The picture for a window of {@code boxWidth} x {@code boxHeight} pixels, fitted inside it
	 * whole. Returns the previous size while a new one is resampled, a wallpaper without images
	 * while the very first is prepared, and {@code null} if no picture could be loaded at all.
	 */
	public Wallpaper get(int boxWidth, int boxHeight) {
		if (source == null) {
			refresh();
		}
		if (source.isCompletedExceptionally()) {
			return null;
		}
		Picture src = source.getNow(null);
		if (src == null) {
			return current;
		}
		if (job != null && job.isDone()) {
			Sized sized = job.isCompletedExceptionally() ? null : job.join();
			job = null;
			if (sized != null) {
				install(sized);
			}
		}
		int bw = Math.max(16, Math.min(boxWidth, 8192));
		int bh = Math.max(16, Math.min(boxHeight, 8192));
		long key = (long) bw << 32 | bh;
		if (builtFor != key && job == null) {
			// fit whole: as large as the window allows, never cropped
			float fit = Math.min(bw / (float) src.width, bh / (float) src.height);
			int w = Math.max(1, Math.round(src.width * fit));
			int h = Math.max(1, Math.round(src.height * fit));
			job = CompletableFuture.supplyAsync(() -> build(src, w, h));
			if (current == null) {
				current = new Wallpaper(null, null, w, h);
			}
			builtFor = key;
		}
		return current;
	}

	/** The picture at its shown size with edges melting to black, and its sharp tinted copy. */
	private static Sized build(Picture src, int w, int h) {
		Picture sharp = src.resize(w, h).fadeEdges(0.22f, 0.04f);
		Picture tinted = sharp.tint(0.05f, 0.20f, 1.10f);
		return new Sized(toNative(sharp), toNative(tinted), w, h, src);
	}

	private static NativeImage toNative(Picture p) {
		NativeImage image = new NativeImage(p.width, p.height, false);
		int[] px = p.argb;
		for (int y = 0; y < p.height; y++) {
			int row = y * p.width;
			for (int x = 0; x < p.width; x++) {
				image.setPixel(x, y, px[row + x]);
			}
		}
		return image;
	}

	private void install(Sized sized) {
		if (source.getNow(null) != sized.source()) {
			// a newer picture replaced this one while it was being resampled
			sized.image().close();
			sized.tinted().close();
			return;
		}
		DynamicTexture sharp = new DynamicTexture(() -> "kro_wallpaper", sized.image());
		DynamicTexture tinted = new DynamicTexture(() -> "kro_wallpaper_tinted", sized.tinted());
		if (texture != null) {
			texture.close();
		}
		if (tintedTexture != null) {
			tintedTexture.close();
		}
		texture = sharp;
		tintedTexture = tinted;
		current = new Wallpaper(new Image(sharp), new Image(tinted), sized.width(), sized.height());
	}
}
