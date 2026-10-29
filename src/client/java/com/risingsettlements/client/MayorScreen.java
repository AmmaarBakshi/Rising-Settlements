package com.risingsettlements.client;

import com.risingsettlements.network.MayorActionPayload;
import com.risingsettlements.network.MayorActionPayload.Action;
import com.risingsettlements.network.MayorScreenPayload;
import com.risingsettlements.network.MayorScreenPayload.Project;
import com.risingsettlements.util.Lang;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The mayor's screen: village stats, the next project, pausing growth and stepping down.
 *
 * <p>The screen only shows what the server sends ({@link MayorScreenPayload}) and sends
 * button presses back ({@link MayorActionPayload}); the server checks and does everything.
 * While it is open it asks for fresh data every two seconds, so progress stays current.
 */
public final class MayorScreen extends Screen {
	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 232;
	private static final int PADDING = 8;
	private static final int REFRESH_TICKS = 40;

	private static final int GOLD = 0xFFFFAA00;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int AQUA = 0xFF55FFFF;

	private MayorScreenPayload data;
	/** Stepping down takes two clicks. */
	private boolean confirmResign;
	private int ticks;
	private int left;
	private int top;

	private MayorScreen(MayorScreenPayload data) {
		super(data.title());
		this.data = data;
	}

	/** Shows data that arrived from the server: opens the screen, or updates it if it is open. */
	public static void receive(Minecraft client, MayorScreenPayload data) {
		if (client.gui.screen() instanceof MayorScreen screen && screen.data.settlement().equals(data.settlement())) {
			screen.data = data;
			screen.rebuildWidgets();
		} else if (data.open()) {
			client.gui.setScreen(new MayorScreen(data));
		}
	}

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = Math.max(4, (height - PANEL_HEIGHT) / 2);
		boolean manage = data.canManage();

		List<Project> projects = data.projects();
		for (int i = 0; i < projects.size(); i++) {
			Project project = projects.get(i);
			boolean chosen = project.id().equals(data.requested());
			Component label = Lang.tr("mayor.screen.project_button", projectName(project.id()), project.cost());
			if (chosen) {
				label = Component.literal("» ").append(label).withStyle(ChatFormatting.GREEN);
			}
			Component tooltip = project.enabled()
					? Lang.tr("mayor.screen.project_tooltip", project.built(), project.cost())
					: Lang.tr("mayor.screen.project_disabled");
			Button button = addRenderableWidget(Button.builder(label, b -> send(Action.REQUEST, project.id()))
					.bounds(left + 5 + (i % 3) * 105, top + 108 + (i / 3) * 24, 100, 20)
					.tooltip(Tooltip.create(tooltip))
					.build());
			button.active = manage && project.enabled() && !chosen;
		}

		int row = top + 158;
		Button auto = addRenderableWidget(Button.builder(Lang.tr("mayor.screen.auto"), b -> send(Action.CLEAR_REQUEST, ""))
				.bounds(left + 5, row, 152, 20)
				.tooltip(Tooltip.create(Lang.tr("mayor.screen.auto_tooltip")))
				.build());
		auto.active = manage && !data.requested().isEmpty();
		Button pause = addRenderableWidget(Button.builder(
						Lang.tr(data.paused() ? "mayor.screen.resume" : "mayor.screen.pause"),
						b -> send(data.paused() ? Action.RESUME : Action.PAUSE, ""))
				.bounds(left + 163, row, 152, 20)
				.build());
		pause.active = manage;

		row = top + 204;
		Button resign = addRenderableWidget(Button.builder(
						Lang.tr(confirmResign ? "mayor.screen.resign_confirm" : "mayor.screen.resign"), b -> resign())
				.bounds(left + 5, row, 152, 20)
				.build());
		resign.active = manage;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(left + 163, row, 152, 20)
				.build());
	}

	@Override
	public void tick() {
		if (++ticks % REFRESH_TICKS == 0) {
			send(Action.REFRESH, "");
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left + PADDING;
		int right = left + PANEL_WIDTH - PADDING;
		graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xD0101010);
		graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF606060);
		graphics.centeredText(font, title, width / 2, top + PADDING, GOLD);

		Component mayor = data.mayor().isEmpty()
				? Lang.tr("mayor.screen.no_mayor")
				: Lang.tr("mayor.screen.mayor", data.mayor());
		graphics.text(font, mayor, x, top + 24, AQUA);
		Component reputation = Lang.tr("mayor.screen.reputation", data.reputation());
		graphics.text(font, reputation, right - font.width(reputation), top + 24, WHITE);
		graphics.text(font, Lang.tr("mayor.screen.population", data.villagers(), data.beds()), x, top + 38, WHITE);
		graphics.text(font, Lang.tr("mayor.screen.resources", data.resources(), data.maxResources(), data.income()),
				x, top + 50, WHITE);

		graphics.text(font, data.status(), x, top + 66, data.paused() ? YELLOW : GREEN);
		if (!data.note().getString().isEmpty()) {
			graphics.textWithWordWrap(font, data.note(), x, top + 78, PANEL_WIDTH - 2 * PADDING, GRAY);
		}

		Component next = data.requested().isEmpty()
				? Lang.tr("mayor.screen.pick_auto")
				: Lang.tr("mayor.screen.pick_chosen", projectName(data.requested()));
		graphics.text(font, next, x, top + 96, GOLD);
		if (!data.canManage()) {
			graphics.centeredText(font, Lang.tr("mayor.screen.read_only"), width / 2, top + 188, GRAY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		// In single player a pause screen would stop the village, and the progress shown here.
		return false;
	}

	private void resign() {
		if (!confirmResign) {
			confirmResign = true;
			rebuildWidgets();
			return;
		}
		send(Action.RESIGN, "");
		onClose();
	}

	private void send(Action action, String project) {
		ClientPlayNetworking.send(new MayorActionPayload(data.settlement(), action, project));
	}

	private static Component projectName(String id) {
		return Lang.tr("mayor.screen.project." + id);
	}
}
