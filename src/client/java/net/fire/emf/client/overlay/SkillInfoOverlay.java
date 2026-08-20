package net.fire.emf.client.overlay;

import net.fire.emf.ElementsMoreFeatures;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.mixin.GuiAccessor;
import net.fire.emf.client.overlay.SkillOverlayTracker.OverlaySnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SkillInfoOverlay {
	public static final int PADDING = 6;
	public static final int LINE_HEIGHT = 12;
	private static final int GOLD = 0xFFFFAA00;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int DARK_AQUA = 0xFF00AAAA;
	private static final int DARK_GRAY = 0xFF555555;
	private static final int GREEN = 0xFF55FF55;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int TITLE = 0xFFFF55FF;
	private static final int BACKGROUND = 0x80000000;
	private static final String TITLE_SUFFIX_PREFIX = " (";
	private static final String TITLE_SUFFIX_SUFFIX = ")";
	private static final int AQUA = 0xFF55FFFF;
	private static final String LABEL_SKILL_XP = "Skill XP: ";
	private static final String LABEL_NEXT_LEVEL = "Next Level: ";
	private static final String LABEL_RESOURCES = "Res/Kills: ";
	private static final String LABEL_RESOURCE_TARGET = "Res/Kills Ziel: ";
	private static final String LABEL_COLLECTION = "Collection: ";
	private static final String PREVIEW_SKILL = "Farming";
	private static final String PREVIEW_SKILL_XP = "1234/min (74.0K/h)";
	private static final String PREVIEW_NEXT_LEVEL = "12min";
	private static final String PREVIEW_RESOURCES = "1500/min (90.0K/h)";
	private static final String PREVIEW_RESOURCE_TARGET = "8min [10.0K]";
	private static final String PREVIEW_COLLECTION = "150.3K/250.0K";

	private SkillInfoOverlay() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SkillInfoOverlay::onClientTick);
		HudElementRegistry.addLast(ElementsMoreFeatures.id("skill_info"), SkillInfoOverlay::render);
	}

	public static int getUnscaledWidth(Minecraft client) {
		return measureWidth(client == null ? null : client.font, previewTitle(), titleSuffix("Weizen"), previewLines());
	}

	public static int getUnscaledHeight() {
		return measureHeight(previewTitle(), previewLines());
	}

	public static int clampX(Minecraft client, int x, int unscaledWidth, float scale) {
		return OverlayBounds.clampX(client, x, unscaledWidth, scale);
	}

	public static int clampY(Minecraft client, int y, int unscaledHeight, float scale) {
		return OverlayBounds.clampY(client, y, unscaledHeight, scale);
	}

	public static void renderPreview(GuiGraphicsExtractor context, Minecraft client) {
		String title = previewTitle();
		String titleSuffix = titleSuffix("Weizen");
		List<OverlayLine> lines = previewLines();
		renderPanel(context, client.font, 1.0f, title, titleSuffix, lines,
				measureWidth(client.font, title, titleSuffix, lines), measureHeight(title, lines));
	}

	private static void onClientTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gui == null) {
			SkillOverlayTracker.forgetPlayer();
			return;
		}

		GuiAccessor gui = (GuiAccessor) client.gui;
		Component overlay = gui.emf$getOverlayMessage();
		boolean visible = overlay != null && gui.emf$getOverlayMessageTime() > 0;
		SkillOverlayTracker.tick(visible ? overlay.getString() : null, visible);
	}

	private static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			SkillXpDebug.overlay("übersprungen (kein Client/Spieler)");
			return;
		}
		if (client.options.hideGui) {
			SkillXpDebug.overlay("übersprungen (HUD ausgeblendet / F1)");
			return;
		}
		if (!EmfConfig.skillXpOverlayVisible()) {
			SkillXpDebug.overlay("übersprungen (Overlay in Config deaktiviert)");
			return;
		}
		if (!SkillOverlayTracker.shouldRender()) {
			SkillXpDebug.overlay("übersprungen (kein gültiger Snapshot oder Alpha=0)");
			return;
		}

		OverlaySnapshot snapshot = SkillOverlayTracker.snapshot();
		if (snapshot == null) {
			SkillXpDebug.overlay("übersprungen (Snapshot null)");
			return;
		}

		String title = snapshot.skill();
		String titleSuffix = titleSuffix(snapshot.resourceName());
		List<OverlayLine> lines = visibleLines(snapshot);
		if ((title == null || title.isBlank()) && titleSuffix.isBlank() && lines.isEmpty()) {
			SkillXpDebug.overlay("übersprungen (keine Overlay-Zeilen aktiv)");
			return;
		}

		Font font = client.font;
		float alpha = SkillOverlayTracker.fadeAlpha();
		EmfConfig config = EmfConfig.HANDLER.instance();
		float scale = config.skillXpOverlayScale <= 0.0f ? 1.0f : config.skillXpOverlayScale;

		int overlayWidth = measureWidth(font, title, titleSuffix, lines);
		int overlayHeight = measureHeight(title, lines);
		int x = clampX(client, config.skillXpOverlayX, overlayWidth, scale);
		int y = clampY(client, config.skillXpOverlayY, overlayHeight, scale);

		var matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		renderPanel(context, font, alpha, title, titleSuffix, lines, overlayWidth, overlayHeight);
		matrices.popMatrix();

		SkillXpDebug.overlay("gerendert x=" + x + " y=" + y
				+ " " + overlayWidth + "x" + overlayHeight
				+ " scale=" + String.format(java.util.Locale.ROOT, "%.2f", scale)
				+ " alpha=" + String.format(java.util.Locale.ROOT, "%.2f", alpha)
				+ " | " + title + " " + lines);
	}

	private static List<OverlayLine> previewLines() {
		return visibleLines(new OverlaySnapshot(PREVIEW_SKILL, "Weizen", PREVIEW_SKILL_XP, PREVIEW_NEXT_LEVEL, PREVIEW_RESOURCES, PREVIEW_RESOURCE_TARGET, PREVIEW_COLLECTION, false));
	}

	private static String previewTitle() {
		return PREVIEW_SKILL;
	}

	private static List<OverlayLine> visibleLines(OverlaySnapshot snapshot) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		List<OverlayLine> lines = new ArrayList<>();
		if (snapshot.collection() != null) {
			if (snapshot.collectionMax()) {
				lines.add(new OverlayLine(LABEL_COLLECTION, snapshot.collection(), AQUA, GREEN, "", false));
			} else {
				lines.add(new OverlayLine(LABEL_COLLECTION, snapshot.collection(), AQUA, YELLOW, "", false));
			}
		}
		if (config.skillXpShowXpRate && snapshot.skillXp() != null) {
			lines.add(new OverlayLine(LABEL_SKILL_XP, snapshot.skillXp(), DARK_AQUA, WHITE, "", false));
		}
		if (config.skillXpShowNextLevel && snapshot.nextLevel() != null) {
			lines.add(new OverlayLine(LABEL_NEXT_LEVEL, snapshot.nextLevel(), GOLD, WHITE, "", false));
		}
		if (config.skillXpShowResources && snapshot.resources() != null) {
			lines.add(new OverlayLine(LABEL_RESOURCES, snapshot.resources(), DARK_AQUA, WHITE, "", false));
		}
		if (config.skillXpShowResourceTarget && snapshot.resourceTarget() != null) {
			String[] parts = OverlayFormat.splitTarget(snapshot.resourceTarget());
			boolean reached = OverlayFormat.REACHED.equals(parts[0]);
			lines.add(new OverlayLine(LABEL_RESOURCE_TARGET, parts[0], GOLD, reached ? GREEN : WHITE, parts[1], reached));
		}
		return lines;
	}

	private static int measureWidth(Font font, String title, String titleSuffix, List<OverlayLine> lines) {
		int width = 80;
		if (font != null) {
			if ((title != null && !title.isBlank()) || (titleSuffix != null && !titleSuffix.isBlank())) {
				width = Math.max(width, font.width((title == null ? "" : title) + (titleSuffix == null ? "" : titleSuffix)));
			}
			for (OverlayLine line : lines) {
				width = Math.max(width, font.width(line.label() + line.value() + line.suffix()));
			}
		}
		return width + PADDING * 2;
	}

	private static int measureHeight(String title, List<OverlayLine> lines) {
		int count = lines.size();
		if (title != null && !title.isBlank()) {
			count++;
		}
		return PADDING * 2 + LINE_HEIGHT * Math.max(1, count);
	}

	private static void renderPanel(GuiGraphicsExtractor context, Font font, float alpha,
			String title, String titleSuffix, List<OverlayLine> lines, int overlayWidth, int overlayHeight) {
		int darkGray = applyAlpha(DARK_GRAY, alpha);
		int titleColor = applyAlpha(TITLE, alpha);
		int yellow = applyAlpha(YELLOW, alpha);
		if (EmfConfig.HANDLER.instance().skillXpShowBackground) {
			context.fill(0, 0, overlayWidth, overlayHeight, applyAlpha(BACKGROUND, alpha));
		}
		int textY = PADDING;
		if ((title != null && !title.isBlank()) || (titleSuffix != null && !titleSuffix.isBlank())) {
			int titleX = PADDING;
			if (title != null && !title.isBlank()) {
				context.text(font, title, titleX, textY, titleColor, false);
				titleX += font.width(title);
			}
			if (titleSuffix != null && !titleSuffix.isBlank()) {
				context.text(font, titleSuffix, titleX, textY, yellow, false);
			}
			textY += LINE_HEIGHT;
		}
		boolean blinkOn = EmfConfig.HANDLER.instance().skillXpReachedBlink;
		for (OverlayLine line : lines) {
			boolean showValue = !line.reached() || !blinkOn || OverlayFormat.blinkVisible();
			drawLabeledLine(context, font, PADDING, textY, line, applyAlpha(line.labelColor(), alpha),
					applyAlpha(line.valueColor(), alpha), darkGray, showValue);
			textY += LINE_HEIGHT;
		}
	}

	private static void drawLabeledLine(GuiGraphicsExtractor context, Font font, int x, int y,
			OverlayLine line, int labelColor, int valueColor, int suffixColor, boolean showValue) {
		context.text(font, line.label(), x, y, labelColor, true);
		int valueX = x + font.width(line.label());
		if (showValue) {
			context.text(font, line.value(), valueX, y, valueColor, true);
		}
		if (line.suffix() != null && !line.suffix().isEmpty()) {
			context.text(font, line.suffix(), valueX + font.width(line.value()), y, suffixColor, true);
		}
	}

	private static int applyAlpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	private static String titleSuffix(String resourceName) {
		if (resourceName == null || resourceName.isBlank()) {
			return "";
		}
		return TITLE_SUFFIX_PREFIX + resourceName + TITLE_SUFFIX_SUFFIX;
	}

	private record OverlayLine(String label, String value, int labelColor, int valueColor, String suffix, boolean reached) {
	}
}
