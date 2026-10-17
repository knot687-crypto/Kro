package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.ui.render.Icons;

public final class NoClickDelay extends Module {
	public NoClickDelay() {
		super("No Click Delay", "A swing that misses no longer locks out your next click for half a second.", Category.COMBAT, Icons.MOUSE_POINTER_CLICK);
	}
}
