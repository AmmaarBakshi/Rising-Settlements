package com.risingsettlements.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** How the mod puts blocks into the world. */
public final class Placement {
	/**
	 * Clients are told about the change, but neighbours are not updated. A village block
	 * is restored exactly as it was recorded, and half-restored pairs (a door whose top
	 * half is not back yet) are never broken by a neighbour update in between.
	 */
	public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private Placement() {
	}

	/** True if a mob or player stands where a solid block would go, so placing it now would trap them. */
	public static boolean blockedByEntity(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty();
	}

	public static void place(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, FLAGS);
		Effects.blockPlaced(level, pos, state);
	}
}
