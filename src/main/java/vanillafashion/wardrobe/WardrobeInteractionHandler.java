package vanillafashion.wardrobe;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import vanillafashion.network.OpenWardrobePayload;
import vanillafashion.network.ServerPayloadSender;

public final class WardrobeInteractionHandler {
	private WardrobeInteractionHandler() {
	}

	public static void register(WardrobeServerAvailability availability, Logger logger) {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide()) {
				if (!availability.isAvailable()) {
					return clientPredictionResult(false, false);
				}

				return clientPredictionResult(
						true,
						WardrobeInteractionRules.isWardrobeCandidate(player, hand, entity)
				);
			}

			boolean wardrobeCandidate =
					WardrobeInteractionRules.isWardrobeCandidate(player, hand, entity);

			if (!wardrobeCandidate || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			if (!ServerPayloadSender.sendIfSupported(serverPlayer.connection, OpenWardrobePayload.INSTANCE)) {
				logger.debug(
						"Vanilla Fashion 客户端未声明接收 OpenWardrobe，保留原版交互；玩家 UUID：{}。",
						serverPlayer.getUUID()
				);
				return InteractionResult.PASS;
			}

			return InteractionResult.CONSUME;
		});

		logger.info("Vanilla Fashion 衣柜交互回调已注册；合法交互由服务器批准后打开界面。");
	}

	static InteractionResult clientPredictionResult(boolean serverAvailable, boolean wardrobeCandidate) {
		return serverAvailable && wardrobeCandidate
				? InteractionResult.CONSUME
				: InteractionResult.PASS;
	}
}
