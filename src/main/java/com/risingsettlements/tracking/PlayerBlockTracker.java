package com.risingsettlements.tracking;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import com.risingsettlements.world.MultiBlocks;
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

	private static void mark(Settlement settlement, BlockPos pos) {
		settlement.addPlayerBlock(pos);
		// The player replaced whatever the village had here: it is the player's block now.
		settlement.blueprint().remove(pos.asLong());
	}
}
