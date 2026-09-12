package dev.zbw3790.fashion.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;

public record WardrobeAvailablePayload() implements CustomPacketPayload {
	public static final WardrobeAvailablePayload INSTANCE = new WardrobeAvailablePayload();
	public static final Type<WardrobeAvailablePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "wardrobe_available")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, WardrobeAvailablePayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<WardrobeAvailablePayload> type() {
		return TYPE;
	}
}
