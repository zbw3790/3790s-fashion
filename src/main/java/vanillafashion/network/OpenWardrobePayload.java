package vanillafashion.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import vanillafashion.VanillaFashion;

public record OpenWardrobePayload() implements CustomPacketPayload {
	public static final OpenWardrobePayload INSTANCE = new OpenWardrobePayload();
	public static final Type<OpenWardrobePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(VanillaFashion.MOD_ID, "open_wardrobe")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobePayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<OpenWardrobePayload> type() {
		return TYPE;
	}
}
