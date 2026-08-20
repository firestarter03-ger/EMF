package net.fire.emf.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fire.emf.client.resource.CollectionScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void emf$cancelScanOnForeignScreen(Screen screen, CallbackInfo ci) {
		CollectionScanner.onForeignScreenOpened(screen);
	}

	@WrapOperation(
			method = "setScreen",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;releaseMouse()V")
	)
	private void emf$skipMouseReleaseDuringCollectionScan(MouseHandler handler, Operation<Void> original) {
		if (CollectionScanner.shouldPreserveInput()) {
			return;
		}
		original.call(handler);
	}
}
