package dev.kro.mc.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.kro.ui.render.GlyphAtlas;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Mirrors glyph atlas pages into GPU textures, uploading only when glyphs were added. */
final class GlyphTextures {
	private GlyphTextures() {
	}

	static TextureSetup setup(GlyphAtlas atlas, int pageIndex) {
		GlyphAtlas.Page page = atlas.page(pageIndex);
		DynamicTexture texture = (DynamicTexture) page.backendTexture;
		if (texture == null) {
			texture = new DynamicTexture(() -> "kro_glyphs_" + pageIndex, GlyphAtlas.SIZE, GlyphAtlas.SIZE, true);
			page.backendTexture = texture;
			page.dirtyX0 = 0;
			page.dirtyY0 = 0;
			page.dirtyX1 = GlyphAtlas.SIZE;
			page.dirtyY1 = GlyphAtlas.SIZE;
			page.uploadedVersion = -1;
		}
		if (page.uploadedVersion != page.version) {
			NativeImage image = texture.getPixels();
			if (image != null) {
				byte[] alpha = page.alpha;
				for (int y = page.dirtyY0; y < page.dirtyY1; y++) {
					int row = y * GlyphAtlas.SIZE;
					for (int x = page.dirtyX0; x < page.dirtyX1; x++) {
						int a = alpha[row + x] & 0xFF;
						image.setPixel(x, y, (a << 24) | 0x00FFFFFF);
					}
				}
				texture.upload();
			}
			page.clearDirty();
			page.uploadedVersion = page.version;
		}
		return TextureSetup.singleTexture(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
	}
}
