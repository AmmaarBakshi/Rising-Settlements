package com.risingsettlements.work;

import com.risingsettlements.village.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
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
	private static final double REACH = 4.5;

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
		Villager picked = find(level, settlement, near, preferred);
		if (picked != null) {
			job.worker = picked.getUUID();
		}
		return picked;
	}

	/** The nearest villager able to work, preferring the given profession (e.g. farmers for farms). */
	public static @Nullable Villager find(ServerLevel level, Settlement settlement, BlockPos near,
										  @Nullable ResourceKey<VillagerProfession> preferred) {
		List<Villager> candidates = level.getEntitiesOfClass(Villager.class, AABB.of(settlement.bounds()),
				villager -> canWork(villager, settlement));
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

	/** Tells the villager to walk to a spot and look at it. */
	public static void guide(Villager villager, BlockPos target) {
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, WALK_SPEED, CLOSE_ENOUGH));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
	}

	/** Close enough to place a block at {@code pos}. Height doesn't matter much: villagers "reach up". */
	public static boolean isNear(Villager villager, BlockPos pos) {
		double dx = villager.getX() - (pos.getX() + 0.5);
		double dz = villager.getZ() - (pos.getZ() + 0.5);
		double dy = Math.abs(villager.getY() - pos.getY());
		return dx * dx + dz * dz <= REACH * REACH && dy <= 8;
	}

	/** Lets the villager go back to its normal routine. */
	public static void release(ServerLevel level, @Nullable UUID workerId) {
		if (workerId != null && level.getEntity(workerId) instanceof Villager villager) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		}
	}
}
