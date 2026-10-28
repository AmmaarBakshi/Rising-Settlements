package com.risingsettlements.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Helpers for blocks that take up two positions: doors and tall plants (upper and lower
 * half) and beds (head and foot). Both halves must always be handled together.
 */
public final class MultiBlocks {
	private MultiBlocks() {
	}

	/** The position of the other half of a two-block block, or null for normal blocks. */
	public static @Nullable BlockPos otherPart(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		}
		if (state.getBlock() instanceof AbstractBedBlock) {
			return pos.relative(AbstractBedBlock.getConnectedDirection(state));
		}
		return null;
	}

	/** True for the half that should be placed first (lower door half, bed foot). */
	public static boolean isPrimaryPart(BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
		}
		if (state.hasProperty(BlockStateProperties.BED_PART)) {
			return state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT;
		}
		return true;
	}
}
