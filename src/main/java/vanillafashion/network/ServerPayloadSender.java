package vanillafashion.network;

import java.util.Objects;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** 所有 Vanilla Fashion S2C 发送都从这里执行连接能力检查。 */
public final class ServerPayloadSender {
	private ServerPayloadSender() {
	}

	public static boolean canSend(
			ServerGamePacketListenerImpl handler,
			CustomPacketPayload.Type<?> payloadType
	) {
		return ServerPlayNetworking.canSend(
				Objects.requireNonNull(handler, "服务端连接不能为 null。"),
				Objects.requireNonNull(payloadType, "S2C payload 类型不能为 null。")
		);
	}

	public static boolean sendIfSupported(
			ServerGamePacketListenerImpl handler,
			CustomPacketPayload payload
	) {
		Objects.requireNonNull(payload, "S2C payload 不能为 null。");

		if (!canSend(handler, payload.type())) {
			return false;
		}

		ServerPlayNetworking.send(handler.getPlayer(), payload);
		return true;
	}
}
