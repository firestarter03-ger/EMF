package net.fire.emf.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fire.emf.client.resource.CollectionScanner;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyMappingReleaseMixin {
	@Inject(method = "releaseAll", at = @At("HEAD"), cancellable = true)
	private static void emf$skipReleaseDuringCollectionScan(CallbackInfo ci) {
		if (CollectionScanner.shouldPreserveInput()) {
			ci.cancel();
		}
	}
}
