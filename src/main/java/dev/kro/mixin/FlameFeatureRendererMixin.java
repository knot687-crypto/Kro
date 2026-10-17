package dev.kro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.kro.Kro;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Low On Fire: vanilla stacks flame layers on a burning entity up to its full height. Reading a
 * shorter height makes the flames stop lower, so they never cover the body above them.
 */
@Mixin(FlameFeatureRenderer.class)
public abstract class FlameFeatureRendererMixin {
	@ModifyExpressionValue(method = "renderFlame", at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;boundingBoxHeight:F"))
	private float kro$lowFlames(float height) {
		Kro kro = Kro.get();
		if (kro == null || !kro.modules.lowOnFire.enabled()) {
			return height;
		}
		return height * kro.modules.lowOnFire.height.floatValue() / 100f;
	}
}
