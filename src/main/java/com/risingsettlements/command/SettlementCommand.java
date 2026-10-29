package com.risingsettlements.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.growth.GrowthPlanner;
import com.risingsettlements.repair.RepairPlanner;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.GrowthState;
import com.risingsettlements.village.Leadership;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import com.risingsettlements.village.VillageDiscovery;
import com.risingsettlements.work.BuildJob;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The {@code /settlement} command.
 *
 * <ul>
 *     <li>{@code /settlement info}: everyone. Shows the nearest village.</li>
 *     <li>{@code /settlement list}: operators. Lists every known village in this dimension.</li>
 *     <li>{@code /settlement scan}: operators. Looks for villages around you right now.</li>
 *     <li>{@code /settlement repair}: operators. Repairs known damage now, without the usual wait.</li>
 *     <li>{@code /settlement grow [project]}: operators. Starts a growth project now, for free.</li>
 *     <li>{@code /settlement pause} and {@code /settlement resume}: operators. Stops or restarts growth.</li>
 *     <li>{@code /settlement cancel}: operators. Stops the current project; placed blocks stay.</li>
 *     <li>{@code /settlement mayor ...} and {@code /settlement reputation ...}: see {@link MayorCommand}.</li>
 * </ul>
 */
public final class SettlementCommand {
	/** How far away a village may be for {@code /settlement info} to find it. */
	private static final int INFO_RANGE = 256;

