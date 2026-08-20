package net.fire.emf.client.mixin;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.Gui;

@Mixin(Gui.class)
public interface GuiAccessor {
	@Accessor("title")
	Component emf$getTitle();

	@Accessor("subtitle")
	Component emf$getSubtitle();

	@Accessor("overlayMessageString")
	Component emf$getOverlayMessage();

	@Accessor("overlayMessageTime")
	int emf$getOverlayMessageTime();
}
