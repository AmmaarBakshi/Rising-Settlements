package com.risingsettlements.work;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One block change a villager will make.
 *
 * @param pos   where
 * @param state the block to put there
 * @param mode  what may be there before (see {@link Mode})
 */
public record Placement(BlockPos pos, BlockState state, Mode mode) {
	public Placement {
		pos = pos.immutable();
	}

	public enum Mode {
		/** Only into empty space or loose plants. Used for repairs and buildings. */
		FILL,
		/** May replace natural ground, e.g. grass becoming a path or farmland. */
		REPLACE_GROUND,
		/** Removes loose plants (grass, flowers, snow layers) so a space is clear. */
		CLEAR
	}
}
