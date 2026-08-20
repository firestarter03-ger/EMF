package net.fire.emf.client.resource;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fire.emf.client.mixin.KeyMappingAccessor;
import net.fire.emf.client.mixin.MinecraftCombatAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.lwjgl.glfw.GLFW;

public final class CollectionScannerMovement {
	private static boolean passthroughActive;
	private static boolean wasAttackDown;
	private static boolean wasUseDown;

	private CollectionScannerMovement() {
	}

	public static void refreshMovementInput(ClientInput input) {
		if (!(input instanceof KeyboardInput)) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getWindow() == null || client.options == null) {
			return;
		}
		if (input != client.player.input) {
			return;
		}

		syncMovementKeys(client);
		input.tick();
	}

	public static void tickCombatPassthrough(Minecraft client) {
		if (client == null || client.player == null || client.options == null || client.getWindow() == null) {
			passthroughActive = false;
			wasAttackDown = false;
			wasUseDown = false;
			return;
		}

		boolean allow = CollectionScanner.shouldAllowMovement();
		if (!allow) {
			if (passthroughActive) {
				syncMouseKeys(client);
			}
			passthroughActive = false;
			wasAttackDown = client.options.keyAttack.isDown();
			wasUseDown = client.options.keyUse.isDown();
			return;
		}

		passthroughActive = true;
		syncMouseKeys(client);

		boolean attackDown = isAttackDown(client);
		boolean useDown = isUseDown(client);
		MinecraftCombatAccessor combat = (MinecraftCombatAccessor) client;

		if (attackDown && !wasAttackDown) {
			combat.emf$startAttack();
		}
		combat.emf$continueAttack(attackDown);

		if (useDown && !wasUseDown) {
			combat.emf$startUseItem();
		} else if (!useDown && wasUseDown && client.player.isUsingItem() && client.gameMode != null) {
			client.gameMode.releaseUsingItem(client.player);
		}

		wasAttackDown = attackDown;
		wasUseDown = useDown;
	}

	private static boolean isAttackDown(Minecraft client) {
		return client.options.keyAttack.isDown() || isMouseButtonDown(client, InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static boolean isUseDown(Minecraft client) {
		return client.options.keyUse.isDown() || isMouseButtonDown(client, InputConstants.MOUSE_BUTTON_RIGHT);
	}

	private static boolean isMouseButtonDown(Minecraft client, int button) {
		Window window = client.getWindow();
		if (window == null) {
			return false;
		}
		return GLFW.glfwGetMouseButton(window.handle(), button) == GLFW.GLFW_PRESS;
	}

	private static void syncMovementKeys(Minecraft client) {
		Window window = client.getWindow();
		Options options = client.options;
		syncKey(window, options.keyUp);
		syncKey(window, options.keyDown);
		syncKey(window, options.keyLeft);
		syncKey(window, options.keyRight);
		syncKey(window, options.keyJump);
		syncKey(window, options.keyShift);
		syncKey(window, options.keySprint);
	}

	private static void syncMouseKeys(Minecraft client) {
		Window window = client.getWindow();
		Options options = client.options;
		syncKey(window, options.keyAttack);
		syncKey(window, options.keyUse);
		syncKey(window, options.keyPickItem);
	}

	private static void syncKey(Window window, KeyMapping mapping) {
		if (mapping.isUnbound()) {
			return;
		}
		InputConstants.Key key = ((KeyMappingAccessor) mapping).emf$getKey();
		int value = key.getValue();
		if (value == InputConstants.UNKNOWN.getValue()) {
			return;
		}

		boolean down;
		if (key.getType() == InputConstants.Type.MOUSE) {
			down = GLFW.glfwGetMouseButton(window.handle(), value) == GLFW.GLFW_PRESS;
		} else if (key.getType() == InputConstants.Type.KEYSYM) {
			down = InputConstants.isKeyDown(window, value);
		} else {
			return;
		}
		mapping.setDown(down);
	}
}
