package com.risingsettlements.mayor;

import com.risingsettlements.build.BuildPlan;
import com.risingsettlements.config.RSConfig;
import com.risingsettlements.network.MayorActionPayload;
import com.risingsettlements.network.MayorStatePayload;
import com.risingsettlements.util.Texts;
import com.risingsettlements.village.Census;
import com.risingsettlements.village.Project;
import com.risingsettlements.village.Settlement;
import com.risingsettlements.village.SettlementManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

/**
 * The mayor system.
 *
 * <p>Players earn standing with a village the vanilla way: trading, curing zombie
 * villagers and defending it from raids all improve what villagers gossip about them.
 * When the total reputation across the village is high enough, the player can claim the
 * bell by sneaking and using it with an empty hand. The mayor then uses the bell the same
 * way to open the mayor screen, where they choose the next project or pause growth.
 *
 * <p>Clients without the mod get the same choices as clickable chat buttons.
 */
public final class MayorOffice {
	/** How far from the village a mayor may be and still manage it. */
	private static final int MANAGE_DISTANCE = 32;

	private MayorOffice() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MayorStatePayload.TYPE, MayorStatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MayorActionPayload.TYPE, MayorActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MayorActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			Settlement settlement = SettlementManager.get(player.level()).byId(payload.settlement());
			if (settlement != null && isNear(player, settlement)) {
				perform(player, settlement, payload.action());
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
					|| !player.getMainHandItem().isEmpty() || player.isSpectator() || !RSConfig.get().enableMayor
					|| !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!level.getBlockState(pos).is(Blocks.BELL)) {
				return InteractionResult.PASS;
			}
			Settlement settlement = SettlementManager.get(serverPlayer.level()).findContaining(pos);
			if (settlement == null || !settlement.bell().equals(pos)) {
				return InteractionResult.PASS;
			}
			useBell(serverPlayer, settlement);
			return InteractionResult.SUCCESS;
		});
	}

	/** Sneak-using the bell: claim it, open the mayor screen, or explain who the mayor is. */
	private static void useBell(ServerPlayer player, Settlement settlement) {
		if (settlement.isMayor(player.getUUID())) {
			openMenu(player, settlement);
		} else if (settlement.mayor() == null) {
			claim(player, settlement);
		} else {
			player.sendSystemMessage(Texts.tr("mayor.taken", settlement.name(), settlement.mayorName()));
		}
	}

	/** Total reputation of a player with the villagers of a settlement (the vanilla gossip score). */
	public static int reputation(ServerLevel level, Settlement settlement, ServerPlayer player) {
		int total = 0;
		for (Villager villager : Census.villagers(level, settlement)) {
			total += villager.getPlayerReputation(player);
		}
		return total;
	}

	/** Tries to make the player mayor. Sends them a message either way. */
	public static boolean claim(ServerPlayer player, Settlement settlement) {
		if (!RSConfig.get().enableMayor) {
			player.sendSystemMessage(Texts.tr("mayor.disabled").withStyle(ChatFormatting.RED));
			return false;
		}
		if (settlement.mayor() != null) {
			player.sendSystemMessage(settlement.isMayor(player.getUUID())
					? Texts.tr("mayor.already", settlement.name())
					: Texts.tr("mayor.taken", settlement.name(), settlement.mayorName()));
			return false;
		}
		int reputation = reputation(player.level(), settlement, player);
		int needed = RSConfig.get().mayorReputation;
		if (reputation < needed) {
			player.sendSystemMessage(Texts.tr("mayor.not_enough", settlement.name(), reputation, needed).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		settlement.setMayor(player.getUUID(), player.getGameProfile().name());
		player.sendSystemMessage(Texts.tr("mayor.claimed", settlement.name()).withStyle(ChatFormatting.GOLD));
		openMenu(player, settlement);
		return true;
	}

	/** True if the player may make choices for this village: its mayor, or an operator. */
	public static boolean canManage(ServerPlayer player, Settlement settlement) {
		return settlement.isMayor(player.getUUID()) || Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	private static boolean isNear(ServerPlayer player, Settlement settlement) {
		return settlement.isWithin(player.blockPosition(), RSConfig.get().maxVillageRadius + MANAGE_DISTANCE);
	}

	/**
	 * Carries out a mayor choice: {@code project:<auto|house|farm|road>}, {@code pause},
	 * {@code resume}, {@code resign} or {@code refresh}. Then shows the updated menu.
	 */
	public static void perform(ServerPlayer player, Settlement settlement, String action) {
		if (!canManage(player, settlement)) {
			player.sendSystemMessage(Texts.tr("mayor.not_mayor", settlement.name()).withStyle(ChatFormatting.RED));
			return;
		}
		if (action.startsWith("project:")) {
			Project project = Project.byId(action.substring("project:".length()));
			settlement.setProject(project);
			player.sendSystemMessage(Texts.tr("mayor.project_set", settlement.name(), Texts.tr("project." + project.id())));
		} else {
			switch (action) {
				case "pause" -> {
					settlement.setGrowthPaused(true);
					player.sendSystemMessage(Texts.tr("mayor.paused", settlement.name()));
				}
				case "resume" -> {
					settlement.setGrowthPaused(false);
					player.sendSystemMessage(Texts.tr("mayor.resumed", settlement.name()));
				}
				case "resign" -> {
					settlement.setMayor(null, "");
					player.sendSystemMessage(Texts.tr("mayor.resigned", settlement.name()));
					return;
				}
				case "refresh" -> {
				}
				default -> {
					return;
				}
			}
		}
		openMenu(player, settlement);
	}

	/** Shows the mayor screen, or a chat menu if the player's client does not have the mod. */
	public static void openMenu(ServerPlayer player, Settlement settlement) {
		MayorStatePayload state = state(player.level(), settlement);
		if (ServerPlayNetworking.canSend(player, MayorStatePayload.TYPE)) {
			ServerPlayNetworking.send(player, state);
		} else {
			sendChatMenu(player, settlement);
		}
	}

	public static MayorStatePayload state(ServerLevel level, Settlement settlement) {
		BuildPlan plan = settlement.activePlan();
		long untilGrowth = Math.max(0, settlement.lastGrowthAt() + RSConfig.get().growthIntervalTicks - level.getGameTime());
		return new MayorStatePayload(settlement.id(), settlement.name(), settlement.style().id(), settlement.mayorName(),
				settlement.villagers(), settlement.beds(), settlement.housesBuilt(),
				settlement.blueprint().count(s -> s.is(Blocks.FARMLAND)), settlement.blueprint().size(),
				settlement.project().id(), settlement.isGrowthPaused(), plan == null ? "" : plan.kind().id(),
				plan == null ? 0 : plan.progressPercent(), settlement.runtime().repairs.pending(), untilGrowth);
	}

	private static void sendChatMenu(ServerPlayer player, Settlement settlement) {
		player.sendSystemMessage(Texts.tr("menu.header", settlement.name()).withStyle(ChatFormatting.GOLD));
		MutableComponent projects = Texts.tr("menu.projects").append(" ");
		for (Project project : Project.values()) {
			boolean current = settlement.project() == project;
			projects.append(button(Texts.tr("project." + project.id()), "/settlement project " + project.id(), current)).append(" ");
		}
		player.sendSystemMessage(projects);
		MutableComponent controls = settlement.isGrowthPaused()
				? button(Texts.tr("menu.resume"), "/settlement resume", false)
				: button(Texts.tr("menu.pause"), "/settlement pause", false);
		controls.append(" ").append(button(Texts.tr("menu.info"), "/settlement info", false));
		controls.append(" ").append(button(Texts.tr("menu.resign"), "/settlement mayor resign", false));
		player.sendSystemMessage(controls);
	}

	private static MutableComponent button(Component label, String command, boolean selected) {
		return Component.literal("[").append(label).append("]").withStyle(style -> style
				.withColor(selected ? ChatFormatting.GREEN : ChatFormatting.AQUA)
				.withUnderlined(!selected)
				.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	/** Lower-case display form of the time left, e.g. {@code "3 min"}. */
	public static String formatTicks(long ticks) {
		long seconds = ticks / 20;
		if (seconds < 60) {
			return seconds + " s";
		}
		long minutes = seconds / 60;
		return minutes < 60 ? minutes + " min" : String.format(Locale.ROOT, "%d h %d min", minutes / 60, minutes % 60);
	}
}
