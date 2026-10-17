package dev.kro.mc.feature;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kro.Kro;
import dev.kro.core.module.impl.AutoCrystal;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Crystal placing and breaking on your crosshair. Every action is a click through vanilla's own
 * key handling, so reach, cooldowns and placement rules behave exactly as if you pressed the
 * button: Kro only decides when. One action per tick at most, each after a randomised delay.
 */
public final class AutoCrystalController {
	/** Ticks a spot or crystal is left alone after acting on it, while the server answers. */
	private static final int LOCKOUT = 4;

	private final Kro kro;
	private long tick;
	private long lastAction = Long.MIN_VALUE / 2;
	private BlockPos armedSpot;
	private long spotReadyAt;
	private BlockPos lockedSpot;
	private long spotLockedUntil;
	private int armedCrystal = Integer.MIN_VALUE;
	private long crystalReadyAt;
	private int lockedCrystal = Integer.MIN_VALUE;
	private long crystalLockedUntil;

	public AutoCrystalController(Kro kro) {
		this.kro = kro;
	}

	public void tick(Minecraft mc) {
		tick++;
		AutoCrystal m = kro.modules.autoCrystal;
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (!m.enabled() || player == null || level == null || mc.gameMode == null || mc.screen != null || player.isSpectator()) {
			disarm();
			return;
		}
		boolean holding = player.getMainHandItem().is(Items.END_CRYSTAL) || player.getOffhandItem().is(Items.END_CRYSTAL);
		boolean triggered = m.activation.is("Always") || mc.options.keyUse.isDown();
		if (!holding || !triggered || (m.pauseOnEat.on() && player.isUsingItem())) {
			disarm();
			return;
		}
		boolean canAct = tick - lastAction >= 1;
		HitResult hit = mc.hitResult;
		if (hit != null && hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystal crystal) {
			armedSpot = null;
			if (!m.hit.on() || (crystal.getId() == lockedCrystal && tick < crystalLockedUntil)) {
				return;
			}
			if (crystal.getId() != armedCrystal) {
				armedCrystal = crystal.getId();
				crystalReadyAt = tick + Math.round(m.breakDelay.random());
			}
			if (tick >= crystalReadyAt && canAct) {
				click(mc.options.keyAttack);
				lastAction = tick;
				lockedCrystal = crystal.getId();
				crystalLockedUntil = tick + LOCKOUT;
				armedCrystal = Integer.MIN_VALUE;
			}
			return;
		}
		armedCrystal = Integer.MIN_VALUE;
		if (!m.place.on() || hit == null || hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult bh)) {
			armedSpot = null;
			return;
		}
		BlockPos spot = bh.getBlockPos();
		if (!canPlaceOn(level, spot) || (spot.equals(lockedSpot) && tick < spotLockedUntil)) {
			armedSpot = null;
			return;
		}
		if (!spot.equals(armedSpot)) {
			armedSpot = spot;
			spotReadyAt = tick + Math.round(m.placeDelay.random());
		}
		if (tick >= spotReadyAt && canAct) {
			click(mc.options.keyUse);
			lastAction = tick;
			lockedSpot = spot;
			spotLockedUntil = tick + LOCKOUT;
			armedSpot = null;
		}
	}

	/** The same checks the end crystal item makes before it places. */
	static boolean canPlaceOn(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
			return false;
		}
		BlockPos above = pos.above();
		if (!level.isEmptyBlock(above)) {
			return false;
		}
		AABB room = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
		return level.getEntities((Entity) null, room).isEmpty();
	}

	private void disarm() {
		armedSpot = null;
		armedCrystal = Integer.MIN_VALUE;
	}

	private static void click(KeyMapping key) {
		KeyMapping.click(InputConstants.getKey(key.saveString()));
	}
}
