package com.risingsettlements.mayor;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.Leadership;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import org.jspecify.annotations.Nullable;

/**
 * How players earn and lose a village's trust.
 *
 * <ul>
 *   <li>Trading with one of its villagers: {@code reputationPerTrade}.</li>
 *   <li>Killing a raider in or near the village: {@code reputationPerRaiderKill}.</li>
 *   <li>Killing any other monster inside the village: {@code reputationPerMonsterKill}.</li>
 *   <li>Donating at the bell: see {@link Donations}.</li>
 *   <li>Killing one of its villagers or iron golems costs {@code villagerKillPenalty} or
 *       {@code golemKillPenalty}.</li>
 * </ul>
 */
public final class Reputation {
	/** Raiders gather around a village, so they count this far outside its border. */
	private static final int RAID_MARGIN = 32;

	private Reputation() {
	}

	public static void register() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(Reputation::onKill);
	}

	/** Called by the trade mixin after a player completes a trade with a villager. */
	public static void onTrade(ServerPlayer player, Villager villager) {
		RSConfig config = RSConfig.get();
		if (!config.mayorEnabled || config.reputationPerTrade == 0 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Settlement settlement = SettlementManager.get(level).findContaining(villager.blockPosition());
		if (settlement != null) {
			change(level, settlement, player, config.reputationPerTrade);
		}
	}

	private static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		RSConfig config = RSConfig.get();
		if (!config.mayorEnabled || !(killer instanceof ServerPlayer player)) {
			return;
		}
		BlockPos pos = killed.blockPosition();
		if (killed instanceof Raider) {
			Settlement settlement = near(level, pos, RAID_MARGIN);
			if (settlement != null && config.reputationPerRaiderKill > 0) {
				change(level, settlement, player, config.reputationPerRaiderKill);
			}
		} else if (killed instanceof Enemy) {
			Settlement settlement = near(level, pos, 0);
			if (settlement != null && config.reputationPerMonsterKill > 0) {
				change(level, settlement, player, config.reputationPerMonsterKill);
			}
		} else if (killed instanceof Villager) {
			Settlement settlement = near(level, pos, 0);
			if (settlement != null && config.villagerKillPenalty > 0) {
				change(level, settlement, player, -config.villagerKillPenalty);
			}
		} else if (killed instanceof IronGolem golem && !golem.isPlayerCreated()) {
			Settlement settlement = near(level, pos, 0);
			if (settlement != null && config.golemKillPenalty > 0) {
				change(level, settlement, player, -config.golemKillPenalty);
			}
		}
	}

	/** The village whose border (grown by {@code margin}) contains the position. */
	private static @Nullable Settlement near(ServerLevel level, BlockPos pos, int margin) {
		for (Settlement settlement : SettlementManager.get(level).all()) {
			if (settlement.bounds().inflatedBy(margin).isInside(pos)) {
				return settlement;
			}
		}
		return null;
	}

	/**
	 * Changes a player's reputation in a village and tells them about it. A mayor whose
	 * reputation falls below half of what it takes to claim the bell loses the office.
	 */
	public static void change(ServerLevel level, Settlement settlement, ServerPlayer player, int amount) {
		Leadership leadership = settlement.leadership();
		int threshold = RSConfig.get().mayorReputation;
		int before = leadership.reputation(player.getUUID());
		int after = leadership.addReputation(player.getUUID(), amount);

		player.sendOverlayMessage(Lang.tr(amount >= 0 ? "reputation.gained" : "reputation.lost",
				Math.abs(amount), settlement.name(), after));
		if (before < threshold && after >= threshold && !leadership.hasMayor()) {
			player.sendSystemMessage(Lang.tr("reputation.can_claim", settlement.name()));
		}
		if (leadership.isMayor(player.getUUID()) && after < threshold / 2) {
			MayorOffice.removeMayor(level, settlement, "mayor.lost_trust");
		}
	}
}
