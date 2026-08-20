package net.fire.emf.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fire.emf.client.resource.CollectionScanner;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@WrapOperation(
			method = "onButton",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"
			)
	)
	private boolean emf$swallowClickDuringScan(Screen screen, MouseButtonEvent event, boolean doubled, Operation<Boolean> original) {
		if (CollectionScanner.onInputWhileScanning()) {
			return true;
		}
		return original.call(screen, event, doubled);
	}

	@WrapOperation(
			method = "onButton",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"
			)
	)
	private boolean emf$swallowReleaseDuringScan(Screen screen, MouseButtonEvent event, Operation<Boolean> original) {
		if (CollectionScanner.onInputWhileScanning()) {
			return true;
		}
		return original.call(screen, event);
	}
}
