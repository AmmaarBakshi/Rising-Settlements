package com.risingsettlements.work;

import com.risingsettlements.village.Settlement;
import com.risingsettlements.world.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Picks villagers to do the work and walks them to the job.
 *
 * <p>Villagers are steered through their own brain (a walk target, like the one they use
 * to go to their workstation), so they path-find, open doors and avoid danger as usual.
 * Sleeping, trading and baby villagers are never picked, so work naturally stops at night.
 */
public final class Workers {
	/** Walking speed: a calm stroll, like villagers going to work. */
	private static final float WALK_SPEED = 0.55F;
	/** The villager stops this close to the target. */
	private static final int CLOSE_ENOUGH = 2;
	/** Horizontal reach (in blocks) at which a villager can place a block. */
	private static final double REACH = 6;
	/**
	 * Villagers can only plan a path of about 48 blocks, so longer walks go through
	 * waypoints this far apart.
	 */
	private static final int WAYPOINT_DISTANCE = 24;

	private Workers() {
	}

	/** Returns the job's current worker, or picks a new one if it has none or can't work. */
	public static @Nullable Villager resolve(ServerLevel level, Settlement settlement, BuildJob job, BlockPos near,
											 @Nullable ResourceKey<VillagerProfession> preferred) {
		if (job.worker != null) {
			Entity entity = level.getEntity(job.worker);
			if (entity instanceof Villager villager && canWork(villager, settlement)) {
				return villager;
			}
			job.worker = null;
		}
		Villager picked = find(level, settlement, near, preferred, job.skippedWorkers);
		if (picked != null) {
			job.worker = picked.getUUID();
		}
		return picked;
	}

	/**
	 * The nearest villager able to work, preferring the given profession (e.g. farmers for
	 * farms), and leaving out villagers that already failed to reach this job.
	 */
	public static @Nullable Villager find(ServerLevel level, Settlement settlement, BlockPos near,
										  @Nullable ResourceKey<VillagerProfession> preferred, Set<UUID> skip) {
		List<Villager> candidates = level.getEntitiesOfClass(Villager.class, AABB.of(settlement.bounds()),
				villager -> canWork(villager, settlement) && !skip.contains(villager.getUUID()));
		Comparator<Villager> byDistance = Comparator.comparingDouble(v -> v.distanceToSqr(near.getX(), near.getY(), near.getZ()));
		if (preferred != null) {
			Villager match = candidates.stream()
					.filter(v -> v.getVillagerData().profession().is(preferred))
					.min(byDistance)
					.orElse(null);
			if (match != null) {
				return match;
			}
		}
		return candidates.stream().min(byDistance).orElse(null);
	}

	static boolean canWork(Villager villager, Settlement settlement) {
		return villager.isAlive()
				&& !villager.isBaby()
				&& !villager.isSleeping()
				&& !villager.isTrading()
				&& settlement.bounds().inflatedBy(16).isInside(villager.blockPosition());
	}

	/** Where the villager should walk to reach {@code target}: the spot itself, or a waypoint towards it. */
	public static BlockPos walkTarget(ServerLevel level, Villager villager, BlockPos target) {
		double dx = target.getX() + 0.5 - villager.getX();
		double dz = target.getZ() + 0.5 - villager.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= WAYPOINT_DISTANCE) {
			return target;
		}
		int x = Mth.floor(villager.getX() + dx / distance * WAYPOINT_DISTANCE);
		int z = Mth.floor(villager.getZ() + dz / distance * WAYPOINT_DISTANCE);
		int ground = Terrain.surface(level, x, z);
		return ground == Terrain.UNKNOWN ? target : new BlockPos(x, ground + 1, z);
	}

	/**
	 * Keeps the villager heading for {@code walkTo} and looking at {@code lookAt}. Some of the
	 * villager's own habits (strolling back to its workstation, gathering at the bell) replace
	 * the walk target now and then, so this is called again every few ticks and only steps in
	 * when the target was changed.
	 */
	public static void steer(Villager villager, BlockPos walkTo, BlockPos lookAt) {
		Brain<?> brain = villager.getBrain();
		boolean onCourse = brain.getMemory(MemoryModuleType.WALK_TARGET)
				.map(walk -> walk.getTarget().currentBlockPosition().equals(walkTo))
				.orElse(false);
		if (onCourse) {
			return;
		}
		// A villager that is "unable to reach" something for too long forgets its workstation,
		// so never let our targets count towards that.
		brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(walkTo, WALK_SPEED, CLOSE_ENOUGH));
		brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(lookAt));
	}

	/** Close enough to place a block at {@code pos}. Height doesn't matter much: villagers "reach up". */
	public static boolean isNear(Villager villager, BlockPos pos) {
		return isWithin(villager, pos, REACH) && Math.abs(villager.getY() - pos.getY()) <= 8;
	}

	/** Horizontal distance check. */
	public static boolean isWithin(Villager villager, BlockPos pos, double distance) {
		return distance(villager, pos) <= distance;
	}

	/** Horizontal distance from the villager to the middle of a block. */
	public static double distance(Villager villager, BlockPos pos) {
		double dx = villager.getX() - (pos.getX() + 0.5);
		double dz = villager.getZ() - (pos.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Lets the villager go back to its normal routine. */
	public static void release(ServerLevel level, @Nullable UUID workerId) {
		if (workerId != null && level.getEntity(workerId) instanceof Villager villager) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
			villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		}
	}
}
