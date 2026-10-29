package com.risingsettlements.growth;

import com.risingsettlements.village.Building;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.work.Placement;
import com.risingsettlements.world.BlockRules;
import com.risingsettlements.world.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a two-block-high wall along the village border, in the village's style.
 *
 * <p>The wall leaves gaps where roads cross the border (plus one block on each side, so
 * carts and players fit), and simply skips spots where it can't stand: water, steep
 * ground, buildings, trees and anything a player placed. Walls are only built when asked
 * for (by the mayor or an operator), since a wall changes how a village looks a lot.
 */
public final class WallPlanner {
	private static final int HEIGHT = 2;

	private WallPlanner() {
	}

	public static @Nullable List<Placement> plan(ServerLevel level, Settlement settlement, int maxBlocks) {
		List<BlockPos> ring = ring(settlement.bounds());
		int size = ring.size();

		// First pass: find the border cells that are roads, so the wall can leave a gap.
		int[] surface = new int[size];
		boolean[] road = new boolean[size];
		for (int i = 0; i < size; i++) {
			BlockPos cell = ring.get(i);
			surface[i] = Terrain.surface(level, cell.getX(), cell.getZ());
			road[i] = surface[i] != Terrain.UNKNOWN
					&& settlement.style().isRoad(level.getBlockState(cell.atY(surface[i])));
		}

		BlockState wall = settlement.style().wall();
		List<Placement> placements = new ArrayList<>();
		for (int i = 0; i < size && placements.size() + HEIGHT <= maxBlocks; i++) {
			boolean nearRoad = road[i] || road[(i + 1) % size] || road[(i + size - 1) % size];
			if (surface[i] == Terrain.UNKNOWN || nearRoad) {
				continue;
			}
			BlockPos ground = ring.get(i).atY(surface[i]);
			if (canStand(level, settlement, ground)) {
				for (int dy = 1; dy <= HEIGHT; dy++) {
					placements.add(new Placement(ground.above(dy), wall, Placement.Mode.FILL));
				}
			}
		}
		return placements.isEmpty() ? null : placements;
	}

	/** The cells along the edge of the border, walking around it in order. */
	private static List<BlockPos> ring(BoundingBox bounds) {
		List<BlockPos> ring = new ArrayList<>();
		int minX = bounds.minX();
		int maxX = bounds.maxX();
		int minZ = bounds.minZ();
		int maxZ = bounds.maxZ();
		for (int x = minX; x < maxX; x++) {
			ring.add(new BlockPos(x, 0, minZ));
		}
		for (int z = minZ; z < maxZ; z++) {
			ring.add(new BlockPos(maxX, 0, z));
		}
		for (int x = maxX; x > minX; x--) {
			ring.add(new BlockPos(x, 0, maxZ));
		}
		for (int z = maxZ; z > minZ; z--) {
			ring.add(new BlockPos(minX, 0, z));
		}
		return ring;
	}

	private static boolean canStand(ServerLevel level, Settlement settlement, BlockPos ground) {
		if (!BlockRules.isGround(level.getBlockState(ground)) || settlement.blueprint().contains(ground)) {
			return false;
		}
		for (Building building : settlement.growth().buildings()) {
			if (building.box().intersects(ground.getX(), ground.getZ(), ground.getX(), ground.getZ())) {
				return false;
			}
		}
		for (int dy = 1; dy <= HEIGHT; dy++) {
			BlockPos pos = ground.above(dy);
			if (!BlockRules.isClearable(level.getBlockState(pos))) {
				return false;
			}
			for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
				if (settlement.isPlayerBlock(near)) {
					return false;
				}
			}
		}
		return true;
	}
}
