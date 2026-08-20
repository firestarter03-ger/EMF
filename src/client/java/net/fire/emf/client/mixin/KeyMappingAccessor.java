package net.fire.emf.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("key")
	InputConstants.Key emf$getKey();
}
