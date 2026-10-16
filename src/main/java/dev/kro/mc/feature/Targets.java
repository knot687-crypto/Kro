package dev.kro.mc.feature;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared target filtering and geometry for the combat modules. */
public final class Targets {
	private Targets() {
	}

	public static boolean isWeapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TRIDENT)) {
			return true;
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_spear");
	}

	/** Mode is one of Players, Mobs, All. */
	public static boolean accepts(LocalPlayer self, Entity e, String mode, boolean ignoreTeam, boolean ignoreInvisible) {
		if (!(e instanceof LivingEntity living) || e == self || !living.isAlive() || living.isDeadOrDying()) {
			return false;
		}
		if (e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof Player p) {
			if (p.isSpectator() || p.isCreative()) {
				return false;
			}
			if (mode.equals("Mobs")) {
				return false;
			}
		} else if (mode.equals("Players")) {
			return false;
		} else if (mode.equals("Mobs") && !(e instanceof Mob)) {
			return false;
		}
		if (ignoreInvisible && e.isInvisible()) {
			return false;
		}
		return !ignoreTeam || !self.isAlliedTo(e);
	}

	/** Closest point of a box to a ray, used so assistance only nudges you onto the nearest edge. */
	public static Vec3 nearestOnBox(AABB box, Vec3 eye, Vec3 look) {
		Vec3 centre = box.getCenter();
		double t = Math.max(0.0, centre.subtract(eye).dot(look));
		Vec3 onRay = eye.add(look.scale(t));
		return new Vec3(
				clamp(onRay.x, box.minX, box.maxX),
				clamp(onRay.y, box.minY, box.maxY),
				clamp(onRay.z, box.minZ, box.maxZ));
	}

	public static double clamp(double v, double min, double max) {
		return v < min ? min : Math.min(v, max);
	}

	/** Yaw and pitch (degrees) that look from {@code from} to {@code to}. */
	public static float[] angles(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
		return new float[] {yaw, pitch};
	}
}
