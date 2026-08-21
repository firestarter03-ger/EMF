package net.fire.emf.client.mixin;

import net.fire.emf.client.session.SessionSummaryOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class InventoryScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void emf$renderSessionSummary(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SessionSummaryOverlay.renderInInventory(context, Minecraft.getInstance(), (InventoryScreen) (Object) this, mouseX, mouseY);
	}
}
