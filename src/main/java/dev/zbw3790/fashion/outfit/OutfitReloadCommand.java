package dev.zbw3790.fashion.outfit;

import java.util.function.Function;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class OutfitReloadCommand {
    private OutfitReloadCommand() { }
    public static void register(Function<CommandSourceStack,OutfitRegistryReloadService.Result> reload) {
        CommandRegistrationCallback.EVENT.register((dispatcher,context,environment) -> dispatcher.register(command(reload)));
    }
    public static LiteralArgumentBuilder<CommandSourceStack> command(Function<CommandSourceStack,OutfitRegistryReloadService.Result> reload) {
        return Commands.literal("fashion3790").then(Commands.literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    var result=reload.apply(context.getSource());
                    if (result.success()) context.getSource().sendSuccess(() -> Component.literal(result.message()),true);
                    else context.getSource().sendFailure(Component.literal(result.message()));
                    return result.success()?1:0;
                }));
    }
}
