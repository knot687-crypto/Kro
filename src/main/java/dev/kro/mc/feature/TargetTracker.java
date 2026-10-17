package dev.kro.mc.feature;

import dev.kro.Kro;
import dev.kro.mc.render.McImage;
import dev.kro.ui.hud.TargetInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashMap;
import java.util.Map;

/** Decides who you are fighting and snapshots them for the Target HUD. */
public final class TargetTracker {
	private static final long MEMORY_MS = 6000L;

	private final Kro kro;
	private final TargetInfo info = new TargetInfo();
	private final Map<Identifier, McImage> faces = new HashMap<>();
	private LivingEntity lastAttacked;
	private long lastAttackAt;
	private LivingEntity current;

	public TargetTracker(Kro kro) {
		this.kro = kro;
	}

	/** Called by the attack mixin whenever the player swings at an entity. */
	public void onAttack(Entity e) {
		if (e instanceof LivingEntity living) {
			lastAttacked = living;
			lastAttackAt = System.currentTimeMillis();
		}
	}

	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			current = null;
			lastAttacked = null;
			return;
		}
		LivingEntity pick = null;
		if (lastAttacked != null && lastAttacked.isAlive() && !lastAttacked.isRemoved()
				&& System.currentTimeMillis() - lastAttackAt < MEMORY_MS && lastAttacked.distanceTo(player) < 14f) {
			pick = lastAttacked;
		} else if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
				&& living != player && living.isAlive() && living.distanceTo(player) < 7f) {
			pick = living;
		}
		current = pick;
	}

	/** The opponent you are fighting right now, or null. */
	public LivingEntity current() {
		return current;
	}

	/** The current target snapshot, or null when there is nobody to show. */
	public TargetInfo info() {
		Minecraft mc = Minecraft.getInstance();
		if (current == null || mc.player == null) {
			return null;
		}
		fill(info, current, mc.player);
		return info;
	}

	/** A preview target (yourself) for the HUD editor. */
	public TargetInfo sample() {
		Minecraft mc = Minecraft.getInstance();
		TargetInfo t = new TargetInfo();
		if (mc.player != null) {
			fill(t, mc.player, mc.player);
			t.health = Math.min(t.maxHealth, Math.max(1f, t.maxHealth * 0.7f));
			t.distance = 3.1f;
			t.hurt = 0f;
			t.selfHealth = t.maxHealth * 0.85f;
		} else {
			t.name = "Opponent";
			t.health = 14f;
		}
		t.id = -42;
		return t;
	}

	private void fill(TargetInfo t, LivingEntity e, LocalPlayer self) {
		t.id = e.getId();
		t.name = e.getName().getString();
		t.player = e instanceof Player;
		t.health = e.getHealth();
		t.maxHealth = Math.max(1f, e.getMaxHealth());
		t.absorption = e.getAbsorptionAmount();
		t.armor = e.getArmorValue();
		t.distance = e.distanceTo(self);
		t.hurt = e.hurtDuration > 0 ? Math.max(0f, e.hurtTime / (float) e.hurtDuration) : 0f;
		t.selfHealth = self.getHealth();
		t.selfMax = Math.max(1f, self.getMaxHealth());
		t.model = e;
		t.face = null;
		if (e instanceof AbstractClientPlayer acp) {
			Identifier skin = acp.getSkin().body().texturePath();
			t.face = faces.computeIfAbsent(skin, id -> new McImage(id, false));
			t.faceU0 = 8f / 64f;
			t.faceV0 = 8f / 64f;
			t.faceU1 = 16f / 64f;
			t.faceV1 = 16f / 64f;
			t.hatU0 = 40f / 64f;
			t.hatV0 = 8f / 64f;
			t.hatU1 = 48f / 64f;
			t.hatV1 = 16f / 64f;
		}
	}
}
