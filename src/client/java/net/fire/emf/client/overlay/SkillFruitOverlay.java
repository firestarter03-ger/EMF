package net.fire.emf.client.overlay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.SkillFruitTracker.FruitLine;
import net.fire.emf.client.overlay.SkillFruitTracker.OverlaySnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SkillFruitOverlay {
	public static final int PADDING = 6;
	public static final int LINE_HEIGHT = 12;
	private static final int TITLE = 0xFF5555FF;
	private static final int LABEL = 0xFF00AAAA;
	private static final int VALUE = 0xFF55FFFF;
	private static final int BACKGROUND = 0x80000000;
	private static final String LABEL_DURATION = "Dauer: ";

	private SkillFruitOverlay() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SkillFruitOverlay::onClientTick);
		HudElementRegistry.addLast(ElementsMoreFeatures.id("skill_fruit_timer"), SkillFruitOverlay::render);
	}

	public static int getUnscaledWidth(Minecraft client) {
		return measureWidth(client == null ? null : client.font, SkillFruitTracker.previewSnapshot());
	}

	public static int getUnscaledHeight() {
		return measureHeight(SkillFruitTracker.previewSnapshot());
	}

	public static void renderPreview(GuiGraphicsExtractor context, Minecraft client) {
		OverlaySnapshot snapshot = SkillFruitTracker.previewSnapshot();
		renderPanel(context, client.font, snapshot, measureWidth(client.font, snapshot), measureHeight(snapshot));
	}

	private static void onClientTick(Minecraft client) {
		SkillFruitTracker.tick(client);
	}

	private static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null || client.options.hideGui) {
			return;
		}
		if (!SkillFruitTracker.shouldRender()) {
			return;
		}
		OverlaySnapshot snapshot = SkillFruitTracker.snapshot();
		if (snapshot == null) {
			return;
		}

		Font font = client.font;
		EmfConfig config = EmfConfig.HANDLER.instance();
		float scale = config.skillFruitOverlayScale <= 0.0f ? 1.0f : config.skillFruitOverlayScale;
		int overlayWidth = measureWidth(font, snapshot);
		int overlayHeight = measureHeight(snapshot);
		int x = OverlayBounds.clampX(client, config.skillFruitOverlayX, overlayWidth, scale);
		int y = OverlayBounds.clampY(client, config.skillFruitOverlayY, overlayHeight, scale);

		var matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		renderPanel(context, font, snapshot, overlayWidth, overlayHeight);
		matrices.popMatrix();
	}

	private static int measureWidth(Font font, OverlaySnapshot snapshot) {
		int width = 80;
		if (font != null && snapshot != null) {
			for (FruitLine line : snapshot.lines()) {
				width = Math.max(width, font.width(line.title()));
				width = Math.max(width, font.width(LABEL_DURATION + line.duration()));
			}
		}
		return width + PADDING * 2;
	}

	private static int measureHeight(OverlaySnapshot snapshot) {
		int fruits = snapshot == null || snapshot.lines().isEmpty() ? 1 : snapshot.lines().size();
		return PADDING * 2 + LINE_HEIGHT * fruits * 2;
	}

	private static void renderPanel(GuiGraphicsExtractor context, Font font, OverlaySnapshot snapshot,
			int overlayWidth, int overlayHeight) {
		if (EmfConfig.HANDLER.instance().skillFruitShowBackground) {
			context.fill(0, 0, overlayWidth, overlayHeight, BACKGROUND);
		}
		int textY = PADDING;
		for (FruitLine line : snapshot.lines()) {
			context.text(font, line.title(), PADDING, textY, TITLE, false);
			textY += LINE_HEIGHT;
			context.text(font, LABEL_DURATION, PADDING, textY, LABEL, true);
			context.text(font, line.duration(), PADDING + font.width(LABEL_DURATION), textY, VALUE, true);
			textY += LINE_HEIGHT;
		}
	}
}
