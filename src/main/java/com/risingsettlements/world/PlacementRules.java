package com.risingsettlements.world;

import com.risingsettlements.village.Settlement;
import com.risingsettlements.work.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The last safety check before the mod changes a block. It runs right before every single
 * placement, so even if the world changed since a job was planned (a player built
 * something, water flowed in), nothing is ever overwritten that shouldn't be.
 */
public final class PlacementRules {
	public enum Verdict {
		/** Go ahead. */
		OK,
		/** Never place this one (a player block is there, or it's already done). */
		SKIP,
		/** Not right now (chunk unloaded, someone standing there, no support yet). */
		LATER
	}

	private PlacementRules() {
	}

	public static Verdict check(ServerLevel level, Settlement settlement, Placement placement) {
		BlockPos pos = placement.pos();
		BlockState target = placement.state();
		if (!level.isLoaded(pos)) {
			return Verdict.LATER;
		}
		if (settlement.isPlayerBlock(pos)) {
			return Verdict.SKIP;
		}

		BlockState current = level.getBlockState(pos);
		if (current == target) {
			return Verdict.SKIP;
		}
		if (!mayReplace(current, placement.mode())) {
			return Verdict.SKIP;
		}

		// Doors and beds: the other half needs room too.
		BlockPos other = MultiBlocks.otherPart(pos, target);
		if (other != null) {
			if (settlement.isPlayerBlock(other) || !BlockRules.isClearable(level.getBlockState(other))) {
				return Verdict.SKIP;
			}
		}

		if (!target.isAir()) {
			if (!target.canSurvive(level, pos) || needsGroundBelow(target) && !hasSolidGround(level, pos)) {
				return Verdict.LATER;
			}
			if (isOccupied(level, pos, target)) {
				return Verdict.LATER;
			}
		}
		return Verdict.OK;
	}

	private static boolean mayReplace(BlockState current, Placement.Mode mode) {
		return switch (mode) {
			case FILL -> BlockRules.isClearable(current);
			case REPLACE_GROUND -> BlockRules.isClearable(current) || BlockRules.isGround(current);
			case CLEAR -> !current.isAir() && BlockRules.isClearable(current);
		};
	}

	/** Paths and farmland would float over a crater; wait until the ground below is back. */
	private static boolean needsGroundBelow(BlockState state) {
		return state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND);
	}

	private static boolean hasSolidGround(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/** Never place a solid block inside a player or mob. */
	private static boolean isOccupied(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty();
	}
}
