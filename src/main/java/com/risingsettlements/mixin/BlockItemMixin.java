package com.risingsettlements.mixin;

import com.risingsettlements.world.PlayerBlockTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells {@link PlayerBlockTracker} about every block a player places. Fabric API has a
 * break event but no place event, so this hooks the end of {@link BlockItem#place}.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
	private void risingSettlements$trackPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() != null && context.getLevel() instanceof ServerLevel level && cir.getReturnValue().consumesAction()) {
			PlayerBlockTracker.onPlayerPlaced(level, context.getClickedPos());
		}
	}
}
