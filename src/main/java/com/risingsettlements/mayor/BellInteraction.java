package com.risingsettlements.mayor;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.network.MayorNetworking;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.Leadership;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * What happens when a player uses a village's bell.
 *
 * <ul>
 *   <li>Normal use: the bell rings as usual, and the action bar shows who the mayor is.</li>
 *   <li>Sneak + use with an empty hand: claim the bell (enough reputation and no mayor yet),
 *       or open the mayor screen (if you are the mayor).</li>
 *   <li>Sneak + use while holding something the village wants: donate it.</li>
 * </ul>
 *
 * <p>This runs on both sides. The client only decides whether to send the click to the
 * server instead of placing the held block against the bell; the server does the rest.
 */
public final class BellInteraction {
	private BellInteraction() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(BellInteraction::onUse);
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !level.getBlockState(pos).is(Blocks.BELL)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getMainHandItem();
		boolean sneakUse = player.isSecondaryUseActive() && (held.isEmpty() || Donations.value(held) > 0);
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return sneakUse ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}

		RSConfig config = RSConfig.get();
		Settlement settlement = SettlementManager.get(serverLevel).findContaining(pos);
		if (settlement == null) {
			return InteractionResult.PASS;
		}
		if (!sneakUse) {
			if (config.mayorEnabled) {
				serverPlayer.sendOverlayMessage(whoIsMayor(settlement, serverPlayer));
			}
			return InteractionResult.PASS;
		}
		if (held.isEmpty()) {
			if (!config.mayorEnabled) {
				return InteractionResult.PASS;
			}
			claimOrOpen(serverLevel, settlement, serverPlayer);
			return InteractionResult.SUCCESS;
		}
		if (!config.donationsEnabled) {
			return InteractionResult.PASS;
		}
		Donations.donate(serverLevel, settlement, serverPlayer, held);
		return InteractionResult.SUCCESS;
	}

	/** "Mapleford · Mayor: Alex", or the player's reputation if there is no mayor yet. */
	public static Component whoIsMayor(Settlement settlement, ServerPlayer player) {
		Leadership leadership = settlement.leadership();
		if (leadership.hasMayor()) {
			return Lang.tr("mayor.bell.mayor", settlement.name(), leadership.mayorName());
		}
		return Lang.tr("mayor.bell.no_mayor", settlement.name(),
				leadership.reputation(player.getUUID()), RSConfig.get().mayorReputation);
	}

	/** Opens the mayor screen for the mayor; anyone else tries to claim the bell. */
	public static void claimOrOpen(ServerLevel level, Settlement settlement, ServerPlayer player) {
		Leadership leadership = settlement.leadership();
		if (leadership.isMayor(player.getUUID())) {
			MayorOffice.refreshName(settlement, player);
			MayorNetworking.open(player, settlement);
			return;
		}
		int reputation = leadership.reputation(player.getUUID());
		switch (MayorOffice.claim(level, settlement, player)) {
			case CLAIMED, ALREADY_MAYOR -> MayorNetworking.open(player, settlement);
			case TAKEN -> player.sendSystemMessage(Lang.tr("mayor.claim.taken",
					leadership.mayorName(), settlement.name(), reputation));
			case NOT_ENOUGH_REPUTATION -> player.sendSystemMessage(Lang.tr("mayor.claim.not_enough",
					settlement.name(), reputation, RSConfig.get().mayorReputation));
			case DISABLED -> player.sendSystemMessage(Lang.tr("mayor.disabled"));
		}
	}
}
