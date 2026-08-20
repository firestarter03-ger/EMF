package net.fire.emf.client.mixin;

import net.fire.emf.client.overlay.editor.OverlayEditorUtility;
import net.fire.emf.client.resource.CollectionScanner;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void emf$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (OverlayEditorUtility.handleKeyPress(event.key())) {
			cir.setReturnValue(true);
			return;
		}
		if (CollectionScanner.onInputWhileScanning()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void emf$hideCollectionScan(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (CollectionScanner.shouldHideScreen((Screen) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void emf$hideCollectionScanBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (CollectionScanner.shouldHideScreen((Screen) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
	private void emf$hideCollectionScanBlur(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (CollectionScanner.shouldHideScreen((Screen) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true)
	private void emf$noPauseDuringCollectionScan(CallbackInfoReturnable<Boolean> cir) {
		if (CollectionScanner.shouldHideScreen((Screen) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
