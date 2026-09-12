package dev.zbw3790.fashion.client.dev.spike;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.zbw3790.fashion.client.render.outfit.OutfitAppearanceProvider;
import dev.zbw3790.fashion.client.render.outfit.OutfitRenderAppearance.Scene;

/** 双重门禁下注册开发输入；独占 fixture 文件和 DynamicTexture 生命周期。 */
public final class OutfitSpikeHarness {
    public static final String ENABLE_PROPERTY = "fashion_3790.outfitSpike";
    public static final String DIRECTORY_PROPERTY = "fashion_3790.outfitSpikeDirectory";
    private static final Logger LOGGER = LoggerFactory.getLogger("fashion_3790/outfit_spike");
    private final OutfitSpikeInputs inputs = new OutfitSpikeInputs();
    private final Map<String, Identifier> textures = new HashMap<>();
    private boolean warned;
    private OutfitSpikeHarness() { }
    public static OutfitAppearanceProvider register() {
        return registerWhen(FabricLoader.getInstance().isDevelopmentEnvironment(), Boolean.getBoolean(ENABLE_PROPERTY), () -> {
            OutfitSpikeHarness harness = new OutfitSpikeHarness();
            harness.install();
            return context -> harness.inputs.resolve(context, harness.textures);
        });
    }
    static OutfitAppearanceProvider registerWhen(boolean development, boolean explicitSwitch,
            Supplier<OutfitAppearanceProvider> registration) {
        return development && explicitSwitch ? Objects.requireNonNull(registration.get()) : OutfitAppearanceProvider.EMPTY;
    }
    private void install() {
        ClientLifecycleEvents.CLIENT_STARTED.register(this::loadTextures);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            inputs.connection(null);
            textures.values().forEach(client.getTextureManager()::release);
            textures.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> inputs.connection(null));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("outfitspike").then(argument("scene", StringArgumentType.word())
                        .then(argument("player", StringArgumentType.word())
                                .then(argument("profile", StringArgumentType.word()).executes(context -> {
                                    var client = context.getSource().getClient();
                                    try {
                                        String scene = StringArgumentType.getString(context, "scene");
                                        Scene selectedScene = switch (scene) {
                                            case "world" -> Scene.WORLD;
                                            case "preview" -> Scene.WARDROBE_PREVIEW;
                                            default -> throw new IllegalArgumentException("场景需为 world 或 preview。");
                                        };
                                        String name = StringArgumentType.getString(context, "player");
                                        var player = name.equals("self") ? client.player : client.level.players().stream()
                                                .filter(value -> value.getName().getString().equals(name)).findFirst().orElse(null);
                                        if (player == null) throw new IllegalArgumentException("当前世界找不到指定玩家。");
                                        String profile = StringArgumentType.getString(context, "profile");
                                        inputs.assign(client.getConnection(), player.getUUID(), selectedScene, profile);
                                        context.getSource().sendFeedback(Component.literal("外层实验输入已更新：" + scene + " / " + name + " / " + profile));
                                        LOGGER.info("外层实验输入已更新：场景={}，组合={}，实际模型={}。", selectedScene, profile, player.getSkin().model());
                                        return 1;
                                    } catch (IllegalArgumentException exception) {
                                        context.getSource().sendError(Component.literal(exception.getMessage()));
                                        return 0;
                                    }
                                }))))));
        LOGGER.info("外层实验门禁已开启；默认无分配，实验输入不写入服务器或存档。");
    }

    private void loadTextures(Minecraft client) {
        String directory = System.getProperty(DIRECTORY_PROPERTY, "");
        if (directory.isBlank()) { warn("没有配置实验资源目录，纹理保持不可用。"); return; }
        Path root = Path.of(directory).toAbsolutePath().normalize();
        for (String model : new String[]{"wide", "slim"}) {
            for (String role : new String[]{"all", "head", "upper", "left", "transparent", "alpha", "mismatch"}) {
                String name = model + "-" + role;
                NativeImage image = null;
                DynamicTexture texture = null;
                try {
                    byte[] bytes = Files.readAllBytes(root.resolve(name + ".png"));
                    image = NativeImage.read(bytes);
                    if (image.getWidth() != 64 || image.getHeight() != 64 || image.format() != NativeImage.Format.RGBA)
                        throw new IllegalArgumentException("实验纹理必须为 64×64 RGBA。");
                    Identifier id = Identifier.fromNamespaceAndPath("fashion_3790", "outfit_spike/" + name);
                    texture = new DynamicTexture(() -> "外层实验纹理 " + name, image);
                    image = null;
                    client.getTextureManager().register(id, texture);
                    texture = null;
                    textures.put(name, id);
                } catch (java.io.IOException | RuntimeException exception) {
                    LOGGER.warn("外层实验纹理不可用：{}。", name);
                } finally {
                    if (texture != null) texture.close();
                    else if (image != null) image.close();
                }
            }
        }
        LOGGER.info("外层实验资源准备结束：{} 份；切换输入不会注销仍可能排队的纹理。", textures.size());
    }

    private void warn(String message) { if (!warned) { warned = true; LOGGER.warn(message); } }
}
