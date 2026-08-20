package net.fire.emf.client.overlay.editor;

import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.LevelInfoOverlay;
import net.fire.emf.client.overlay.OverlayBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

public class LevelInfoDraggableOverlay implements DraggableOverlay {
	@Override
	public String getOverlayName() {
		return "Level Tracker";
	}

	@Override
	public int getX() {
		Minecraft client = Minecraft.getInstance();
		return OverlayBounds.clampX(client, EmfConfig.HANDLER.instance().levelTrackerOverlayX, LevelInfoOverlay.getUnscaledWidth(client), scale());
	}

	@Override
	public int getY() {
		Minecraft client = Minecraft.getInstance();
		return OverlayBounds.clampY(client, EmfConfig.HANDLER.instance().levelTrackerOverlayY, LevelInfoOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public int getWidth() {
		return (int) (LevelInfoOverlay.getUnscaledWidth(Minecraft.getInstance()) * scale());
	}

	@Override
	public int getHeight() {
		return (int) (LevelInfoOverlay.getUnscaledHeight() * scale());
	}

	@Override
	public void setPosition(int x, int y) {
		Minecraft client = Minecraft.getInstance();
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.levelTrackerOverlayX = OverlayBounds.clampX(client, x, LevelInfoOverlay.getUnscaledWidth(client), scale());
		config.levelTrackerOverlayY = OverlayBounds.clampY(client, y, LevelInfoOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public void setSize(int width, int height) {
		Minecraft client = Minecraft.getInstance();
		int unscaledWidth = LevelInfoOverlay.getUnscaledWidth(client);
		int unscaledHeight = LevelInfoOverlay.getUnscaledHeight();
		float scale = ((float) width / unscaledWidth + (float) height / unscaledHeight) / 2.0f;
		EmfConfig.HANDLER.instance().levelTrackerOverlayScale = Math.max(0.1f, Math.min(5.0f, scale));
	}

	@Override
	public void renderInEditMode(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}

		int unscaledWidth = LevelInfoOverlay.getUnscaledWidth(client);
		int unscaledHeight = LevelInfoOverlay.getUnscaledHeight();
		int x = getX();
		int y = getY();
		float scale = scale();

		context.outline(x, y, (int) (unscaledWidth * scale), (int) (unscaledHeight * scale), 0xFFFF0000);

		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		LevelInfoOverlay.renderPreview(context, client);
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
		return EmfConfig.levelTrackerOverlayVisible();
	}

	@Override
	public Component getTooltip() {
		return Component.literal("Level Tracker Overlay");
	}

	@Override
	public void resetToDefault() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.levelTrackerOverlayX = 8;
		config.levelTrackerOverlayY = 70;
		config.levelTrackerOverlayScale = 1.0f;
	}

	@Override
	public void resetSizeToDefault() {
		EmfConfig.HANDLER.instance().levelTrackerOverlayScale = 1.0f;
	}

	private static float scale() {
		float scale = EmfConfig.HANDLER.instance().levelTrackerOverlayScale;
		return scale <= 0.0f ? 1.0f : scale;
	}
}
