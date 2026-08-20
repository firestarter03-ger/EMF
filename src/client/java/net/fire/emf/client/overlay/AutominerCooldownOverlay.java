package net.fire.emf.client.overlay;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.function.OffhandSwapper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class AutominerCooldownOverlay {
	public static final int PADDING = 6;
	public static final int LINE_HEIGHT = 12;
	private static final int GOLD = 0xFFFFAA00;
	private static final int DARK_AQUA = 0xFF00AAAA;
	private static final int AQUA = 0xFF55FFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int BACKGROUND = 0x80000000;
	private static final String TITLE_TEXT = "Autominer";
	private static final Component TITLE = Component.literal(TITLE_TEXT).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	private static final String LABEL_COOLDOWN = "Cooldown: ";
	private static final String PREVIEW_STATUS = "12sek";

	private AutominerCooldownOverlay() {
	}

	public static void register() {
		HudElementRegistry.addLast(ElementsMoreFeatures.id("autominer_cooldown"), AutominerCooldownOverlay::render);
	}

	public static int getUnscaledWidth(Minecraft client) {
		return measureWidth(client == null ? null : client.font, PREVIEW_STATUS);
	}

	public static int getUnscaledHeight() {
		return PADDING * 2 + LINE_HEIGHT * 2;
	}

	public static void renderPreview(GuiGraphicsExtractor context, Minecraft client) {
		renderPanel(context, client.font, PREVIEW_STATUS, AQUA,
				measureWidth(client.font, PREVIEW_STATUS), getUnscaledHeight());
	}

	private static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null || client.options.hideGui) {
			return;
		}
		if (!EmfConfig.autominerCooldownOverlayVisible()) {
			return;
		}
		if (!shouldRender()) {
			return;
		}

		String status = OffhandSwapper.statusText();
		int valueColor = valueColor(status);
		Font font = client.font;
		EmfConfig config = EmfConfig.HANDLER.instance();
		float scale = config.autominerCooldownOverlayScale <= 0.0f ? 1.0f : config.autominerCooldownOverlayScale;
		int overlayWidth = measureWidth(font, status);
		int overlayHeight = getUnscaledHeight();
		int x = OverlayBounds.clampX(client, config.autominerCooldownOverlayX, overlayWidth, scale);
		int y = OverlayBounds.clampY(client, config.autominerCooldownOverlayY, overlayHeight, scale);

		var matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		renderPanel(context, font, status, valueColor, overlayWidth, overlayHeight);
		matrices.popMatrix();
	}

	private static boolean shouldRender() {
		if (!OffhandSwapper.isDetectionEnabled()) {
			return false;
		}
		return OffhandSwapper.isCooldownActive()
				|| (!EmfConfig.HANDLER.instance().autominerCooldownShowTitle && OffhandSwapper.isExpireFlashActive());
	}

	private static int valueColor(String status) {
		if ("Bereit".equals(status)) {
			return GREEN;
		}
		if ("Aus".equals(status) || "Kein Autominer".equals(status)) {
			return GRAY;
		}
		return AQUA;
	}

	private static int measureWidth(Font font, String status) {
		int width = 80;
		if (font != null) {
			width = Math.max(width, font.width(TITLE));
			width = Math.max(width, font.width(LABEL_COOLDOWN + status));
		}
		return width + PADDING * 2;
	}

	private static void renderPanel(GuiGraphicsExtractor context, Font font, String status, int valueColor,
			int overlayWidth, int overlayHeight) {
		if (EmfConfig.HANDLER.instance().autominerCooldownShowBackground) {
			context.fill(0, 0, overlayWidth, overlayHeight, BACKGROUND);
		}
		int textY = PADDING;
		int titleX = Math.max(PADDING, (overlayWidth - font.width(TITLE)) / 2);
		context.text(font, TITLE, titleX, textY, GOLD, false);
		textY += LINE_HEIGHT;
		context.text(font, LABEL_COOLDOWN, PADDING, textY, DARK_AQUA, true);
		context.text(font, status, PADDING + font.width(LABEL_COOLDOWN), textY, valueColor, true);
	}
}
