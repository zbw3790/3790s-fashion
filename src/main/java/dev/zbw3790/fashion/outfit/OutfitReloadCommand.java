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
                    if (result.success()) context.getSource().sendSuccess(() -> feedback(result),true);
                    else context.getSource().sendFailure(feedback(result));
                    return result.success()?1:0;
                }));
    }
    /** 用户反馈走现有聊天 Component；技术诊断继续保留服务器原始结果，不改变重载事务。 */
    public static Component feedback(OutfitRegistryReloadService.Result result) {
        if (result.success()) return result.changed()
                ? Component.translatable("commands.fashion_3790.reload.changed",result.generation(),result.refreshed(),result.pinned(),result.diagnostics().size())
                : Component.translatable("commands.fashion_3790.reload.unchanged");
        String key=switch(result.message()) {
            case "装束根目录不可用或扫描不可信，保留当前目录和全部选择。" -> "outfit_unavailable";
            case "盔甲根目录不可用或扫描不可信，两个领域都保留此前快照。" -> "armor_unavailable";
            case "时装服务只读、已停止或运行期容量／版本耗尽，保留当前目录和全部选择。" -> "read_only";
            case "时装服务尚未就绪或正在停止。" -> "not_ready";
            default -> null;
        };
        // 未识别的未来诊断按字面保留，避免把错误原因吞掉或误报成功。
        return key==null?Component.literal(result.message()):Component.translatable("commands.fashion_3790.reload."+key);
    }

}
