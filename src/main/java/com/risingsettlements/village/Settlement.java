package com.risingsettlements.village;

import com.mojang.serialization.Codec;
import com.risingsettlements.build.BuildPlan;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

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

	/** The player who claimed the bell, if any. */
	private @Nullable UUID mayor;
	private String mayorName = "";
	/** What the mayor asked the village to build next. */
	private Project project = Project.AUTO;
	private boolean growthPaused;
	/** Game time when the last growth project started (or the village was found). */
	private long lastGrowthAt;
	private int housesBuilt;
	private int farmsBuilt;
	private int roadsBuilt;
	/** The project being built right now. Saved, so a half-built house is finished after a restart. */
	private @Nullable BuildPlan activePlan;

	/** Set whenever something worth saving changes; cleared after the world saves. */
	private boolean dirty;

	/** Work in progress that is not saved (see {@link SettlementRuntime}). */
	private final SettlementRuntime runtime = new SettlementRuntime();

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
		this.lastGrowthAt = foundedAt;
	}

	public static Settlement create(String name, BlockPos bell, BoundingBox bounds, VillageStyle style, long gameTime) {
		Settlement settlement = new Settlement(UUID.randomUUID(), name, bell, bounds, style, gameTime,
				new Blueprint(), new LongOpenHashSet());
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

	// ------------------------------------------------------------------ mayor and growth

	public @Nullable UUID mayor() {
		return mayor;
	}

	public String mayorName() {
		return mayorName;
	}

	public boolean isMayor(UUID player) {
		return player.equals(mayor);
	}

	public void setMayor(@Nullable UUID mayor, String name) {
		this.mayor = mayor;
		this.mayorName = mayor == null ? "" : name;
		markDirty();
	}

	public Project project() {
		return project;
	}

	public void setProject(Project project) {
		this.project = project;
		markDirty();
	}

	public boolean isGrowthPaused() {
		return growthPaused;
	}

	public void setGrowthPaused(boolean paused) {
		this.growthPaused = paused;
		markDirty();
	}

	public long lastGrowthAt() {
		return lastGrowthAt;
	}

	public void setLastGrowthAt(long gameTime) {
		this.lastGrowthAt = gameTime;
		markDirty();
	}

	public int housesBuilt() {
		return housesBuilt;
	}

	public int farmsBuilt() {
		return farmsBuilt;
	}

	public int roadsBuilt() {
		return roadsBuilt;
	}

	/** Counts a finished project. */
	public void recordBuilt(BuildPlan.Kind kind) {
		switch (kind) {
			case HOUSE -> housesBuilt++;
			case FARM -> farmsBuilt++;
			case ROAD -> roadsBuilt++;
		}
		markDirty();
	}

	public @Nullable BuildPlan activePlan() {
		return activePlan;
	}

	public void setActivePlan(@Nullable BuildPlan plan) {
		this.activePlan = plan;
		markDirty();
	}

	// ------------------------------------------------------------------ areas

	/** Inside the village's current border. */
	public boolean contains(BlockPos pos) {
		return bounds.isInside(pos);
	}

	/**
	 * Grows the border to include a newly built area plus the village margin, but never
	 * past {@code maxRadius} blocks from the bell.
	 */
	public void expandToInclude(BoundingBox area, int margin, int maxRadius) {
		BoundingBox grown = BoundingBox.encapsulating(bounds, area.inflatedBy(margin));
		setBounds(new BoundingBox(
				Math.max(grown.minX(), bell.getX() - maxRadius),
				grown.minY(),
				Math.max(grown.minZ(), bell.getZ() - maxRadius),
				Math.min(grown.maxX(), bell.getX() + maxRadius),
				grown.maxY(),
				Math.min(grown.maxZ(), bell.getZ() + maxRadius)));
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
		tag.put("blueprint", blueprint.save());
		tag.putLongArray("player_blocks", playerBlocks.toLongArray());
		if (mayor != null) {
			tag.store("mayor", UUIDUtil.CODEC, mayor);
			tag.putString("mayor_name", mayorName);
		}
		tag.putString("project", project.id());
		tag.putBoolean("growth_paused", growthPaused);
		tag.putLong("last_growth_at", lastGrowthAt);
		tag.putInt("houses_built", housesBuilt);
		tag.putInt("farms_built", farmsBuilt);
		tag.putInt("roads_built", roadsBuilt);
		if (activePlan != null) {
			tag.put("active_plan", activePlan.save());
		}
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
		settlement.mayor = tag.read("mayor", UUIDUtil.CODEC).orElse(null);
		settlement.mayorName = tag.getStringOr("mayor_name", "");
		settlement.project = Project.byId(tag.getStringOr("project", "auto"));
		settlement.growthPaused = tag.getBooleanOr("growth_paused", false);
		settlement.lastGrowthAt = tag.getLongOr("last_growth_at", settlement.foundedAt);
		settlement.housesBuilt = tag.getIntOr("houses_built", 0);
		settlement.farmsBuilt = tag.getIntOr("farms_built", 0);
		settlement.roadsBuilt = tag.getIntOr("roads_built", 0);
		settlement.activePlan = tag.getCompound("active_plan").map(BuildPlan::load).orElse(null);
		return settlement;
	}
}
