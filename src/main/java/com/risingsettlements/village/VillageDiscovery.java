package com.risingsettlements.village;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.config.RSConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds villages near players.
 *
 * <p>Every village has a bell at its meeting point, and the game already keeps track of
 * bells as "meeting" points of interest. Every few seconds this looks through the points
 * of interest of the <b>loaded</b> chunks around each player. A bell that belongs to a
 * generated village structure, and is not part of a known settlement yet, becomes a new
 * settlement.
 */
public final class VillageDiscovery {
	private VillageDiscovery() {
	}

	public static void tick(ServerLevel level, SettlementManager manager) {
		RSConfig config = RSConfig.get();
		if (level.getGameTime() % config.discoveryIntervalTicks != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				scanAround(level, manager, player.blockPosition(), config.discoveryRadius);
			}
		}
	}

	/**
	 * Looks for new villages around a position.
	 *
	 * @return the settlements that were created
	 */
	public static List<Settlement> scanAround(ServerLevel level, SettlementManager manager, BlockPos center, int radius) {
		PoiManager poi = level.getPoiManager();
		ChunkPos centerChunk = ChunkPos.containing(center);
		int chunkRadius = Math.floorDiv(radius, 16) + 1;
		List<Settlement> created = new ArrayList<>();

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				int cx = centerChunk.x() + dx;
				int cz = centerChunk.z() + dz;
				// Only read chunks that are already loaded; never load chunks just to look.
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				List<BlockPos> bells = poi.getInChunk(type -> type.is(PoiTypes.MEETING), new ChunkPos(cx, cz), PoiManager.Occupancy.ANY)
						.map(PoiRecord::getPos)
						.toList();
				for (BlockPos bell : bells) {
					Settlement settlement = tryCreate(level, manager, bell);
					if (settlement != null) {
						created.add(settlement);
					}
				}
			}
		}
		return created;
	}

	private static @Nullable Settlement tryCreate(ServerLevel level, SettlementManager manager, BlockPos bell) {
		if (manager.findContaining(bell) != null || !level.getBlockState(bell).is(Blocks.BELL)) {
			return null;
		}
		// Only bells inside a generated village count. A bell a player placed somewhere else does not.
		StructureStart start = level.structureManager().getStructureWithPieceAt(bell, StructureTags.VILLAGE);
		if (!start.isValid()) {
			return null;
		}

		RSConfig config = RSConfig.get();
		BoundingBox bounds = clampToRadius(start.getBoundingBox(), bell, config);
		VillageStyle style = VillageStyle.detect(level, start, bell);
		String name = SettlementNames.generate(level.getRandom());

		Settlement settlement = Settlement.create(name, bell, bounds, style, level.getGameTime());
		manager.add(settlement);
		RisingSettlements.LOGGER.info("Found village {} ({}) at {}", name, style.id(), bell.toShortString());
		return settlement;
	}

	/** Village border: the structure plus a margin, but never further than the max radius from the bell. */
	private static BoundingBox clampToRadius(BoundingBox structure, BlockPos bell, RSConfig config) {
		int margin = config.villageMargin;
		int r = config.maxVillageRadius;
		return new BoundingBox(
				Math.max(structure.minX() - margin, bell.getX() - r),
				structure.minY() - 4,
				Math.max(structure.minZ() - margin, bell.getZ() - r),
				Math.min(structure.maxX() + margin, bell.getX() + r),
				structure.maxY() + 8,
				Math.min(structure.maxZ() + margin, bell.getZ() + r));
	}
}
