package net.fire.emf.client.overlay.editor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public interface DraggableOverlay {
	String getOverlayName();

	int getX();

	int getY();

	int getWidth();

	int getHeight();

	void setPosition(int x, int y);

	default void setSize(int width, int height) {
	}

	void renderInEditMode(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta);

	default boolean isHovered(int mouseX, int mouseY) {
		return mouseX >= getX() && mouseX <= getX() + getWidth()
				&& mouseY >= getY() && mouseY <= getY() + getHeight();
	}

	default boolean isResizeArea(int mouseX, int mouseY) {
		int resizeSize = 8;
		return mouseX >= getX() + getWidth() - resizeSize
				&& mouseX <= getX() + getWidth()
				&& mouseY >= getY() + getHeight() - resizeSize
				&& mouseY <= getY() + getHeight();
	}

	default boolean isResetArea(int mouseX, int mouseY) {
		int resetSize = 10;
		return mouseX >= getX() + getWidth() - resetSize
				&& mouseX <= getX() + getWidth()
				&& mouseY >= getY()
				&& mouseY <= getY() + resetSize;
	}

	void savePosition();

	boolean isEnabled();

	default boolean isConfigEnabled() {
		return isEnabled();
	}

	default Component getTooltip() {
		return Component.literal(getOverlayName());
	}

	void resetToDefault();

	default void resetSizeToDefault() {
	}
}
