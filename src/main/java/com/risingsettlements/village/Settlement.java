package com.risingsettlements.village;

import com.mojang.serialization.Codec;
import com.risingsettlements.config.RSConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.UUID;

/**
 * Everything the mod knows about one village.
 *
 * <p>A settlement is centred on its bell. Its {@link #bounds() bounds} cover the original
 * village plus a margin, and grow as the village builds. The {@link #blueprint()} lists
 * every block that belongs to the village, and {@link #isPlayerBlock player blocks} are
 * positions the mod must never touch.
 */
public final class Settlement {
	/** Saves are written as compound tags so new fields can be added without breaking old worlds. */
	public static final Codec<Settlement> CODEC = CompoundTag.CODEC.xmap(Settlement::load, Settlement::save);

	private final UUID id;
	private final String name;
	private final BlockPos bell;
	private BoundingBox bounds;
	private final VillageStyle style;
	private final long foundedAt;
	private final Blueprint blueprint;
	private final LongOpenHashSet playerBlocks;

	/** True once all blocks of the original village have been recorded in the blueprint. */
	private boolean surveyed;
	private int villagers;
	private int beds;
	private int blocksRepaired;

	/** Set whenever something worth saving changes; cleared after the world saves. */
	private boolean dirty;

	/** Work in progress that is not saved (see {@link SettlementRuntime}). */
	private final SettlementRuntime runtime = new SettlementRuntime();

	/** Resources, current project and everything built so far. */
	private final GrowthState growth = new GrowthState(this);

	private Settlement(UUID id, String name, BlockPos bell, BoundingBox bounds, VillageStyle style, long foundedAt,
					   Blueprint blueprint, LongOpenHashSet playerBlocks) {
		this.id = id;
		this.name = name;
		this.bell = bell.immutable();
		this.bounds = bounds;
		this.style = style;
		this.foundedAt = foundedAt;
		this.blueprint = blueprint;
		this.playerBlocks = playerBlocks;
	}

	public static Settlement create(String name, BlockPos bell, BoundingBox bounds, VillageStyle style, long gameTime) {
		Settlement settlement = new Settlement(UUID.randomUUID(), name, bell, bounds, style, gameTime,
				new Blueprint(), new LongOpenHashSet());
		settlement.growth.initNew(RSConfig.get().startingResources);
		settlement.dirty = true;
		return settlement;
	}

	// ------------------------------------------------------------------ basic data

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public BlockPos bell() {
		return bell;
	}

	public BoundingBox bounds() {
		return bounds;
	}

	public void setBounds(BoundingBox bounds) {
		this.bounds = bounds;
		markDirty();
	}

	public VillageStyle style() {
		return style;
	}

	public long foundedAt() {
		return foundedAt;
	}

	public Blueprint blueprint() {
		return blueprint;
	}

	public SettlementRuntime runtime() {
		return runtime;
	}

	public GrowthState growth() {
		return growth;
	}

	public boolean isSurveyed() {
		return surveyed;
	}

	public void setSurveyed(boolean surveyed) {
		this.surveyed = surveyed;
		markDirty();
	}

	public int villagers() {
		return villagers;
	}

	public int beds() {
		return beds;
	}

	public void setCensus(int villagers, int beds) {
		if (this.villagers != villagers || this.beds != beds) {
			this.villagers = villagers;
			this.beds = beds;
			markDirty();
		}
	}

	public int blocksRepaired() {
		return blocksRepaired;
	}

	public void addBlocksRepaired(int count) {
		blocksRepaired += count;
		markDirty();
	}

	// ------------------------------------------------------------------ areas

	/** Inside the village's current border. */
	public boolean contains(BlockPos pos) {
		return bounds.isInside(pos);
	}

	/** Horizontal (square) distance check around the bell, ignoring height. */
	public boolean isWithin(BlockPos pos, int radius) {
		return Math.abs(pos.getX() - bell.getX()) <= radius && Math.abs(pos.getZ() - bell.getZ()) <= radius;
	}

	// ------------------------------------------------------------------ player blocks

	public boolean isPlayerBlock(BlockPos pos) {
		return playerBlocks.contains(pos.asLong());
	}

	public boolean isPlayerBlock(long pos) {
		return playerBlocks.contains(pos);
	}

	public void addPlayerBlock(BlockPos pos) {
		if (playerBlocks.add(pos.asLong())) {
			markDirty();
		}
	}

	public void removePlayerBlock(BlockPos pos) {
		if (playerBlocks.remove(pos.asLong())) {
			markDirty();
		}
	}

	public int playerBlockCount() {
		return playerBlocks.size();
	}

	// ------------------------------------------------------------------ dirty tracking

	public void markDirty() {
		dirty = true;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void clearDirty() {
		dirty = false;
	}

	// ------------------------------------------------------------------ saving

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.store("id", UUIDUtil.CODEC, id);
		tag.putString("name", name);
		tag.store("bell", BlockPos.CODEC, bell);
		tag.store("bounds", BoundingBox.CODEC, bounds);
		tag.putString("style", style.id());
		tag.putLong("founded_at", foundedAt);
		tag.putBoolean("surveyed", surveyed);
		tag.putInt("villagers", villagers);
		tag.putInt("beds", beds);
		tag.putInt("blocks_repaired", blocksRepaired);
		tag.put("blueprint", blueprint.save());
		tag.putLongArray("player_blocks", playerBlocks.toLongArray());
		tag.put("growth", growth.save());
		return tag;
	}

	private static Settlement load(CompoundTag tag) {
		BlockPos bell = tag.read("bell", BlockPos.CODEC).orElse(BlockPos.ZERO);
		Settlement settlement = new Settlement(
				tag.read("id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID),
				tag.getStringOr("name", "Village"),
				bell,
				tag.read("bounds", BoundingBox.CODEC).orElseGet(() -> new BoundingBox(bell).inflatedBy(32)),
				VillageStyle.byId(tag.getStringOr("style", "plains")),
				tag.getLongOr("founded_at", 0L),
				Blueprint.load(tag.getCompoundOrEmpty("blueprint")),
				new LongOpenHashSet(tag.getLongArray("player_blocks").orElse(new long[0])));
		settlement.surveyed = tag.getBooleanOr("surveyed", false);
		settlement.villagers = tag.getIntOr("villagers", 0);
		settlement.beds = tag.getIntOr("beds", 0);
		settlement.blocksRepaired = tag.getIntOr("blocks_repaired", 0);
		settlement.growth.load(tag.getCompoundOrEmpty("growth"), RSConfig.get().startingResources);
		return settlement;
	}
}
