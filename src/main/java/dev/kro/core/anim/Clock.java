package dev.kro.core.anim;

/**
 * Shared time source for all UI animation. Animations advance lazily from their last sample, so
 * they stay frame-rate independent without an explicit update pass.
 */
public final class Clock {
	/** Global animation speed multiplier (user preference). */
	private static volatile float speed = 1f;
	private static final long START = System.nanoTime();
	/** Deterministic time for offline rendering; {@link Long#MIN_VALUE} means real time. */
	private static volatile long fixed = Long.MIN_VALUE;

	private Clock() {
	}

	public static long nanos() {
		long f = fixed;
		return f != Long.MIN_VALUE ? f : System.nanoTime();
	}

	/** Seconds since the client started, for looping ambient motion. */
	public static double seconds() {
		return (nanos() - START) / 1.0e9;
	}

	/** Pins the clock (used by the offline preview renderer). */
	public static void setFixed(long nanos) {
		fixed = nanos;
	}

	public static void advance(long nanos) {
		fixed = (fixed == Long.MIN_VALUE ? System.nanoTime() : fixed) + nanos;
	}

	public static float speed() {
		return speed;
	}

	public static void setSpeed(float s) {
		speed = Math.max(0.1f, s);
	}

	/** Elapsed seconds between two nanoTime samples, scaled by the global speed and clamped. */
	static float delta(long from, long to) {
		float dt = (to - from) / 1.0e9f;
		if (dt < 0f) {
			return 0f;
		}
		return Math.min(dt, 0.1f) * speed;
	}
}
