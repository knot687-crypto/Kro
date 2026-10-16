package dev.kro.ui.hud;

import dev.kro.ui.render.ImageRef;

/** A snapshot of the current opponent, filled by the game layer each frame. */
public final class TargetInfo {
	public String name = "";
	public boolean player;
	public float health;
	public float maxHealth = 20f;
	public float absorption;
	public int armor;
	public float distance;
	/** 0..1, 1 right after the target was hurt. */
	public float hurt;
	/** Face texture region (players), or null to use a 3D model / fallback. */
	public ImageRef face;
	public float faceU0;
	public float faceV0;
	public float faceU1;
	public float faceV1;
	public float hatU0;
	public float hatV0;
	public float hatU1;
	public float hatV1;
	/** Backend payload that draws a live model into the avatar box, or null. */
	public Object model;
	public float selfHealth;
	public float selfMax = 20f;
	/** Stable identity so animations reset when the target changes. */
	public int id;

	public TargetInfo copy() {
		TargetInfo t = new TargetInfo();
		t.name = name;
		t.player = player;
		t.health = health;
		t.maxHealth = maxHealth;
		t.absorption = absorption;
		t.armor = armor;
		t.distance = distance;
		t.hurt = hurt;
		t.face = face;
		t.faceU0 = faceU0;
		t.faceV0 = faceV0;
		t.faceU1 = faceU1;
		t.faceV1 = faceV1;
		t.hatU0 = hatU0;
		t.hatV0 = hatV0;
		t.hatU1 = hatU1;
		t.hatV1 = hatV1;
		t.model = model;
		t.selfHealth = selfHealth;
		t.selfMax = selfMax;
		t.id = id;
		return t;
	}
}
