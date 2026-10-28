package com.risingsettlements.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The building style of a village. New buildings, roads and farms use the materials of
 * the village's style so they blend in with the original houses.
 */
public enum VillageStyle {
	PLAINS,
	DESERT,
	SAVANNA,
	TAIGA,
	SNOWY;

	/** Lower-case name used in save files, template paths and the config. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------ materials

	/** Road surface. Desert villages pave with smooth sandstone, everyone else uses dirt paths. */
	public BlockState road() {
		return this == DESERT ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
	}

	/** True for blocks that count as a road in this style (used to connect new roads to old ones). */
	public boolean isRoad(BlockState state) {
		return state.is(Blocks.DIRT_PATH) || state.is(road().getBlock());
	}

	/** Border of new farm fields. */
	public BlockState farmBorder(Direction.Axis axis) {
		Block log = switch (this) {
			case PLAINS -> Blocks.OAK_LOG;
			case DESERT -> Blocks.CUT_SANDSTONE;
			case SAVANNA -> Blocks.ACACIA_LOG;
			case TAIGA, SNOWY -> Blocks.SPRUCE_LOG;
		};
		BlockState state = log.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
	}

	public BlockState fence() {
		return switch (this) {
			case PLAINS -> Blocks.OAK_FENCE.defaultBlockState();
			case DESERT -> Blocks.BIRCH_FENCE.defaultBlockState();
			case SAVANNA -> Blocks.ACACIA_FENCE.defaultBlockState();
			case TAIGA, SNOWY -> Blocks.SPRUCE_FENCE.defaultBlockState();
		};
	}

	/** Material of the village wall project. */
	public BlockState wall() {
		return switch (this) {
			case PLAINS -> Blocks.COBBLESTONE_WALL.defaultBlockState();
			case DESERT -> Blocks.SANDSTONE_WALL.defaultBlockState();
			case SAVANNA -> Blocks.ACACIA_FENCE.defaultBlockState();
			case TAIGA -> Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState();
			case SNOWY -> Blocks.STONE_BRICK_WALL.defaultBlockState();
		};
	}

	/** Crops planted in new fields, matching what each biome's villages usually grow. */
	public List<Block> crops() {
		return switch (this) {
			case PLAINS -> List.of(Blocks.WHEAT, Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES);
			case DESERT, SAVANNA -> List.of(Blocks.WHEAT);
			case TAIGA -> List.of(Blocks.POTATOES, Blocks.CARROTS, Blocks.WHEAT);
			case SNOWY -> List.of(Blocks.BEETROOTS, Blocks.POTATOES);
		};
	}

	public static VillageStyle byId(String id) {
		for (VillageStyle style : values()) {
			if (style.id().equals(id)) {
				return style;
			}
		}
		return PLAINS;
	}

	/**
	 * Works out the style of a village. The vanilla village structure type is the most
	 * reliable signal ({@code minecraft:village_desert} and so on). Villages added by other
	 * mods or data packs fall back to the biome at the bell.
	 */
	public static VillageStyle detect(ServerLevel level, StructureStart start, BlockPos bell) {
		Identifier structureId = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
		VillageStyle fromStructure = fromStructureId(structureId);
		return fromStructure != null ? fromStructure : fromBiome(level.getBiome(bell));
	}

	static @Nullable VillageStyle fromStructureId(@Nullable Identifier id) {
		if (id == null) {
			return null;
		}
		String path = id.getPath();
		for (VillageStyle style : values()) {
			if (path.equals("village_" + style.id())) {
				return style;
			}
		}
		return null;
	}

	static VillageStyle fromBiome(Holder<Biome> biome) {
		if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
			return DESERT;
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
			return SAVANNA;
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
			return SNOWY;
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
			return TAIGA;
		}
		return PLAINS;
	}
}
