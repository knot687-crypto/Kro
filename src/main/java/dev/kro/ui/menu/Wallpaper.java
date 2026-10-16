package dev.kro.ui.menu;

import dev.kro.ui.render.ImageRef;

/**
 * A picture shown behind the menu. A portrait picture stands beside the window with its
 * edges melting into a black stage; a landscape one covers the screen.
 *
 * @param pixelWidth   texture width in texels
 * @param pixelHeight  texture height in texels; when it matches the screen's pixel height the
 *                     picture is drawn one texel per pixel
 * @param subjectLeft  left edge of the subject, as a fraction of the picture's width
 * @param subjectRight right edge of the subject, as a fraction of the picture's width
 */
public record Wallpaper(ImageRef image, int pixelWidth, int pixelHeight, float subjectLeft, float subjectRight) {
	/** Width over height. */
	public float aspect() {
		return pixelWidth / (float) pixelHeight;
	}

	public boolean portrait() {
		return aspect() < 1.15f;
	}
}
