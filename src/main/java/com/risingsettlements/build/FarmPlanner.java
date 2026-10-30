package com.risingsettlements.build;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.BlockRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Farmers either widen an existing field (tilling soil next to it that is close enough
 * to water) or, when no field can grow, lay out a new square field with a water source
 * (covered by a lily pad) in the middle and a log border, near one of the streets.
 */
public final class FarmPlanner {
	private static final int FIELD_SIZE = 9;
	private static final int WATER_REACH = 4;

	private FarmPlanner() {
	}

	static @Nullable BuildPlan plan(ServerLevel level, Settlement settlement, List<BlockPos> roads, RandomSource random) {
		BuildPlan plan = extend(level, settlement, random);
		return plan != null ? plan : newField(level, settlement, roads, random);
	}

	private static @Nullable BuildPlan extend(ServerLevel level, Settlement settlement, RandomSource random) {
		List<BlockPos> farmland = new ArrayList<>(settlement.blueprint().find(state -> state.is(Blocks.FARMLAND)));
		if (farmland.isEmpty()) {
			return null;
		}
		Util.shuffle(farmland, random);
		int limit = RSConfig.get().farmExtendBlocks;
		Set<BlockPos> chosen = new LinkedHashSet<>();
		Block crop = Blocks.WHEAT;

		for (BlockPos field : farmland) {
			if (chosen.size() >= limit) {
				break;
			}
			if (!level.isLoaded(field)) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos soil = field.relative(dir);
				if (chosen.size() < limit && !chosen.contains(soil) && canTill(level, settlement, soil)) {
					chosen.add(soil);
					if (level.getBlockState(field.above()).getBlock() instanceof CropBlock cropBlock) {
						crop = cropBlock;
					}
				}
			}
		}
		if (chosen.size() < 3) {
			return null;
		}

		BuildPlan plan = new BuildPlan(BuildPlan.Kind.FARM);
		for (BlockPos soil : chosen) {
			plan.clear(soil.above());
			plan.add(soil, wetFarmland(), BuildPlan.REPLACE_GROUND);
		}
		for (BlockPos soil : chosen) {
			plan.add(soil.above(), crop.defaultBlockState(), BuildPlan.NO_RECORD);
		}
		return plan;
	}

	private static boolean canTill(ServerLevel level, Settlement settlement, BlockPos soil) {
		if (!level.isLoaded(soil) || settlement.blueprint().contains(soil) || settlement.isPlayerBlock(soil)
				|| settlement.isPlayerBlock(soil.above()) || !settlement.isWithin(soil, RSConfig.get().maxVillageRadius - 1)) {
			return false;
		}
		return BlockRules.isTillable(level.getBlockState(soil))
				&& SiteFinder.groundY(level, soil.getX(), soil.getZ()) == soil.getY()
				&& BlockRules.isClearable(level.getBlockState(soil.above()))
				&& nearWater(level, soil);
	}

	/** Farmland stays wet if water is within four blocks, on the same level or one above. */
	private static boolean nearWater(ServerLevel level, BlockPos soil) {
		for (BlockPos pos : BlockPos.betweenClosed(soil.offset(-WATER_REACH, 0, -WATER_REACH), soil.offset(WATER_REACH, 1, WATER_REACH))) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable BuildPlan newField(ServerLevel level, Settlement settlement, List<BlockPos> roads, RandomSource random) {
		Block crop = StylePalette.CROPS.get(random.nextInt(StylePalette.CROPS.size()));
		Block edge = StylePalette.of(settlement.style()).fieldEdge();
		int half = FIELD_SIZE / 2;

		SiteFinder.Site site = null;
		search:
		for (BlockPos anchor : RoadPlanner.anchors(settlement, roads, 24, random)) {
			for (Direction side : Util.shuffledCopy(Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new), random)) {
				for (int gap : roads.isEmpty() ? new int[]{8, 12, 16} : new int[]{2, 3}) {
					for (int shift : new int[]{0, -3, 3}) {
						BlockPos center = anchor.relative(side, gap + half).relative(side.getClockWise(), shift);
						site = SiteFinder.check(level, settlement, center.getX() - half, center.getZ() - half,
								FIELD_SIZE, FIELD_SIZE, 1, 2);
						if (site != null) {
							break search;
						}
					}
				}
			}
		}
		if (site == null) {
			return null;
		}
		int y = site.maxGround();
		BuildPlan plan = new BuildPlan(BuildPlan.Kind.FARM);
		for (int dx = 0; dx < FIELD_SIZE; dx++) {
			for (int dz = 0; dz < FIELD_SIZE; dz++) {
				plan.clear(new BlockPos(site.minX() + dx, y + 1, site.minZ() + dz));
			}
		}
		for (int dx = 0; dx < FIELD_SIZE; dx++) {
			for (int dz = 0; dz < FIELD_SIZE; dz++) {
				BlockPos pos = new BlockPos(site.minX() + dx, y, site.minZ() + dz);
				BlockState state;
				if (dz == 0 || dz == FIELD_SIZE - 1) {
					state = edge.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
				} else if (dx == 0 || dx == FIELD_SIZE - 1) {
					state = edge.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
				} else if (dx == half && dz == half) {
					state = Blocks.WATER.defaultBlockState();
				} else {
					state = wetFarmland();
				}
				plan.add(pos, state, BuildPlan.REPLACE_GROUND);
			}
		}
		for (int dx = 1; dx < FIELD_SIZE - 1; dx++) {
			for (int dz = 1; dz < FIELD_SIZE - 1; dz++) {
				BlockPos above = new BlockPos(site.minX() + dx, y + 1, site.minZ() + dz);
				if (dx != half || dz != half) {
					plan.add(above, crop.defaultBlockState(), BuildPlan.NO_RECORD);
				} else {
					// Keeps the water from freezing over in cold biomes, so the field stays wet.
					plan.add(above, Blocks.LILY_PAD.defaultBlockState());
				}
			}
		}
		return plan;
	}

	private static BlockState wetFarmland() {
		return Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
	}
}
