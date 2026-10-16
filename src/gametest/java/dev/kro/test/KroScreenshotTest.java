package dev.kro.test;

import dev.kro.Kro;
import dev.kro.mc.screen.HudEditorScreen;
import dev.kro.mc.screen.KroScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * Boots the game with Kro and drives it like a player: opens the menu with its key, browses a
 * few modules, opens the HUD editor, lands a hit on a zombie and turns on a world theme, taking
 * a screenshot of each for visual review.
 */
public class KroScreenshotTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.waitTicks(5);
		context.takeScreenshot("00-title");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			context.waitTicks(20);
			context.takeScreenshot("01-world");

			openMenu(context);
			context.takeScreenshot("02-menu");

			clickCard(context, 0);
			context.waitTicks(30);
			context.takeScreenshot("03-aim-assist");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(20);

			clickCard(context, 7);
			context.waitTicks(30);
			context.takeScreenshot("04-interface");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(20);
			closeMenu(context);

			context.runOnClient(mc -> Kro.get().modules.ui.background.set("Blur"));
			openMenu(context);
			context.takeScreenshot("05-menu-blur");
			closeMenu(context);
			context.runOnClient(mc -> Kro.get().modules.ui.background.set("Picture"));

			context.setScreen(HudEditorScreen::new);
			context.waitTicks(30);
			context.takeScreenshot("06-hud-editor");
			context.setScreen(() -> null);
			context.waitTicks(10);

			context.runOnClient(mc -> {
				Kro.get().modules.targetHud.setEnabled(true);
				Kro.get().modules.hitSparks.setEnabled(true);
			});
			world.getServer().runCommand("execute as @p at @p run summon zombie ^ ^ ^2.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
			context.waitTicks(5);
			world.getServer().runCommand("execute as @p at @p run tp @s ~ ~ ~ facing entity @e[type=zombie,limit=1,sort=nearest] eyes");
			context.waitTicks(10);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(3);
			context.takeScreenshot("07-fight");
			context.waitTicks(30);
			context.takeScreenshot("08-target-hud");

			context.runOnClient(mc -> {
				Kro.get().modules.worldTheme.preset.set("Sakura");
				Kro.get().modules.worldTheme.setEnabled(true);
			});
			context.waitTicks(60);
			context.takeScreenshot("09-world-theme");
		}
	}

	private static void openMenu(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(KroScreen.class);
		context.waitTicks(40);
	}

	private static void closeMenu(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		context.waitForScreen(null);
		context.waitTicks(10);
	}

	/** Moves the cursor onto a module card the way a player would, then clicks it. */
	private static void clickCard(ClientGameTestContext context, int index) {
		double[] at = context.computeOnClient(mc -> {
			float[] c = ((KroScreen) mc.screen).cardCentre(index);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {c[0] * scale, (c[1] + 20f) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(10);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}
}
