package dev.kro.mc.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** An image backed by a texture registered with the game's texture manager, such as a skin. */
public final class McImage implements GpuImage {
	private final Identifier id;
	private final boolean smooth;

	public McImage(Identifier id, boolean smooth) {
		this.id = id;
		this.smooth = smooth;
	}

	@Override
	public Object handle() {
		return id;
	}

	@Override
	public GpuTextureView view() {
		AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
		return texture.getTextureView();
	}

	@Override
	public GpuSampler sampler() {
		return RenderSystem.getSamplerCache().getClampToEdge(smooth ? FilterMode.LINEAR : FilterMode.NEAREST);
	}
}
