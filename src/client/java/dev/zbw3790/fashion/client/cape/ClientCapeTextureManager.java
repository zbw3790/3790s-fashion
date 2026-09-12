package dev.zbw3790.fashion.client.cape;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import dev.zbw3790.fashion.Fashion3790;
import dev.zbw3790.fashion.cape.CapeAssetHash;
import dev.zbw3790.fashion.cape.CapeTextureAsset;

public final class ClientCapeTextureManager {
	private final ClientCapeTextureRegistry registry;

	public ClientCapeTextureManager() {
		this(new ClientCapeTextureRegistry());
	}

	ClientCapeTextureManager(ClientCapeTextureRegistry registry) {
		this.registry = Objects.requireNonNull(registry, "运行时纹理注册状态不能为 null。");
	}

	public static Identifier identifierFor(String sha256) {
		return Identifier.fromNamespaceAndPath(
				Fashion3790.MOD_ID,
				"asset/" + CapeAssetHash.requireValid(sha256, "运行时纹理 SHA-256 ")
		);
	}

	public Optional<Identifier> find(String sha256) {
		return registry.find(sha256);
	}

	public RegistrationResult registerIfAvailable(
			TextureManager textureManager,
			ClientCapeAssetStore assetStore,
			String sha256,
			Logger logger
	) {
		Objects.requireNonNull(textureManager, "Minecraft TextureManager 不能为 null。");
		Objects.requireNonNull(assetStore, "客户端 Cape Asset Store 不能为 null。");
		Objects.requireNonNull(logger, "运行时纹理日志记录器不能为 null。");

		if (registry.find(sha256).isPresent()) {
			return RegistrationResult.ALREADY_REGISTERED;
		}

		Optional<byte[]> storedBytes = assetStore.find(sha256);

		if (storedBytes.isEmpty()) {
			return RegistrationResult.ASSET_MISSING;
		}

		NativeImage image = null;
		DynamicTexture texture = null;

		try {
			image = NativeImage.read(storedBytes.orElseThrow());

			if (image.getWidth() != CapeTextureAsset.REQUIRED_WIDTH
					|| image.getHeight() != CapeTextureAsset.REQUIRED_HEIGHT
					|| image.format() != NativeImage.Format.RGBA) {
				return RegistrationResult.INVALID_IMAGE;
			}

			Identifier identifier = identifierFor(sha256);
			texture = new DynamicTexture(
					() -> "3790's Fashion Cape 运行时纹理 " + shortHash(sha256),
					image
			);
			image = null;
			textureManager.register(identifier, texture);
			texture = null;
			registry.register(sha256, identifier);
			return RegistrationResult.REGISTERED;
		} catch (IOException | RuntimeException exception) {
			logger.warn("3790's Fashion 创建 Cape 运行时纹理失败：{}。", shortHash(sha256));
			return RegistrationResult.FAILED;
		} finally {
			if (texture != null) {
				texture.close();
			} else if (image != null) {
				image.close();
			}
		}
	}

	public int registerAvailable(
			TextureManager textureManager,
			ClientCapeAssetStore assetStore,
			Set<String> requiredHashes,
			Logger logger
	) {
		int registered = 0;

		for (String sha256 : requiredHashes) {
			if (registerIfAvailable(textureManager, assetStore, sha256, logger)
					== RegistrationResult.REGISTERED) {
				registered++;
			}
		}

		return registered;
	}

	public int retain(TextureManager textureManager, Set<String> requiredHashes) {
		Objects.requireNonNull(textureManager, "Minecraft TextureManager 不能为 null。");
		int released = 0;

		for (Identifier identifier : registry.retain(requiredHashes)) {
			textureManager.release(identifier);
			released++;
		}

		return released;
	}

	public int clear(TextureManager textureManager) {
		Objects.requireNonNull(textureManager, "Minecraft TextureManager 不能为 null。");
		int released = 0;

		for (Identifier identifier : registry.clear()) {
			textureManager.release(identifier);
			released++;
		}

		return released;
	}

	public int size() {
		return registry.size();
	}

	private static String shortHash(String sha256) {
		return sha256.substring(0, 12);
	}

	public enum RegistrationResult {
		REGISTERED,
		ALREADY_REGISTERED,
		ASSET_MISSING,
		INVALID_IMAGE,
		FAILED
	}
}
