package com.risingsettlements.tracking;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import com.risingsettlements.world.MultiBlocks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Remembers which blocks players place near villages, so the mod never touches them.
 *
 * <p>Placements are tracked in the whole area a village could ever grow into (its
 * maximum radius around the bell), not just its current border. That way a village that
 * grows later still knows about builds that were there first.
 */
public final class PlayerBlockTracker {
	private PlayerBlockTracker() {
	}

	/** Called (from a mixin) after a player successfully places a block. */
	public static void onPlayerPlaced(ServerLevel level, BlockPos pos, BlockState state) {
		int radius = RSConfig.get().maxVillageRadius;
		for (Settlement settlement : SettlementManager.get(level).findAround(pos, radius)) {
			mark(settlement, pos);
			BlockPos other = MultiBlocks.otherPart(pos, state);
			if (other != null) {
				mark(settlement, other);
			}
		}
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				onPlayerBroke(serverLevel, pos, state);
			}
		});
	}

	/**
	 * A player broke a block. If it was a village block, the player chose to remove it, so
	 * the village will not rebuild it. If it was the player's own block, the spot is free again.
	 */
	public static void onPlayerBroke(ServerLevel level, BlockPos pos, BlockState state) {
		int radius = RSConfig.get().maxVillageRadius;
		for (Settlement settlement : SettlementManager.get(level).findAround(pos, radius)) {
			forget(settlement, pos);
			BlockPos other = MultiBlocks.otherPart(pos, state);
			if (other != null) {
				forget(settlement, other);
			}
		}
	}

	private static void forget(Settlement settlement, BlockPos pos) {
		settlement.blueprint().remove(pos.asLong());
		settlement.removePlayerBlock(pos);
		settlement.markDirty();
	}

	private static void mark(Settlement settlement, BlockPos pos) {
		settlement.addPlayerBlock(pos);
		// The player replaced whatever the village had here: it is the player's block now.
		settlement.blueprint().remove(pos.asLong());
	}
}
