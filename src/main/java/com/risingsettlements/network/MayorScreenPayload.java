package com.risingsettlements.network;

import com.risingsettlements.RisingSettlements;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: everything the mayor screen shows. Sent when the mayor opens the
 * screen, after each button press, and every couple of seconds while it stays open.
 *
 * @param settlement which village this is about
 * @param title      the village name and style, e.g. "Mapleford (plains village)"
 * @param mayor      the mayor's name, or empty if there is none
 * @param canManage  whether the viewer may press the buttons (they are the mayor and close to the bell)
 * @param reputation the viewer's reputation in this village
 * @param status     what the village is doing now ("Building: house (40% done)", "Growth is paused"...)
 * @param note       why it isn't building, or empty
 * @param requested  the project the mayor picked, or empty
 * @param projects   every project the mayor can pick, in display order
 */
public record MayorScreenPayload(
		UUID settlement,
		Component title,
		String mayor,
		boolean canManage,
		int reputation,
		int villagers,
		int beds,
		int resources,
		int maxResources,
		int income,
		Component status,
		Component note,
		String requested,
		boolean paused,
		List<Project> projects) implements CustomPacketPayload {

	public static final Type<MayorScreenPayload> TYPE = new Type<>(RisingSettlements.id("mayor_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MayorScreenPayload> CODEC =
			CustomPacketPayload.codec(MayorScreenPayload::write, MayorScreenPayload::read);

	/** Most villages offer six projects; a limit keeps a broken packet from allocating much. */
	private static final int MAX_PROJECTS = 32;

	/**
	 * @param id      the project's id, e.g. "house"
	 * @param cost    resources it costs
	 * @param built   how many the village has finished
	 * @param enabled whether the server allows it
	 */
	public record Project(String id, int cost, int built, boolean enabled) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(settlement);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, title);
		buf.writeUtf(mayor);
		buf.writeBoolean(canManage);
		buf.writeVarInt(reputation);
		buf.writeVarInt(villagers);
		buf.writeVarInt(beds);
		buf.writeVarInt(resources);
		buf.writeVarInt(maxResources);
		buf.writeVarInt(income);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, status);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, note);
		buf.writeUtf(requested);
		buf.writeBoolean(paused);
		buf.writeVarInt(projects.size());
		for (Project project : projects) {
			buf.writeUtf(project.id());
			buf.writeVarInt(project.cost());
			buf.writeVarInt(project.built());
			buf.writeBoolean(project.enabled());
		}
	}

	private static MayorScreenPayload read(RegistryFriendlyByteBuf buf) {
		UUID settlement = buf.readUUID();
		Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		String mayor = buf.readUtf();
		boolean canManage = buf.readBoolean();
		int reputation = buf.readVarInt();
		int villagers = buf.readVarInt();
		int beds = buf.readVarInt();
		int resources = buf.readVarInt();
		int maxResources = buf.readVarInt();
		int income = buf.readVarInt();
		Component status = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		Component note = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		String requested = buf.readUtf();
		boolean paused = buf.readBoolean();
		int count = Math.min(buf.readVarInt(), MAX_PROJECTS);
		List<Project> projects = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			projects.add(new Project(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
		}
		return new MayorScreenPayload(settlement, title, mayor, canManage, reputation, villagers, beds,
				resources, maxResources, income, status, note, requested, paused, List.copyOf(projects));
	}

	@Override
	public Type<MayorScreenPayload> type() {
		return TYPE;
	}
}
