package net.fire.emf.client.mixin;

import net.fire.emf.client.overlay.SkillFruitTracker;
import net.fire.emf.client.overlay.SkillOverlayTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Unique
	private BlockState emf$pendingBrokenState;

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void emf$captureBrokenBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		emf$pendingBrokenState = level == null ? null : level.getBlockState(pos);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void emf$onBlockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && emf$pendingBrokenState != null) {
			SkillOverlayTracker.onBlockBroken(emf$pendingBrokenState);
		}
		emf$pendingBrokenState = null;
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void emf$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		SkillFruitTracker.onUse(player, hand);
	}
}
