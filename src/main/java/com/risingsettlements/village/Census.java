package com.risingsettlements.village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Counts the villagers and beds of a settlement. Growth uses the numbers to decide what
 * the village needs (a full village wants houses, a hungry one wants farms).
 *
 * <p>Only loaded chunks are looked at, so the census never loads chunks by itself.
 */
public final class Census {
	private Census() {
	}

	public static void run(ServerLevel level, Settlement settlement) {
		BoundingBox bounds = settlement.bounds();
		List<Villager> villagers = villagers(level, settlement);
		int beds = 0;
		PoiManager poi = level.getPoiManager();
		for (ChunkPos chunk : bounds.intersectingChunks().toList()) {
			if (!level.hasChunk(chunk.x(), chunk.z())) {
				continue;
			}
			beds += (int) poi.getInChunk(type -> type.is(PoiTypes.HOME), chunk, PoiManager.Occupancy.ANY)
					.filter(record -> bounds.isInside(record.getPos()))
					.count();
		}
		settlement.setCensus(villagers.size(), beds);
		WorkerVillagers.tidyTools(settlement, villagers);
	}

	/** All loaded villagers inside the settlement's border. */
	public static List<Villager> villagers(ServerLevel level, Settlement settlement) {
		return level.getEntitiesOfClass(Villager.class, AABB.of(settlement.bounds()), Villager::isAlive);
	}
}
