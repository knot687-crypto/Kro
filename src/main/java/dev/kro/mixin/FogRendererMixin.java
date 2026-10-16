package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World Theme: fog tint. The controller ignores a colour it has already tinted. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFZ)Lorg/joml/Vector4f;",
			at = @At("RETURN"), require = 0)
	private void kro$fog(CallbackInfoReturnable<Vector4f> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active()) {
			kro.worldTheme.fog(cir.getReturnValue());
		}
	}

	@Inject(method = "computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)Lorg/joml/Vector4f;",
			at = @At("RETURN"), require = 0)
	private void kro$fogDefault(CallbackInfoReturnable<Vector4f> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active()) {
			kro.worldTheme.fog(cir.getReturnValue());
		}
	}
}
