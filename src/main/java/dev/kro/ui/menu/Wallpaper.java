package dev.kro.ui.menu;

import dev.kro.ui.render.ImageRef;

/**
 * The picture shown whole inside the menu window, behind the interface.
 *
 * @param image       the picture itself, resampled to the exact pixel size it is shown at
 * @param tinted      the same picture, equally sharp, with highlights tamed for glass surfaces
 * @param pixelWidth  texel width of both images; drawn one texel per pixel when it matches
 * @param pixelHeight texel height of both images
 */
public record Wallpaper(ImageRef image, ImageRef tinted, int pixelWidth, int pixelHeight) {
	/** Width over height. */
	public float aspect() {
		return pixelWidth / (float) pixelHeight;
	}

	public boolean ready() {
		return image != null && tinted != null;
	}
}
