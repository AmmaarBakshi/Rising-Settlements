package com.risingsettlements.mayor;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.GrowthState;
import com.risingsettlements.village.Settlement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Items players can give a village at its bell, and what they are worth.
 *
 * <p>Donated items become village resources, which pay for new projects. Every
 * {@code resourcesPerReputation} resources in one donation also earn a point of reputation.
 */
public final class Donations {
	/** Building materials, valuables and food, and how many resources one item is worth. */
	private static final Map<Item, Integer> VALUES = Map.ofEntries(
			Map.entry(Items.COBBLESTONE, 1),
			Map.entry(Items.COBBLED_DEEPSLATE, 1),
			Map.entry(Items.STONE, 1),
			Map.entry(Items.SANDSTONE, 1),
			Map.entry(Items.STONE_BRICKS, 2),
			Map.entry(Items.BRICKS, 2),
			Map.entry(Items.GLASS, 1),
			Map.entry(Items.COPPER_INGOT, 2),
			Map.entry(Items.IRON_INGOT, 4),
			Map.entry(Items.GOLD_INGOT, 4),
			Map.entry(Items.EMERALD, 10),
			Map.entry(Items.DIAMOND, 20),
			Map.entry(Items.COPPER_BLOCK.weathering().unaffected(), 18),
			Map.entry(Items.IRON_BLOCK, 36),
			Map.entry(Items.GOLD_BLOCK, 36),
			Map.entry(Items.EMERALD_BLOCK, 90),
			Map.entry(Items.DIAMOND_BLOCK, 180),
			Map.entry(Items.WHEAT, 1),
			Map.entry(Items.CARROT, 1),
			Map.entry(Items.POTATO, 1),
			Map.entry(Items.BEETROOT, 1),
			Map.entry(Items.BREAD, 3),
			Map.entry(Items.HAY_BLOCK, 9));

	private Donations() {
	}

	/** Resources one of these items is worth, or 0 if the village doesn't want it. */
	public static int value(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (stack.is(ItemTags.LOGS)) {
			return 1;
		}
		if (stack.is(ItemTags.PLANKS)) {
			return 1;
		}
		return VALUES.getOrDefault(stack.getItem(), 0);
	}

	/**
	 * Gives as much of the stack as the village can store. Items it doesn't want or has no
	 * room for stay in the player's hand.
	 */
	public static void donate(ServerLevel level, Settlement settlement, ServerPlayer player, ItemStack stack) {
		RSConfig config = RSConfig.get();
		int each = value(stack);
		if (each == 0) {
			player.sendSystemMessage(Lang.tr("donate.unwanted", stack.getHoverName()));
			return;
		}
		GrowthState growth = settlement.growth();
		int room = config.maxResources - growth.resources();
		int count = Math.min(stack.getCount(), room / each);
		if (count <= 0) {
			player.sendSystemMessage(Lang.tr("donate.full", settlement.name()));
			return;
		}
		int resources = count * each;
		growth.addResources(resources, config.maxResources);
		stack.shrink(count);
		level.playSound(null, settlement.bell(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendSystemMessage(Lang.tr("donate.thanks", settlement.name(), resources, growth.resources()));

		int reputation = resources / config.resourcesPerReputation;
		if (reputation > 0) {
			Reputation.change(level, settlement, player, reputation);
		}
	}
}
