package net.fire.emf.client.overlay;

import net.minecraft.client.Minecraft;

public final class OverlayBounds {
	private OverlayBounds() {
	}

	public static int clampX(Minecraft client, int x, int unscaledWidth, float scale) {
		return clampAxis(x, scaledSize(unscaledWidth, scale), screenWidth(client));
	}

	public static int clampY(Minecraft client, int y, int unscaledHeight, float scale) {
		return clampAxis(y, scaledSize(unscaledHeight, scale), screenHeight(client));
	}

	private static int scaledSize(int unscaled, float scale) {
		return Math.round(unscaled * scale);
	}

	private static int screenWidth(Minecraft client) {
		if (client == null || client.getWindow() == null) {
			return Integer.MAX_VALUE;
		}
		return client.getWindow().getGuiScaledWidth();
	}

	private static int screenHeight(Minecraft client) {
		if (client == null || client.getWindow() == null) {
			return Integer.MAX_VALUE;
		}
		return client.getWindow().getGuiScaledHeight();
	}

	private static int clampAxis(int value, int size, int screenSize) {
		return Math.max(0, Math.min(value, Math.max(0, screenSize - size)));
	}
}
