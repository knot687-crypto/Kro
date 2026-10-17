package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Stretch Res: the two perspective projections of the 3D view, the world's (also used for
 * culling) and the first-person hand's, are built for the stretched resolution's ratio.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@ModifyArg(method = "getProjectionMatrix", at = @At(value = "INVOKE",
			target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"), index = 1)
	private float kro$worldAspect(float aspect) {
		Kro kro = Kro.get();
		return kro == null ? aspect : kro.stretchRes.aspect(aspect);
	}

	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/CachedPerspectiveProjectionMatrixBuffer;getBuffer(IIF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
	private int kro$handWidth(int width, int height, float fov) {
		Kro kro = Kro.get();
		return kro == null ? width : kro.stretchRes.width(width, height);
	}
}
