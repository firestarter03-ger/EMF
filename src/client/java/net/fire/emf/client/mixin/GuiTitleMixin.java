package net.fire.emf.client.mixin;

import net.fire.emf.client.title.TitleAlerts;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiTitleMixin {
	@Inject(method = "setTitle(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void emf$blockResourceBagTitle(Component title, CallbackInfo ci) {
		if (TitleAlerts.shouldBlockTitle(title)) {
			ci.cancel();
		}
	}

	@Inject(method = "setSubtitle(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
	private void emf$blockResourceBagSubtitle(Component subtitle, CallbackInfo ci) {
		if (TitleAlerts.shouldBlockTitle(subtitle)) {
			ci.cancel();
		}
	}
}
