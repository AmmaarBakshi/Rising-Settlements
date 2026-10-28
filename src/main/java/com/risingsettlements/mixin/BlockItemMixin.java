package com.risingsettlements.mixin;

import com.risingsettlements.tracking.PlayerBlockTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports every block a player places with a block item (including doors, beds, slabs and
 * so on), so the mod can protect it. Fabric API has no "block placed" event, so this hooks
 * {@code BlockItem.placeBlock}, which all block items go through.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void risingSettlements$trackPlacement(BlockPlaceContext context, BlockState placementState,
												  CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getLevel() instanceof ServerLevel level
				&& context.getPlayer() instanceof ServerPlayer) {
			PlayerBlockTracker.onPlayerPlaced(level, context.getClickedPos(), placementState);
		}
	}
}
