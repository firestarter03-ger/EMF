package net.fire.emf.client.mixin;

import net.fire.emf.client.overlay.SkillOverlayTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class ActionBarMixin {
	@Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("TAIL"))
	private void emf$onSetOverlayMessage(Component message, boolean animate, CallbackInfo ci) {
		if (message != null) {
			String text = message.getString();
			SkillOverlayTracker.onActionBar(text);
			net.fire.emf.client.session.SessionTracker.onActionBar(text);
		}
	}
}
