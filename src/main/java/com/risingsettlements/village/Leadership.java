package com.risingsettlements.village;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Who leads a village and how much the villagers trust each player.
 *
 * <p>Players earn reputation by trading with the villagers, defending the village and
 * donating to it. Hurting the village costs reputation. A player with enough reputation
 * can claim the bell and become the village's mayor; there is only ever one mayor.
 */
public final class Leadership {
	/** Reputation never goes outside this range, so it can't be farmed forever. */
	public static final int MIN_REPUTATION = -1000;
	public static final int MAX_REPUTATION = 10000;

	private final Settlement owner;
	private final Object2IntOpenHashMap<UUID> reputation = new Object2IntOpenHashMap<>();
	private @Nullable UUID mayor;
	/** The mayor's name when they took office, so it can be shown while they are offline. */
	private String mayorName = "";

	Leadership(Settlement owner) {
		this.owner = owner;
	}

	public int reputation(UUID player) {
		return reputation.getInt(player);
	}

	/** Changes a player's reputation and returns the new value. */
	public int addReputation(UUID player, int amount) {
		int value = Math.max(MIN_REPUTATION, Math.min(MAX_REPUTATION, reputation.getInt(player) + amount));
		reputation.put(player, value);
		owner.markDirty();
		return value;
	}

	public @Nullable UUID mayor() {
		return mayor;
	}

	public String mayorName() {
		return mayorName;
	}

	public boolean hasMayor() {
		return mayor != null;
	}

	public boolean isMayor(UUID player) {
		return player.equals(mayor);
	}

	public void setMayor(UUID player, String name) {
		mayor = player;
		mayorName = name;
		owner.markDirty();
	}

	public void clearMayor() {
		mayor = null;
		mayorName = "";
		owner.markDirty();
	}

	// ------------------------------------------------------------------ saving

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		if (mayor != null) {
			tag.store("mayor", UUIDUtil.CODEC, mayor);
			tag.putString("mayor_name", mayorName);
		}
		ListTag list = new ListTag();
		for (Object2IntMap.Entry<UUID> entry : reputation.object2IntEntrySet()) {
			CompoundTag player = new CompoundTag();
			player.store("player", UUIDUtil.CODEC, entry.getKey());
			player.putInt("value", entry.getIntValue());
			list.add(player);
		}
		tag.put("reputation", list);
		return tag;
	}

	void load(CompoundTag tag) {
		mayor = tag.read("mayor", UUIDUtil.CODEC).orElse(null);
		mayorName = tag.getStringOr("mayor_name", "");
		for (Tag entry : tag.getListOrEmpty("reputation")) {
			if (entry instanceof CompoundTag player) {
				player.read("player", UUIDUtil.CODEC).ifPresent(id -> reputation.put(id, player.getIntOr("value", 0)));
			}
		}
	}
}
