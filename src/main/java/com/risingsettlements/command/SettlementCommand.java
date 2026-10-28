package com.risingsettlements.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import com.risingsettlements.village.VillageDiscovery;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
						.executes(SettlementCommand::scan)));
	}

	private static int info(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		BlockPos pos = BlockPos.containing(source.getPosition());
		Settlement settlement = SettlementManager.get(source.getLevel()).findNearest(pos, INFO_RANGE);
		if (settlement == null) {
			source.sendFailure(Lang.tr("command.no_village", INFO_RANGE));
			return 0;
		}
		for (Component line : describe(source.getLevel(), settlement, pos)) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	/** The lines shown by {@code /settlement info}. */
	public static List<Component> describe(ServerLevel level, Settlement settlement, BlockPos viewer) {
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
		lines.add(Lang.tr(settlement.isSurveyed() ? "command.info.blocks" : "command.info.blocks_surveying",
				settlement.blueprint().size()));
		lines.add(Lang.tr("command.info.player_blocks", settlement.playerBlockCount()));
		return lines;
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
