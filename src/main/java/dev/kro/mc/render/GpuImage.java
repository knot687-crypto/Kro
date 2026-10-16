package dev.kro.mc.render;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.kro.ui.render.ImageRef;

/** An {@link ImageRef} the GUI backend can bind directly. */
public interface GpuImage extends ImageRef {
	GpuTextureView view();

	GpuSampler sampler();
}
