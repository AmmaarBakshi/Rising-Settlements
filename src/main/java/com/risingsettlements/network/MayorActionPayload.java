package com.risingsettlements.network;

import com.risingsettlements.RisingSettlements;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Client to server: the mayor pressed a button on the mayor screen. The server checks
 * that the sender really is the mayor and is near the village before doing anything.
 *
 * @param action one of the actions understood by {@code MayorActions}, such as {@code project:house}
 */
public record MayorActionPayload(UUID settlement, String action) implements CustomPacketPayload {
	public static final Type<MayorActionPayload> TYPE = new Type<>(RisingSettlements.id("mayor_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MayorActionPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, MayorActionPayload::settlement,
			ByteBufCodecs.stringUtf8(64), MayorActionPayload::action,
			MayorActionPayload::new);

	@Override
	public Type<MayorActionPayload> type() {
		return TYPE;
	}
}
