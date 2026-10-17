package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.core.module.impl.WTap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * After a sprint hit, lets go of forward for a tick or two, exactly like tapping W: the sprint
 * ends, so the server hears it start again, and your next hit carries sprint knockback. When
 * forward comes back the sprint key is held for a tick, so the sprint resumes however you sprint.
 */
public final class WTapController {
	private final Kro kro;
	private int wait = -1;
	private int length;
	private int releasing;
	private int resprint;

	public WTapController(Kro kro) {
		this.kro = kro;
	}

	/** Read by the forward key while the player moves. */
	public boolean releasing() {
		return releasing > 0;
	}

	/** Read by the sprint key: the tick forward returns, so the sprint starts again. */
	public boolean resprinting() {
		return resprint > 0;
	}

	public void onAttack(Minecraft mc, Entity target) {
		WTap m = kro.modules.wTap;
		if (!m.enabled() || mc.player == null || !mc.player.isSprinting() || !(target instanceof LivingEntity)) {
			return;
		}
		if (m.playersOnly.on() && !(target instanceof Player)) {
			return;
		}
		if (ThreadLocalRandom.current().nextDouble(100.0) >= m.chance.value()) {
			return;
		}
		wait = (int) Math.round(m.delay.random());
		length = Math.max(1, (int) Math.round(m.release.random()));
	}

	public void startTick(Minecraft mc) {
		if (resprint > 0) {
			resprint--;
		}
		if (releasing > 0 && --releasing == 0) {
			resprint = 1;
		}
		if (mc.player == null || mc.screen != null) {
			wait = -1;
			releasing = 0;
			resprint = 0;
			return;
		}
		if (wait >= 0) {
			if (wait == 0) {
				releasing = length;
				wait = -1;
			} else {
				wait--;
			}
		}
	}
}
