package dev.kro.core.anim;

/**
 * Damped harmonic spring. Gives UI motion a physical feel: quick response, gentle settle and an
 * optional touch of overshoot. Integrated with fixed sub-steps for stability at low frame rates.
 */
public final class Spring {
	/** Critically damped: no overshoot. */
	public static final float[] SMOOTH = {170f, 26f};
	/** Fast with a hint of overshoot. */
	public static final float[] SNAPPY = {380f, 30f};
	/** Visible bounce, for playful accents. */
	public static final float[] BOUNCY = {420f, 19f};
	/** Slow and soft, for large surfaces. */
	public static final float[] GENTLE = {90f, 19f};

	private static final float STEP = 1f / 240f;

	private float value;
	private float velocity;
	private float target;
	private final float stiffness;
	private final float damping;
	private long last;

	public Spring(float initial, float stiffness, float damping) {
		this.value = initial;
		this.target = initial;
		this.stiffness = stiffness;
		this.damping = damping;
		this.last = Clock.nanos();
	}

	public Spring(float initial, float[] preset) {
		this(initial, preset[0], preset[1]);
	}

	public Spring to(float target) {
		this.target = target;
		return this;
	}

	public Spring to(boolean on) {
		return to(on ? 1f : 0f);
	}

	/** Adds an instantaneous velocity kick, e.g. for a click "pop". */
	public Spring kick(float v) {
		this.velocity += v;
		return this;
	}

	public float get() {
		long now = Clock.nanos();
		float dt = Clock.delta(last, now);
		last = now;
		if (value == target && velocity == 0f) {
			return value;
		}
		while (dt > 0f) {
			float h = Math.min(STEP, dt);
			float a = -stiffness * (value - target) - damping * velocity;
			velocity += a * h;
			value += velocity * h;
			dt -= h;
		}
		if (Math.abs(value - target) < 1.0e-4f && Math.abs(velocity) < 1.0e-3f) {
			value = target;
			velocity = 0f;
		}
		return value;
	}

	public float target() {
		return target;
	}

	public void snap(float v) {
		value = v;
		target = v;
		velocity = 0f;
		last = Clock.nanos();
	}
}
