package net.fire.emf.client.overlay.editor;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class OverlayEditorUtility {
	private static boolean initialized;
	private static boolean overlayEditorOpen;
	private static KeyMapping overlayEditorKeyMapping;
	private static long lastToggleMs;

	private OverlayEditorUtility() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		overlayEditorKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.emf.overlay_editor",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				KeyCategories.of("emf", "overlay")
		));
		ClientTickEvents.END_CLIENT_TICK.register(OverlayEditorUtility::onClientTick);
		initialized = true;
	}

	private static void onClientTick(Minecraft client) {
		if (client.player == null || client.getWindow() == null || client.screen != null) {
			return;
		}
		if (overlayEditorKeyMapping != null && overlayEditorKeyMapping.consumeClick()) {
			toggleOverlayEditor();
		}
	}

	public static void toggleOverlayEditor() {
		if (overlayEditorOpen) {
			closeOverlayEditor();
		} else {
			openOverlayEditor();
		}
	}

	public static void openOverlayEditor() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		client.setScreen(new OverlayEditorScreen());
		overlayEditorOpen = true;
	}

	public static void closeOverlayEditor() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.screen instanceof OverlayEditorScreen) {
			client.screen.onClose();
		}
		overlayEditorOpen = false;
	}

	public static void setOverlayEditorOpen(boolean open) {
		overlayEditorOpen = open;
	}

	public static boolean isOverlayEditorOpen() {
		return overlayEditorOpen;
	}

	public static KeyMapping getOverlayEditorKeyMapping() {
		return overlayEditorKeyMapping;
	}

	public static boolean handleKeyPress(int keyCode) {
		if (overlayEditorKeyMapping == null) {
			return false;
		}
		if (!overlayEditorKeyMapping.matches(new KeyEvent(keyCode, -1, 0))) {
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - lastToggleMs < 150L) {
			return true;
		}
		lastToggleMs = now;
		toggleOverlayEditor();
		return true;
	}
}
