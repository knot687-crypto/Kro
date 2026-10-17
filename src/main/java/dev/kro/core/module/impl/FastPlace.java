package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.RangeSetting;
import dev.kro.ui.render.Icons;

public final class FastPlace extends Module {
	public final RangeSetting delay = range("Delay", "Ticks between uses while right click is held, picked fresh each time. Vanilla waits 4.", 1, 2, 1, 4, 1, "t");
	public final BoolSetting blocks = bool("Blocks", "Place blocks faster.", true);
	public final BoolSetting crystals = bool("Crystals", "Place end crystals faster.", true);
	public final BoolSetting other = bool("Other items", "Also speed up other items held to use, like experience bottles and snowballs.", false);

	public FastPlace() {
		super("Fast Place", "Holding right click places blocks and crystals faster than vanilla's four-tick wait.", Category.CRYSTAL, Icons.LAYERS);
	}
}
