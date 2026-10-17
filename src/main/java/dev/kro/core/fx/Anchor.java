package dev.kro.core.fx;

/** Something an effect follows: a player or mob, seen through whatever the game layer knows. */
public interface Anchor {
	/** Still in the world and alive. */
	boolean present();

	/** Feet position, interpolated to the frame. */
	double x(float partialTick);

	double y(float partialTick);

	double z(float partialTick);

	double height();

	double width();

	/** 1 right after being hurt, fading to 0. */
	float hurt();
}
