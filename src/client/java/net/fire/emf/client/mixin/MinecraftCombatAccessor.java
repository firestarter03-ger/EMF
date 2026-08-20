package net.fire.emf.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftCombatAccessor {
	@Invoker("continueAttack")
	void emf$continueAttack(boolean leftClick);

	@Invoker("startAttack")
	boolean emf$startAttack();

	@Invoker("startUseItem")
	void emf$startUseItem();
}
