package dev.kro.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kro.core.util.MathUtil;

import java.util.Locale;

public final class NumberSetting extends Setting<Double> {
	private final double defaultValue;
	private final double min;
	private final double max;
	private final double step;
	private final String unit;
	private double value;

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String unit) {
		super(name, description);
		this.defaultValue = defaultValue;
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
		this.value = defaultValue;
	}

	@Override
	public Double get() {
		return value;
	}

	public double value() {
		return value;
	}

	public float floatValue() {
		return (float) value;
	}

	public int intValue() {
		return (int) Math.round(value);
	}

	@Override
	public void set(Double v) {
		value = MathUtil.clamp(MathUtil.snap(v, step), min, max);
	}

	/** Sets from a 0..1 slider fraction. */
	public void setFraction(double f) {
		set(min + (max - min) * MathUtil.clamp(f, 0.0, 1.0));
	}

	public double fraction() {
		return max == min ? 0.0 : (value - min) / (max - min);
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public String unit() {
		return unit;
	}

	@Override
	public void reset() {
		value = defaultValue;
	}

	/** Human readable value, decimals derived from the step size. */
	public String format() {
		return format(value);
	}

	public String format(double v) {
		int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
		String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
		return unit.isEmpty() ? s : s + unit;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
