package dev.kro.core.anim;

/**
 * Exponential approach animation: every sample moves the value towards its target by
 * {@code 1 - e^(-rate * dt)}. Smooth, frame-rate independent and interruptible.
 */
public final class Anim {
	private float value;
	private float target;
	private final float rate;
	private long last;

	public Anim(float initial, float rate) {
		this.value = initial;
		this.target = initial;
		this.rate = rate;
		this.last = Clock.nanos();
	}

	/** A medium-speed animation (about 95% of the way in a quarter second). */
	public Anim(float initial) {
		this(initial, 12f);
	}

	public Anim to(float target) {
		this.target = target;
		return this;
	}

	public Anim to(boolean on) {
		return to(on ? 1f : 0f);
	}

	public float get() {
		long now = Clock.nanos();
		float dt = Clock.delta(last, now);
		last = now;
		if (value != target) {
			float k = 1f - (float) Math.exp(-rate * dt);
			value += (target - value) * k;
			if (Math.abs(target - value) < 1.0e-4f) {
				value = target;
			}
		}
		return value;
	}

	public float target() {
		return target;
	}

	public void snap(float v) {
		value = v;
		target = v;
		last = Clock.nanos();
	}

	public boolean settled() {
		return value == target;
	}
}
