package com.risingsettlements.world;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central rules for which blocks the mod may record, replace or build on.
 *
 * <p>Together with the player-block tracking these rules implement the mod's most
 * important promise: <b>player builds are never touched</b>. The mod only ever changes a
 * position when it is empty, holds loose natural vegetation, is part of the village's own
 * blueprint, or (for foundations, roads and fields) is natural ground at the surface. It
 * never changes a position where a player placed or broke a block.
 */
public final class BlockRules {
	private BlockRules() {
	}

	/**
	 * Natural ground and plants. These are not recorded as village blocks, because they
	 * change on their own (crops grow, leaves decay, snow melts) and should not be "repaired".
	 */
	public static boolean isNatural(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
			return true;
		}
		return isGround(state)
				|| isClearable(state)
				|| state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.SAPLINGS)
				|| state.is(BlockTags.CROPS)
				|| state.is(BlockTags.FIRE)
				|| state.is(Blocks.SUGAR_CANE)
				|| state.is(Blocks.CACTUS)
				|| state.is(Blocks.PUMPKIN)
				|| state.is(Blocks.MELON)
				|| state.is(Blocks.BAMBOO)
				|| state.is(Blocks.ICE);
	}

	/** Natural ground that new buildings, roads and farms may sit on. */
	public static boolean isGround(BlockState state) {
		return state.is(BlockTags.DIRT)
				|| state.is(BlockTags.GRASS_BLOCKS)
				|| state.is(BlockTags.SAND)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(Blocks.SNOW_BLOCK)
				|| state.is(Blocks.PODZOL)
				|| state.is(Blocks.MYCELIUM)
				|| state.is(Blocks.MUD)
				|| state.is(Blocks.MOSS_BLOCK)
				|| state.is(Blocks.GRAVEL)
				|| state.is(Blocks.CLAY)
				|| state.is(Blocks.SANDSTONE)
				|| state.is(Blocks.TERRACOTTA);
	}

	/**
	 * Empty space or loose natural plants (grass, flowers, snow layers). The mod may clear
	 * these when it builds. Liquids are excluded: building in water is never allowed.
	 */
	public static boolean isClearable(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return state.is(BlockTags.REPLACEABLE)
				|| state.is(BlockTags.SMALL_FLOWERS)
				|| state.is(BlockTags.SAPLINGS)
				|| state.is(Blocks.SWEET_BERRY_BUSH);
	}

	/** Blocks that a village records in its blueprint when it is first found. */
	public static boolean isVillageBlock(BlockState state) {
		return !isNatural(state) && !state.is(Blocks.STRUCTURE_VOID) && !state.is(Blocks.JIGSAW);
	}

	/** Village streets. New houses connect to these, and new streets grow from their ends. */
	public static boolean isRoad(BlockState state) {
		return state.is(Blocks.DIRT_PATH);
	}

	/** Natural soil that farmers may till into farmland. */
	public static boolean isTillable(BlockState state) {
		return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK);
	}

	/**
	 * Village blocks that wear down on their own: farmland trampled into dirt by mobs, paths
	 * turned to dirt by something placed on top, field water frozen into ice. Villagers
	 * restore them like missing blocks.
	 */
	public static boolean isWornDown(BlockState current, BlockState wanted) {
		if (current.is(Blocks.DIRT)) {
			return wanted.is(Blocks.FARMLAND) || wanted.is(Blocks.DIRT_PATH);
		}
		return current.is(Blocks.ICE) && wanted.is(Blocks.WATER);
	}
}
