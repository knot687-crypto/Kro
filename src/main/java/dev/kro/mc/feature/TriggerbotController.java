package dev.kro.mc.feature;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kro.Kro;
import dev.kro.core.module.impl.Triggerbot;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Clicks for you when your crosshair is already on a valid target, after a randomised
 * reaction delay. The click goes through vanilla's own key handling, so cooldowns, misses and
 * every other rule behave exactly as if you pressed the button.
 */
public final class TriggerbotController {
	private final Kro kro;
	private Entity seen;
	private long readyAt;
	private long nextClick;

	public TriggerbotController(Kro kro) {
		this.kro = kro;
	}

	public void tick(Minecraft mc) {
		Triggerbot m = kro.modules.triggerbot;
		LocalPlayer player = mc.player;
		if (!m.enabled() || player == null || mc.screen != null || mc.gameMode == null) {
			seen = null;
			return;
		}
		HitResult hit = mc.hitResult;
		Entity e = hit instanceof EntityHitResult eh && hit.getType() == HitResult.Type.ENTITY ? eh.getEntity() : null;
		boolean valid = e != null
				&& Targets.accepts(player, e, m.targets.get(), m.ignoreTeam.on(), m.ignoreInvisible.on())
				&& !player.isUsingItem()
				&& (!m.weaponOnly.on() || Targets.isWeapon(player.getMainHandItem()));
		if (!valid) {
			seen = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (e != seen) {
			seen = e;
			readyAt = now + (long) m.reaction.random();
		}
		if (now < readyAt) {
			return;
		}
		if (m.timing.is("Cooldown")) {
			if (player.getAttackStrengthScale(0.5f) >= m.charge.floatValue() / 100f) {
				click(mc);
			}
		} else if (now >= nextClick) {
			click(mc);
			double cps = Math.max(1.0, m.cps.random());
			nextClick = now + (long) (1000.0 / cps);
		}
	}

	private static void click(Minecraft mc) {
		KeyMapping.click(InputConstants.getKey(mc.options.keyAttack.saveString()));
	}
}
