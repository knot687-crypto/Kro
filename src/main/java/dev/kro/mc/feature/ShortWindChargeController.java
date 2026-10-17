package dev.kro.mc.feature;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kro.Kro;
import dev.kro.core.module.impl.ShortWindCharge;
import dev.kro.mixin.KeyMappingAccessor;
import dev.kro.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

/**
 * A wind charge thrown straight at your feet for an instant launch. The look-down, the throw and
 * the look back all happen inside one tick, before the frame is drawn, so the camera never
 * moves; the throw itself is a vanilla use of the item, which carries that aim to the server.
 */
public final class ShortWindChargeController {
	private final Kro kro;
	private boolean requested;
	/** A High launch jumped last tick and throws on this one. */
	private boolean airborne;
	private int forceJump;
	private int originalSlot = -1;
	private boolean throwing;
	private float pitch;
	private float bob;

	public ShortWindChargeController(Kro kro) {
		this.kro = kro;
	}

	/** Read by the jump key while the player moves. */
	public boolean forceJump() {
		return forceJump > 0;
	}

	/** True from a throw until the end of its tick: the hotbar and aim belong to the throw. */
	public boolean throwing() {
		return throwing;
	}

	/** A key or mouse button went down (codes as in {@link dev.kro.core.util.Keys}). */
	public void onKey(int code) {
		ShortWindCharge m = kro.modules.shortWindCharge;
		if (m.enabled() && m.activation.is("Key") && m.throwKey.bound() && code == m.throwKey.key()) {
			requested = true;
		}
	}

	public void startTick(Minecraft mc) {
		if (forceJump > 0) {
			forceJump--;
		}
		ShortWindCharge m = kro.modules.shortWindCharge;
		LocalPlayer player = mc.player;
		if (!m.enabled() || player == null || mc.gameMode == null || mc.screen != null || player.isSpectator()) {
			requested = false;
			airborne = false;
			originalSlot = -1;
			return;
		}
		Inventory inv = player.getInventory();
		if (airborne) {
			airborne = false;
			if (canThrow(player, inv)) {
				throwNow(mc, player, inv, true);
			} else {
				originalSlot = -1;
			}
			return;
		}
		// a right click with the mace is queued for this tick: the throw takes its place
		boolean byUse = m.activation.is("Use with mace") && player.getMainHandItem().is(Items.MACE)
				&& ((KeyMappingAccessor) mc.options.keyUse).kro$clickCount() > 0;
		if (!requested && !byUse) {
			return;
		}
		requested = false;
		if (kro.hitCrystal.holdsUse() || !canThrow(player, inv)) {
			return;
		}
		originalSlot = inv.getSelectedSlot();
		if (m.height.is("High") && player.onGround()) {
			// jump this tick, throw on the next one while rising
			forceJump = 1;
			airborne = true;
			return;
		}
		throwNow(mc, player, inv, !byUse);
	}

	private static boolean canThrow(LocalPlayer player, Inventory inv) {
		int charge = windChargeSlot(inv);
		return charge >= 0 && !player.getCooldowns().isOnCooldown(inv.getItem(charge)) && !player.isUsingItem()
				&& !player.isFallFlying() && !player.isSwimming();
	}

	private void throwNow(Minecraft mc, LocalPlayer player, Inventory inv, boolean click) {
		int charge = windChargeSlot(inv);
		if (charge < 0) {
			originalSlot = -1;
			return;
		}
		inv.setSelectedSlot(charge);
		pitch = player.getXRot();
		bob = player.xBob;
		player.setXRot(90f);
		if (click) {
			KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
		}
		throwing = true;
	}

	/** End of the tick, after the throw went out: everything is put back before a frame is drawn. */
	public void endTick(Minecraft mc) {
		if (!throwing) {
			return;
		}
		throwing = false;
		LocalPlayer player = mc.player;
		if (player == null) {
			originalSlot = -1;
			return;
		}
		// as if the tick had run with your own aim: view, hand sway and crosshair target
		player.setXRot(pitch);
		player.xRotO = pitch;
		player.xBobO = bob;
		player.xBob = bob + (pitch - bob) * 0.5f;
		if (kro.modules.shortWindCharge.swapBack.on() && originalSlot >= 0) {
			player.getInventory().setSelectedSlot(originalSlot);
			if (mc.gameMode != null) {
				((MultiPlayerGameModeAccessor) mc.gameMode).kro$syncCarriedItem();
			}
		}
		originalSlot = -1;
		mc.gameRenderer.pick(1f);
	}

	private static int windChargeSlot(Inventory inv) {
		int selected = inv.getSelectedSlot();
		if (inv.getItem(selected).is(Items.WIND_CHARGE)) {
			return selected;
		}
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (inv.getItem(i).is(Items.WIND_CHARGE)) {
				return i;
			}
		}
		return -1;
	}
}
