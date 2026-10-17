package dev.kro.mc.fx;

import dev.kro.core.fx.Effects;
import dev.kro.core.fx.Fx;
import dev.kro.core.module.impl.BowTrajectory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Bow Trajectory: the flight of the shot you would release this frame, simulated tick by tick the
 * way the game moves that projectile (its launch speed, gravity, drag in air and water, and your
 * own momentum) and stopped by the first block or body in its way. Drawn as a ribbon of light
 * with a pulse running along it, with the spot it lands on marked.
 */
final class Trajectory {
	private static final int MAX_TICKS = 300;
	private static final double MAX_LENGTH = 160.0;
	/** Degrees between the arrows of a multishot crossbow. */
	private static final float MULTISHOT_SPREAD = 10f;
	private static final int TARGET_COLOR = 0xFFFF4F6D;

	/** How a projectile moves each tick. */
	private enum Motion {
		/** Arrows and tridents: move, then drag and gravity. */
		ARROW,
		/** Thrown items: gravity and drag, then move. */
		THROWN,
		/** Wind charges: a straight line. */
		STRAIGHT
	}

	/**
	 * @param lift  degrees the throw is aimed above your view (potions and bottles)
	 * @param drop  how far below your eyes the projectile starts
	 * @param inherit whether your own movement is added to the launch
	 */
	private record Shot(float speed, double gravity, double drag, double waterDrag, float lift, double drop, boolean inherit, Motion motion) {
	}

	/** What is about to be shot: from which hand, how, how bright its arc is and how many arrows. */
	private record Aim(InteractionHand hand, Shot shot, float strength, int count) {
	}

	private static final Shot CROSSBOW = new Shot(3.15f, 0.05, 0.99, 0.6, 0f, 0.1, false, Motion.ARROW);
	private static final Shot TRIDENT = new Shot(TridentItem.PROJECTILE_SHOOT_POWER, 0.05, 0.99, 0.99, 0f, 0.1, true, Motion.ARROW);
	private static final Shot THROWN = new Shot(1.5f, 0.03, 0.99, 0.8, 0f, 0.1, true, Motion.THROWN);
	private static final Shot POTION = new Shot(0.5f, 0.05, 0.99, 0.8, -20f, 0.1, true, Motion.THROWN);
	private static final Shot BOTTLE = new Shot(0.7f, 0.07, 0.99, 0.8, -20f, 0.1, true, Motion.THROWN);
	private static final Shot WIND = new Shot(1.5f, 0.0, 1.0, 1.0, 0f, 0.0, true, Motion.STRAIGHT);

	private final double[] xs = new double[MAX_TICKS + 1];
	private final double[] ys = new double[MAX_TICKS + 1];
	private final double[] zs = new double[MAX_TICKS + 1];
	private final double[] along = new double[MAX_TICKS + 1];
	private final double[] px = new double[MAX_TICKS + 1];
	private final double[] py = new double[MAX_TICKS + 1];
	private final double[] pz = new double[MAX_TICKS + 1];
	private final float[] widths = new float[MAX_TICKS + 1];
	private final float[] glowWidths = new float[MAX_TICKS + 1];
	private final int[] colors = new int[MAX_TICKS + 1];
	private final int[] glowColors = new int[MAX_TICKS + 1];
	private HitResult hit;

