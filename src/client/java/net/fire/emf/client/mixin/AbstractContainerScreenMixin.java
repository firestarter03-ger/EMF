package net.fire.emf.client.mixin;

import net.fire.emf.client.overlay.editor.OverlayEditorButtonUtility;
import net.fire.emf.client.overlay.editor.OverlayEditorUtility;
import net.fire.emf.client.session.SessionSummaryOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void emf$renderOverlayEditorButton(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (OverlayEditorButtonUtility.shouldShowButton()) {
			OverlayEditorButtonUtility.renderButton(context, screen, mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void emf$onMouseClicked(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof InventoryScreen
				&& SessionSummaryOverlay.handleClick(event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
			return;
		}
		if (OverlayEditorButtonUtility.handleButtonClick(event.x(), event.y(), event.button())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void emf$sessionScroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof InventoryScreen
				&& SessionSummaryOverlay.handleScroll(mouseX, mouseY, scrollY)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void emf$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (OverlayEditorUtility.handleKeyPress(event.key())) {
			cir.setReturnValue(true);
		}
	}
}
