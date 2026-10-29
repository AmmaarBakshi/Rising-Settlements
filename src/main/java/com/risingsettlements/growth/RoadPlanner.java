package com.risingsettlements.growth;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Building;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.VillageStyle;
import com.risingsettlements.work.Placement;
import com.risingsettlements.world.BlockRules;
import com.risingsettlements.world.Terrain;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans new roads with a breadth-first search over the land's surface.
 *
 * <p>A road may only go over natural ground with room to walk above it, may climb at
 * most one block per step, and never crosses water, buildings or player blocks. The
 * search ends at the first existing road it reaches, so new roads always join the
 * village's network. Roads are three blocks wide, like vanilla village streets.
 */
public final class RoadPlanner {
	/** Search limits, so a hopeless search costs at most a few milliseconds. */
	private static final int MAX_NODES = 6000;
	private static final int MAX_LENGTH = 80;

	private RoadPlanner() {
	}

	/**
	 * Plans a road from {@code start} to the nearest existing road.
	 *
	 * @param avoid    extra areas the road must not cross, such as a house that is about to be built
	 * @param fromRoad build it starting at the existing road and working towards {@code start}
	 * @return the placements (empty if {@code start} already is a road), or null if there is no route
	 */
	public static @Nullable List<Placement> connect(ServerLevel level, Settlement settlement, BlockPos start,
			List<BoundingBox> avoid, boolean fromRoad) {
		Search search = new Search(level, settlement, avoid);
		int startY = Terrain.surface(level, start.getX(), start.getZ());
		if (startY == Terrain.UNKNOWN) {
			return null;
		}
		if (search.isRoad(start.getX(), startY, start.getZ())) {
			return List.of();
		}
		if (!search.isWalkable(start.getX(), startY, start.getZ())) {
			return null;
		}

		long startKey = key(start.getX(), start.getZ());
		Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
		Long2IntOpenHashMap height = new Long2IntOpenHashMap();
		Long2IntOpenHashMap distance = new Long2IntOpenHashMap();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		parent.put(startKey, startKey);
		height.put(startKey, startY);
		distance.put(startKey, 0);
		queue.enqueue(startKey);

		while (!queue.isEmpty() && parent.size() < MAX_NODES) {
			long current = queue.dequeueLong();
			int x = x(current);
			int z = z(current);
			int y = height.get(current);
			int steps = distance.get(current);
			if (steps >= MAX_LENGTH) {
				continue;
			}
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				int nx = x + direction.getStepX();
				int nz = z + direction.getStepZ();
				long next = key(nx, nz);
				if (parent.containsKey(next)) {
					continue;
				}
				int ny = Terrain.surface(level, nx, nz);
				if (ny == Terrain.UNKNOWN || Math.abs(ny - y) > 1) {
					continue;
				}
				if (search.isRoad(nx, ny, nz)) {
					List<BlockPos> path = path(parent, height, current);
					return search.build(fromRoad ? path.reversed() : path);
				}
				if (search.isWalkable(nx, ny, nz)) {
					parent.put(next, current);
					height.put(next, ny);
					distance.put(next, steps + 1);
					queue.enqueue(next);
				}
			}
		}
		return null;
	}

	/**
	 * Plans a road that reaches further out: from the nearest road to a random spot just
	 * beyond the village's current roads. It is built from the road outwards, so the
	 * villager doing it walks along with the new road.
	 */
	public static @Nullable List<Placement> extend(ServerLevel level, Settlement settlement, List<BlockPos> roads,
			RandomSource random, int attempts) {
		BlockPos bell = settlement.bell();
		double reach = 0;
		for (BlockPos road : roads) {
			reach = Math.max(reach, Math.sqrt(road.distSqr(bell)));
		}
		int limit = RSConfig.get().maxVillageRadius - 8;
		for (int i = 0; i < attempts; i++) {
			double distance = Math.min(limit, reach + 8 + random.nextInt(16));
			double angle = random.nextDouble() * Math.PI * 2;
			BlockPos target = new BlockPos(bell.getX() + Mth.floor(Math.cos(angle) * distance), 0,
					bell.getZ() + Mth.floor(Math.sin(angle) * distance));
			List<Placement> road = connect(level, settlement, target, List.of(), true);
			if (road != null && road.size() >= 8) {
				return road;
			}
		}
		return null;
	}

	/** The searched path from the start to {@code end}, as surface positions. */
	private static List<BlockPos> path(Long2LongOpenHashMap parent, Long2IntOpenHashMap height, long end) {
		List<BlockPos> path = new ArrayList<>();
		long current = end;
		while (true) {
			path.add(new BlockPos(x(current), height.get(current), z(current)));
			long previous = parent.get(current);
			if (previous == current) {
				break;
			}
			current = previous;
		}
		return path.reversed();
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int x(long key) {
		return (int) (key >> 32);
	}

	private static int z(long key) {
		return (int) key;
	}

	/** The rules for one search, with the village's state looked up once. */
	private static final class Search {
		private final ServerLevel level;
		private final Settlement settlement;
		private final VillageStyle style;
		private final List<BoundingBox> blocked = new ArrayList<>();
		private final long maxDistanceSqr;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Search(ServerLevel level, Settlement settlement, List<BoundingBox> avoid) {
			this.level = level;
			this.settlement = settlement;
			this.style = settlement.style();
			this.blocked.addAll(avoid);
			for (Building building : settlement.growth().buildings()) {
				blocked.add(building.box());
			}
			long reach = RSConfig.get().maxVillageRadius - 2;
			this.maxDistanceSqr = reach * reach;
		}

		/** An existing road with room to walk on it. */
		boolean isRoad(int x, int y, int z) {
			return style.isRoad(level.getBlockState(pos.set(x, y, z))) && isFree(x, y + 1, z);
		}

		/** Natural ground that a road may be laid on. */
		boolean isWalkable(int x, int y, int z) {
			BlockPos bell = settlement.bell();
			long dx = x - bell.getX();
			long dz = z - bell.getZ();
			if (dx * dx + dz * dz > maxDistanceSqr) {
				return false;
			}
			for (BoundingBox box : blocked) {
				if (box.intersects(x, z, x, z)) {
					return false;
				}
			}
			pos.set(x, y, z);
			if (settlement.isPlayerBlock(pos) || settlement.blueprint().contains(pos)) {
				return false;
			}
			return BlockRules.isGround(level.getBlockState(pos)) && isFree(x, y + 1, z) && isFree(x, y + 2, z);
		}

		private boolean isFree(int x, int y, int z) {
			pos.set(x, y, z);
			return !settlement.isPlayerBlock(pos) && BlockRules.isClearable(level.getBlockState(pos));
		}

		/** Widens the path to three blocks and turns it into placements. */
		List<Placement> build(List<BlockPos> path) {
			LongLinkedOpenHashSet cells = new LongLinkedOpenHashSet();
			List<BlockPos> ordered = new ArrayList<>();
			for (BlockPos cell : path) {
				if (cells.add(key(cell.getX(), cell.getZ()))) {
					ordered.add(cell);
				}
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					int nx = cell.getX() + direction.getStepX();
					int nz = cell.getZ() + direction.getStepZ();
					if (cells.contains(key(nx, nz))) {
						continue;
					}
					int ny = Terrain.surface(level, nx, nz);
					if (ny != Terrain.UNKNOWN && Math.abs(ny - cell.getY()) <= 1 && isWalkable(nx, ny, nz)) {
						cells.add(key(nx, nz));
						ordered.add(new BlockPos(nx, ny, nz));
					}
				}
			}

			BlockState road = style.road();
			BlockState air = Blocks.AIR.defaultBlockState();
			List<Placement> placements = new ArrayList<>();
			for (BlockPos cell : ordered) {
				BlockPos above = cell.above();
				if (!level.getBlockState(above).isAir()) {
					placements.add(new Placement(above, air, Placement.Mode.CLEAR));
				}
				placements.add(new Placement(cell, road, Placement.Mode.REPLACE_GROUND));
			}
			return placements;
		}
	}
}
