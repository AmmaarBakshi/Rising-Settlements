package com.risingsettlements.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.growth.GrowthPlanner;
import com.risingsettlements.mayor.BellInteraction;
import com.risingsettlements.mayor.MayorOffice;
import com.risingsettlements.mayor.Reputation;
import com.risingsettlements.network.MayorNetworking;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.Leadership;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.work.BuildJob;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * {@code /settlement mayor ...} and {@code /settlement reputation ...}.
 *
 * <p>The mayor normally uses the bell and the mayor screen. These commands do the same
 * things in chat, for players who joined without the mod on their client.
 *
 * <ul>
 *     <li>{@code /settlement mayor}: everyone. Opens the mayor screen, or says who the mayor is.</li>
 *     <li>{@code /settlement mayor claim}: everyone, near the bell. Claims the bell.</li>
 *     <li>{@code /settlement mayor request <project>}, {@code auto}, {@code pause}, {@code resume},
 *         {@code resign}: the mayor, near the bell.</li>
 *     <li>{@code /settlement mayor remove}: operators. Removes the nearest village's mayor.</li>
 *     <li>{@code /settlement reputation <players> <amount>}: operators. Adds (or with a
 *         negative amount, takes away) reputation in the nearest village.</li>
 * </ul>
 */
final class MayorCommand {
	private MayorCommand() {
	}

	@FunctionalInterface
	private interface MayorAction {
		/** Does the action and returns the reply for the mayor, or null for none. */
		@Nullable Component run(ServerLevel level, Settlement settlement, ServerPlayer mayor);
	}

	static LiteralArgumentBuilder<CommandSourceStack> mayor() {
		LiteralArgumentBuilder<CommandSourceStack> request = Commands.literal("request");
		for (BuildJob.Kind kind : MayorNetworking.PROJECTS) {
			request.then(Commands.literal(kind.id()).executes(context -> manage(context, (level, settlement, mayor) -> {
				if (!GrowthPlanner.isEnabled(kind, RSConfig.get())) {
					return Lang.tr("command.grow.not_allowed");
				}
				settlement.growth().setRequested(kind);
				return Lang.tr("mayor.request.done", settlement.name(), SettlementCommand.projectName(kind));
			})));
		}
		return Commands.literal("mayor")
				.executes(MayorCommand::open)
				.then(Commands.literal("claim").executes(MayorCommand::claim))
				.then(request)
				.then(Commands.literal("auto").executes(context -> manage(context, (level, settlement, mayor) -> {
					settlement.growth().setRequested(null);
					return Lang.tr("mayor.request.auto", settlement.name());
				})))
				.then(Commands.literal("pause").executes(context -> manage(context, (level, settlement, mayor) -> {
					GrowthPlanner.setPaused(level, settlement, true);
					return Lang.tr("command.pause.result", settlement.name());
				})))
				.then(Commands.literal("resume").executes(context -> manage(context, (level, settlement, mayor) -> {
					GrowthPlanner.setPaused(level, settlement, false);
					return Lang.tr("command.resume.result", settlement.name());
				})))
				.then(Commands.literal("resign").executes(context -> manage(context, (level, settlement, mayor) -> {
					MayorOffice.resign(level, settlement, mayor);
					return null;
				})))
				.then(Commands.literal("remove")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(MayorCommand::remove));
	}

	static LiteralArgumentBuilder<CommandSourceStack> reputation() {
		return Commands.literal("reputation")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("players", EntityArgument.players())
						.then(Commands.argument("amount", IntegerArgumentType.integer(-10000, 10000))
								.executes(MayorCommand::addReputation)));
	}

	private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Settlement settlement = SettlementCommand.nearest(source);
		if (settlement == null) {
			return 0;
		}
		if (MayorOffice.canManage(player, settlement)) {
			MayorNetworking.open(player, settlement);
			return 1;
		}
		source.sendSuccess(() -> BellInteraction.whoIsMayor(settlement, player), false);
		if (settlement.leadership().isMayor(player.getUUID())) {
			source.sendFailure(Lang.tr("mayor.too_far", settlement.name()));
		}
		return 0;
	}

	private static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Settlement settlement = SettlementCommand.nearest(source);
		if (settlement == null) {
			return 0;
		}
		if (!isNearBell(player, settlement)) {
			source.sendFailure(Lang.tr("mayor.too_far", settlement.name()));
			return 0;
		}
		BellInteraction.claimOrOpen(source.getLevel(), settlement, player);
		return settlement.leadership().isMayor(player.getUUID()) ? 1 : 0;
	}

	/** Runs a mayor-only action after checking that the player is the mayor and near the bell. */
	private static int manage(CommandContext<CommandSourceStack> context, MayorAction action) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Settlement settlement = SettlementCommand.nearest(source);
		if (settlement == null) {
			return 0;
		}
		if (!settlement.leadership().isMayor(player.getUUID())) {
			source.sendFailure(Lang.tr("mayor.not_mayor", settlement.name()));
			return 0;
		}
		if (!MayorOffice.canManage(player, settlement)) {
			source.sendFailure(Lang.tr("mayor.too_far", settlement.name()));
			return 0;
		}
		Component result = action.run(source.getLevel(), settlement, player);
		if (result != null) {
			source.sendSuccess(() -> result, false);
		}
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		Settlement settlement = SettlementCommand.nearest(source);
		if (settlement == null) {
			return 0;
		}
		Leadership leadership = settlement.leadership();
		if (!leadership.hasMayor()) {
			source.sendFailure(Lang.tr("mayor.none", settlement.name()));
			return 0;
		}
		String name = leadership.mayorName();
		MayorOffice.removeMayor(source.getLevel(), settlement, "mayor.removed");
		source.sendSuccess(() -> Lang.tr("mayor.removed", name, settlement.name()), true);
		return 1;
	}

	private static int addReputation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		Settlement settlement = SettlementCommand.nearest(source);
		if (settlement == null) {
			return 0;
		}
		for (ServerPlayer player : players) {
			Reputation.change(source.getLevel(), settlement, player, amount);
		}
		source.sendSuccess(() -> Lang.tr("command.reputation.result", players.size(), settlement.name(), amount), true);
		return players.size();
	}

	private static boolean isNearBell(ServerPlayer player, Settlement settlement) {
		return player.position().closerThan(Vec3.atCenterOf(settlement.bell()), MayorOffice.BELL_RANGE);
	}
}
