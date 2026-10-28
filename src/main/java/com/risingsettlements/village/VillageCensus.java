package com.risingsettlements.village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Counts the villagers and beds of a settlement. Growth uses these numbers: a village only
 * builds a new house when its villagers are running out of beds.
 */
public final class VillageCensus {
	private VillageCensus() {
	}

	public static void run(ServerLevel level, Settlement settlement) {
		BoundingBox bounds = settlement.bounds();
		int villagers = level.getEntitiesOfClass(Villager.class, AABB.of(bounds), Villager::isAlive).size();
		settlement.setCensus(villagers, countBeds(level, bounds));
	}

	/** Beds are "home" points of interest. Only loaded chunks are counted. */
	private static int countBeds(ServerLevel level, BoundingBox bounds) {
		PoiManager poi = level.getPoiManager();
		int beds = 0;
		for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
			for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
				if (level.hasChunk(cx, cz)) {
					beds += (int) poi.getInChunk(type -> type.is(PoiTypes.HOME), new ChunkPos(cx, cz), PoiManager.Occupancy.ANY)
							.filter(record -> bounds.isInside(record.getPos()))
							.count();
				}
			}
		}
		return beds;
	}
}
