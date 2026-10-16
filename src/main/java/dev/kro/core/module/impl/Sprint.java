package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.ui.render.Icons;

public final class Sprint extends Module {
	public final BoolSetting indicator = bool("Show indicator", "Display a small sprint chip on your HUD.", false);

	public Sprint() {
		super("Sprint", "Holds sprint for you. Vanilla rules still apply: hunger, sneaking and blocking stop it.", Category.MOVEMENT, Icons.WIND);
	}
}
