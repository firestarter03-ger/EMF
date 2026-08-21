package net.fire.emf.client.session;

import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.mixin.AbstractContainerScreenAccessor;
import net.fire.emf.client.overlay.OverlayBounds;
import net.fire.emf.client.session.SessionModels.DisplayMode;
import net.fire.emf.client.session.SessionModels.LiveSession;
import net.fire.emf.client.session.SessionModels.LootStack;
import net.fire.emf.client.session.SessionModels.MobStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SessionSummaryOverlay {
	public static final int PADDING = 6;
	public static final int LINE_HEIGHT = 11;
	private static final int ARROW_BOX = 12;
	private static final int ARROW_GAP = 3;
	private static final int HEADER_LINE_GAP = 3;
	private static final int INVENTORY_GAP = 4;
	private static final int MIN_WIDTH = 160;
	private static final Pattern MOB_LEVEL = Pattern.compile("(?i)^(.*?)\\s*Lvl\\s*(\\d+)\\s*$");
	private static final int TITLE = 0xFFFFFF55;
	private static final int LABEL = 0xFFAAAAAA;
	private static final int VALUE = 0xFFFFFFFF;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int BACKGROUND = 0xC0101010;
	private static final int SCROLLBAR = 0xFF808080;
	private static final int SCROLLBAR_BG = 0xFF303030;
	private static final int DIVIDER = 0xFF555555;
	private static final int ARROW_BOX_BG = 0xFF1A1A1A;
	private static final int ARROW_BOX_BORDER = 0xFF55FFFF;

	private static int scrollOffset;
	private static final List<HitRegion> hitRegions = new ArrayList<>();
	private static HitRegion leftArrow;
	private static HitRegion rightArrow;

	private SessionSummaryOverlay() {
	}

	public static int getUnscaledWidth() {
		Minecraft client = Minecraft.getInstance();
		Font font = client == null ? null : client.font;
		if (font == null) {
			return MIN_WIDTH;
		}
		return measurePanelWidth(font, SessionTracker.displayMode().label());
	}

	public static int getUnscaledHeight() {
		return 160;
	}

	private static int measurePanelWidth(Font font, String modeLabel) {
		int modeRow = ARROW_BOX + ARROW_GAP + font.width(modeLabel) + ARROW_GAP + ARROW_BOX;
		int titleRow = font.width("Session Zusammenfassung");
		return Math.max(MIN_WIDTH, Math.max(modeRow, titleRow) + PADDING * 2);
	}

	public static void renderPreview(GuiGraphicsExtractor context, Minecraft client) {
		Font font = client.font;
		int width = measurePanelWidth(font, "Alle");
		int height = getUnscaledHeight();
		context.fill(0, 0, width, height, BACKGROUND);
		drawHeader(context, font, width, "Alle", -1, -1);
		int dividerY = headerBottom();
		context.fill(PADDING, dividerY, width - PADDING, dividerY + 1, DIVIDER);
		context.text(font, "Zeit: 0:12h", PADDING, dividerY + 4, VALUE, false);
		context.text(font, "↓ Schleim Lvl 12: 20", PADDING, dividerY + 4 + LINE_HEIGHT, ACCENT, false);
	}

	public static void renderInInventory(GuiGraphicsExtractor context, Minecraft client, InventoryScreen screen, int mouseX, int mouseY) {
		if (!shouldRender(client, screen)) {
			return;
		}
		EmfConfig config = EmfConfig.HANDLER.instance();
		float scale = config.sessionSummaryOverlayScale <= 0.0f ? 1.0f : config.sessionSummaryOverlayScale;
		int panelW = getUnscaledWidth();
		int panelH = getUnscaledHeight();
		int x = resolveX(client, screen, config, panelW, scale);
		int y = resolveY(client, screen, config, panelH, scale);

		var matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		int localX = (int) Math.floor((mouseX - x) / scale);
		int localY = (int) Math.floor((mouseY - y) / scale);
		renderPanel(context, client.font, panelW, panelH, localX, localY, mouseX, mouseY);
		matrices.popMatrix();
	}

	public static boolean handleClick(double mouseX, double mouseY, int button) {
		Minecraft client = Minecraft.getInstance();
		if (button != 0 || !(client.screen instanceof InventoryScreen screen) || !shouldRender(client, screen)) {
			return false;
		}
		EmfConfig config = EmfConfig.HANDLER.instance();
		float scale = config.sessionSummaryOverlayScale <= 0.0f ? 1.0f : config.sessionSummaryOverlayScale;
		int panelW = getUnscaledWidth();
		int panelH = getUnscaledHeight();
		int x = resolveX(client, screen, config, panelW, scale);
		int y = resolveY(client, screen, config, panelH, scale);
		int localX = (int) Math.floor((mouseX - x) / scale);
		int localY = (int) Math.floor((mouseY - y) / scale);
		if (localX < 0 || localY < 0 || localX > panelW || localY > panelH) {
			return false;
		}
		if (leftArrow != null && leftArrow.contains(localX, localY)) {
			SessionTracker.cycleDisplayMode(-1);
			return true;
		}
		if (rightArrow != null && rightArrow.contains(localX, localY)) {
			SessionTracker.cycleDisplayMode(1);
			return true;
		}
		for (HitRegion region : hitRegions) {
			if (region.contains(localX, localY) && region.mobName != null) {
				SessionTracker.toggleExpanded(region.mobName);
				return true;
			}
		}
		return true;
	}

	public static boolean handleScroll(double mouseX, double mouseY, double scrollY) {
		Minecraft client = Minecraft.getInstance();
		if (!(client.screen instanceof InventoryScreen screen) || !shouldRender(client, screen) || scrollY == 0.0) {
			return false;
		}
		EmfConfig config = EmfConfig.HANDLER.instance();
		float scale = config.sessionSummaryOverlayScale <= 0.0f ? 1.0f : config.sessionSummaryOverlayScale;
		int panelW = getUnscaledWidth();
		int panelH = getUnscaledHeight();
		int x = resolveX(client, screen, config, panelW, scale);
		int y = resolveY(client, screen, config, panelH, scale);
		int localX = (int) Math.floor((mouseX - x) / scale);
		int localY = (int) Math.floor((mouseY - y) / scale);
		if (localX < 0 || localY < 0 || localX > panelW || localY > panelH) {
			return false;
		}
		scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(scrollY) * LINE_HEIGHT * 2);
		return true;
	}

	private static boolean shouldRender(Minecraft client, InventoryScreen screen) {
		if (client == null || client.player == null || screen == null) {
			return false;
		}
		return SessionTracker.isFeatureEnabled() && EmfConfig.sessionSummaryOverlayVisible();
	}

	private static int resolveX(Minecraft client, InventoryScreen screen, EmfConfig config, int panelW, float scale) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int scaledW = Math.max(1, Math.round(panelW * scale));
		int anchoredX = accessor.emf$getLeftPos() - INVENTORY_GAP - scaledW;
		int configured = config.sessionSummaryOverlayX;
		int x = configured < 0 ? anchoredX : Math.min(configured, anchoredX);
		return OverlayBounds.clampX(client, Math.max(0, x), panelW, scale);
	}

	private static int resolveY(Minecraft client, InventoryScreen screen, EmfConfig config, int panelH, float scale) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int configured = config.sessionSummaryOverlayY;
		int y = configured < 0 ? accessor.emf$getTopPos() : configured;
		return OverlayBounds.clampY(client, y, panelH, scale);
	}

	private static int headerBottom() {
		return PADDING + ARROW_BOX + HEADER_LINE_GAP + LINE_HEIGHT + 2;
	}

	private static void renderPanel(GuiGraphicsExtractor context, Font font, int width, int height,
			int localMouseX, int localMouseY, int screenMouseX, int screenMouseY) {
		hitRegions.clear();
		leftArrow = null;
		rightArrow = null;
		context.fill(0, 0, width, height, BACKGROUND);

		DisplayMode mode = SessionTracker.displayMode();
		drawHeader(context, font, width, mode.label(), localMouseX, localMouseY);

		int dividerY = headerBottom();
		context.fill(PADDING, dividerY, width - PADDING, dividerY + 1, DIVIDER);

		List<Line> lines = buildLines(mode, System.currentTimeMillis());
		int contentTop = dividerY + 4;
		int contentBottom = height - PADDING;
		int contentHeight = Math.max(1, contentBottom - contentTop);
		int totalHeight = lines.size() * LINE_HEIGHT;
		int maxScroll = Math.max(0, totalHeight - contentHeight);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		context.enableScissor(0, contentTop, width - 6, contentBottom);
		int drawY = contentTop - scrollOffset;
		for (Line line : lines) {
			if (drawY + LINE_HEIGHT >= contentTop && drawY <= contentBottom) {
				if (line.display != null) {
					context.text(font, line.display, PADDING, drawY, VALUE, false);
				} else {
					context.text(font, line.text, PADDING, drawY, line.color, false);
				}
				if (line.mobToggle) {
					hitRegions.add(new HitRegion(PADDING, drawY, width - 12, LINE_HEIGHT, line.mobName));
				}
				if (line.hoverLines != null && !line.hoverLines.isEmpty()
						&& localMouseX >= PADDING && localMouseX <= width - 8
						&& localMouseY >= drawY && localMouseY <= drawY + LINE_HEIGHT) {
					context.setComponentTooltipForNextFrame(font, line.hoverLines, screenMouseX, screenMouseY);
				}
			}
			drawY += LINE_HEIGHT;
		}
		context.disableScissor();

		if (maxScroll > 0) {
			int barX = width - 4;
			int barTop = contentTop;
			int barHeight = contentHeight;
			context.fill(barX, barTop, barX + 3, barTop + barHeight, SCROLLBAR_BG);
			float ratio = contentHeight / (float) totalHeight;
			int thumbHeight = Math.max(8, (int) (barHeight * ratio));
			int thumbTravel = barHeight - thumbHeight;
			int thumbY = barTop + (maxScroll == 0 ? 0 : (int) (thumbTravel * (scrollOffset / (float) maxScroll)));
			context.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, SCROLLBAR);
		}
	}

	private static void drawHeader(GuiGraphicsExtractor context, Font font, int width, String modeLabel, int mouseX, int mouseY) {
		int contentWidth = width - PADDING * 2;
		int modeRowWidth = ARROW_BOX + ARROW_GAP + font.width(modeLabel) + ARROW_GAP + ARROW_BOX;
		int cursorX = PADDING + Math.max(0, (contentWidth - modeRowWidth) / 2);
		int modeY = PADDING;
		int labelY = modeY - 1 + (ARROW_BOX - font.lineHeight) / 2 + 1;

		boolean leftHovered = mouseX >= cursorX && mouseX <= cursorX + ARROW_BOX
				&& mouseY >= modeY - 1 && mouseY <= modeY + ARROW_BOX;
		leftArrow = drawArrowButton(context, font, cursorX, modeY, "<", leftHovered);
		cursorX += ARROW_BOX + ARROW_GAP;
		context.text(font, modeLabel, cursorX, labelY, TITLE, false);
		cursorX += font.width(modeLabel) + ARROW_GAP;
		boolean rightHovered = mouseX >= cursorX && mouseX <= cursorX + ARROW_BOX
				&& mouseY >= modeY - 1 && mouseY <= modeY + ARROW_BOX;
		rightArrow = drawArrowButton(context, font, cursorX, modeY, ">", rightHovered);

		String title = "Session Zusammenfassung";
		int titleX = PADDING + Math.max(0, (contentWidth - font.width(title)) / 2);
		context.text(font, title, titleX, PADDING + ARROW_BOX + HEADER_LINE_GAP, TITLE, false);
	}

	private static HitRegion drawArrowButton(GuiGraphicsExtractor context, Font font, int x, int y, String symbol, boolean hovered) {
		int boxY = y - 1;
		int bg = hovered ? 0xFF2A3A3A : ARROW_BOX_BG;
		context.fill(x, boxY, x + ARROW_BOX, boxY + ARROW_BOX, bg);
		context.outline(x, boxY, ARROW_BOX, ARROW_BOX, ARROW_BOX_BORDER);
		int textX = x + (ARROW_BOX - font.width(symbol)) / 2;
		int textY = boxY + (ARROW_BOX - font.lineHeight) / 2 + 1;
		context.text(font, symbol, textX, textY, ACCENT, false);
		return new HitRegion(x - 1, boxY - 1, ARROW_BOX + 2, ARROW_BOX + 2, null);
	}

	private static List<Line> buildLines(DisplayMode mode, long now) {
		List<Line> lines = new ArrayList<>();
		if (mode == DisplayMode.MOB) {
			return buildMobModeLines(lines, now);
		}
		LiveSession session = SessionTracker.allSession();
		if (session == null) {
			lines.add(plain("Keine aktive Session", LABEL));
			return lines;
		}
		lines.add(plain("Zeit: " + SessionTracker.formatElapsed(session.elapsedMs(now)), VALUE));

		if (session.mobs.isEmpty()) {
			lines.add(plain("Noch keine Kills", LABEL));
			return lines;
		}
		List<Map.Entry<String, MobStats>> entries = new ArrayList<>(session.mobs.entrySet());
		entries.sort(Comparator.comparing((Map.Entry<String, MobStats> e) -> mobSortKey(e.getKey())));
		for (Map.Entry<String, MobStats> entry : entries) {
			String mob = entry.getKey();
			MobStats stats = entry.getValue();
			boolean expanded = SessionTracker.isExpanded(mob);
			String marker = expanded ? "↓ " : "→ ";
			lines.add(new Line(marker + mob + ": " + stats.kills, ACCENT, true, mob, null, null));
			if (expanded) {
				appendLoot(lines, stats);
			}
		}
		return lines;
	}

	/**
	 * Mob Spezifisch: Zeit = aktuelle Mob-Phase, Kills/Loot = Summe über die ganze Session (wie in Alle).
	 * Sonst fehlen Loot/Kills nach Mobwechsel und Rückkehr.
	 */
	private static List<Line> buildMobModeLines(List<Line> lines, long now) {
		LiveSession mobLive = SessionTracker.mobSession();
		LiveSession all = SessionTracker.allSession();
		if (mobLive == null) {
			lines.add(plain("Keine aktive Session", LABEL));
			return lines;
		}
		lines.add(plain("Zeit: " + SessionTracker.formatElapsed(mobLive.elapsedMs(now)), VALUE));
		String mob = mobLive.currentMobName;
		if (mob == null || mob.isBlank()) {
			lines.add(plain("Noch kein Mob erkannt", LABEL));
			return lines;
		}
		MobStats stats = null;
		if (all != null) {
			stats = all.mobs.get(mob);
		}
		if (stats == null) {
			stats = mobLive.mobs.getOrDefault(mob, new MobStats());
		}
		lines.add(plain(mob + ": " + stats.kills, ACCENT));
		appendLoot(lines, stats);
		return lines;
	}

	private static MobSortKey mobSortKey(String mobName) {
		Matcher matcher = MOB_LEVEL.matcher(mobName == null ? "" : mobName.trim());
		if (matcher.matches()) {
			return new MobSortKey(matcher.group(1).trim().toLowerCase(Locale.ROOT), parseLevel(matcher.group(2)), mobName);
		}
		return new MobSortKey(mobName == null ? "" : mobName.toLowerCase(Locale.ROOT), Integer.MAX_VALUE, mobName);
	}

	private static int parseLevel(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return Integer.MAX_VALUE;
		}
	}

	private static void appendLoot(List<Line> lines, MobStats stats) {
		lines.add(plain("Erhaltener Loot:", LABEL));
		List<LootStack> loot = stats.sortedLoot();
		if (loot.isEmpty()) {
			lines.add(plain("• kein Loot", LABEL));
			return;
		}
		for (LootStack stack : loot) {
			MutableComponent display = Component.literal("• " + stack.count + "x ");
			Component name = SessionComponents.fromJson(stack.itemNameJson);
			String namePlain = name.getString().trim();
			if (namePlain.isBlank() || looksLikeJsonDump(namePlain)) {
				name = Component.literal(stack.itemName == null ? "?" : stack.itemName);
			} else {
				name = trimLeadingWhitespace(name);
			}
			display.append(name);
			List<Component> hover = SessionComponents.fromJsonList(stack.hoverLineJsons);
			hover.removeIf(line -> line != null && looksLikeJsonDump(line.getString()));
			if (hover.isEmpty() && stack.hoverText != null && !stack.hoverText.isBlank()) {
				for (String part : stack.hoverText.split("\n", -1)) {
					hover.add(part.isEmpty() ? Component.literal(" ") : Component.literal(part));
				}
			}
			lines.add(new Line(display.getString(), VALUE, false, null, display, hover));
		}
	}

	private static boolean looksLikeJsonDump(String value) {
		if (value == null) {
			return false;
		}
		String trimmed = value.trim();
		return trimmed.startsWith("[") || trimmed.startsWith("{");
	}

	private static Component trimLeadingWhitespace(Component component) {
		if (component == null) {
			return Component.empty();
		}
		MutableComponent result = Component.empty();
		AtomicBoolean started = new AtomicBoolean(false);
		component.visit((style, text) -> {
			if (text == null || text.isEmpty()) {
				return java.util.Optional.empty();
			}
			String value = text;
			if (!started.get()) {
				value = value.replaceFirst("^\\s+", "");
				if (value.isEmpty()) {
					return java.util.Optional.empty();
				}
				started.set(true);
			}
			result.append(Component.literal(value).withStyle(style));
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return result.getString().isBlank() ? component : result;
	}

	private static Line plain(String text, int color) {
		return new Line(text, color, false, null, null, null);
	}

	private record MobSortKey(String baseName, int level, String fullName) implements Comparable<MobSortKey> {
		@Override
		public int compareTo(MobSortKey other) {
			int byName = baseName.compareTo(other.baseName);
			if (byName != 0) {
				return byName;
			}
			int byLevel = Integer.compare(level, other.level);
			if (byLevel != 0) {
				return byLevel;
			}
			return String.valueOf(fullName).compareToIgnoreCase(String.valueOf(other.fullName));
		}
	}

	private record Line(String text, int color, boolean mobToggle, String mobName, Component display, List<Component> hoverLines) {
	}

	private record HitRegion(int x, int y, int w, int h, String mobName) {
		boolean contains(int px, int py) {
			return px >= x && px <= x + w && py >= y && py <= y + h;
		}
	}
}