	void draw(Fx fx, BowTrajectory m, Minecraft mc, float pt, double clock) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (!m.enabled() || player == null || level == null || player.isSpectator()) {
			return;
		}
		Aim aim = aim(player, m);
		if (aim == null) {
			return;
		}
		float yaw = player.getYRot(pt);
		float pitch = player.getXRot(pt);
		Vec3 eye = player.getEyePosition(pt);
		Vec3 start = new Vec3(eye.x, eye.y - aim.shot().drop(), eye.z);
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		for (int k = 0; k < aim.count(); k++) {
			// multishot fires the middle arrow, then one to each side
			float angle = k == 0 ? 0f : (k % 2 == 1 ? -MULTISHOT_SPREAD : MULTISHOT_SPREAD);
			int n = simulate(level, player, aim.shot(), start, launch(player, aim.shot(), yaw, pitch, angle));
			render(fx, m, player, aim, n, yaw, pitch, firstPerson, clock, pt);
		}
	}

	// ------------------------------------------------------------------ what is aimed

	private static Aim aim(LocalPlayer player, BowTrajectory m) {
		if (player.isUsingItem()) {
			ItemStack use = player.getUseItem();
			InteractionHand hand = player.getUsedItemHand();
			if (use.is(Items.BOW)) {
				float power = BowItem.getPowerForTime(player.getTicksUsingItem());
				// under a tenth of a draw the bow does not shoot at all
				return power < 0.1f ? null : new Aim(hand, arrow(power * 3f), 0.4f + 0.6f * power, 1);
			}
			if (use.is(Items.TRIDENT)) {
				if (EnchantmentHelper.getTridentSpinAttackStrength(use, player) > 0f) {
					// riptide launches you, not the trident
					return null;
				}
				boolean ready = player.getTicksUsingItem() >= TridentItem.THROW_THRESHOLD_TIME;
				return new Aim(hand, TRIDENT, ready ? 1f : 0.4f, 1);
			}
			return null;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			Aim aim = held(player, m, player.getItemInHand(hand), hand);
			if (aim != null) {
				return aim;
			}
		}
		return null;
	}

	private static Aim held(LocalPlayer player, BowTrajectory m, ItemStack stack, InteractionHand hand) {
		if (stack.is(Items.BOW)) {
			return m.beforeDraw.on() && !player.getProjectile(stack).isEmpty() ? new Aim(hand, arrow(3f), 0.55f, 1) : null;
		}
		if (stack.is(Items.CROSSBOW)) {
			ChargedProjectiles loaded = stack.get(DataComponents.CHARGED_PROJECTILES);
			if (!CrossbowItem.isCharged(stack) || loaded == null || loaded.contains(Items.FIREWORK_ROCKET)) {
				// fireworks fly under their own power
				return null;
			}
			return new Aim(hand, CROSSBOW, 1f, Math.max(1, loaded.getItems().size()));
		}
		Shot shot = m.throwables.on() ? thrown(stack) : null;
		return shot == null ? null : new Aim(hand, shot, 0.85f, 1);
	}

	private static Shot arrow(float speed) {
		return new Shot(speed, 0.05, 0.99, 0.6, 0f, 0.1, true, Motion.ARROW);
	}

	private static Shot thrown(ItemStack s) {
		if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG) || s.is(Items.BLUE_EGG) || s.is(Items.BROWN_EGG)) {
			return THROWN;
		}
		if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) {
			return POTION;
		}
		if (s.is(Items.EXPERIENCE_BOTTLE)) {
			return BOTTLE;
		}
		return s.is(Items.WIND_CHARGE) ? WIND : null;
	}

	// ------------------------------------------------------------------ flight

	/** The launch velocity, as the game computes it from your aim. */
	private static Vec3 launch(LocalPlayer player, Shot shot, float yaw, float pitch, float angle) {
		Vec3 dir;
		if (angle == 0f) {
			double y = Math.toRadians(yaw), p = Math.toRadians(pitch), l = Math.toRadians(pitch + shot.lift());
			dir = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(l), Math.cos(y) * Math.cos(p));
		} else {
			// a multishot crossbow turns its side arrows about your view's up axis
			Vec3 view = viewVector(pitch, yaw);
			Vec3 up = viewVector(pitch - 90f, yaw);
			double a = Math.toRadians(angle), c = Math.cos(a), s = Math.sin(a);
			dir = view.scale(c).add(up.cross(view).scale(s)).add(up.scale(up.dot(view) * (1.0 - c)));
		}
		Vec3 v = dir.normalize().scale(shot.speed());
		if (shot.inherit()) {
			Vec3 own = player.getKnownMovement();
			v = v.add(own.x, player.onGround() ? 0.0 : own.y, own.z);
		}
		return v;
	}

	private static Vec3 viewVector(float pitch, float yaw) {
		double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
		return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}

	/** Flies the shot; fills the path and {@link #hit}, and returns the number of points. */
	private int simulate(ClientLevel level, LocalPlayer player, Shot shot, Vec3 start, Vec3 velocity) {
		Predicate<Entity> hittable = e -> e != player && e.isAlive() && e.isPickable() && !e.isSpectator() && e != player.getVehicle();
		double x = start.x, y = start.y, z = start.z;
		double vx = velocity.x, vy = velocity.y, vz = velocity.z;
		double floor = player.getY() - 200.0;
		double length = 0.0;
		xs[0] = x;
		ys[0] = y;
		zs[0] = z;
		along[0] = 0.0;
		int n = 1;
		hit = null;
		for (int tick = 1; tick <= MAX_TICKS; tick++) {
			boolean water = level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
			if (shot.motion() == Motion.THROWN) {
				double d = water ? shot.waterDrag() : shot.drag();
				vy -= shot.gravity();
				vx *= d;
				vy *= d;
				vz *= d;
			} else if (shot.motion() == Motion.ARROW && water) {
				vx *= shot.waterDrag();
				vy *= shot.waterDrag();
				vz *= shot.waterDrag();
			}
			Vec3 from = new Vec3(x, y, z);
			Vec3 to = new Vec3(x + vx, y + vy, z + vz);
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
			AABB sweep = new AABB(from.x, from.y, from.z, end.x, end.y, end.z).inflate(1.0);
			EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, player, from, end, sweep, hittable, margin(tick));
			if (entity != null) {
				hit = entity;
				end = entity.getLocation();
			} else if (block.getType() != HitResult.Type.MISS) {
				hit = block;
			}
			length += end.distanceTo(from);
			x = end.x;
			y = end.y;
			z = end.z;
			xs[n] = x;
			ys[n] = y;
			zs[n] = z;
			along[n] = length;
			n++;
			if (hit != null || length > MAX_LENGTH || y < floor) {
				break;
			}
			if (shot.motion() == Motion.ARROW) {
				if (!water) {
					vx *= shot.drag();
					vy *= shot.drag();
					vz *= shot.drag();
				}
				vy -= shot.gravity();
			}
		}
		return n;
	}

	/** How much a young projectile's reach toward bodies has grown: none at first, 0.3 by tick 8. */
	private static float margin(int tick) {
		return Math.max(0f, Math.min(0.3f, (tick - 2) / 20f));
	}

	// ------------------------------------------------------------------ light

	private void render(Fx fx, BowTrajectory m, LocalPlayer player, Aim aim, int n, float yaw, float pitch,
			boolean firstPerson, double clock, float pt) {
		if (n < 2) {
			return;
		}
		// in first person the arc leaves the hand holding it and settles onto its true path
		double ox = 0.0, oy = 0.0, oz = 0.0;
		if (firstPerson) {
			HumanoidArm arm = aim.hand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			double side = arm == HumanoidArm.RIGHT ? 0.16 : -0.16;
			double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
			double ux = -Math.sin(yr) * Math.sin(pr), uy = Math.cos(pr), uz = Math.cos(yr) * Math.sin(pr);
			ox = -Math.cos(yr) * side - ux * 0.1;
			oy = -uy * 0.1;
			oz = -Math.sin(yr) * side - uz * 0.1;
		}
		String palette = m.palette.get();
		int custom = m.customColor.color();
		float strength = aim.strength();
		double cx = fx.cameraX(), cy = fx.cameraY(), cz = fx.cameraZ();
		for (int i = 0; i < n; i++) {
			double s = along[i];
			double settle = firstPerson ? sq(Math.max(0.0, 1.0 - s / 5.0)) : 0.0;
			px[i] = xs[i] + ox * settle;
			py[i] = ys[i] + oy * settle;
			pz[i] = zs[i] + oz * settle;
			double dist = Math.sqrt(sq(px[i] - cx) + sq(py[i] - cy) + sq(pz[i] - cz));
			// a steady width on screen, near or far
			widths[i] = (float) Math.max(0.016, Math.min(0.3, dist * 0.0075));
			glowWidths[i] = widths[i] * 3.4f;
			double near = smooth((dist - 0.6) / 1.8);
			double shimmer = 0.8 + 0.2 * Math.sin(s * 1.6 - clock * 6.5);
			int color = Effects.palette(palette, custom, s * 0.035, clock);
			colors[i] = Fx.fade(color, 0.9 * strength * near * shimmer);
			glowColors[i] = Fx.fade(color, 0.2 * strength * near);
		}
		fx.ribbon(px, py, pz, glowWidths, glowColors, n);
		fx.ribbon(px, py, pz, widths, colors, n);

		// a pulse running out along the arc shows which way it flies
		double total = along[n - 1];
		double at = (clock * 0.7 % 1.0) * total;
		for (int i = 0; i < n - 1; i++) {
			if (along[i + 1] >= at) {
				double f = along[i + 1] > along[i] ? (at - along[i]) / (along[i + 1] - along[i]) : 0.0;
				double qx = px[i] + (px[i + 1] - px[i]) * f, qy = py[i] + (py[i + 1] - py[i]) * f, qz = pz[i] + (pz[i + 1] - pz[i]) * f;
				double dist = Math.sqrt(sq(qx - cx) + sq(qy - cy) + sq(qz - cz));
				double fadeOut = Math.min(1.0, (total - at) / 1.5);
				int color = Effects.palette(palette, custom, at * 0.035, clock);
				fx.orb(qx, qy, qz, Math.max(0.06, Math.min(0.9, dist * 0.026)), Fx.fade(color, 0.85 * strength * smooth((dist - 0.6) / 1.8) * fadeOut));
				break;
			}
		}
		if (hit != null) {
			mark(fx, palette, custom, strength, clock, pt, m.highlight.on());
		}
	}

	/** The landing: a ring where it strikes the ground, a glow on a wall, a column around a body. */
	private void mark(Fx fx, String palette, int custom, float strength, double clock, float pt, boolean highlight) {
		int a = Effects.palette(palette, custom, 0.0, clock);
		int b = Effects.palette(palette, custom, 0.5, clock);
		double pulse = 1.0 + 0.08 * Math.sin(clock * 5.0);
		Vec3 at = hit.getLocation();
		if (hit instanceof EntityHitResult eh) {
			fx.orb(at.x, at.y, at.z, 0.13, Fx.fade(a, 0.9 * strength));
			Entity e = eh.getEntity();
			if (highlight && e instanceof LivingEntity) {
				Vec3 feet = e.getPosition(pt);
				double r = e.getBbWidth() * 0.75;
				fx.cylinder(feet.x, feet.y + 0.02, feet.z, r * pulse, feet.y + e.getBbHeight() * 0.9,
						Fx.fade(TARGET_COLOR, 0.55 * strength), Fx.fade(b, 0.45 * strength), clock * 2.0);
				fx.groundRing(feet.x, feet.y + 0.03, feet.z, r * 1.25 * pulse, 0.06,
						Fx.fade(TARGET_COLOR, 0.9 * strength), Fx.fade(a, 0.9 * strength), clock * 1.6);
				fx.groundGlow(feet.x, feet.y + 0.02, feet.z, r * 1.8, Fx.fade(TARGET_COLOR, 0.3 * strength));
			}
		} else if (hit instanceof BlockHitResult bh) {
			Direction face = bh.getDirection();
			if (face == Direction.UP) {
				double y = at.y + 0.015;
				fx.groundRing(at.x, y, at.z, 0.34 * pulse, 0.065, Fx.fade(a, 0.95 * strength), Fx.fade(b, 0.95 * strength), clock * 1.6);
				fx.groundGlow(at.x, y, at.z, 0.62, Fx.fade(a, 0.38 * strength));
				fx.orb(at.x, y + 0.04, at.z, 0.1, Fx.fade(b, 0.9 * strength));
			} else {
				double x = at.x + face.getStepX() * 0.04, y = at.y + face.getStepY() * 0.04, z = at.z + face.getStepZ() * 0.04;
				fx.halo(x, y, z, 0.3 * pulse, Fx.fade(a, 0.85 * strength));
				fx.orb(x, y, z, 0.11, Fx.fade(b, 0.9 * strength));
			}
		}
	}

	private static double sq(double v) {
		return v * v;
	}

	private static double smooth(double t) {
		t = t < 0.0 ? 0.0 : Math.min(t, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
