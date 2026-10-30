package com.risingsettlements.build;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lays paths on the surface: a short path from a new house to the nearest street, or a
 * new street leading out from the edge of the village. Paths follow the ground, may climb
 * or drop one block per step, and stop at water, trees, buildings and player blocks.
 */
public final class RoadPlanner {
	/** Marks a column that cannot take a path. */
	private static final int BLOCKED = Integer.MIN_VALUE;

	private RoadPlanner() {
	}

	/**
	 * Adds a path from {@code start} to the nearest existing street.
	 *
	 * @param avoid area the path must not cross (the house it starts from)
	 * @return number of path blocks added
	 */
	static int connect(BuildPlan plan, ServerLevel level, Settlement settlement, BlockPos start, List<BlockPos> roads,
					   BoundingBox avoid, int maxLength) {
		BlockPos target = nearest(roads, start);
		if (target == null) {
			return 0;
		}
		int x = start.getX();
		int z = start.getZ();
		int prevY = SiteFinder.groundY(level, x, z);
		int count = 0;
		for (int i = 0; i < maxLength; i++) {
			if (!SiteFinder.isLoaded(level, x, z)) {
				break;
			}
			int gy = SiteFinder.groundY(level, x, z);
			BlockPos ground = new BlockPos(x, gy, z);
			if (BlockRules.isRoad(level.getBlockState(ground)) && settlement.blueprint().contains(ground)) {
				break; // Reached a street.
			}
			if (avoid.intersects(x, z, x, z) || pave(plan, level, settlement, x, z, prevY) == BLOCKED) {
				break;
			}
			count++;
			prevY = gy;
			int dx = target.getX() - x;
			int dz = target.getZ() - z;
			if (dx == 0 && dz == 0) {
				break;
			}
			if (Math.abs(dx) >= Math.abs(dz)) {
				x += Integer.signum(dx);
			} else {
				z += Integer.signum(dz);
			}
		}
		return count;
	}

	/** A new street leading away from the bell, starting at one of the outermost path blocks. */
	static @Nullable BuildPlan extend(ServerLevel level, Settlement settlement, List<BlockPos> roads, RandomSource random) {
		if (roads.isEmpty()) {
			return null;
		}
		BlockPos bell = settlement.bell();
		List<BlockPos> outer = roads.stream()
				.sorted(Comparator.comparingDouble((BlockPos p) -> p.distSqr(bell)).reversed())
				.limit(12)
				.toList();
		int radius = RSConfig.get().maxVillageRadius - 2;

		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos start = outer.get(random.nextInt(outer.size()));
			Direction dir = awayFrom(bell, start);
			if (random.nextInt(3) == 0) {
				dir = random.nextBoolean() ? dir.getClockWise() : dir.getCounterClockWise();
			}
			int length = 6 + random.nextInt(9);
			BuildPlan plan = new BuildPlan(BuildPlan.Kind.ROAD);
			int x = start.getX();
			int z = start.getZ();
			int prevY = start.getY();
			int count = 0;
			for (int i = 0; i < length; i++) {
				x += dir.getStepX();
				z += dir.getStepZ();
				if (!settlement.isWithin(new BlockPos(x, 0, z), radius) || !SiteFinder.isLoaded(level, x, z)
						|| (i > 0 && besideRoad(level, x, z, dir))) {
					break;
				}
				int gy = pave(plan, level, settlement, x, z, prevY);
				if (gy == BLOCKED) {
					break;
				}
				prevY = gy;
				count++;
			}
			if (count >= 4) {
				return plan;
			}
		}
		return null;
	}

	/**
	 * Adds the steps for one path block in a column, if the column allows it.
	 *
	 * @return the ground height of the column, or {@link #BLOCKED}
	 */
	private static int pave(BuildPlan plan, ServerLevel level, Settlement settlement, int x, int z, int prevY) {
		int gy = SiteFinder.groundY(level, x, z);
		if (Math.abs(gy - prevY) > 1) {
			return BLOCKED;
		}
		BlockPos ground = new BlockPos(x, gy, z);
		BlockPos above = ground.above();
		BlockState state = level.getBlockState(ground);
		if (!BlockRules.isGround(state) || settlement.blueprint().contains(ground) || settlement.isPlayerBlock(ground)
				|| settlement.isPlayerBlock(above) || !BlockRules.isClearable(level.getBlockState(above))) {
			return BLOCKED;
		}
		plan.clear(above);
		plan.add(ground, Blocks.DIRT_PATH.defaultBlockState(), BuildPlan.REPLACE_GROUND);
		return gy;
	}

	/** True if a path already runs right next to this column, so a new street would just double it. */
	private static boolean besideRoad(ServerLevel level, int x, int z, Direction dir) {
		for (Direction side : new Direction[]{dir.getClockWise(), dir.getCounterClockWise()}) {
			int sx = x + side.getStepX();
			int sz = z + side.getStepZ();
			if (SiteFinder.isLoaded(level, sx, sz)
					&& BlockRules.isRoad(level.getBlockState(new BlockPos(sx, SiteFinder.groundY(level, sx, sz), sz)))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Street blocks to build next to, outermost first (there is more free space at the
	 * edge of a village), lightly shuffled so villages don't always grow in the same
	 * direction. Falls back to the bell if the village has no streets.
	 */
	static List<BlockPos> anchors(Settlement settlement, List<BlockPos> roads, int limit, RandomSource random) {
		if (roads.isEmpty()) {
			return List.of(settlement.bell());
		}
		BlockPos bell = settlement.bell();
		List<BlockPos> outer = new ArrayList<>(roads.stream()
				.sorted(Comparator.comparingDouble((BlockPos p) -> p.distSqr(bell)).reversed())
				.limit(limit)
				.toList());
		Util.shuffle(outer, random);
		return outer;
	}

	static @Nullable BlockPos nearest(List<BlockPos> positions, BlockPos to) {
		BlockPos best = null;
		long bestDist = Long.MAX_VALUE;
		for (BlockPos pos : positions) {
			long dx = pos.getX() - to.getX();
			long dz = pos.getZ() - to.getZ();
			long dist = dx * dx + dz * dz;
			if (dist < bestDist) {
				best = pos;
				bestDist = dist;
			}
		}
		return best;
	}

	/** The horizontal direction that points most directly from {@code from} to {@code to}. */
	static Direction awayFrom(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
