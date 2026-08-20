package net.fire.emf.client.mixin;

import net.fire.emf.client.overlay.editor.OverlayEditorUtility;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void emf$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (OverlayEditorUtility.handleKeyPress(event.key())) {
			cir.setReturnValue(true);
		}
	}
}
