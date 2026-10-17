package dev.kro.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's click timers: the wait between held right-click uses, and the lockout after a missed swing. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("rightClickDelay")
	int kro$rightClickDelay();

	@Accessor("rightClickDelay")
	void kro$setRightClickDelay(int ticks);

	@Accessor("missTime")
	void kro$setMissTime(int ticks);
}
