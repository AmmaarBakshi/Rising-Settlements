package com.risingsettlements.mayor;

import com.risingsettlements.config.RSConfig;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.Leadership;
import com.risingsettlements.village.Settlement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Claiming the bell, stepping down and losing the office. Each village has at most one
 * mayor at a time.
 */
public final class MayorOffice {
	/** The mayor has to be this close to the bell to manage the village. */
	public static final double BELL_RANGE = 32;
	/** Players this far outside a village's border still hear its news. */
	private static final int NEWS_RANGE = 48;

	public enum Claim {
		CLAIMED,
		DISABLED,
		ALREADY_MAYOR,
		TAKEN,
		NOT_ENOUGH_REPUTATION
	}

	private MayorOffice() {
	}

	/** Makes the player mayor if nobody else is and they have enough reputation. */
	public static Claim claim(ServerLevel level, Settlement settlement, ServerPlayer player) {
		RSConfig config = RSConfig.get();
		Leadership leadership = settlement.leadership();
		if (!config.mayorEnabled) {
			return Claim.DISABLED;
		}
		if (leadership.isMayor(player.getUUID())) {
			return Claim.ALREADY_MAYOR;
		}
		if (leadership.hasMayor()) {
			return Claim.TAKEN;
		}
		if (leadership.reputation(player.getUUID()) < config.mayorReputation) {
			return Claim.NOT_ENOUGH_REPUTATION;
		}
		leadership.setMayor(player.getUUID(), player.getPlainTextName());
		level.playSound(null, settlement.bell(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		announce(level, settlement, Lang.tr("mayor.claimed", player.getPlainTextName(), settlement.name()));
		return Claim.CLAIMED;
	}

	/** The mayor steps down. */
	public static void resign(ServerLevel level, Settlement settlement, ServerPlayer player) {
		if (settlement.leadership().isMayor(player.getUUID())) {
			removeMayor(level, settlement, "mayor.resigned");
		}
	}

	/** Removes the current mayor and tells everyone nearby why ({@code reasonKey} takes the name and village). */
	public static void removeMayor(ServerLevel level, Settlement settlement, String reasonKey) {
		Leadership leadership = settlement.leadership();
		UUID mayor = leadership.mayor();
		if (mayor == null) {
			return;
		}
		Component message = Lang.tr(reasonKey, leadership.mayorName(), settlement.name());
		leadership.clearMayor();
		announce(level, settlement, message);
		// Make sure the old mayor hears it even if they are far away.
		ServerPlayer old = level.getServer().getPlayerList().getPlayer(mayor);
		if (old != null && !isNearVillage(old, level, settlement)) {
			old.sendSystemMessage(message);
		}
	}

	/** Whether the player is the mayor, standing close enough to the bell to manage the village. */
	public static boolean canManage(ServerPlayer player, Settlement settlement) {
		return settlement.leadership().isMayor(player.getUUID())
				&& player.position().closerThan(Vec3.atCenterOf(settlement.bell()), BELL_RANGE);
	}

	/** Keeps the mayor's stored name up to date, in case they changed it. */
	public static void refreshName(Settlement settlement, ServerPlayer player) {
		Leadership leadership = settlement.leadership();
		String name = player.getPlainTextName();
		if (leadership.isMayor(player.getUUID()) && !leadership.mayorName().equals(name)) {
			leadership.setMayor(player.getUUID(), name);
		}
	}

	/** Sends a message to every player in or near the village. */
	public static void announce(ServerLevel level, Settlement settlement, Component message) {
		for (ServerPlayer player : level.players()) {
			if (isNearVillage(player, level, settlement)) {
				player.sendSystemMessage(message);
			}
		}
	}

	private static boolean isNearVillage(ServerPlayer player, ServerLevel level, Settlement settlement) {
		return player.level() == level && settlement.bounds().inflatedBy(NEWS_RANGE).isInside(player.blockPosition());
	}
}
