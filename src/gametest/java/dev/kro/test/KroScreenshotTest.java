package dev.kro.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public class KroScreenshotTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.waitTicks(5);
		context.takeScreenshot("00-title");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("01-world");
		}
	}
}
