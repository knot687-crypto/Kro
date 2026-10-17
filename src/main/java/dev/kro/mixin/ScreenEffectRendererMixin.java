package dev.kro.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kro.Kro;
import dev.kro.core.module.impl.LowFire;
import dev.kro.core.module.impl.SmallerTotem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person screen effects. Smaller Totem scales (or hides) the totem that flies up when one
 * saves you; Low Fire sinks and fades the burning overlay, or removes it under Fire Resistance.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	/** Low Fire's full drop, in the overlay's own units (it is drawn at -0.3). */
	@Unique
	private static final float MAX_EXTRA_DROP = 0.45f;

	@Shadow
	private ItemStack itemActivationItem;

	@Unique
	private boolean kro$isTotemPop() {
		Kro kro = Kro.get();
		return kro != null && kro.modules.smallerTotem.enabled() && itemActivationItem != null && itemActivationItem.is(Items.TOTEM_OF_UNDYING);
	}

	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
	private void kro$hidePop(PoseStack poseStack, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
		if (kro$isTotemPop() && Kro.get().modules.smallerTotem.popSize.value() <= 0.0) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItemActivationAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER))
	private void kro$shrinkPop(PoseStack poseStack, float partialTick, SubmitNodeCollector collector, CallbackInfo ci) {
		if (kro$isTotemPop()) {
			SmallerTotem m = Kro.get().modules.smallerTotem;
			float s = m.popSize.floatValue() / 100f;
			poseStack.scale(s, s, s);
		}
	}

	@Unique
	private static LowFire kro$lowFire() {
		Kro kro = Kro.get();
		return kro != null && kro.modules.lowFire.enabled() ? kro.modules.lowFire : null;
	}

	@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
	private static void kro$fireResistant(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		LowFire m = kro$lowFire();
		Minecraft mc = Minecraft.getInstance();
		if (m != null && m.hideResistant.on() && mc.player != null && mc.player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			ci.cancel();
		}
	}

	@ModifyArg(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1)
	private static float kro$fireLower(float y) {
		LowFire m = kro$lowFire();
		return m == null ? y : y - MAX_EXTRA_DROP * m.lower.floatValue() / 100f;
	}

	@ModifyArg(method = "renderFire", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), index = 3)
	private static float kro$fireOpacity(float alpha) {
		LowFire m = kro$lowFire();
		return m == null ? alpha : alpha * m.opacity.floatValue() / 100f;
	}
}
