package net.fire.emf.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fire.emf.client.resource.CollectionScanner;
import net.fire.emf.client.resource.CollectionScannerMovement;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	@WrapOperation(
			method = "aiStep",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V")
	)
	private void emf$movementDuringCollectionScan(ClientInput instance, Operation<Void> original) {
		original.call(instance);
		if (CollectionScanner.shouldAllowMovement()) {
			CollectionScannerMovement.refreshMovementInput(instance);
		}
	}
}
