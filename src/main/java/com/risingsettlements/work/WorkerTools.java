package com.risingsettlements.work;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Villagers hold a tool that fits the job while they work: a pickaxe for repairs, walls and
 * wells, an axe for houses, a hoe for farms, a shovel for roads and a lantern for lamp posts.
 *
 * <p>The tool is only for show. It is marked so it can't be mixed up with anything else a
 * villager holds, it never drops, and it is taken away again when the work stops (and when
 * the server shuts down, so no villager keeps one if the mod is removed).
 */
public final class WorkerTools {
	private static final String MARK = "rising_settlements_tool";
	private static final CompoundTag MARK_TAG = markTag();

	private WorkerTools() {
	}

	/** Takes the tools away from every worker when the server stops. */
	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				for (Settlement settlement : SettlementManager.get(level).all()) {
					BuildJob repairs = settlement.runtime().repairJob;
					BuildJob project = settlement.growth().project();
					if (repairs != null) {
						Workers.release(level, repairs.workerId());
					}
					if (project != null) {
						Workers.release(level, project.workerId());
					}
				}
			}
		});
	}

	/** Puts the job's tool in the villager's hand, unless it is holding something of its own. */
	public static void equip(Villager villager, BuildJob.Kind kind) {
		if (!RSConfig.get().workersHoldTools) {
			return;
		}
		Item tool = toolFor(kind);
		ItemStack held = villager.getMainHandItem();
		if (held.is(tool) && isOurs(held)) {
			return;
		}
		if (!held.isEmpty() && !isOurs(held)) {
			// E.g. it is showing a player its trades; try again later.
			return;
		}
		ItemStack stack = new ItemStack(tool);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(MARK_TAG));
		villager.setItemSlot(EquipmentSlot.MAINHAND, stack);
		villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	/** Takes our tool away again (leaves anything else alone). */
	public static void unequip(Villager villager) {
		if (isOurs(villager.getMainHandItem())) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	private static Item toolFor(BuildJob.Kind kind) {
		return switch (kind) {
			case REPAIR, WALL, WELL -> Items.IRON_PICKAXE;
			case HOUSE -> Items.IRON_AXE;
			case FARM -> Items.IRON_HOE;
			case ROAD -> Items.IRON_SHOVEL;
			case LAMPS -> Items.LANTERN;
		};
	}

	private static boolean isOurs(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.matchedBy(MARK_TAG);
	}

	private static CompoundTag markTag() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		return tag;
	}
}
