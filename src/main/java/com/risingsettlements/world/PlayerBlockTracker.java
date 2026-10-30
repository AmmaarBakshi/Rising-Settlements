package com.risingsettlements.world;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * Remembers every position a player has placed or broken a block at near a village.
 *
 * <p>These positions are off limits for the mod forever: it never repairs, replaces or
 * builds over them. Breaking counts too, so a player who removes a village wall on
 * purpose (to make a doorway, say) does not see villagers put it back, and a hole dug for
 * a basement is never filled in by a new house's foundation.
 */
public final class PlayerBlockTracker {
	private PlayerBlockTracker() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				onPlayerChanged(serverLevel, pos, state);
			}
		});
	}

	/** Called by {@code BlockItemMixin} after a player successfully places a block. */
	public static void onPlayerPlaced(ServerLevel level, BlockPos pos) {
		onPlayerChanged(level, pos, level.getBlockState(pos));
	}

	private static void onPlayerChanged(ServerLevel level, BlockPos pos, BlockState state) {
		List<Settlement> nearby = SettlementManager.get(level).findAround(pos, RSConfig.get().maxVillageRadius);
		if (nearby.isEmpty()) {
			return;
		}
		BlockPos other = otherHalf(pos, state);
		for (Settlement settlement : nearby) {
			mark(settlement, pos);
			if (other != null) {
				mark(settlement, other);
			}
		}
	}

	private static void mark(Settlement settlement, BlockPos pos) {
		settlement.addPlayerBlock(pos);
		settlement.blueprint().remove(pos.asLong());
	}

	/** Doors, tall plants and beds fill two positions; both belong to the player. */
	private static BlockPos otherHalf(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		}
		if (state.getBlock() instanceof BedBlock) {
			Direction facing = state.getValue(BedBlock.FACING);
			return state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
		}
		return null;
	}
}
