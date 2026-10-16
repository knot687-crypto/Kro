package dev.kro.mc.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.kro.ui.render.GlyphAtlas;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Mirrors glyph atlas pages into GPU textures, uploading only when glyphs were added. */
final class GlyphTextures {
	/** A page's texture and the CPU image it uploads from. */
	private record Page(DynamicTexture texture, NativeImage image) {
	}

	private GlyphTextures() {
	}

	static TextureSetup setup(GlyphAtlas atlas, int pageIndex) {
		GlyphAtlas.Page page = atlas.page(pageIndex);
		Page gpu = (Page) page.backendTexture;
		if (gpu == null) {
			NativeImage image = new NativeImage(GlyphAtlas.SIZE, GlyphAtlas.SIZE, true);
			gpu = new Page(new DynamicTexture(() -> "kro_glyphs_" + pageIndex, image), image);
			page.backendTexture = gpu;
			page.dirtyX0 = 0;
			page.dirtyY0 = 0;
			page.dirtyX1 = GlyphAtlas.SIZE;
			page.dirtyY1 = GlyphAtlas.SIZE;
			page.uploadedVersion = -1;
		}
		if (page.uploadedVersion != page.version) {
			NativeImage image = gpu.image();
			byte[] alpha = page.alpha;
			for (int y = page.dirtyY0; y < page.dirtyY1; y++) {
				int row = y * GlyphAtlas.SIZE;
				for (int x = page.dirtyX0; x < page.dirtyX1; x++) {
					int a = alpha[row + x] & 0xFF;
					image.setPixel(x, y, (a << 24) | 0x00FFFFFF);
				}
			}
			gpu.texture().upload();
			page.clearDirty();
			page.uploadedVersion = page.version;
		}
		return TextureSetup.singleTexture(gpu.texture().getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
	}
}
