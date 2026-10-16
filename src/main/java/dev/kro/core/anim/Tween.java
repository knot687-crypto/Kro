package dev.kro.core.anim;

/** A one-shot, time-based progress value (0..1) that can be restarted. */
public final class Tween {
	private final float duration;
	private long start;
	private boolean running;

	public Tween(float durationSeconds) {
		this.duration = durationSeconds;
		this.start = Long.MIN_VALUE;
	}

	public Tween restart() {
		start = Clock.nanos();
		running = true;
		return this;
	}

	/** Linear progress 0..1; 1 once finished or if never started. */
	public float progress() {
		if (!running) {
			return 1f;
		}
		float t = (Clock.nanos() - start) / 1.0e9f * Clock.speed() / duration;
		if (t >= 1f) {
			running = false;
			return 1f;
		}
		return t;
	}

	public boolean running() {
		progress();
		return running;
	}
}
