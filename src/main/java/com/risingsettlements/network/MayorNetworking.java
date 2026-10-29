package com.risingsettlements.network;

import com.risingsettlements.command.SettlementCommand;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.growth.GrowthPlanner;
import com.risingsettlements.mayor.MayorOffice;
import com.risingsettlements.util.Lang;
import com.risingsettlements.village.GrowthState;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import com.risingsettlements.work.BuildJob;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the mayor screen: sends its data and carries out the buttons.
 *
 * <p>Players without the mod on their client can't open the screen; they get the same
 * information in chat and can use {@code /settlement mayor ...} instead.
 */
public final class MayorNetworking {
	/** The projects a mayor can pick, in the order the screen shows them. */
	public static final List<BuildJob.Kind> PROJECTS = List.of(
			BuildJob.Kind.HOUSE, BuildJob.Kind.FARM, BuildJob.Kind.ROAD,
			BuildJob.Kind.WELL, BuildJob.Kind.LAMPS, BuildJob.Kind.WALL);

	private MayorNetworking() {
	}

	/** Registers both payloads (needed on client and server) and the server's button handler. */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MayorScreenPayload.TYPE, MayorScreenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MayorActionPayload.TYPE, MayorActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MayorActionPayload.TYPE,
				(payload, context) -> handle(context.player(), payload));
	}

	/** Opens the mayor screen, or shows the same information in chat if the client lacks the mod. */
	public static void open(ServerPlayer player, Settlement settlement) {
		ServerLevel level = player.level();
		if (ServerPlayNetworking.canSend(player, MayorScreenPayload.TYPE)) {
			ServerPlayNetworking.send(player, data(level, settlement, player));
			return;
		}
		for (Component line : SettlementCommand.describe(level, settlement, player.blockPosition(), player)) {
			player.sendSystemMessage(line);
		}
		player.sendSystemMessage(Lang.tr("mayor.no_screen").withStyle(ChatFormatting.GRAY));
	}

	private static void handle(ServerPlayer player, MayorActionPayload payload) {
		ServerLevel level = player.level();
		Settlement settlement = SettlementManager.get(level).byId(payload.settlement());
		if (settlement == null) {
			return;
		}
		if (!MayorOffice.canManage(player, settlement)) {
			// Not (or no longer) the mayor, or walked away: the screen turns read-only.
			ServerPlayNetworking.send(player, data(level, settlement, player));
			return;
		}
		switch (payload.action()) {
			case REQUEST -> {
				BuildJob.Kind kind = BuildJob.Kind.byId(payload.project());
				if (PROJECTS.contains(kind) && GrowthPlanner.isEnabled(kind, RSConfig.get())) {
					settlement.growth().setRequested(kind);
				}
			}
			case CLEAR_REQUEST -> settlement.growth().setRequested(null);
			case PAUSE -> GrowthPlanner.setPaused(level, settlement, true);
			case RESUME -> GrowthPlanner.setPaused(level, settlement, false);
			case RESIGN -> {
				MayorOffice.resign(level, settlement, player);
				return;
			}
			case REFRESH -> {
			}
		}
		ServerPlayNetworking.send(player, data(level, settlement, player));
	}

	/** Everything the screen shows, as seen by {@code viewer}. */
	public static MayorScreenPayload data(ServerLevel level, Settlement settlement, ServerPlayer viewer) {
		RSConfig config = RSConfig.get();
		GrowthState growth = settlement.growth();
		BuildJob project = growth.project();
		BuildJob repairs = settlement.runtime().repairJob;

		Component status;
		if (!config.growthEnabled) {
			status = Lang.tr("command.info.growth_disabled");
		} else if (project != null) {
			status = Lang.tr("command.info.project", SettlementCommand.projectName(project.kind()), project.percent());
		} else if (growth.isPaused()) {
			status = Lang.tr("command.info.paused");
		} else if (repairs != null) {
			status = Lang.tr("command.info.repairing", repairs.percent());
		} else {
			long minutes = Math.max(0, growth.nextProjectTime() - level.getGameTime()) / 1200;
			status = Lang.tr("command.info.next_project", minutes);
		}
		Component note = project == null ? SettlementCommand.noteText(growth.note()) : null;

		List<MayorScreenPayload.Project> projects = new ArrayList<>();
		for (BuildJob.Kind kind : PROJECTS) {
			projects.add(new MayorScreenPayload.Project(kind.id(), GrowthPlanner.cost(kind, config),
					growth.completed(kind), config.growthEnabled && GrowthPlanner.isEnabled(kind, config)));
		}
		BuildJob.Kind requested = growth.requested();
		return new MayorScreenPayload(
				settlement.id(),
				Lang.tr("mayor.screen.title", settlement.name(), Lang.tr("style." + settlement.style().id())),
				settlement.leadership().mayorName(),
				MayorOffice.canManage(viewer, settlement),
				settlement.leadership().reputation(viewer.getUUID()),
				settlement.villagers(),
				settlement.beds(),
				growth.resources(),
				config.maxResources,
				settlement.villagers() * config.resourcesPerVillagerPerDay,
				status,
				note != null ? note : Component.empty(),
				requested != null ? requested.id() : "",
				growth.isPaused(),
				projects);
	}
}
