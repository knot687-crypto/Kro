package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World Theme: sky and cloud colours are environment attributes the camera samples every frame;
 * tint them as they are read. Cosmetic and client-side only.
 */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class EnvironmentAttributeProbeMixin {
	@Inject(method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;",
			at = @At("RETURN"), cancellable = true, require = 0)
	private void kro$themeColours(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> cir) {
		Kro kro = Kro.get();
		if (kro != null && kro.worldTheme.active() && cir.getReturnValue() instanceof Integer colour) {
			int tinted = kro.worldTheme.attribute(attribute, colour);
			if (tinted != colour) {
				cir.setReturnValue(tinted);
			}
		}
	}
}
