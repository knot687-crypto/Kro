package dev.kro.ui.menu;

import dev.kro.ui.render.ImageRef;

/**
 * The picture that fills the menu window, behind the interface.
 *
 * @param image       the picture itself, ideally resampled to the window's exact pixel width
 * @param glass       a blurred, highlight-tamed copy that glass surfaces show through
 * @param pixelWidth  texel width of {@code image}; drawn one texel per pixel when it matches
 * @param pixelHeight texel height of {@code image}
 * @param focusX      where the eye goes (the subject's head), as a fraction of the width
 * @param focusY      where the eye goes, as a fraction of the height
 */
public record Wallpaper(ImageRef image, ImageRef glass, int pixelWidth, int pixelHeight, float focusX, float focusY) {
	/** Width over height. */
	public float aspect() {
		return pixelWidth / (float) pixelHeight;
	}

	public boolean ready() {
		return image != null && glass != null;
	}
}
