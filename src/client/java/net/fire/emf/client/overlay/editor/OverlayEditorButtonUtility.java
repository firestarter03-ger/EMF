package net.fire.emf.client.overlay.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class OverlayEditorButtonUtility {
	private static final int BUTTON_WIDTH = 40;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_PADDING = 5;

	private static int buttonX = -1;
	private static int buttonY = -1;

	private OverlayEditorButtonUtility() {
	}

	public static void renderButton(GuiGraphicsExtractor context, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getWindow() == null) {
			return;
		}

		int screenHeight = client.getWindow().getGuiScaledHeight();
		buttonX = BUTTON_PADDING;
		buttonY = screenHeight - BUTTON_HEIGHT - BUTTON_PADDING;

		boolean hovered = mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH
				&& mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT;

		int backgroundColor = hovered ? 0xFF5A8A7A : 0xFF4B6A69;
		int borderColor = hovered ? 0xFF7AAAA9 : 0xFF6A8A89;
		context.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, backgroundColor);
		context.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + 1, borderColor);
		context.fill(buttonX, buttonY + BUTTON_HEIGHT - 1, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, borderColor);
		context.fill(buttonX, buttonY, buttonX + 1, buttonY + BUTTON_HEIGHT, borderColor);
		context.fill(buttonX + BUTTON_WIDTH - 1, buttonY, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, borderColor);

		Component buttonText = Component.literal(hotkeyText());
		int textX = buttonX + (BUTTON_WIDTH - client.font.width(buttonText)) / 2;
		int textY = buttonY + (BUTTON_HEIGHT - client.font.lineHeight) / 2;
		context.text(client.font, buttonText, textX, textY, 0xFFFFFFFF, true);

		if (hovered) {
			context.setComponentTooltipForNextFrame(client.font,
					List.of(Component.literal("Overlay Editor")), mouseX, mouseY);
		}
	}

	public static boolean handleButtonClick(double mouseX, double mouseY, int button) {
		if (button != 0 || buttonX < 0 || buttonY < 0) {
			return false;
		}
		if (mouseX >= buttonX && mouseX <= buttonX + BUTTON_WIDTH
				&& mouseY >= buttonY && mouseY <= buttonY + BUTTON_HEIGHT) {
			OverlayEditorUtility.openOverlayEditor();
			return true;
		}
		return false;
	}

	public static boolean shouldShowButton() {
		Minecraft client = Minecraft.getInstance();
		return client != null && !(client.screen instanceof OverlayEditorScreen);
	}

	private static String hotkeyText() {
		var keyBinding = OverlayEditorUtility.getOverlayEditorKeyMapping();
		if (keyBinding == null) {
			return "F6";
		}
		try {
			Component localized = keyBinding.getTranslatedKeyMessage();
			if (localized != null) {
				String text = localized.getString().replaceAll("§[0-9a-fk-or]", "");
				if (!text.isEmpty()) {
					return text;
				}
			}
		} catch (Exception ignored) {
		}
		return "F6";
	}
}
