package dev.kro;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Kro implements ClientModInitializer {
	public static final String MOD_ID = "kro";
	public static final Logger LOGGER = LoggerFactory.getLogger("Kro");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Kro initialised");
	}
}
