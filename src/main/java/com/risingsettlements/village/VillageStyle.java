package com.risingsettlements.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

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
