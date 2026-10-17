package dev.kro.core.module.impl;

import dev.kro.core.module.Category;
import dev.kro.core.module.Module;
import dev.kro.core.setting.BoolSetting;
import dev.kro.core.setting.KeySetting;
import dev.kro.core.setting.ModeSetting;
import dev.kro.core.util.Keys;
import dev.kro.ui.render.Icons;

public final class ShortWindCharge extends Module {
	public final ModeSetting activation = mode("Activation", "Use with mace: right click while holding a mace. Key: press the throw key below.", "Use with mace", "Use with mace", "Key");
	public final KeySetting throwKey = key("Throw key", "Throws a wind charge at your feet.", Keys.NONE);
	public final ModeSetting height = mode("Height", "Short throws at once for a quick low hop; High jumps first for the tallest launch.", "Short", "Short", "High");
	public final BoolSetting swapBack = bool("Swap back", "Return to the item you were holding.", true);

	public ShortWindCharge() {
		super("Short Wind Charge", "Throws a wind charge straight at your feet for an instant boost, then puts your aim and item back.", Category.MOVEMENT, Icons.WIND);
		throwKey.visibleWhen(() -> activation.is("Key"));
	}
}
