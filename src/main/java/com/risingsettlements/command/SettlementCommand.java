package com.risingsettlements.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.risingsettlements.build.BuildPlan;
import com.risingsettlements.build.GrowthPlanner;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.mayor.MayorOffice;
import com.risingsettlements.util.Texts;
import com.risingsettlements.village.Project;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import com.risingsettlements.village.VillageDiscovery;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@code /settlement}: look at villages, and manage them as mayor or operator.
 *
 * <ul>
 *   <li>{@code info}, {@code list}: anyone</li>
 *   <li>{@code menu}, {@code project}, {@code pause}, {@code resume}, {@code mayor claim|resign}:
 *       the village's mayor (operators may use them too)</li>
 *   <li>{@code scan}, {@code grow}, {@code repair}, {@code forget}, {@code mayor clear}: operators</li>
 * </ul>
 *
 * The village a command acts on is the one the player is standing in, or else the one
 * with the nearest bell.
 */
public final class SettlementCommand {
	private SettlementCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("settlement")
				.then(Commands.literal("info").executes(SettlementCommand::info))
				.then(Commands.literal("list").executes(SettlementCommand::list))
				.then(Commands.literal("menu").executes(ctx -> mayorAction(ctx, "refresh")))
				.then(Commands.literal("pause").executes(ctx -> mayorAction(ctx, "pause")))
				.then(Commands.literal("resume").executes(ctx -> mayorAction(ctx, "resume")))
				.then(Commands.literal("project")
						.then(Commands.argument("project", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										Arrays.stream(Project.values()).map(Project::id), builder))
								.executes(ctx -> mayorAction(ctx, "project:" + Project.byId(StringArgumentType.getString(ctx, "project")).id()))))
				.then(Commands.literal("mayor")
						.then(Commands.literal("claim").executes(SettlementCommand::claim))
						.then(Commands.literal("resign").executes(ctx -> mayorAction(ctx, "resign")))
						.then(Commands.literal("clear")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(SettlementCommand::clearMayor)))
				.then(Commands.literal("scan")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::scan))
				.then(Commands.literal("grow")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::grow))
				.then(Commands.literal("repair")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::repair))
				.then(Commands.literal("forget")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SettlementCommand::forget)));
	}

	// ------------------------------------------------------------------ everyone

	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Settlement s = nearby(source);
		if (s == null) {
			return 0;
		}
		ServerLevel level = source.getLevel();
		RSConfig config = RSConfig.get();
		List<Component> lines = new ArrayList<>();
		lines.add(Texts.tr("info.header", s.name(), Texts.tr("style." + s.style().id()), s.bell().toShortString())
				.withStyle(ChatFormatting.GOLD));
		lines.add(Texts.tr("info.population", s.villagers(), s.beds()));
		lines.add(s.isSurveyed()
				? Texts.tr("info.blocks", s.blueprint().size(), s.playerBlockCount())
				: Texts.tr("info.surveying", s.blueprint().size()));
		lines.add(Texts.tr("info.built", s.housesBuilt(), s.farmsBuilt(), s.roadsBuilt(),
				s.blueprint().count(state -> state.is(Blocks.FARMLAND))));

		BuildPlan plan = s.activePlan();
		if (plan != null) {
			lines.add(Texts.tr("info.building", Texts.tr("project." + plan.kind().id()), plan.progressPercent()));
		} else if (s.isGrowthPaused()) {
			lines.add(Texts.tr("info.paused"));
		} else if (s.villagers() < config.minVillagersToGrow) {
			lines.add(Texts.tr("info.too_few", config.minVillagersToGrow));
		} else {
			long left = Math.max(0, s.lastGrowthAt() + config.growthIntervalTicks - level.getGameTime());
			lines.add(Texts.tr("info.next", Texts.tr("project." + s.project().id()), MayorOffice.formatTicks(left)));
		}
		if (s.runtime().repairs.pending() > 0) {
			lines.add(Texts.tr("info.repairs", s.runtime().repairs.pending()));
		}

		if (s.mayor() != null) {
			lines.add(Texts.tr("info.mayor", s.mayorName()));
		} else if (config.enableMayor) {
			lines.add(Texts.tr("info.no_mayor"));
		}
		if (config.enableMayor && source.getPlayer() != null) {
			lines.add(Texts.tr("info.reputation", MayorOffice.reputation(level, s, source.getPlayer()), config.mayorReputation));
		}
		lines.forEach(line -> source.sendSuccess(() -> line, false));
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Collection<Settlement> all = SettlementManager.get(source.getLevel()).all();
		if (all.isEmpty()) {
			source.sendSuccess(() -> Texts.tr("list.empty"), false);
			return 0;
		}
		source.sendSuccess(() -> Texts.tr("list.header", all.size()).withStyle(ChatFormatting.GOLD), false);
		for (Settlement s : all) {
			Component line = Texts.tr("list.entry", s.name(), Texts.tr("style." + s.style().id()), s.bell().toShortString(),
					s.villagers(), s.mayor() == null ? "-" : s.mayorName());
			source.sendSuccess(() -> line, false);
		}
		return all.size();
	}

	// ------------------------------------------------------------------ mayor

	private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Settlement s = nearby(ctx.getSource());
		return s != null && MayorOffice.claim(player, s) ? 1 : 0;
	}

	private static int mayorAction(CommandContext<CommandSourceStack> ctx, String action) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Settlement s = nearby(ctx.getSource());
		if (s == null) {
			return 0;
		}
		MayorOffice.perform(player, s, action);
		return 1;
	}

	// ------------------------------------------------------------------ operators

	private static int clearMayor(CommandContext<CommandSourceStack> ctx) {
		Settlement s = nearby(ctx.getSource());
		if (s == null) {
			return 0;
		}
		s.setMayor(null, "");
		ctx.getSource().sendSuccess(() -> Texts.tr("admin.mayor_cleared", s.name()), true);
		return 1;
	}

	private static int scan(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		List<Settlement> found = VillageDiscovery.scanAround(level, SettlementManager.get(level),
				BlockPos.containing(source.getPosition()), RSConfig.get().discoveryRadius);
		source.sendSuccess(() -> Texts.tr("admin.scanned", found.size()), true);
		return found.size();
	}

	private static int grow(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Settlement s = nearby(source);
		if (s == null) {
			return 0;
		}
		if (!s.isSurveyed()) {
			source.sendFailure(Texts.tr("admin.not_surveyed", s.name()));
			return 0;
		}
		if (s.activePlan() != null) {
			source.sendFailure(Texts.tr("admin.busy", s.name(), Texts.tr("project." + s.activePlan().kind().id())));
			return 0;
		}
		BuildPlan plan = GrowthPlanner.start(source.getLevel(), s);
		if (plan == null) {
			source.sendFailure(Texts.tr("admin.no_site", s.name()));
			return 0;
		}
		source.sendSuccess(() -> Texts.tr("admin.growing", s.name(), Texts.tr("project." + plan.kind().id()), plan.blockCount()), true);
		return 1;
	}

	private static int repair(CommandContext<CommandSourceStack> ctx) {
		Settlement s = nearby(ctx.getSource());
		if (s == null) {
			return 0;
		}
		s.runtime().repairs.requestScan();
		ctx.getSource().sendSuccess(() -> Texts.tr("admin.repair", s.name()), true);
		return 1;
	}

	private static int forget(CommandContext<CommandSourceStack> ctx) {
		Settlement s = nearby(ctx.getSource());
		if (s == null) {
			return 0;
		}
		SettlementManager.get(ctx.getSource().getLevel()).remove(s);
		ctx.getSource().sendSuccess(() -> Texts.tr("admin.forgotten", s.name()), true);
		return 1;
	}

	// ------------------------------------------------------------------ helpers

	private static Settlement nearby(CommandSourceStack source) {
		Settlement s = SettlementManager.get(source.getLevel()).findFor(BlockPos.containing(source.getPosition()));
		if (s == null) {
			source.sendFailure(Texts.tr("command.none_nearby"));
		}
		return s;
	}
}
