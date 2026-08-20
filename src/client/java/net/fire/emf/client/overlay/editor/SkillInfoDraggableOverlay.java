package net.fire.emf.client.overlay.editor;

import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.SkillInfoOverlay;
import net.fire.emf.client.overlay.OverlayBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

public class SkillInfoDraggableOverlay implements DraggableOverlay {
	@Override
	public String getOverlayName() {
		return "Farming Tracker";
	}

	@Override
	public int getX() {
		Minecraft client = Minecraft.getInstance();
		return OverlayBounds.clampX(client, EmfConfig.HANDLER.instance().skillXpOverlayX, SkillInfoOverlay.getUnscaledWidth(client), scale());
	}

	@Override
	public int getY() {
		Minecraft client = Minecraft.getInstance();
		return OverlayBounds.clampY(client, EmfConfig.HANDLER.instance().skillXpOverlayY, SkillInfoOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public int getWidth() {
		return (int) (SkillInfoOverlay.getUnscaledWidth(Minecraft.getInstance()) * scale());
	}

	@Override
	public int getHeight() {
		return (int) (SkillInfoOverlay.getUnscaledHeight() * scale());
	}

	@Override
	public void setPosition(int x, int y) {
		Minecraft client = Minecraft.getInstance();
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.skillXpOverlayX = OverlayBounds.clampX(client, x, SkillInfoOverlay.getUnscaledWidth(client), scale());
		config.skillXpOverlayY = OverlayBounds.clampY(client, y, SkillInfoOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public void setSize(int width, int height) {
		Minecraft client = Minecraft.getInstance();
		int unscaledWidth = SkillInfoOverlay.getUnscaledWidth(client);
		int unscaledHeight = SkillInfoOverlay.getUnscaledHeight();
		float scale = ((float) width / unscaledWidth + (float) height / unscaledHeight) / 2.0f;
		EmfConfig.HANDLER.instance().skillXpOverlayScale = Math.max(0.1f, Math.min(5.0f, scale));
	}

	@Override
	public void renderInEditMode(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}

		int unscaledWidth = SkillInfoOverlay.getUnscaledWidth(client);
		int unscaledHeight = SkillInfoOverlay.getUnscaledHeight();
		int x = getX();
		int y = getY();
		float scale = scale();

		context.outline(x, y, (int) (unscaledWidth * scale), (int) (unscaledHeight * scale), 0xFFFF0000);

		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		SkillInfoOverlay.renderPreview(context, client);
		matrices.popMatrix();
	}

	@Override
	public void savePosition() {
		EmfConfig.HANDLER.save();
	}

	@Override
	public boolean isEnabled() {
		return isConfigEnabled();
	}

	@Override
	public boolean isConfigEnabled() {
		return EmfConfig.skillXpOverlayVisible();
	}

	@Override
	public Component getTooltip() {
		return Component.literal("Farming Tracker Overlay");
	}

	@Override
	public void resetToDefault() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.skillXpOverlayX = 8;
		config.skillXpOverlayY = 8;
		config.skillXpOverlayScale = 1.0f;
	}

	@Override
	public void resetSizeToDefault() {
		EmfConfig.HANDLER.instance().skillXpOverlayScale = 1.0f;
	}

	private static float scale() {
		float scale = EmfConfig.HANDLER.instance().skillXpOverlayScale;
		return scale <= 0.0f ? 1.0f : scale;
	}
}
