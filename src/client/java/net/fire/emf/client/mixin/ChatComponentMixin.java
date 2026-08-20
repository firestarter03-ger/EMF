package net.fire.emf.client.mixin;

import net.fire.emf.client.title.TitleAlerts;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@Inject(method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("TAIL"))
	private void emf$onServerSystemMessage(Component message, CallbackInfo ci) {
		TitleAlerts.onSystemChat(message);
	}

	@Inject(method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("TAIL"))
	private void emf$onClientSystemMessage(Component message, CallbackInfo ci) {
		TitleAlerts.onSystemChat(message);
	}
}
