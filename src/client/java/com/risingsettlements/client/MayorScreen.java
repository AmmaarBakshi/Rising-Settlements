package com.risingsettlements.client;

import com.risingsettlements.mayor.MayorOffice;
import com.risingsettlements.network.MayorActionPayload;
import com.risingsettlements.network.MayorStatePayload;
import com.risingsettlements.util.Texts;
import com.risingsettlements.village.Project;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The mayor screen, opened by sneak-using the bell of a village you are mayor of.
 *
 * <p>Shows the village's state and lets the mayor choose the next project, pause growth
 * or resign. Every button sends a {@link MayorActionPayload}; the server checks it,
 * applies it and replies with a fresh {@link MayorStatePayload}, which reopens this screen
 * with the new values.
 */
public class MayorScreen extends Screen {
	private static final int BUTTON_WIDTH = 74;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int LINE_HEIGHT = 11;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFFA0A0A0;

	private final MayorStatePayload state;

	public MayorScreen(MayorStatePayload state) {
		super(Texts.tr("screen.title", state.name()));
		this.state = state;
	}

	@Override
	protected void init() {
		int rowWidth = Project.values().length * (BUTTON_WIDTH + GAP) - GAP;
		int left = (width - rowWidth) / 2;
		int projectsY = height / 2 + 20;

		for (int i = 0; i < Project.values().length; i++) {
			Project project = Project.values()[i];
			Button button = Button.builder(Texts.tr("project." + project.id()), b -> send("project:" + project.id()))
					.bounds(left + i * (BUTTON_WIDTH + GAP), projectsY, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			button.active = !project.id().equals(state.project());
			addRenderableWidget(button);
		}

		int controlsY = projectsY + BUTTON_HEIGHT + GAP * 2;
		int controlsLeft = (width - (3 * (BUTTON_WIDTH + GAP) - GAP)) / 2;
		addRenderableWidget(Button.builder(Texts.tr(state.paused() ? "menu.resume" : "menu.pause"),
						b -> send(state.paused() ? "resume" : "pause"))
				.bounds(controlsLeft, controlsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Texts.tr("menu.resign"), b -> {
					send("resign");
					onClose();
				})
				.bounds(controlsLeft + BUTTON_WIDTH + GAP, controlsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Texts.tr("screen.close"), b -> onClose())
				.bounds(controlsLeft + 2 * (BUTTON_WIDTH + GAP), controlsY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void send(String action) {
		ClientPlayNetworking.send(new MayorActionPayload(state.settlement(), action));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		List<Component> lines = lines();
		int top = height / 2 + 12 - (lines.size() + 2) * LINE_HEIGHT;
		graphics.centeredText(font, title.getString(), width / 2, top, TEXT_COLOR);
		for (int i = 0; i < lines.size(); i++) {
			Component line = lines.get(i);
			graphics.text(font, line, (width - font.width(line)) / 2, top + (i + 2) * LINE_HEIGHT, TEXT_COLOR);
		}
		graphics.centeredText(font, Texts.tr("screen.choose").getString(), width / 2, height / 2 + 8, LABEL_COLOR);
	}

	private List<Component> lines() {
		List<Component> lines = new ArrayList<>();
		lines.add(Texts.tr("info.population", state.villagers(), state.beds()));
		lines.add(Texts.tr("screen.stats", state.houses(), state.farmland(), state.blocks()));
		if (!state.building().isEmpty()) {
			lines.add(Texts.tr("info.building", Texts.tr("project." + state.building()), state.progress()));
		} else if (state.paused()) {
			lines.add(Texts.tr("info.paused"));
		} else {
			lines.add(Texts.tr("info.next", Texts.tr("project." + state.project()), MayorOffice.formatTicks(state.ticksUntilGrowth())));
		}
		if (state.damaged() > 0) {
			lines.add(Texts.tr("info.repairs", state.damaged()));
		}
		return lines;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
