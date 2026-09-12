package dev.zbw3790.fashion.client.network;

import java.util.Objects;

/** 使用 Fabric 当前连接的发送器对象隔离排队中的旧连接消息。 */
public final class ClientConnectionIdentity {
	private ClientConnectionIdentity() {
	}

	public static boolean isCurrentSender(Object responseSender, Object currentSender) {
		return responseSender != null && responseSender == currentSender;
	}

	public static boolean runIfCurrent(
			Object responseSender,
			Object currentSender,
			Runnable receiver
	) {
		Objects.requireNonNull(receiver, "连接消息处理器不能为 null。");

		if (!isCurrentSender(responseSender, currentSender)) {
			return false;
		}

		receiver.run();
		return true;
	}
}