	private SettlementCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("settlement")
				.then(Commands.literal("info").executes(SettlementCommand::info))
				.then(Commands.literal("list")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::list))
				.then(Commands.literal("scan")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::scan))
				.then(Commands.literal("repair")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::repair))
				.then(growCommand())
				.then(Commands.literal("pause")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> setPaused(context, true)))
				.then(Commands.literal("resume")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> setPaused(context, false)))
				.then(Commands.literal("cancel")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::cancel))
				.then(MayorCommand.mayor())
				.then(MayorCommand.reputation()));
	}

	/** {@code /settlement grow} lets the village choose; {@code /settlement grow <project>} picks one. */
	private static LiteralArgumentBuilder<CommandSourceStack> growCommand() {
		LiteralArgumentBuilder<CommandSourceStack> grow = Commands.literal("grow")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(context -> grow(context, null));
		for (BuildJob.Kind kind : BuildJob.Kind.values()) {
			if (kind != BuildJob.Kind.REPAIR) {
				grow.then(Commands.literal(kind.id()).executes(context -> grow(context, kind)));
			}
		}
		return grow;
	}

	private static int grow(CommandContext<CommandSourceStack> context, BuildJob.@Nullable Kind kind) {
		CommandSourceStack source = context.getSource();
		Settlement settlement = nearest(source);
		if (settlement == null) {
			return 0;
		}
		BuildJob current = settlement.growth().project();
		if (current != null) {
			source.sendFailure(Lang.tr("command.grow.busy", settlement.name(), projectName(current.kind()), current.percent()));
			return 0;
		}
		ServerLevel level = source.getLevel();
		GrowthPlanner.Result result = GrowthPlanner.startNext(level, settlement, level.getGameTime(), kind, true);
		Component project = result.kind() != null ? projectName(result.kind()) : Component.empty();
		switch (result.outcome()) {
			case STARTED -> source.sendSuccess(() -> Lang.tr("command.grow.started", settlement.name(), project, result.blocks()), true);
			case NO_SPACE -> source.sendFailure(Lang.tr("command.grow.no_space", settlement.name(), project));
			case FULLY_GROWN -> source.sendFailure(Lang.tr("command.grow.fully_grown", settlement.name()));
			case SAVING_UP, NOT_ALLOWED -> source.sendFailure(Lang.tr("command.grow.not_allowed"));
		}
		return result.outcome() == GrowthPlanner.Outcome.STARTED ? 1 : 0;
	}

	private static int setPaused(CommandContext<CommandSourceStack> context, boolean paused) {
		Settlement settlement = nearest(context.getSource());
		if (settlement == null) {
			return 0;
		}
		GrowthPlanner.setPaused(context.getSource().getLevel(), settlement, paused);
		context.getSource().sendSuccess(() -> Lang.tr(paused ? "command.pause.result" : "command.resume.result", settlement.name()), true);
		return 1;
	}

	private static int cancel(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		Settlement settlement = nearest(source);
		if (settlement == null) {
			return 0;
		}
		if (!GrowthPlanner.cancel(source.getLevel(), settlement)) {
			source.sendFailure(Lang.tr("command.cancel.none", settlement.name()));
			return 0;
		}
		source.sendSuccess(() -> Lang.tr("command.cancel.result", settlement.name()), true);
		return 1;
	}

	/** The translated name of a kind of project, like "house" or "lamp posts". */
	public static Component projectName(BuildJob.Kind kind) {
		return Lang.tr("project." + kind.id());
	}

	/** The nearest settlement, or null after telling the player there is none. */
	static @Nullable Settlement nearest(CommandSourceStack source) {
		BlockPos pos = BlockPos.containing(source.getPosition());
		Settlement settlement = SettlementManager.get(source.getLevel()).findNearest(pos, INFO_RANGE);
		if (settlement == null) {
			source.sendFailure(Lang.tr("command.no_village", INFO_RANGE));
		}
		return settlement;
	}

	private static int repair(CommandContext<CommandSourceStack> context) {
		Settlement settlement = nearest(context.getSource());
		if (settlement == null) {
			return 0;
		}
		int count = RepairPlanner.hurry(settlement);
		context.getSource().sendSuccess(() -> Lang.tr("command.repair.result", settlement.name(), count), true);
		return count;
	}

	private static int info(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		Settlement settlement = nearest(source);
		if (settlement == null) {
			return 0;
		}
		for (Component line : describe(source.getLevel(), settlement, BlockPos.containing(source.getPosition()), source.getPlayer())) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	/** The lines shown by {@code /settlement info}; {@code player} is the viewer, if it is a player. */
	public static List<Component> describe(ServerLevel level, Settlement settlement, BlockPos viewer, @Nullable ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		BoundingBox bounds = settlement.bounds();
		BlockPos bell = settlement.bell();
		int distance = (int) Math.sqrt(bell.distSqr(viewer));

		lines.add(Lang.tr("command.info.header", settlement.name(), Lang.tr("style." + settlement.style().id()))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		lines.add(Lang.tr("command.info.bell", bell.getX(), bell.getY(), bell.getZ(), distance));
		lines.add(Lang.tr("command.info.border", bounds.getXSpan(), bounds.getZSpan(),
				bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ()));
		lines.add(Lang.tr("command.info.population", settlement.villagers(), settlement.beds()));
		lines.addAll(describeLeadership(settlement, player));
		lines.add(Lang.tr(settlement.isSurveyed() ? "command.info.blocks" : "command.info.blocks_surveying",
				settlement.blueprint().size()));
		lines.add(Lang.tr("command.info.player_blocks", settlement.playerBlockCount()));
		lines.add(Lang.tr("command.info.repairs", settlement.blocksRepaired(), settlement.runtime().damage.missingCount()));
		BuildJob repairJob = settlement.runtime().repairJob;
		if (repairJob != null) {
			lines.add(Lang.tr("command.info.repairing", repairJob.percent()).withStyle(ChatFormatting.GREEN));
		}
		lines.addAll(describeGrowth(level, settlement));
		return lines;
	}

	/** Who the mayor is, and the viewer's reputation. */
	private static List<Component> describeLeadership(Settlement settlement, @Nullable ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		RSConfig config = RSConfig.get();
		if (!config.mayorEnabled) {
			return lines;
		}
		Leadership leadership = settlement.leadership();
		lines.add(leadership.hasMayor()
				? Lang.tr("command.info.mayor", leadership.mayorName()).withStyle(ChatFormatting.AQUA)
				: Lang.tr("command.info.no_mayor", config.mayorReputation));
		if (player != null) {
			lines.add(Lang.tr("command.info.reputation", leadership.reputation(player.getUUID())));
		}
		return lines;
	}

	/** Resources, the current project and what the village has built. */
	private static List<Component> describeGrowth(ServerLevel level, Settlement settlement) {
		List<Component> lines = new ArrayList<>();
		RSConfig config = RSConfig.get();
		GrowthState growth = settlement.growth();
		if (!config.growthEnabled) {
			lines.add(Lang.tr("command.info.growth_disabled").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		lines.add(Lang.tr("command.info.resources", growth.resources(), settlement.villagers() * config.resourcesPerVillagerPerDay));

		BuildJob project = growth.project();
		if (project != null) {
			lines.add(Lang.tr("command.info.project", projectName(project.kind()), project.percent()).withStyle(ChatFormatting.GREEN));
		} else if (!growth.isPaused()) {
			long minutes = Math.max(0, growth.nextProjectTime() - level.getGameTime()) / 1200;
			lines.add(Lang.tr("command.info.next_project", minutes));
		}
		if (growth.isPaused()) {
			lines.add(Lang.tr("command.info.paused").withStyle(ChatFormatting.YELLOW));
		}
		Component note = noteText(growth.note());
		if (note != null && project == null) {
			lines.add(note.copy().withStyle(ChatFormatting.GRAY));
		}
		lines.add(Lang.tr("command.info.built",
				growth.completed(BuildJob.Kind.HOUSE), growth.completed(BuildJob.Kind.FARM), growth.completed(BuildJob.Kind.ROAD),
				growth.completed(BuildJob.Kind.WELL), growth.completed(BuildJob.Kind.LAMPS), growth.completed(BuildJob.Kind.WALL)));
		return lines;
	}

	/** Explains why the village isn't building, from the note the growth planner left. */
	public static @Nullable Component noteText(String note) {
		if (note.isEmpty()) {
			return null;
		}
		String[] parts = note.split(":", 2);
		if (parts.length == 2) {
			BuildJob.Kind kind = BuildJob.Kind.byId(parts[1]);
			return Lang.tr("growth.note." + parts[0], projectName(kind), GrowthPlanner.cost(kind, RSConfig.get()));
		}
		return Lang.tr("growth.note." + note);
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		Collection<Settlement> all = SettlementManager.get(source.getLevel()).all();
		source.sendSuccess(() -> Lang.tr("command.list.header", all.size()).withStyle(ChatFormatting.GOLD), false);
		for (Settlement settlement : all) {
			BlockPos bell = settlement.bell();
			source.sendSuccess(() -> Lang.tr("command.list.entry", settlement.name(),
					Lang.tr("style." + settlement.style().id()), bell.getX(), bell.getY(), bell.getZ(), settlement.villagers()), false);
		}
		return all.size();
	}

	private static int scan(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		List<Settlement> found = VillageDiscovery.scanAround(level, SettlementManager.get(level), pos, RSConfig.get().discoveryRadius);
		source.sendSuccess(() -> Lang.tr("command.scan.result", found.size()), true);
		return found.size();
	}
}
