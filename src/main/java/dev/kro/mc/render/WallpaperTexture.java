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
 * The picture that fills the menu window. Decoded once, then for every window size resampled
 * on a worker thread to the window's exact pixel width, so it is drawn one texel per pixel and
 * stays crisp at any resolution, along with a frosted copy for the glass surfaces on top of it.
 * Only the GPU uploads happen on the render thread.
 * <p>
 * A {@code wallpaper.png} / {@code .jpg} in {@code config/kro} replaces the bundled picture.
 */
public final class WallpaperTexture {
	private static final String BUNDLED = "/assets/kro/wallpaper/default.png";
	private static final String[] CUSTOM = {"wallpaper.png", "wallpaper.jpg", "wallpaper.jpeg"};
	/** Frost blur in design units: the same softness on every screen. */
	private static final float FROST = 9f;
	/** The menu window's design width the frost is tuned at. */
	private static final float DESIGN_WINDOW = 744f;

	private final Path dir;
	private Path customPath;
	private long customStamp;
	private CompletableFuture<Source> source;
	private CompletableFuture<Sized> job;
	private int builtWidth = -1;
	private DynamicTexture texture;
	private DynamicTexture glassTexture;
	private Wallpaper current;

	private record Source(Picture picture, float focusX, float focusY) {
	}

	private record Sized(NativeImage image, NativeImage glass, int width, int height, Source source) {
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
			builtWidth = -1;
		}
	}

	private static Source load(Path custom) {
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
		float[] focus = picture.focus();
		return new Source(picture, focus[0], focus[1]);
	}

	/**
	 * The window picture for a window {@code pixelWidth} pixels wide: the previous size while a
	 * new one is resampled, a wallpaper without images while the very first is prepared, and
	 * {@code null} if no picture could be loaded at all.
	 */
	public Wallpaper get(int pixelWidth) {
		if (source == null) {
			refresh();
		}
		if (source.isCompletedExceptionally()) {
			return null;
		}
		Source src = source.getNow(null);
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
		pixelWidth = Math.max(16, Math.min(pixelWidth, 8192));
		if (builtWidth != pixelWidth && job == null) {
			int w = pixelWidth;
			int h = Math.max(1, Math.round(w / src.picture().aspect()));
			job = CompletableFuture.supplyAsync(() -> build(src, w, h));
			if (current == null) {
				current = new Wallpaper(null, null, w, h, src.focusX(), src.focusY());
			}
			builtWidth = w;
		}
		return current;
	}

	private static Sized build(Source src, int w, int h) {
		Picture sharp = src.picture().resize(w, h);
		int gw = Math.max(1, w / 2), gh = Math.max(1, h / 2);
		float sigma = FROST * gw / DESIGN_WINDOW;
		Picture frost = sharp.resize(gw, gh).blur(sigma).glassTone(0.035f, 0.14f, 1.18f);
		return new Sized(toNative(sharp), toNative(frost), w, h, src);
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
			sized.glass().close();
			return;
		}
		DynamicTexture sharp = new DynamicTexture(() -> "kro_wallpaper", sized.image());
		DynamicTexture frost = new DynamicTexture(() -> "kro_wallpaper_glass", sized.glass());
		if (texture != null) {
			texture.close();
		}
		if (glassTexture != null) {
			glassTexture.close();
		}
		texture = sharp;
		glassTexture = frost;
		Source src = sized.source();
		current = new Wallpaper(new Image(sharp), new Image(frost), sized.width(), sized.height(), src.focusX(), src.focusY());
	}
}
