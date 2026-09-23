package dev.zbw3790.fashion.outfit;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutfitReloadCommandTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap(); }
    static class Source extends CommandSourceStack {
        final List<String> success=new ArrayList<>(),failure=new ArrayList<>();
        final List<Component> components=new ArrayList<>();
        Source(PermissionSet permissions,String name){super(CommandSource.NULL,Vec3.ZERO,Vec2.ZERO,null,permissions,name,Component.literal(name),null,null);}
        @Override public void sendSuccess(Supplier<Component> message,boolean notify){Component value=message.get();components.add(value);success.add(value.getString());}
        @Override public void sendFailure(Component message){failure.add(message.getString());}
    }
    @Test void adminAndConsoleUseOfficialPermissionAndDelegateOnce() throws Exception {
        for(var source:List.of(new Source(LevelBasedPermissionSet.GAMEMASTER,"管理员"),new Source(PermissionSet.ALL_PERMISSIONS,"控制台"))) {
            var calls=new AtomicInteger();var dispatcher=new CommandDispatcher<CommandSourceStack>();
            dispatcher.register(OutfitReloadCommand.command(s->{assertSame(source,s);calls.incrementAndGet();return new OutfitRegistryReloadService.Result(true,true,"装束重载完成",1,1,0,List.of());}));
            assertEquals(1,dispatcher.execute("fashion3790 reload",source));assertEquals(1,calls.get());assertEquals("commands.fashion_3790.reload.changed",((net.minecraft.network.chat.contents.TranslatableContents)source.components.getFirst().getContents()).getKey());assertEquals(1,source.success.size());assertTrue(source.failure.isEmpty());
        }
    }
    @Test void unauthorizedPlayerAndModeratorCannotInvokeReload() {
        for(var permissions:List.of(PermissionSet.NO_PERMISSIONS,LevelBasedPermissionSet.MODERATOR)) {
            var dispatcher=new CommandDispatcher<CommandSourceStack>();dispatcher.register(OutfitReloadCommand.command(s->{fail("权限不足不能调用服务。");return null;}));
            assertThrows(CommandSyntaxException.class,()->dispatcher.execute("fashion3790 reload",new Source(permissions,"普通玩家")));
        }
    }
    @Test void serviceFailureUsesFailureChannelAndReturnsZero() throws Exception {
        var source=new Source(PermissionSet.ALL_PERMISSIONS,"控制台");var dispatcher=new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(OutfitReloadCommand.command(s->OutfitRegistryReloadService.Result.failed("根目录不可信")));
        assertEquals(0,dispatcher.execute("fashion3790 reload",source));assertEquals(List.of("根目录不可信"),source.failure);assertTrue(source.success.isEmpty());
    }
    @Test void noChangeStillReportsSuccessWithoutInventingRefreshCount() throws Exception {
        var source=new Source(LevelBasedPermissionSet.ADMIN,"管理员");var dispatcher=new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(OutfitReloadCommand.command(s->new OutfitRegistryReloadService.Result(true,false,"内容没有变化",3,0,0,List.of())));
        assertEquals(1,dispatcher.execute("fashion3790 reload",source));assertEquals("commands.fashion_3790.reload.unchanged",((net.minecraft.network.chat.contents.TranslatableContents)source.components.getFirst().getContents()).getKey());assertEquals(1,source.success.size());assertTrue(source.failure.isEmpty());
    }
}
