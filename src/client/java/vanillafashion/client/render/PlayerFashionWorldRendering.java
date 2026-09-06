package vanillafashion.client.render;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class PlayerFashionWorldRendering {
	private PlayerFashionWorldRendering() {
	}

	public static void register(PlayerFashionAppearanceResolver appearances) {
		LevelExtractionEvents.END_EXTRACTION.register(context -> extract(
				context.levelState().entityRenderStates,
				id -> playerId(context.level().getEntity(id), id),
				appearances::resolve));
	}

	static Optional<UUID> playerId(Entity entity, int expectedId) {
		return entity instanceof Player player && player.getId() == expectedId
				? Optional.of(player.getUUID()) : Optional.empty();
	}

	static void extract(Iterable<EntityRenderState> states, IntFunction<Optional<UUID>> lookup,
			Function<UUID, PlayerFashionRenderAppearance> appearances) {
		for (EntityRenderState state : states) {
			if (state instanceof AvatarRenderState avatar) {
				// 每帧都写入，包括 lookup miss；不保留复用 RenderState 上的旧外观。
				var appearance = lookup.apply(avatar.id).map(appearances)
						.orElseGet(PlayerFashionRenderAppearance::unknown);
				PlayerFashionRenderState.attach(avatar, appearance);
			}
		}
	}
}
