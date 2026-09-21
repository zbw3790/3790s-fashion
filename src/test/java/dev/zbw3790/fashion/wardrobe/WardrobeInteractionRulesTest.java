package dev.zbw3790.fashion.wardrobe;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.InteractionResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WardrobeInteractionRulesTest {
    @Test void onlyArmorStandFinalPassAndNonSpectatorCanFallback() {
        assertTrue(WardrobeInteractionRules.matches(true,false,InteractionResult.PASS));
        assertFalse(WardrobeInteractionRules.matches(false,false,InteractionResult.PASS));
        assertFalse(WardrobeInteractionRules.matches(true,true,InteractionResult.PASS));
        for(var result:new InteractionResult[]{InteractionResult.SUCCESS,InteractionResult.SUCCESS_SERVER,InteractionResult.CONSUME,InteractionResult.FAIL})
            assertFalse(WardrobeInteractionRules.matches(true,false,result));
    }
    @Test void clientPredictsServerSwingWithoutCallingOpenOrSwingLocally() {
        var result=WardrobeInteractionHandler.fallback(InteractionResult.PASS,true,true,true,()->{fail("客户端不能发送开窗。");return false;});
        assertTrue(result.consumesAction());assertEquals(InteractionResult.SUCCESS_SERVER.withoutItem(),result);
    }
    @Test void unsupportedServerLeavesVanillaPass() {
        assertSame(InteractionResult.PASS,WardrobeInteractionHandler.fallback(InteractionResult.PASS,true,true,false,()->{fail();return false;}));
    }
    @Test void serverSendsOnceAndConsumesOnlySuccessfulOpen() {
        var opens=new AtomicInteger();assertTrue(WardrobeInteractionHandler.fallback(InteractionResult.PASS,true,false,false,()->{opens.incrementAndGet();return true;}).consumesAction());
        assertEquals(InteractionResult.SwingSource.SERVER,((InteractionResult.Success)WardrobeInteractionHandler.fallback(InteractionResult.PASS,true,false,false,()->true)).swingSource());
        assertEquals(1,opens.get());assertSame(InteractionResult.PASS,WardrobeInteractionHandler.fallback(InteractionResult.PASS,true,false,false,()->false));
    }
    @Test void consumedVanillaResultsAreNeverOpenedAgain() {
        for(var result:new InteractionResult[]{InteractionResult.SUCCESS,InteractionResult.SUCCESS_SERVER,InteractionResult.CONSUME,InteractionResult.FAIL})
            assertSame(result,WardrobeInteractionHandler.fallback(result,true,false,true,()->{fail("原版已处理结果不能开窗。");return false;}));
    }
    @Test void ineligibleTargetNeverCallsOpen() {
        assertSame(InteractionResult.PASS,WardrobeInteractionHandler.fallback(InteractionResult.PASS,false,false,true,()->{fail();return false;}));
    }
}
