package com.risingsettlements.build;

import com.risingsettlements.village.VillageStyle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Building materials for each village style, so new houses and fields look like they
 * belong next to the original ones.
 *
 * @param corner     corner posts of houses (placed upright if it is a log)
 * @param wall       house walls and gable ends
 * @param foundation the bottom ring of a house and anything needed to level the ground
 * @param floor      the floor inside a house
 * @param stairs     pitched roofs
 * @param slab       the ridge of a pitched roof
 * @param flatRoof   when set, houses get a flat roof of this block instead of a pitched one
 * @param door       front doors
 * @param window     windows
 * @param bed        the bed inside each new house, which gives the village room for a new villager
 * @param fieldEdge  the border around a new field
 */
public record StylePalette(Block corner, Block wall, Block foundation, Block floor, Block stairs, Block slab,
						   Block flatRoof, Block door, Block window, Block bed, Block fieldEdge) {
	/** Job site blocks. Each new house gets one, so the villager who moves in can find work. */
	public static final List<Block> WORKSTATIONS = List.of(
			Blocks.COMPOSTER, Blocks.BARREL, Blocks.SMOKER, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE,
			Blocks.FLETCHING_TABLE, Blocks.LECTERN, Blocks.SMITHING_TABLE, Blocks.BLAST_FURNACE,
			Blocks.STONECUTTER, Blocks.GRINDSTONE);

	/** Crops for new fields. */
	public static final List<Block> CROPS = List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS);

	public static StylePalette of(VillageStyle style) {
		return switch (style) {
			case PLAINS -> new StylePalette(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OAK_PLANKS,
					Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.AIR, Blocks.OAK_DOOR, Blocks.GLASS_PANE,
					Blocks.BED.white(), Blocks.OAK_LOG);
			case DESERT -> new StylePalette(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
					Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB,
					Blocks.SMOOTH_SANDSTONE, Blocks.JUNGLE_DOOR, Blocks.GLASS_PANE, Blocks.BED.yellow(), Blocks.JUNGLE_LOG);
			case SAVANNA -> new StylePalette(Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.COBBLESTONE,
					Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.AIR, Blocks.ACACIA_DOOR,
					Blocks.GLASS_PANE, Blocks.BED.orange(), Blocks.ACACIA_LOG);
			case TAIGA -> new StylePalette(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.MOSSY_COBBLESTONE,
					Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.AIR, Blocks.SPRUCE_DOOR,
					Blocks.GLASS_PANE, Blocks.BED.brown(), Blocks.SPRUCE_LOG);
			case SNOWY -> new StylePalette(Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS,
					Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.AIR, Blocks.SPRUCE_DOOR,
					Blocks.GLASS_PANE, Blocks.BED.lightBlue(), Blocks.STRIPPED_SPRUCE_LOG);
		};
	}

	public boolean hasFlatRoof() {
		return flatRoof != Blocks.AIR;
	}
}
