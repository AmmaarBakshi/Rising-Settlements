package com.risingsettlements.network;

import com.risingsettlements.RisingSettlements;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client to server: the mayor pressed a button on the mayor screen. The server checks that
 * the player really is the mayor and close to the bell before doing anything.
 *
 * @param settlement which village the screen is about
 * @param action     what to do
 * @param project    the project id for {@link Action#REQUEST}, otherwise empty
 */
public record MayorActionPayload(UUID settlement, Action action, String project) implements CustomPacketPayload {
	public static final Type<MayorActionPayload> TYPE = new Type<>(RisingSettlements.id("mayor_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MayorActionPayload> CODEC =
			CustomPacketPayload.codec(MayorActionPayload::write, MayorActionPayload::read);

	/** Project ids are short words like "house"; nothing longer is accepted. */
	private static final int MAX_PROJECT_LENGTH = 32;

	public enum Action {
		/** Build this project next. */
		REQUEST,
		/** Let the village choose its next project again. */
		CLEAR_REQUEST,
		PAUSE,
		RESUME,
		/** Step down as mayor. */
		RESIGN,
		/** Just send fresh screen data. */
		REFRESH
	}

	public MayorActionPayload(UUID settlement, Action action) {
		this(settlement, action, "");
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(settlement);
		buf.writeVarInt(action.ordinal());
		buf.writeUtf(project, MAX_PROJECT_LENGTH);
	}

	private static MayorActionPayload read(RegistryFriendlyByteBuf buf) {
		UUID settlement = buf.readUUID();
		int action = buf.readVarInt();
		if (action < 0 || action >= Action.values().length) {
			throw new DecoderException("Unknown mayor action " + action);
		}
		return new MayorActionPayload(settlement, Action.values()[action], buf.readUtf(MAX_PROJECT_LENGTH));
	}

	@Override
	public Type<MayorActionPayload> type() {
		return TYPE;
	}
}
