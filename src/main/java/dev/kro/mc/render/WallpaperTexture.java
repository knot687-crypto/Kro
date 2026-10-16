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
 * The picture behind the menu. It is decoded once, then resampled on a worker thread to the
 * screen's exact pixel height whenever that changes, so it is always drawn one texel per pixel:
 * crisp at 1080p, 1440p and 4K alike. Only the GPU upload happens on the render thread.
 * <p>
 * A {@code wallpaper.png} / {@code .jpg} in {@code config/kro} replaces the bundled picture.
 */
public final class WallpaperTexture {
	private static final String BUNDLED = "/assets/kro/wallpaper/default.png";
	private static final String[] CUSTOM = {"wallpaper.png", "wallpaper.jpg", "wallpaper.jpeg"};

	private final Path dir;
	private Path customPath;
	private long customStamp;
	private CompletableFuture<Source> source;
	private CompletableFuture<Sized> job;
	private int builtHeight = -1;
	private DynamicTexture texture;
	private Wallpaper current;

	private record Source(Picture picture, float subjectLeft, float subjectRight) {
	}

	private record Sized(NativeImage image, int width, int height, Source source) {
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
			builtHeight = -1;
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
		float[] span = picture.subjectSpan();
		return span != null ? new Source(picture, span[0], span[1]) : new Source(picture, 0.15f, 0.85f);
	}

	/**
	 * The wallpaper for a screen {@code pixelHeight} pixels tall. Returns a wallpaper without an
	 * image while the first texture is prepared (so the layout is already right), the previous
	 * size while a new one is resampled, and {@code null} if no picture could be loaded.
	 */
	public Wallpaper get(int pixelHeight) {
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
		pixelHeight = Math.max(1, Math.min(pixelHeight, 8192));
		if (builtHeight != pixelHeight && job == null) {
			int h = pixelHeight;
			int w = Math.max(1, Math.round(h * src.picture().aspect()));
			job = CompletableFuture.supplyAsync(() -> toNative(src, w, h));
			if (current == null || current.image() == null) {
				current = new Wallpaper(null, w, h, src.subjectLeft(), src.subjectRight());
			}
			builtHeight = h;
		}
		return current;
	}

	private static Sized toNative(Source src, int w, int h) {
		Picture sized = src.picture().resize(w, h);
		NativeImage image = new NativeImage(w, h, false);
		int[] px = sized.argb;
		for (int y = 0; y < h; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) {
				image.setPixel(x, y, px[row + x]);
			}
		}
		return new Sized(image, w, h, src);
	}

	private void install(Sized sized) {
		if (source.getNow(null) != sized.source()) {
			// a newer picture replaced this one while it was being resampled
			sized.image().close();
			return;
		}
		DynamicTexture next = new DynamicTexture(() -> "kro_wallpaper", sized.image());
		if (texture != null) {
			texture.close();
		}
		texture = next;
		current = new Wallpaper(new Image(next), sized.width(), sized.height(), sized.source().subjectLeft(), sized.source().subjectRight());
	}

	/** True when a picture is loaded and drawable. */
	public boolean ready() {
		return current != null && current.image() != null;
	}
}
