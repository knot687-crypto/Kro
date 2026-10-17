package dev.kro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.kro.Kro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Explosion, motion and entity events as they reach the client: No Explosions drops the visual
 * parts of a blast, Velocity scales the push you are given, and totem pops and deaths feed
 * Kro's effects.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Unique
	private static final byte TOTEM_POP = 35;
	@Unique
	private static final byte DEATH = 3;

	@WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private boolean kro$blastCloud(ClientLevel level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
		Kro kro = Kro.get();
		return kro == null || !kro.modules.noExplosions.enabled() || !kro.modules.noExplosions.cloud.on();
	}

	@WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V"))
	private boolean kro$blastDebris(ClientLevel level, Vec3 center, float radius, int blockCount, WeightedList<ExplosionParticleInfo> particles) {
		Kro kro = Kro.get();
		return kro == null || !kro.modules.noExplosions.enabled() || !kro.modules.noExplosions.debris.on();
	}

	@ModifyExpressionValue(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
	private Optional<Vec3> kro$blastPush(Optional<Vec3> push, @Local(argsOnly = true) ClientboundExplodePacket packet) {
		Kro kro = Kro.get();
		return kro == null ? push : kro.velocity.explosion(push, packet.explosionParticle());
	}

	@WrapOperation(method = "handleSetEntityMotion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"))
	private void kro$motion(Entity entity, Vec3 motion, Operation<Void> original) {
		Kro kro = Kro.get();
		original.call(entity, kro == null ? motion : kro.velocity.motion(entity, motion));
	}

	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void kro$blastSeen(ClientboundExplodePacket packet, CallbackInfo ci) {
		Kro kro = Kro.get();
		if (kro != null) {
			kro.effects.onExplosion(packet.center(), packet.radius());
		}
	}

	@WrapWithCondition(method = "handleEntityEvent", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
	private boolean kro$totemParticles(ParticleEngine engine, Entity entity, ParticleOptions particle, int lifetime) {
		Kro kro = Kro.get();
		return kro == null || !kro.effects.replacesTotemParticles(entity);
	}

	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void kro$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Kro kro = Kro.get();
		Minecraft mc = Minecraft.getInstance();
		if (kro == null || mc.level == null) {
			return;
		}
		Entity entity = packet.getEntity(mc.level);
		if (entity == null) {
			return;
		}
		if (packet.getEventId() == TOTEM_POP) {
			kro.effects.onTotemPop(entity);
		} else if (packet.getEventId() == DEATH) {
			kro.effects.onDeath(entity);
		}
	}
}
