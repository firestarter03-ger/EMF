package net.fire.emf.client.overlay;

import net.fire.emf.ElementsMoreFeatures;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.LevelOverlayTracker.OverlaySnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class LevelInfoOverlay {
	public static final int PADDING = 6;
	public static final int LINE_HEIGHT = 12;
	private static final int GREEN = 0xFF55FF55;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int DARK_GRAY = 0xFF555555;
	private static final int TITLE = 0xFFFF55FF;
	private static final int BACKGROUND = 0x80000000;
	private static final String TITLE_TEXT = "Erfahrung";
	private static final String LABEL_LEVEL = "Level: ";
	private static final String LABEL_TARGET = "Ziel: ";
	private static final String PREVIEW_LEVEL = "2/min";
	private static final String PREVIEW_TARGET = "8min [50]";

	private LevelInfoOverlay() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(LevelInfoOverlay::onClientTick);
		HudElementRegistry.addLast(ElementsMoreFeatures.id("level_tracker"), LevelInfoOverlay::render);
	}

	public static int getUnscaledWidth(Minecraft client) {
		return measureWidth(client == null ? null : client.font, previewLines());
	}

	public static int getUnscaledHeight() {
		return measureHeight(previewLines());
	}

	public static void renderPreview(GuiGraphicsExtractor context, Minecraft client) {
		List<OverlayLine> lines = previewLines();
		renderPanel(context, client.font, 1.0f, lines, measureWidth(client.font, lines), measureHeight(lines));
	}

	private static void onClientTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			LevelOverlayTracker.tick(null);
			return;
		}
		if (!EmfConfig.levelTrackerOverlayVisible()) {
			return;
		}
		LevelOverlayTracker.tick(client.player);
	}

	private static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		if (client.options.hideGui) {
			return;
		}
		if (!EmfConfig.levelTrackerOverlayVisible()) {
			return;
		}
		if (!LevelOverlayTracker.shouldRender()) {
			return;
		}

		OverlaySnapshot snapshot = LevelOverlayTracker.snapshot();
		if (snapshot == null) {
			return;
		}

		List<OverlayLine> lines = visibleLines(snapshot);
		Font font = client.font;
		float alpha = LevelOverlayTracker.fadeAlpha();
		EmfConfig config = EmfConfig.HANDLER.instance();
		float scale = config.levelTrackerOverlayScale <= 0.0f ? 1.0f : config.levelTrackerOverlayScale;

		int overlayWidth = measureWidth(font, lines);
		int overlayHeight = measureHeight(lines);
		int x = OverlayBounds.clampX(client, config.levelTrackerOverlayX, overlayWidth, scale);
		int y = OverlayBounds.clampY(client, config.levelTrackerOverlayY, overlayHeight, scale);

		var matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		renderPanel(context, font, alpha, lines, overlayWidth, overlayHeight);
		matrices.popMatrix();
	}

	private static List<OverlayLine> previewLines() {
		return visibleLines(new OverlaySnapshot(PREVIEW_LEVEL, PREVIEW_TARGET));
	}

	private static List<OverlayLine> visibleLines(OverlaySnapshot snapshot) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		List<OverlayLine> lines = new ArrayList<>();
		if (config.levelTrackerShowLevelRate && snapshot.levelRate() != null) {
			lines.add(new OverlayLine(LABEL_LEVEL, snapshot.levelRate(), "", false));
		}
		if (config.levelTrackerShowTarget && snapshot.target() != null) {
			String[] parts = OverlayFormat.splitTarget(snapshot.target());
			boolean reached = OverlayFormat.REACHED.equals(parts[0]);
			lines.add(new OverlayLine(LABEL_TARGET, parts[0], parts[1], reached));
		}
		return lines;
	}

	private static int measureWidth(Font font, List<OverlayLine> lines) {
		int width = font == null ? 80 : font.width(TITLE_TEXT);
		if (font != null) {
			for (OverlayLine line : lines) {
				width = Math.max(width, font.width(line.label() + line.value() + line.suffix()));
			}
		}
		return width + PADDING * 2;
	}

	private static int measureHeight(List<OverlayLine> lines) {
		return PADDING * 2 + LINE_HEIGHT * (1 + lines.size());
	}

	private static void renderPanel(GuiGraphicsExtractor context, Font font, float alpha,
			List<OverlayLine> lines, int overlayWidth, int overlayHeight) {
		int green = applyAlpha(GREEN, alpha);
		int white = applyAlpha(WHITE, alpha);
		int darkGray = applyAlpha(DARK_GRAY, alpha);
		int titleColor = applyAlpha(TITLE, alpha);
		if (EmfConfig.HANDLER.instance().levelTrackerShowBackground) {
			context.fill(0, 0, overlayWidth, overlayHeight, applyAlpha(BACKGROUND, alpha));
		}
		int textY = PADDING;
		context.text(font, TITLE_TEXT, PADDING, textY, titleColor, false);
		textY += LINE_HEIGHT;
		boolean blinkOn = EmfConfig.HANDLER.instance().levelTrackerReachedBlink;
		for (OverlayLine line : lines) {
			context.text(font, line.label(), PADDING, textY, green, true);
			int valueX = PADDING + font.width(line.label());
			boolean showValue = !line.reached() || !blinkOn || OverlayFormat.blinkVisible();
			if (showValue) {
				int valueColor = line.reached() ? green : white;
				context.text(font, line.value(), valueX, textY, valueColor, true);
			}
			if (line.suffix() != null && !line.suffix().isEmpty()) {
				context.text(font, line.suffix(), valueX + font.width(line.value()), textY, darkGray, true);
			}
			textY += LINE_HEIGHT;
		}
	}

	private static int applyAlpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	private record OverlayLine(String label, String value, String suffix, boolean reached) {
	}
}
