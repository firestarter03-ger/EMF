package net.fire.emf.client.overlay.editor;

import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.function.OffhandSwapper;
import net.fire.emf.client.overlay.AutominerCooldownOverlay;
import net.fire.emf.client.overlay.OverlayBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

public class AutominerCooldownDraggableOverlay implements DraggableOverlay {
	@Override
	public String getOverlayName() {
		return "Autominer Cooldown";
	}

	@Override
	public int getX() {
		Minecraft client = Minecraft.getInstance();
		return OverlayBounds.clampX(client, EmfConfig.HANDLER.instance().autominerCooldownOverlayX,
				AutominerCooldownOverlay.getUnscaledWidth(client), scale());
	}

	@Override
	public int getY() {
		Minecraft client = Minecraft.getInstance();
		return OverlayBounds.clampY(client, EmfConfig.HANDLER.instance().autominerCooldownOverlayY,
				AutominerCooldownOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public int getWidth() {
		return (int) (AutominerCooldownOverlay.getUnscaledWidth(Minecraft.getInstance()) * scale());
	}

	@Override
	public int getHeight() {
		return (int) (AutominerCooldownOverlay.getUnscaledHeight() * scale());
	}

	@Override
	public void setPosition(int x, int y) {
		Minecraft client = Minecraft.getInstance();
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.autominerCooldownOverlayX = OverlayBounds.clampX(client, x,
				AutominerCooldownOverlay.getUnscaledWidth(client), scale());
		config.autominerCooldownOverlayY = OverlayBounds.clampY(client, y,
				AutominerCooldownOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public void setSize(int width, int height) {
		Minecraft client = Minecraft.getInstance();
		int unscaledWidth = AutominerCooldownOverlay.getUnscaledWidth(client);
		int unscaledHeight = AutominerCooldownOverlay.getUnscaledHeight();
		float scale = ((float) width / unscaledWidth + (float) height / unscaledHeight) / 2.0f;
		EmfConfig.HANDLER.instance().autominerCooldownOverlayScale = Math.max(0.1f, Math.min(5.0f, scale));
	}

	@Override
	public void renderInEditMode(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}

		int unscaledWidth = AutominerCooldownOverlay.getUnscaledWidth(client);
		int unscaledHeight = AutominerCooldownOverlay.getUnscaledHeight();
		int x = getX();
		int y = getY();
		float scale = scale();

		context.outline(x, y, (int) (unscaledWidth * scale), (int) (unscaledHeight * scale), 0xFFFF0000);

		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		AutominerCooldownOverlay.renderPreview(context, client);
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
		return OffhandSwapper.isDetectionEnabled();
	}

	@Override
	public Component getTooltip() {
		return Component.literal("Autominer Cooldown Overlay");
	}

	@Override
	public void resetToDefault() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.autominerCooldownOverlayX = 8;
		config.autominerCooldownOverlayY = 194;
		config.autominerCooldownOverlayScale = 1.0f;
	}

	@Override
	public void resetSizeToDefault() {
		EmfConfig.HANDLER.instance().autominerCooldownOverlayScale = 1.0f;
	}

	private static float scale() {
		float scale = EmfConfig.HANDLER.instance().autominerCooldownOverlayScale;
		return scale <= 0.0f ? 1.0f : scale;
	}
}
