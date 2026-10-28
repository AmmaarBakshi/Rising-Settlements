package com.risingsettlements.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.risingsettlements.RisingSettlements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All settlements of one dimension, saved in the world folder as
 * {@code <dimension>/data/rising_settlements/settlements.dat}.
 *
 * <p>Get it with {@link #get(ServerLevel)}. Because it is normal vanilla saved data, it is
 * written whenever the world saves and survives restarts.
 */
public final class SettlementManager extends SavedData {
	private static final int FORMAT_VERSION = 1;

	public static final Codec<SettlementManager> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("format", FORMAT_VERSION).forGetter(m -> FORMAT_VERSION),
			Settlement.CODEC.listOf().optionalFieldOf("settlements", List.of()).forGetter(m -> List.copyOf(m.settlements.values()))
	).apply(i, (format, list) -> new SettlementManager(list)));

	/**
	 * The data fixer type is {@code null}: this is mod data, so vanilla has no fixes for it.
	 * Fabric API makes a {@code null} type safe.
	 */
	public static final SavedDataType<SettlementManager> TYPE =
			new SavedDataType<>(RisingSettlements.id("settlements"), SettlementManager::new, CODEC, null);

	private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();

	public SettlementManager() {
	}

	private SettlementManager(List<Settlement> loaded) {
		for (Settlement settlement : loaded) {
			settlements.put(settlement.id(), settlement);
		}
	}

	public static SettlementManager get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public Collection<Settlement> all() {
		return Collections.unmodifiableCollection(settlements.values());
	}

	public @Nullable Settlement byId(UUID id) {
		return settlements.get(id);
	}

	public void add(Settlement settlement) {
		settlements.put(settlement.id(), settlement);
		setDirty();
	}

	public void remove(Settlement settlement) {
		settlements.remove(settlement.id());
		setDirty();
	}

	/** The settlement whose border contains this position, if any. */
	public @Nullable Settlement findContaining(BlockPos pos) {
		for (Settlement settlement : settlements.values()) {
			if (settlement.contains(pos)) {
				return settlement;
			}
		}
		return null;
	}

	/** The settlement with the closest bell, within {@code maxDistance} blocks. */
	public @Nullable Settlement findNearest(BlockPos pos, int maxDistance) {
		Settlement best = null;
		double bestDist = (double) maxDistance * maxDistance;
		for (Settlement settlement : settlements.values()) {
			double dist = settlement.bell().distSqr(pos);
			if (dist <= bestDist) {
				best = settlement;
				bestDist = dist;
			}
		}
		return best;
	}

	/** All settlements within {@code radius} blocks (horizontally) of a position. */
	public List<Settlement> findAround(BlockPos pos, int radius) {
		return settlements.values().stream().filter(s -> s.isWithin(pos, radius)).toList();
	}

	// ------------------------------------------------------------------ dirty tracking

	@Override
	public boolean isDirty() {
		if (super.isDirty()) {
			return true;
		}
		for (Settlement settlement : settlements.values()) {
			if (settlement.isDirty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void setDirty(boolean dirty) {
		super.setDirty(dirty);
		if (!dirty) {
			// Called by the game right after saving.
			settlements.values().forEach(Settlement::clearDirty);
		}
	}
}
