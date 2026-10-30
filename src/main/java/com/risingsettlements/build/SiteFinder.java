package com.risingsettlements.build;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Looks at the terrain to decide whether something can be built on a spot.
 *
 * <p>A good site is natural ground, fairly flat, inside the village's maximum radius, has
 * nothing but air and loose plants above it, and contains no village blocks and no
 * positions a player has touched. A one-block ring around it is checked too, so new
 * buildings never touch trees, other buildings or player builds.
 */
public final class SiteFinder {
	/**
	 * A spot that passed the checks.
	 *
	 * @param minGround height of the lowest ground block inside the footprint
	 * @param maxGround height of the highest ground block inside the footprint
	 */
	public record Site(int minX, int minZ, int sizeX, int sizeZ, int minGround, int maxGround) {
	}

	private SiteFinder() {
	}

	/** Height of the top solid block in a column, ignoring leaves and loose plants. */
	public static int groundY(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	public static boolean isLoaded(ServerLevel level, int x, int z) {
		return level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
	}

	/**
	 * Checks a rectangular footprint.
	 *
	 * @param maxStep     largest height difference allowed between ground blocks in the footprint
	 * @param clearHeight how many blocks above the highest ground must be free
	 */
	public static @Nullable Site check(ServerLevel level, Settlement settlement, int minX, int minZ, int sizeX, int sizeZ,
									   int maxStep, int clearHeight) {
		int radius = RSConfig.get().maxVillageRadius - 1;
		if (!settlement.isWithin(new BlockPos(minX - 1, 0, minZ - 1), radius)
				|| !settlement.isWithin(new BlockPos(minX + sizeX, 0, minZ + sizeZ), radius)) {
			return null;
		}

		int width = sizeX + 2;
		int depth = sizeZ + 2;
		int[] ground = new int[width * depth];
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				int x = minX - 1 + dx;
				int z = minZ - 1 + dz;
				if (!isLoaded(level, x, z)) {
					return null;
				}
				int gy = groundY(level, x, z);
				pos.set(x, gy, z);
				BlockState state = level.getBlockState(pos);
				boolean ring = dx == 0 || dz == 0 || dx == width - 1 || dz == depth - 1;
				if (ring && BlockRules.isRoad(state)) {
					ground[dx * depth + dz] = gy; // Streets may run right past the site.
					continue;
				}
				if (!BlockRules.isGround(state) || settlement.blueprint().contains(pos) || settlement.isPlayerBlock(pos)) {
					return null;
				}
				ground[dx * depth + dz] = gy;
				if (!ring) {
					min = Math.min(min, gy);
					max = Math.max(max, gy);
				}
			}
		}
		if (max - min > maxStep) {
			return null;
		}

		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < depth; dz++) {
				int x = minX - 1 + dx;
				int z = minZ - 1 + dz;
				int columnGround = ground[dx * depth + dz];
				for (int y = min - 1; y <= max + clearHeight; y++) {
					pos.set(x, y, z);
					long packed = pos.asLong();
					if (y < columnGround) {
						if (settlement.blueprint().contains(packed) || settlement.isPlayerBlock(packed)) {
							return null;
						}
					} else if (y > columnGround) {
						if (settlement.blueprint().contains(packed) || settlement.isPlayerBlock(packed)
								|| !BlockRules.isClearable(level.getBlockState(pos))) {
							return null;
						}
					}
				}
			}
		}
		return new Site(minX, minZ, sizeX, sizeZ, min, max);
	}
}
