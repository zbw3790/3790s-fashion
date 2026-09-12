package dev.zbw3790.fashion.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.Fashion3790;

public record OpenWardrobePayload() implements CustomPacketPayload {
	public static final OpenWardrobePayload INSTANCE = new OpenWardrobePayload();
	public static final Type<OpenWardrobePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(Fashion3790.MOD_ID, "open_wardrobe")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobePayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<OpenWardrobePayload> type() {
		return TYPE;
	}
}
