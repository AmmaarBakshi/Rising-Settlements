package com.risingsettlements.growth;

import com.risingsettlements.village.Building;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.work.Placement;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans lamp posts along the roads: two fence posts with a lantern on top, standing on
 * the ground right next to a road. Lamps keep their distance from each other and from
 * the village's other lights, so the village gets lit evenly instead of in one spot.
 */
public final class LampPlanner {
	/** Minimum distance between two lights. */
	private static final int SPACING = 10;
	/** Most road blocks to look at per planning attempt. */
	private static final int MAX_CHECKS = 400;

	private LampPlanner() {
	}

	public static @Nullable List<Placement> plan(ServerLevel level, Settlement settlement, List<BlockPos> roads,
			RandomSource random, int maxLamps) {
		List<BlockPos> lights = new ArrayList<>(settlement.blueprint().find(state -> state.getLightEmission() >= 10));
		List<BlockPos> shuffled = new ArrayList<>(roads);
		Util.shuffle(shuffled, random);

		List<BlockPos> lamps = new ArrayList<>();
		int checks = 0;
		for (BlockPos road : shuffled) {
			if (lamps.size() >= maxLamps || checks++ >= MAX_CHECKS) {
				break;
			}
			BlockPos side = road.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
			if (Terrain.surface(level, side.getX(), side.getZ()) != road.getY()) {
				continue;
			}
			if (isFarFrom(side, lights) && canStand(level, settlement, side)) {
				lamps.add(side);
				lights.add(side);
			}
		}
		if (lamps.isEmpty()) {
			return null;
		}

		BlockState fence = settlement.style().fence();
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
		List<Placement> placements = new ArrayList<>();
		for (BlockPos ground : lamps) {
			placements.add(new Placement(ground.above(1), fence, Placement.Mode.FILL));
			placements.add(new Placement(ground.above(2), fence, Placement.Mode.FILL));
			placements.add(new Placement(ground.above(3), lantern, Placement.Mode.FILL));
		}
		return placements;
	}

	private static boolean isFarFrom(BlockPos pos, List<BlockPos> lights) {
		for (BlockPos light : lights) {
			int dx = light.getX() - pos.getX();
			int dz = light.getZ() - pos.getZ();
			if (dx * dx + dz * dz < SPACING * SPACING) {
				return false;
			}
		}
		return true;
	}

	/** Natural ground with three free blocks above it and no player blocks right next to it. */
	private static boolean canStand(ServerLevel level, Settlement settlement, BlockPos ground) {
		if (!BlockRules.isGround(level.getBlockState(ground)) || settlement.blueprint().contains(ground)) {
			return false;
		}
		for (Building building : settlement.growth().buildings()) {
			if (building.box().intersects(ground.getX(), ground.getZ(), ground.getX(), ground.getZ())) {
				return false;
			}
		}
		for (int dy = 1; dy <= 3; dy++) {
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
