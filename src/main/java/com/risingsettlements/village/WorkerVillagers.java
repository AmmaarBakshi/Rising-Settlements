package com.risingsettlements.village;

import com.risingsettlements.RisingSettlements;
import com.risingsettlements.config.RSConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Makes it look like villagers do the work: the nearest free villager walks over to
 * whatever is being built or repaired and holds a fitting tool while it happens.
 *
 * <p>The tool is purely cosmetic. It is marked so it can be told apart from items the
 * villager holds for other reasons (such as an emerald while trading), it never drops on
 * death, and it is taken away again once the work stops.
 */
public final class WorkerVillagers {
	private static final String TOOL_MARKER = RisingSettlements.MOD_ID + "_tool";
	/** How long a villager keeps the tool after the last block it worked on. */
	private static final int TOOL_TICKS = 100;
	private static final int SEARCH_RADIUS = 32;

	private WorkerVillagers() {
	}

	/** Sends a villager to work at {@code pos}, holding {@code tool}. */
	public static void work(ServerLevel level, Settlement settlement, BlockPos pos, Item tool) {
		SettlementRuntime runtime = settlement.runtime();
		Villager worker = currentWorker(level, runtime);
		if (worker == null || worker.distanceToSqr(Vec3.atCenterOf(pos)) > SEARCH_RADIUS * SEARCH_RADIUS) {
			clearTool(worker);
			worker = findFree(level, pos);
			runtime.worker = worker == null ? null : worker.getUUID();
		}
		if (worker == null) {
			return;
		}
		runtime.workerUntil = level.getGameTime() + TOOL_TICKS;
		worker.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.5F, 2));

		if (RSConfig.get().villagersHoldTools && (worker.getMainHandItem().isEmpty() || isOurTool(worker.getMainHandItem()))) {
			if (!worker.getMainHandItem().is(tool)) {
				worker.setItemSlot(EquipmentSlot.MAINHAND, markedTool(tool));
				worker.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			}
		}
	}

	/** Ends work that has gone quiet: the worker puts the tool away. */
	public static void tick(ServerLevel level, Settlement settlement) {
		SettlementRuntime runtime = settlement.runtime();
		if (runtime.worker != null && level.getGameTime() > runtime.workerUntil) {
			clearTool(currentWorker(level, runtime));
			runtime.worker = null;
		}
	}

	/**
	 * Takes leftover tools away from villagers who are not working. Work progress is not
	 * saved, so after a restart a villager could otherwise keep its tool forever.
	 */
	static void tidyTools(Settlement settlement, List<Villager> villagers) {
		for (Villager villager : villagers) {
			if (!villager.getUUID().equals(settlement.runtime().worker)) {
				clearTool(villager);
			}
		}
	}

	private static @Nullable Villager currentWorker(ServerLevel level, SettlementRuntime runtime) {
		if (runtime.worker == null) {
			return null;
		}
		Entity entity = level.getEntity(runtime.worker);
		return entity instanceof Villager villager && villager.isAlive() ? villager : null;
	}

	private static @Nullable Villager findFree(ServerLevel level, BlockPos pos) {
		return level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(SEARCH_RADIUS),
						v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !v.isTrading())
				.stream()
				.min(Comparator.comparingDouble(v -> v.distanceToSqr(Vec3.atCenterOf(pos))))
				.orElse(null);
	}

	private static void clearTool(@Nullable Villager villager) {
		if (villager != null && isOurTool(villager.getMainHandItem())) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	private static ItemStack markedTool(Item tool) {
		ItemStack stack = new ItemStack(tool);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TOOL_MARKER, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static boolean isOurTool(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(TOOL_MARKER, false);
	}
}
