package net.fire.emf.client.session;

import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.overlay.OverlayBounds;
import net.fire.emf.client.overlay.editor.DraggableOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

public class SessionSummaryDraggableOverlay implements DraggableOverlay {
	@Override
	public String getOverlayName() {
		return "Session Zusammenfassung";
	}

	@Override
	public int getX() {
		Minecraft client = Minecraft.getInstance();
		int configured = EmfConfig.HANDLER.instance().sessionSummaryOverlayX;
		int x = configured < 0 ? 8 : configured;
		return OverlayBounds.clampX(client, x, SessionSummaryOverlay.getUnscaledWidth(), scale());
	}

	@Override
	public int getY() {
		Minecraft client = Minecraft.getInstance();
		int configured = EmfConfig.HANDLER.instance().sessionSummaryOverlayY;
		int y = configured < 0 ? 8 : configured;
		return OverlayBounds.clampY(client, y, SessionSummaryOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public int getWidth() {
		return (int) (SessionSummaryOverlay.getUnscaledWidth() * scale());
	}

	@Override
	public int getHeight() {
		return (int) (SessionSummaryOverlay.getUnscaledHeight() * scale());
	}

	@Override
	public void setPosition(int x, int y) {
		Minecraft client = Minecraft.getInstance();
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.sessionSummaryOverlayX = OverlayBounds.clampX(client, x, SessionSummaryOverlay.getUnscaledWidth(), scale());
		config.sessionSummaryOverlayY = OverlayBounds.clampY(client, y, SessionSummaryOverlay.getUnscaledHeight(), scale());
	}

	@Override
	public void setSize(int width, int height) {
		Minecraft client = Minecraft.getInstance();
		int unscaledWidth = SessionSummaryOverlay.getUnscaledWidth();
		int unscaledHeight = SessionSummaryOverlay.getUnscaledHeight();
		float scale = ((float) width / unscaledWidth + (float) height / unscaledHeight) / 2.0f;
		EmfConfig.HANDLER.instance().sessionSummaryOverlayScale = Math.max(0.1f, Math.min(5.0f, scale));
	}

	@Override
	public void renderInEditMode(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		int x = getX();
		int y = getY();
		float scale = scale();
		int unscaledWidth = SessionSummaryOverlay.getUnscaledWidth();
		int unscaledHeight = SessionSummaryOverlay.getUnscaledHeight();
		context.outline(x, y, (int) (unscaledWidth * scale), (int) (unscaledHeight * scale), 0xFFFF0000);
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		SessionSummaryOverlay.renderPreview(context, client);
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
		return EmfConfig.sessionSummaryOverlayVisible();
	}

	@Override
	public Component getTooltip() {
		return Component.literal("Session Zusammenfassung (nur im Spielerinventar)");
	}

	@Override
	public void resetToDefault() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		config.sessionSummaryOverlayX = -1;
		config.sessionSummaryOverlayY = -1;
		config.sessionSummaryOverlayScale = 1.0f;
	}

	@Override
	public void resetSizeToDefault() {
		EmfConfig.HANDLER.instance().sessionSummaryOverlayScale = 1.0f;
	}

	private static float scale() {
		float scale = EmfConfig.HANDLER.instance().sessionSummaryOverlayScale;
		return scale <= 0.0f ? 1.0f : scale;
	}
}
