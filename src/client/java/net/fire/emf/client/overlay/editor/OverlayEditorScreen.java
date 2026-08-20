package net.fire.emf.client.overlay.editor;

import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.function.OffhandSwapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class OverlayEditorScreen extends Screen {
	private static final int MIN_OVERLAY_RESIZE_WIDTH = 20;
	private static final int MIN_OVERLAY_RESIZE_HEIGHT = 6;
	private static final int GEAR_SIZE = 12;
	private static final int CHECKBOX_SIZE = 12;
	private static final int SETTINGS_ROW_HEIGHT = 25;
	private static final int RESOURCE_TARGET_EXTRA_Y = 10;
	private static final Identifier SETTINGS_ICON = ElementsMoreFeatures.id("icons/hud/settings.png");

	private final List<DraggableOverlay> overlays = new ArrayList<>();
	private final Screen previousScreen;
	private DraggableOverlay draggingOverlay;
	private DraggableOverlay resizingOverlay;
	private int dragOffsetX;
	private int dragOffsetY;
	private int resizeStartX;
	private int resizeStartY;
	private int resizeStartWidth;
	private int resizeStartHeight;
	private int resizeStartOverlayX;
	private boolean overlaySettingsOpen;
	private LineSettings openSettings = LineSettings.NONE;
	private StringWidget titleWidget;
	private EditBox targetField;
	private int overlayBoxX;
	private int overlayBoxY;
	private int overlayBoxWidth;
	private int overlayBoxHeight;
	private int skillSettingsBoxX;
	private int skillSettingsBoxY;
	private int skillSettingsBoxWidth;
	private int skillSettingsBoxHeight;

	public OverlayEditorScreen() {
		super(Component.literal("Overlay Editor"));
		this.previousScreen = Minecraft.getInstance().screen;
		overlays.add(new SkillInfoDraggableOverlay());
		overlays.add(new LevelInfoDraggableOverlay());
		overlays.add(new SkillFruitDraggableOverlay());
		overlays.add(new AutominerCooldownDraggableOverlay());
	}

	@Override
	protected void init() {
		super.init();

		titleWidget = new StringWidget(Component.literal("Overlay Editor - Drag & Drop to reposition overlays"), font);
		titleWidget.setPosition(width / 2 - titleWidget.getWidth() / 2, 20);
		addRenderableWidget(titleWidget);

		int buttonWidth = 80;
		int buttonSpacing = 10;
		int totalWidth = 3 * buttonWidth + 2 * buttonSpacing;
		int startX = width / 2 - totalWidth / 2;

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(startX, height - 30, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Overlay"), button -> {
			overlaySettingsOpen = !overlaySettingsOpen;
			if (!overlaySettingsOpen) {
				closeLineSettings();
			}
		}).bounds(startX + buttonWidth + buttonSpacing, height - 30, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset All"), button -> resetAllOverlays())
				.bounds(startX + 2 * (buttonWidth + buttonSpacing), height - 30, buttonWidth, 20).build());

		targetField = new EditBox(font, 0, 0, 120, 18, Component.literal("Ziel"));
		targetField.setMaxLength(12);
		targetField.setValue("");
		targetField.setResponder(this::onTargetTyped);
		targetField.setVisible(false);
		targetField.setCanLoseFocus(true);
		addRenderableWidget(targetField);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (previousScreen != null) {
			previousScreen.extractRenderState(context, mouseX, mouseY, delta);
		}

		context.fill(0, 0, width, height, 0x20000000);
		titleWidget.extractRenderState(context, mouseX, mouseY, delta);

		for (DraggableOverlay overlay : overlays) {
			if (!overlay.isConfigEnabled()) {
				continue;
			}
			overlay.renderInEditMode(context, mouseX, mouseY, delta);
			if (overlay.isHovered(mouseX, mouseY)) {
				renderResizeHandle(context, overlay);
				renderResetHandle(context, overlay);
			}
		}

		renderInstructions(context);
		if (overlaySettingsOpen) {
			renderOverlaySettings(context, mouseX, mouseY);
		}
		if (openSettings == LineSettings.NONE && targetField != null) {
			targetField.setVisible(false);
			targetField.setFocused(false);
		}

		super.extractRenderState(context, mouseX, mouseY, delta);
		if (openSettings == LineSettings.FARMING) {
			renderSkillLineSettings(context, mouseX, mouseY);
			renderTargetField(context, mouseX, mouseY, delta);
		} else if (openSettings == LineSettings.LEVEL) {
			renderLevelLineSettings(context, mouseX, mouseY);
			renderTargetField(context, mouseX, mouseY, delta);
		} else if (openSettings == LineSettings.FRUIT) {
			renderFruitLineSettings(context, mouseX, mouseY);
		} else if (openSettings == LineSettings.AUTOMINER) {
			renderAutominerLineSettings(context, mouseX, mouseY);
		}
		renderOverlayHandleTooltips(context, mouseX, mouseY);
	}

	private void renderOverlayHandleTooltips(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		for (DraggableOverlay overlay : overlays) {
			if (!overlay.isConfigEnabled()) {
				continue;
			}
			if (overlay.isResetArea(mouseX, mouseY)) {
				context.setComponentTooltipForNextFrame(font, List.of(Component.literal("Größe zurücksetzen")), mouseX, mouseY);
				return;
			}
			if (overlay.isResizeArea(mouseX, mouseY)) {
				context.setComponentTooltipForNextFrame(font, List.of(Component.literal("Ziehen um Größe anzupassen")), mouseX, mouseY);
				return;
			}
		}
	}

	private void renderResizeHandle(GuiGraphicsExtractor context, DraggableOverlay overlay) {
		int handleSize = 10;
		int x = overlay.getX() + overlay.getWidth() - handleSize;
		int y = overlay.getY() + overlay.getHeight() - handleSize;
		context.fill(x, y, x + handleSize, y + handleSize, 0xFFFFFFFF);
		context.fill(x + 1, y + 1, x + handleSize - 1, y + handleSize - 1, 0xFF000000);
		int arrowLength = handleSize - 4;
		for (int i = 0; i < arrowLength; i++) {
			int px = x + 2 + i;
			int py = y + 2 + i;
			if (px < x + handleSize - 1 && py < y + handleSize - 1) {
				context.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
			}
		}
		context.fill(x + handleSize - 3, y + handleSize - 2, x + handleSize - 1, y + handleSize - 1, 0xFFFFFFFF);
		context.fill(x + handleSize - 2, y + handleSize - 3, x + handleSize - 1, y + handleSize - 2, 0xFFFFFFFF);
		context.fill(x + 1, y + 1, x + 3, y + 2, 0xFFFFFFFF);
		context.fill(x + 1, y + 2, x + 2, y + 3, 0xFFFFFFFF);
	}

	private void renderResetHandle(GuiGraphicsExtractor context, DraggableOverlay overlay) {
		int handleSize = 10;
		int x = overlay.getX() + overlay.getWidth() - handleSize;
		int y = overlay.getY();
		context.fill(x, y, x + handleSize, y + handleSize, 0xFFFFFFFF);
		context.fill(x + 1, y + 1, x + handleSize - 1, y + handleSize - 1, 0xFF000000);

		float scale = 0.7f;
		String arrowText = "<-";
		float textX = x + (handleSize - font.width(arrowText) * scale) / 2.0f;
		float textY = y + (handleSize - font.lineHeight * scale) / 2.0f;
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(textX, textY);
		matrices.scale(scale, scale);
		context.text(font, arrowText, 0, 0, 0xFFFFFFFF, false);
		matrices.popMatrix();
	}

	private void renderInstructions(GuiGraphicsExtractor context) {
		int y = height - 80;
		String[] instructions = {
				"Left Click + Drag: Move overlay",
				"Left Click + Drag (corner): Resize overlay",
				"Left Click + Arrow Keys: Move overlay 1 pixel",
				"ESC: Close editor"
		};
		for (String instruction : instructions) {
			context.text(font, instruction, 10, y, 0xFFFFFFFF, false);
			y += 12;
		}
	}

	private void renderOverlaySettings(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		overlayBoxWidth = 250;
		overlayBoxHeight = overlays.size() * 25 + 40;
		overlayBoxX = width / 2 - overlayBoxWidth / 2;
		overlayBoxY = height / 2 - overlayBoxHeight / 2;
		context.fill(overlayBoxX, overlayBoxY, overlayBoxX + overlayBoxWidth, overlayBoxY + overlayBoxHeight, 0xFF000000);
		context.outline(overlayBoxX, overlayBoxY, overlayBoxWidth, overlayBoxHeight, 0xFFFFFFFF);
		context.text(font, "Overlay Settings", overlayBoxX + 10, overlayBoxY + 10, 0xFFFFFF00, false);

		int y = overlayBoxY + 35;
		int checkboxX = overlayBoxX + 10;
		for (DraggableOverlay overlay : overlays) {
			boolean enabled = overlay.isConfigEnabled();
			drawCheckbox(context, checkboxX, y, CHECKBOX_SIZE, enabled);
			context.text(font, overlay.getOverlayName(), checkboxX + CHECKBOX_SIZE + 5, y + 1, enabled ? 0xFFFFFFFF : 0xFF808080, false);

			int gearX = overlayBoxX + overlayBoxWidth - GEAR_SIZE - 10;
			int gearY = y - 1;
			boolean gearHovered = mouseX >= gearX - 1 && mouseX <= gearX + GEAR_SIZE + 1
					&& mouseY >= gearY - 1 && mouseY <= gearY + GEAR_SIZE + 1;
			context.outline(gearX - 1, gearY - 1, GEAR_SIZE + 2, GEAR_SIZE + 2, 0xFFFFFFFF);
			drawSettingsIcon(context, gearX, gearY, GEAR_SIZE);
			if (gearHovered) {
				context.fill(gearX - 1, gearY - 1, gearX + GEAR_SIZE + 1, gearY + GEAR_SIZE + 1, 0x40FFFFFF);
			}
			y += 25;
		}
	}

	private void layoutSkillSettingsBox() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		skillSettingsBoxWidth = 260;
		skillSettingsBoxHeight = config.skillXpShowResourceTarget ? 270 : 201;
		skillSettingsBoxX = width / 2 - skillSettingsBoxWidth / 2;
		skillSettingsBoxY = height / 2 - skillSettingsBoxHeight / 2;
		layoutTargetField(config.skillXpShowResourceTarget, resourceTargetFieldY());
	}

	private void layoutFruitSettingsBox() {
		skillSettingsBoxWidth = 260;
		skillSettingsBoxHeight = 110;
		skillSettingsBoxX = width / 2 - skillSettingsBoxWidth / 2;
		skillSettingsBoxY = height / 2 - skillSettingsBoxHeight / 2;
		if (targetField != null) {
			targetField.setVisible(false);
			targetField.setFocused(false);
		}
	}

	private void layoutAutominerSettingsBox() {
		skillSettingsBoxWidth = 260;
		skillSettingsBoxHeight = 130;
		skillSettingsBoxX = width / 2 - skillSettingsBoxWidth / 2;
		skillSettingsBoxY = height / 2 - skillSettingsBoxHeight / 2;
		if (targetField != null) {
			targetField.setVisible(false);
			targetField.setFocused(false);
		}
	}

	private void layoutLevelSettingsBox() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		skillSettingsBoxWidth = 260;
		skillSettingsBoxHeight = config.levelTrackerShowTarget ? 220 : 158;
		skillSettingsBoxX = width / 2 - skillSettingsBoxWidth / 2;
		skillSettingsBoxY = height / 2 - skillSettingsBoxHeight / 2;
		layoutTargetField(config.levelTrackerShowTarget, levelTargetFieldY());
	}

	private void layoutTargetField(boolean showField, int fieldY) {
		if (targetField == null) {
			return;
		}
		targetField.setVisible(showField);
		if (showField) {
			targetField.setPosition(skillSettingsBoxX + 26, fieldY);
			targetField.setWidth(skillSettingsBoxWidth - 36);
		} else {
			targetField.setFocused(false);
		}
	}

	private int resourceTargetFieldY() {
		return skillSettingsBoxY + 35 + SETTINGS_ROW_HEIGHT * 2 + 8 + SETTINGS_ROW_HEIGHT * 4 + RESOURCE_TARGET_EXTRA_Y;
	}

	private int levelTargetFieldY() {
		return skillSettingsBoxY + 35 + SETTINGS_ROW_HEIGHT * 2 + 8 + SETTINGS_ROW_HEIGHT * 2 + RESOURCE_TARGET_EXTRA_Y;
	}

	private int reachedBlinkRowY(int fieldY) {
		return fieldY + 22;
	}

	private void renderTargetField(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		if (targetField != null && targetField.isVisible()) {
			targetField.extractRenderState(context, mouseX, mouseY, delta);
		}
	}

	private void renderSkillLineSettings(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		layoutSkillSettingsBox();

		context.fill(0, 0, width, height, 0xA0000000);
		context.fill(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxX + skillSettingsBoxWidth, skillSettingsBoxY + skillSettingsBoxHeight, 0xFF000000);
		context.outline(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxWidth, skillSettingsBoxHeight, 0xFFFFFFFF);
		context.text(font, "Farming Tracker Settings", skillSettingsBoxX + 10, skillSettingsBoxY + 10, 0xFFFFFF00, false);

		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		y = drawSettingRow(context, checkboxX, y, "Hintergrund de-/aktivieren", config.skillXpShowBackground);
		y = drawSettingRow(context, checkboxX, y, "Dauerhaft anzeigen", config.skillXpAlwaysShow);
		y = drawSettingsDivider(context, y);
		y = drawSettingRow(context, checkboxX, y, "Skill XP", config.skillXpShowXpRate);
		y = drawSettingRow(context, checkboxX, y, "Nächstes Level", config.skillXpShowNextLevel);
		y = drawSettingRow(context, checkboxX, y, "Res/Kills", config.skillXpShowResources);
		y = drawSettingRow(context, checkboxX, y, "Ziel Res/Kills", config.skillXpShowResourceTarget);

		if (config.skillXpShowResourceTarget) {
			context.text(font, "Ziel", checkboxX, y - 12 + RESOURCE_TARGET_EXTRA_Y, 0xFFAAAAAA, false);
			drawSettingRow(context, checkboxX, reachedBlinkRowY(resourceTargetFieldY()), "Erreicht blinken", config.skillXpReachedBlink);
		}
	}

	private void renderLevelLineSettings(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		layoutLevelSettingsBox();

		context.fill(0, 0, width, height, 0xA0000000);
		context.fill(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxX + skillSettingsBoxWidth, skillSettingsBoxY + skillSettingsBoxHeight, 0xFF000000);
		context.outline(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxWidth, skillSettingsBoxHeight, 0xFFFFFFFF);
		context.text(font, "Level Tracker Settings", skillSettingsBoxX + 10, skillSettingsBoxY + 10, 0xFFFFFF00, false);

		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		y = drawSettingRow(context, checkboxX, y, "Hintergrund de-/aktivieren", config.levelTrackerShowBackground);
		y = drawSettingRow(context, checkboxX, y, "Dauerhaft anzeigen", config.levelTrackerAlwaysShow);
		y = drawSettingsDivider(context, y);
		y = drawSettingRow(context, checkboxX, y, "Level", config.levelTrackerShowLevelRate);
		y = drawSettingRow(context, checkboxX, y, "Ziel", config.levelTrackerShowTarget);

		if (config.levelTrackerShowTarget) {
			context.text(font, "Zielwert", checkboxX, y - 12 + RESOURCE_TARGET_EXTRA_Y, 0xFFAAAAAA, false);
			drawSettingRow(context, checkboxX, reachedBlinkRowY(levelTargetFieldY()), "Erreicht blinken", config.levelTrackerReachedBlink);
		}
	}

	private void renderFruitLineSettings(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		layoutFruitSettingsBox();

		context.fill(0, 0, width, height, 0xA0000000);
		context.fill(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxX + skillSettingsBoxWidth, skillSettingsBoxY + skillSettingsBoxHeight, 0xFF000000);
		context.outline(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxWidth, skillSettingsBoxHeight, 0xFFFFFFFF);
		context.text(font, "Skillfrüchte Timer Settings", skillSettingsBoxX + 10, skillSettingsBoxY + 10, 0xFFFFFF00, false);

		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		y = drawSettingRow(context, checkboxX, y, "Hintergrund de-/aktivieren", config.skillFruitShowBackground);
		drawSettingRow(context, checkboxX, y, "Dauerhaft anzeigen", config.skillFruitAlwaysShow);
	}

	private void renderAutominerLineSettings(GuiGraphicsExtractor context, int mouseX, int mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		layoutAutominerSettingsBox();

		context.fill(0, 0, width, height, 0xA0000000);
		context.fill(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxX + skillSettingsBoxWidth, skillSettingsBoxY + skillSettingsBoxHeight, 0xFF000000);
		context.outline(skillSettingsBoxX, skillSettingsBoxY, skillSettingsBoxWidth, skillSettingsBoxHeight, 0xFFFFFFFF);
		context.text(font, "Autominer Cooldown Settings", skillSettingsBoxX + 10, skillSettingsBoxY + 10, 0xFFFFFF00, false);

		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		y = drawSettingRow(context, checkboxX, y, "Hintergrund an/aus", config.autominerCooldownShowBackground);
		y = drawSettingRow(context, checkboxX, y, "Im Titel anzeigen an/aus", config.autominerCooldownShowTitle);
		drawSettingRow(context, checkboxX, y, "Sound an/aus", config.autominerCooldownPlaySound);
	}

	private int drawSettingRow(GuiGraphicsExtractor context, int x, int y, String label, boolean enabled) {
		drawCheckbox(context, x, y, CHECKBOX_SIZE, enabled);
		context.text(font, label, x + CHECKBOX_SIZE + 6, y + 2, enabled ? 0xFFFFFFFF : 0xFF808080, false);
		return y + SETTINGS_ROW_HEIGHT;
	}

	private int drawSettingsDivider(GuiGraphicsExtractor context, int y) {
		int left = skillSettingsBoxX + 10;
		int right = skillSettingsBoxX + skillSettingsBoxWidth - 10;
		int lineY = y - 6;
		context.fill(left, lineY, right, lineY + 1, 0xFF808080);
		return y + 8;
	}

	private void drawCheckbox(GuiGraphicsExtractor context, int x, int y, int size, boolean enabled) {
		context.fill(x, y, x + size, y + size, 0xFF303030);
		context.outline(x, y, size, size, 0xFFFFFFFF);
		if (!enabled) {
			return;
		}
		int color = 0xFFFFFFFF;
		for (int i = 2; i <= size - 4; i++) {
			context.fill(x + i, y + i, x + i + 2, y + i + 2, color);
			context.fill(x + size - 2 - i, y + i, x + size - i, y + i + 2, color);
		}
	}

	private void drawSettingsIcon(GuiGraphicsExtractor context, int x, int y, int size) {
		try {
			context.blit(RenderPipelines.GUI_TEXTURED, SETTINGS_ICON, x, y, 0.0f, 0.0f, size, size, size, size);
		} catch (Exception e) {
			context.outline(x, y, size, size, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (handleLineSettingsClick(event, doubled)) {
			return true;
		}
		if (handleOverlaySettingsClick(mouseX, mouseY, button)) {
			return true;
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (DraggableOverlay overlay : overlays) {
				if (overlay.isConfigEnabled() && overlay.isResetArea((int) mouseX, (int) mouseY)) {
					overlay.resetSizeToDefault();
					overlay.savePosition();
					return true;
				}
			}
			for (DraggableOverlay overlay : overlays) {
				if (overlay.isConfigEnabled() && overlay.isResizeArea((int) mouseX, (int) mouseY)) {
					resizingOverlay = overlay;
					resizeStartX = (int) mouseX;
					resizeStartY = (int) mouseY;
					resizeStartWidth = overlay.getWidth();
					resizeStartHeight = overlay.getHeight();
					resizeStartOverlayX = overlay.getX();
					return true;
				}
			}
			for (DraggableOverlay overlay : overlays) {
				if (overlay.isConfigEnabled() && overlay.isHovered((int) mouseX, (int) mouseY)) {
					draggingOverlay = overlay;
					dragOffsetX = (int) mouseX - overlay.getX();
					dragOffsetY = (int) mouseY - overlay.getY();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private boolean handleOverlaySettingsClick(double mouseX, double mouseY, int button) {
		if (!overlaySettingsOpen || button != 0) {
			return false;
		}

		if (mouseX < overlayBoxX || mouseX > overlayBoxX + overlayBoxWidth || mouseY < overlayBoxY || mouseY > overlayBoxY + overlayBoxHeight) {
			if (openSettings != LineSettings.NONE && isInsideSkillSettings(mouseX, mouseY)) {
				return false;
			}
			overlaySettingsOpen = false;
			closeLineSettings();
			return false;
		}

		int y = overlayBoxY + 35;
		int checkboxX = overlayBoxX + 10;
		for (DraggableOverlay overlay : overlays) {
			int gearX = overlayBoxX + overlayBoxWidth - GEAR_SIZE - 10;
			int gearY = y - 1;
			boolean clickedGear = mouseX >= gearX - 1 && mouseX <= gearX + GEAR_SIZE + 1
					&& mouseY >= gearY - 1 && mouseY <= gearY + GEAR_SIZE + 1;
			if (clickedGear) {
				if (overlay instanceof SkillInfoDraggableOverlay) {
					toggleLineSettings(LineSettings.FARMING);
				} else if (overlay instanceof LevelInfoDraggableOverlay) {
					toggleLineSettings(LineSettings.LEVEL);
				} else if (overlay instanceof SkillFruitDraggableOverlay) {
					toggleLineSettings(LineSettings.FRUIT);
				} else if (overlay instanceof AutominerCooldownDraggableOverlay) {
					toggleLineSettings(LineSettings.AUTOMINER);
				}
				return true;
			}

			int textX = checkboxX + CHECKBOX_SIZE + 5;
			int textWidth = font.width(overlay.getOverlayName());
			boolean clicked = (mouseX >= checkboxX && mouseX <= checkboxX + CHECKBOX_SIZE && mouseY >= y && mouseY <= y + CHECKBOX_SIZE)
					|| (mouseX >= textX && mouseX <= textX + textWidth && mouseY >= y && mouseY <= y + font.lineHeight);
			if (clicked) {
				toggleOverlayEnabled(overlay);
				EmfConfig.HANDLER.save();
				return true;
			}
			y += 25;
		}
		return false;
	}

	private boolean handleLineSettingsClick(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (openSettings == LineSettings.NONE || event.button() != 0) {
			return false;
		}
		if (openSettings == LineSettings.FARMING) {
			layoutSkillSettingsBox();
		} else if (openSettings == LineSettings.LEVEL) {
			layoutLevelSettingsBox();
		} else if (openSettings == LineSettings.FRUIT) {
			layoutFruitSettingsBox();
		} else if (openSettings == LineSettings.AUTOMINER) {
			layoutAutominerSettingsBox();
		}

		if (isInsideTargetField(mouseX, mouseY)) {
			focusTargetField();
			return targetField.mouseClicked(event, doubled);
		}

		if (targetField != null && targetField.isFocused()) {
			confirmTarget();
			targetField.setFocused(false);
			setFocused(null);
		}

		if (!isInsideSkillSettings(mouseX, mouseY)) {
			closeLineSettings();
			return true;
		}

		if (openSettings == LineSettings.FARMING) {
			return handleFarmingSettingsClick(mouseX, mouseY);
		}
		if (openSettings == LineSettings.LEVEL) {
			return handleLevelSettingsClick(mouseX, mouseY);
		}
		if (openSettings == LineSettings.AUTOMINER) {
			return handleAutominerSettingsClick(mouseX, mouseY);
		}
		return handleFruitSettingsClick(mouseX, mouseY);
	}

	private boolean handleFarmingSettingsClick(double mouseX, double mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Hintergrund de-/aktivieren")) {
			config.skillXpShowBackground = !config.skillXpShowBackground;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Dauerhaft anzeigen")) {
			config.skillXpAlwaysShow = !config.skillXpAlwaysShow;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT + 8;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Skill XP")) {
			config.skillXpShowXpRate = !config.skillXpShowXpRate;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Nächstes Level")) {
			config.skillXpShowNextLevel = !config.skillXpShowNextLevel;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Res/Kills")) {
			config.skillXpShowResources = !config.skillXpShowResources;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Ziel Res/Kills")) {
			config.skillXpShowResourceTarget = !config.skillXpShowResourceTarget;
			if (!config.skillXpShowResourceTarget) {
				confirmTarget();
			} else {
				syncTargetFieldValue();
			}
			layoutSkillSettingsBox();
			EmfConfig.HANDLER.save();
			return true;
		}
		if (config.skillXpShowResourceTarget
				&& clickSettingRow(mouseX, mouseY, checkboxX, reachedBlinkRowY(resourceTargetFieldY()), "Erreicht blinken")) {
			config.skillXpReachedBlink = !config.skillXpReachedBlink;
			EmfConfig.HANDLER.save();
			return true;
		}
		return true;
	}

	private boolean handleLevelSettingsClick(double mouseX, double mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Hintergrund de-/aktivieren")) {
			config.levelTrackerShowBackground = !config.levelTrackerShowBackground;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Dauerhaft anzeigen")) {
			config.levelTrackerAlwaysShow = !config.levelTrackerAlwaysShow;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT + 8;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Level")) {
			config.levelTrackerShowLevelRate = !config.levelTrackerShowLevelRate;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Ziel")) {
			config.levelTrackerShowTarget = !config.levelTrackerShowTarget;
			if (!config.levelTrackerShowTarget) {
				confirmTarget();
			} else {
				syncTargetFieldValue();
			}
			layoutLevelSettingsBox();
			EmfConfig.HANDLER.save();
			return true;
		}
		if (config.levelTrackerShowTarget
				&& clickSettingRow(mouseX, mouseY, checkboxX, reachedBlinkRowY(levelTargetFieldY()), "Erreicht blinken")) {
			config.levelTrackerReachedBlink = !config.levelTrackerReachedBlink;
			EmfConfig.HANDLER.save();
			return true;
		}
		return true;
	}

	private boolean handleFruitSettingsClick(double mouseX, double mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Hintergrund de-/aktivieren")) {
			config.skillFruitShowBackground = !config.skillFruitShowBackground;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Dauerhaft anzeigen")) {
			config.skillFruitAlwaysShow = !config.skillFruitAlwaysShow;
			EmfConfig.HANDLER.save();
			return true;
		}
		return true;
	}

	private boolean handleAutominerSettingsClick(double mouseX, double mouseY) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		int y = skillSettingsBoxY + 35;
		int checkboxX = skillSettingsBoxX + 10;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Hintergrund an/aus")) {
			config.autominerCooldownShowBackground = !config.autominerCooldownShowBackground;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Im Titel anzeigen an/aus")) {
			config.autominerCooldownShowTitle = !config.autominerCooldownShowTitle;
			EmfConfig.HANDLER.save();
			return true;
		}
		y += SETTINGS_ROW_HEIGHT;
		if (clickSettingRow(mouseX, mouseY, checkboxX, y, "Sound an/aus")) {
			config.autominerCooldownPlaySound = !config.autominerCooldownPlaySound;
			EmfConfig.HANDLER.save();
			return true;
		}
		return true;
	}

	private boolean clickSettingRow(double mouseX, double mouseY, int x, int y, String label) {
		int textWidth = font.width(label);
		return (mouseX >= x && mouseX <= x + CHECKBOX_SIZE && mouseY >= y && mouseY <= y + CHECKBOX_SIZE)
				|| (mouseX >= x + CHECKBOX_SIZE + 6 && mouseX <= x + CHECKBOX_SIZE + 6 + textWidth && mouseY >= y && mouseY <= y + font.lineHeight);
	}

	private boolean isInsideSkillSettings(double mouseX, double mouseY) {
		return mouseX >= skillSettingsBoxX && mouseX <= skillSettingsBoxX + skillSettingsBoxWidth
				&& mouseY >= skillSettingsBoxY && mouseY <= skillSettingsBoxY + skillSettingsBoxHeight;
	}

	private boolean isInsideTargetField(double mouseX, double mouseY) {
		if (targetField == null || !targetField.isVisible()) {
			return false;
		}
		int x = targetField.getX();
		int y = targetField.getY();
		return mouseX >= x && mouseX <= x + targetField.getWidth()
				&& mouseY >= y && mouseY <= y + targetField.getHeight();
	}

	private void focusTargetField() {
		if (targetField == null) {
			return;
		}
		targetField.setVisible(true);
		targetField.setFocused(true);
		setFocused(targetField);
	}

	private void toggleLineSettings(LineSettings settings) {
		if (openSettings == settings) {
			closeLineSettings();
			return;
		}
		confirmTarget();
		openSettings = settings;
		syncTargetFieldValue();
		if (settings == LineSettings.FARMING) {
			layoutSkillSettingsBox();
		} else if (settings == LineSettings.LEVEL) {
			layoutLevelSettingsBox();
		} else if (settings == LineSettings.FRUIT) {
			layoutFruitSettingsBox();
		} else {
			layoutAutominerSettingsBox();
		}
	}

	private void closeLineSettings() {
		confirmTarget();
		openSettings = LineSettings.NONE;
		if (targetField != null) {
			targetField.setVisible(false);
			targetField.setFocused(false);
		}
		setFocused(null);
	}

	private void syncTargetFieldValue() {
		if (targetField == null) {
			return;
		}
		long target = currentTargetValue();
		targetField.setResponder(s -> {
		});
		targetField.setValue(target > 0L ? Long.toString(target) : "");
		targetField.setResponder(this::onTargetTyped);
	}

	private long currentTargetValue() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		if (openSettings == LineSettings.LEVEL) {
			return config.levelTrackerTarget;
		}
		return config.skillXpResourceTarget;
	}

	private void onTargetTyped(String text) {
		if (text == null || text.isEmpty() || text.chars().allMatch(Character::isDigit)) {
			return;
		}
		long current = currentTargetValue();
		targetField.setResponder(s -> {
		});
		targetField.setValue(current > 0L ? Long.toString(current) : "");
		targetField.setResponder(this::onTargetTyped);
	}

	private void confirmTarget() {
		if (targetField == null || openSettings == LineSettings.NONE) {
			return;
		}
		String text = targetField.getValue();
		long value = 0L;
		if (text != null && !text.isBlank()) {
			try {
				value = Long.parseLong(text);
			} catch (NumberFormatException ignored) {
				return;
			}
		} else {
			targetField.setValue("");
		}
		EmfConfig config = EmfConfig.HANDLER.instance();
		if (openSettings == LineSettings.LEVEL) {
			config.levelTrackerTarget = value;
		} else if (openSettings == LineSettings.FARMING) {
			config.skillXpResourceTarget = value;
		}
		EmfConfig.HANDLER.save();
	}

	private void toggleOverlayEnabled(DraggableOverlay overlay) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		boolean enabled = !overlay.isConfigEnabled();
		if (overlay instanceof SkillInfoDraggableOverlay) {
			config.showSkillXpOverlay = enabled;
			config.skillXpOverlayEnabled = enabled;
		} else if (overlay instanceof LevelInfoDraggableOverlay) {
			config.showLevelTrackerOverlay = enabled;
			config.levelTrackerOverlayEnabled = enabled;
		} else if (overlay instanceof SkillFruitDraggableOverlay) {
			config.showSkillFruitOverlay = enabled;
			config.skillFruitOverlayEnabled = enabled;
		} else if (overlay instanceof AutominerCooldownDraggableOverlay) {
			OffhandSwapper.setDetectionEnabled(enabled, false);
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseDragged(event, deltaX, deltaY);
		}

		if (draggingOverlay != null) {
			int newX = Math.max(0, Math.min((int) mouseX - dragOffsetX, width - draggingOverlay.getWidth()));
			int newY = Math.max(0, Math.min((int) mouseY - dragOffsetY, height - draggingOverlay.getHeight()));
			draggingOverlay.setPosition(newX, newY);
			draggingOverlay.savePosition();
			return true;
		}

		if (resizingOverlay != null) {
			int newWidth = Math.max(MIN_OVERLAY_RESIZE_WIDTH, resizeStartWidth + ((int) mouseX - resizeStartX));
			int newHeight = Math.max(MIN_OVERLAY_RESIZE_HEIGHT, resizeStartHeight + ((int) mouseY - resizeStartY));
			resizingOverlay.setSize(newWidth, newHeight);

			Minecraft client = Minecraft.getInstance();
			if (client != null && client.getWindow() != null) {
				int screenWidth = client.getWindow().getGuiScaledWidth();
				int screenHeight = client.getWindow().getGuiScaledHeight();
				boolean isOnRightSide = resizeStartOverlayX >= screenWidth / 2;
				int newX = isOnRightSide
						? Math.max(0, Math.min(resizeStartOverlayX + resizeStartWidth - newWidth, screenWidth - newWidth))
						: resizeStartOverlayX;
				if (!isOnRightSide && newX + newWidth > screenWidth) {
					newX = screenWidth - newWidth;
				}
				int newY = Math.max(0, Math.min(resizingOverlay.getY(), screenHeight - newHeight));
				resizingOverlay.setPosition(newX, newY);
			}
			resizingOverlay.savePosition();
			return true;
		}

		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			if (draggingOverlay != null) {
				draggingOverlay.savePosition();
				draggingOverlay = null;
				return true;
			}
			if (resizingOverlay != null) {
				resizingOverlay.savePosition();
				resizingOverlay = null;
				return true;
			}
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (targetField != null && targetField.isVisible() && targetField.isFocused()
				&& targetField.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int keyCode = event.key();
		if (targetField != null && targetField.isVisible() && targetField.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				confirmTarget();
				targetField.setFocused(false);
				setFocused(null);
				return true;
			}
			if (keyCode != GLFW.GLFW_KEY_ESCAPE && targetField.keyPressed(event)) {
				return true;
			}
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (openSettings != LineSettings.NONE) {
				closeLineSettings();
				return true;
			}
			onClose();
			return true;
		}
		var overlayKey = OverlayEditorUtility.getOverlayEditorKeyMapping();
		if (overlayKey != null && overlayKey.matches(new KeyEvent(keyCode, -1, 0))) {
			onClose();
			return true;
		}

		if (draggingOverlay != null) {
			long windowHandle = Minecraft.getInstance().getWindow().handle();
			boolean leftMousePressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
			if (leftMousePressed) {
				int newX = draggingOverlay.getX();
				int newY = draggingOverlay.getY();
				if (keyCode == GLFW.GLFW_KEY_LEFT) {
					newX--;
				} else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
					newX++;
				} else if (keyCode == GLFW.GLFW_KEY_UP) {
					newY--;
				} else if (keyCode == GLFW.GLFW_KEY_DOWN) {
					newY++;
				} else {
					return super.keyPressed(event);
				}
				newX = Math.max(0, Math.min(newX, width - draggingOverlay.getWidth()));
				newY = Math.max(0, Math.min(newY, height - draggingOverlay.getHeight()));
				draggingOverlay.setPosition(newX, newY);
				draggingOverlay.savePosition();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void resetAllOverlays() {
		for (DraggableOverlay overlay : overlays) {
			if (!overlay.isConfigEnabled()) {
				continue;
			}
			overlay.resetToDefault();
			int maxX = Math.max(0, width - overlay.getWidth());
			int maxY = Math.max(0, height - overlay.getHeight());
			overlay.setPosition(Math.max(0, Math.min(overlay.getX(), maxX)), Math.max(0, Math.min(overlay.getY(), maxY)));
			overlay.savePosition();
		}
	}

	@Override
	public void onClose() {
		for (DraggableOverlay overlay : overlays) {
			overlay.savePosition();
		}
		EmfConfig.HANDLER.save();
		OverlayEditorUtility.setOverlayEditorOpen(false);
		Minecraft client = Minecraft.getInstance();
		if (client != null && previousScreen != null) {
			client.setScreen(previousScreen);
		} else {
			super.onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum LineSettings {
		NONE,
		FARMING,
		LEVEL,
		FRUIT,
		AUTOMINER
	}
}
