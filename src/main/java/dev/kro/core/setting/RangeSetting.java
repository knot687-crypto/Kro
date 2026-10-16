package dev.kro.core.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.kro.core.util.MathUtil;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** A min/max pair, e.g. a reaction-time window. Random samples fall inside the range. */
public final class RangeSetting extends Setting<double[]> {
	private final double defaultLow;
	private final double defaultHigh;
	private final double min;
	private final double max;
	private final double step;
	private final String unit;
	private double low;
	private double high;

	public RangeSetting(String name, String description, double low, double high, double min, double max, double step, String unit) {
		super(name, description);
		this.defaultLow = low;
		this.defaultHigh = high;
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
		this.low = low;
		this.high = high;
	}

	@Override
	public double[] get() {
		return new double[] {low, high};
	}

	@Override
	public void set(double[] v) {
		setLow(v[0]);
		setHigh(v[1]);
	}

	public double low() {
		return low;
	}

	public double high() {
		return high;
	}

	public void setLow(double v) {
		low = MathUtil.clamp(MathUtil.snap(v, step), min, max);
		if (low > high) {
			high = low;
		}
	}

	public void setHigh(double v) {
		high = MathUtil.clamp(MathUtil.snap(v, step), min, max);
		if (high < low) {
			low = high;
		}
	}

	public double fractionOf(double v) {
		return max == min ? 0.0 : (v - min) / (max - min);
	}

	public double valueAt(double fraction) {
		return min + (max - min) * MathUtil.clamp(fraction, 0.0, 1.0);
	}

	/** Uniform random value inside the configured window. */
	public double random() {
		if (high <= low) {
			return low;
		}
		return ThreadLocalRandom.current().nextDouble(low, high);
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public String unit() {
		return unit;
	}

	@Override
	public void reset() {
		low = defaultLow;
		high = defaultHigh;
	}

	public String format() {
		int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
		String f = "%." + decimals + "f";
		return String.format(Locale.ROOT, f, low) + " – " + String.format(Locale.ROOT, f, high) + unit;
	}

	@Override
	public JsonElement toJson() {
		JsonArray a = new JsonArray();
		a.add(low);
		a.add(high);
		return a;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonArray() && json.getAsJsonArray().size() == 2) {
			double l = json.getAsJsonArray().get(0).getAsDouble();
			double h = json.getAsJsonArray().get(1).getAsDouble();
			high = MathUtil.clamp(h, min, max);
			low = MathUtil.clamp(Math.min(l, high), min, max);
		}
	}
}
