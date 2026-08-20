package net.fire.emf.client.mixin;

import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.controllers.dropdown.DropdownWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DropdownWidget.class)
public class YaclDropdownWidgetMixin {
	@Shadow
	protected Dimension<Integer> dropdownDim;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void emf$opaqueDropdownBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (dropdownDim == null) {
			return;
		}
		graphics.fill(dropdownDim.x(), dropdownDim.y(), dropdownDim.xLimit(), dropdownDim.yLimit(), 0xFF1A1A1A);
	}
}
