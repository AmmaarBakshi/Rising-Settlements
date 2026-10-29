package com.risingsettlements.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Reads the shape of the land. Every method here checks that chunks are loaded first,
 * because reading an unloaded chunk would make the server load (or even generate) it.
 */
public final class Terrain {
	/** Returned by {@link #surface} when the column's chunk isn't loaded. */
	public static final int UNKNOWN = Integer.MIN_VALUE;

	private Terrain() {
	}

	/**
	 * The height of the top block of a column that you could stand on, skipping plants,
	 * snow layers and leaves. This is the block that roads replace and buildings sit on.
	 */
	public static int surface(ServerLevel level, int x, int z) {
		if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			return UNKNOWN;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		int minY = level.getMinY();
		while (y > minY && BlockRules.isClearable(level.getBlockState(pos.setY(y)))) {
			y--;
		}
		return y;
	}

	/** True if every chunk touched by the box (looking only at x and z) is loaded. */
	public static boolean isLoaded(ServerLevel level, BoundingBox box) {
		int minCx = SectionPos.blockToSectionCoord(box.minX());
		int maxCx = SectionPos.blockToSectionCoord(box.maxX());
		int minCz = SectionPos.blockToSectionCoord(box.minZ());
		int maxCz = SectionPos.blockToSectionCoord(box.maxZ());
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}
}
