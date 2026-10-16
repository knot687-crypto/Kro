package dev.kro.mixin;

import dev.kro.Kro;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sprint and Jump Reset act by holding the real keys, so every vanilla rule (hunger, blindness,
 * sneaking, using items, ground checks) still decides what actually happens.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void kro$isDown(CallbackInfoReturnable<Boolean> cir) {
		Kro kro = Kro.get();
		Minecraft mc = Minecraft.getInstance();
		if (kro == null || mc == null || mc.options == null || mc.player == null) {
			return;
		}
		Object self = this;
		if (self == mc.options.keySprint && kro.modules.sprint.enabled() && mc.screen == null) {
			cir.setReturnValue(true);
		} else if (self == mc.options.keyJump && kro.jumpReset.forceJump()) {
			cir.setReturnValue(true);
		}
	}
}
