package com.risingsettlements.network;

import com.risingsettlements.RisingSettlements;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Server to client: everything the mayor screen shows about a village. Sent when the
 * mayor opens the screen and again after every choice they make on it.
 *
 * @param building        kind of the project being built right now, or empty
 * @param progress        how far the current project is, 0 to 100
 * @param damaged         missing blocks waiting to be repaired
 * @param ticksUntilGrowth time until the next project may start, 0 if it may start now
 */
public record MayorStatePayload(UUID settlement, String name, String style, String mayorName, int villagers, int beds,
								int houses, int farmland, int blocks, String project, boolean paused, String building,
								int progress, int damaged, long ticksUntilGrowth) implements CustomPacketPayload {
	public static final Type<MayorStatePayload> TYPE = new Type<>(RisingSettlements.id("mayor_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MayorStatePayload> CODEC =
			CustomPacketPayload.codec(MayorStatePayload::write, MayorStatePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(settlement);
		buf.writeUtf(name);
		buf.writeUtf(style);
		buf.writeUtf(mayorName);
		buf.writeVarInt(villagers);
		buf.writeVarInt(beds);
		buf.writeVarInt(houses);
		buf.writeVarInt(farmland);
		buf.writeVarInt(blocks);
		buf.writeUtf(project);
		buf.writeBoolean(paused);
		buf.writeUtf(building);
		buf.writeVarInt(progress);
		buf.writeVarInt(damaged);
		buf.writeVarLong(ticksUntilGrowth);
	}

	private static MayorStatePayload read(RegistryFriendlyByteBuf buf) {
		return new MayorStatePayload(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readBoolean(),
				buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
	}

	@Override
	public Type<MayorStatePayload> type() {
		return TYPE;
	}
}
