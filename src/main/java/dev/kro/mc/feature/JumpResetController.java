package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.JumpReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Presses jump for one tick right as a hit lands, the same timing practised players use to
 * soften knockback. It does nothing a player could not do by hand, and the chance setting keeps
 * it imperfect on purpose.
 */
public final class JumpResetController {
	private final Kro kro;
	private int lastHurtTime;
	private int countdown = -1;
	private boolean forceJump;

	public JumpResetController(Kro kro) {
		this.kro = kro;
	}

	/** Read by the jump key mixin while the player ticks. */
	public boolean forceJump() {
		return forceJump;
	}

	public void startTick(Minecraft mc) {
		forceJump = false;
		LocalPlayer player = mc.player;
		JumpReset m = kro.modules.jumpReset;
		if (player == null || mc.screen != null) {
			lastHurtTime = 0;
			countdown = -1;
			return;
		}
		int hurt = player.hurtTime;
		boolean freshHit = hurt > lastHurtTime && hurt >= player.hurtDuration - 1;
		lastHurtTime = hurt;
		if (freshHit && m.enabled() && shouldReact(mc, player, m)) {
			countdown = (int) Math.round(m.delay.random());
		}
		if (countdown >= 0) {
			if (countdown == 0) {
				if (player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()) {
					forceJump = true;
				}
				countdown = -1;
			} else {
				countdown--;
			}
		}
	}

	public void endTick(Minecraft mc) {
		forceJump = false;
	}

	private boolean shouldReact(Minecraft mc, LocalPlayer player, JumpReset m) {
		if (ThreadLocalRandom.current().nextDouble(100.0) >= m.chance.value()) {
			return false;
		}
		if (m.onlyMoving.on() && !(mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown())) {
			return false;
		}
		if (m.onlyAttacker.on()) {
			boolean near = false;
			for (Player p : mc.level.players()) {
				if (p != player && !p.isSpectator() && p.distanceToSqr(player) < 5.0 * 5.0) {
					near = true;
					break;
				}
			}
			return near;
		}
		return true;
	}
}
