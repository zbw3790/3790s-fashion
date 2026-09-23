package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.WardrobeS04Fixture.*;
import java.util.*;
import java.util.stream.IntStream;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(WardrobeLanguageTestSupport.class)
class WardrobeLocalizationTest {
    @ParameterizedTest @ValueSource(strings={"zh_cn","en_us"})
    void actualClientLanguageConsumesNamespaceAndLiteralData(String locale) {
        WardrobeLanguageTestSupport.use(locale);
        assertTrue(Language.getInstance().has("gui.fashion_3790.title"));
        assertEquals(locale.equals("zh_cn")?"衣柜":"Wardrobe",WardrobeText.text("title").getString());
        String authorName="gui.fashion_3790.title 100% %s {name} 中English";
        assertEquals(authorName,Component.literal(authorName).getString());
        assertTrue(WardrobeText.text("status.current",authorName).getString().endsWith(authorName));
        assertEquals("missing.fashion_3790.key",Component.translatable("missing.fashion_3790.key").getString());
        assertEquals("fallback "+authorName,Component.translatableWithFallback("missing.fashion_3790.key","fallback %s",authorName).getString());
        assertEquals("B / A / 100%",Component.translatableWithFallback("format.fixture","%2$s / %1$s / 100%%","A","B").getString());
        // 未提供的语言沿用 Vanilla 的 en_us 回退，不增加第三种产品语言。
        assertEquals("Wardrobe",WardrobeLanguageTestSupport.load("xx_missing").getOrDefault("gui.fashion_3790.title"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void languageRefreshPreservesTheSameSessionAndPendingConflict(boolean pending) {
        var f=new WardrobeS04Fixture();var screen=f.open();var session=screen.selectionSession();
        session.select(Optional.of(CAPE_A));screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        screen.outfitContent().setScope(OutfitScope.HEAD);screen.outfitContent().select(LOOK);
        screen.outfitContent().changePage(true);screen.outfitContent().openDetail();
        var b=screen.layout().previewDragBounds();screen.previewRotation().beginDrag(b.centerX(),b.centerY(),0,b);
        screen.previewRotation().drag(0,47);screen.previewRotation().endDrag(0);
        screen.children().stream().filter(WardrobePreviewModeButton.class::isInstance)
                .map(WardrobePreviewModeButton.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(257,0,0));
        if(pending) { screen.applySelection();f.update(state(1,PlayerFashionStoredState.DEFAULT.withCape(Optional.of(CAPE_B))));screen.tick(); }
        var draft=session.fullSnapshot();var baseline=session.fullState();long request=session.pendingRequestId();
        for(String locale:List.of("en_us","zh_cn","en_us")) {
            WardrobeLanguageTestSupport.use(locale);screen.tick();
            assertSame(session,screen.selectionSession());assertEquals(draft,session.fullSnapshot());assertEquals(baseline,session.fullState());
            assertEquals(request,session.pendingRequestId());assertEquals(pending,session.waiting());assertEquals(pending,session.conflict());
            assertEquals(47F,screen.previewRotation().yawDegrees());assertEquals(WardrobePreviewMode.ELYTRA,screen.previewMode());
            assertEquals(OutfitScope.HEAD,screen.outfitContent().scope());assertEquals(1,screen.outfitContent().pageIndex());
            assertEquals(OutfitWardrobeContent.Panel.DETAIL,screen.outfitContent().panel());
            assertEquals(WardrobeScreen.SelectedTab.OUTFIT,screen.selectedTab());
            assertNotNull(button(screen,locale.equals("en_us")?"Apply":"应用"));
            if(pending) {
                assertEquals(WardrobeStatusText.Priority.ERROR,screen.statusText().priority());
                assertEquals(WardrobeText.string("status.conflict"),screen.statusText().secondLine());
                assertTrue(screen.statusText().fullText().contains(WardrobeText.string("status.pending_fact")));
                assertFalse(screen.canApply());assertFalse(button(screen,locale.equals("en_us")?"Reload":"重新加载").active);
            }
        }
        assertEquals(pending?1:0,f.sent.size());
    }

    @Test void closingThenChangingLanguageDoesNotPersistUnappliedDraft() {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.selectionSession().select(Optional.of(CAPE_A));screen.onClose();
        WardrobeLanguageTestSupport.use("en_us");var reopened=f.open();
        assertTrue(reopened.selectionSession().draft().isEmpty());assertFalse(reopened.selectionSession().dirty());assertTrue(f.sent.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"zh_cn","en_us"})
    void serverCommandUsesTranslatableComponentsWithoutChangingTheReloadResult(String locale) {
        WardrobeLanguageTestSupport.use(locale);
        var result=new OutfitRegistryReloadService.Result(true,true,"技术日志",7,2,1,List.of());
        String text=OutfitReloadCommand.feedback(result).getString();
        assertTrue(text.contains("7") && text.contains("2") && text.contains("1"));assertFalse(text.contains("commands."));
        for(String reason:List.of("装束根目录不可用或扫描不可信，保留当前目录和全部选择。",
                "盔甲根目录不可用或扫描不可信，两个领域都保留此前快照。",
                "时装服务只读、已停止或运行期容量／版本耗尽，保留当前目录和全部选择。","时装服务尚未就绪或正在停止。")) {
            String failure=OutfitReloadCommand.feedback(OutfitRegistryReloadService.Result.failed(reason)).getString();
            assertFalse(failure.contains("commands."));if(locale.equals("en_us")) assertFalse(failure.codePoints().anyMatch(c -> c>127));
        }
        String unknown="未来技术错误 %s {x}";
        assertEquals(unknown,OutfitReloadCommand.feedback(OutfitRegistryReloadService.Result.failed(unknown)).getString());
    }

    @ParameterizedTest @ValueSource(ints={192,200,211,320,480,960})
    void tooltipCenterKeyboardAndEdgesPreserveTargetAndReadableBounds(int width) {
        int height=240;var layout=WardrobeLayout.calculate(width,height,9);
        for(var target:List.of(layout.previewModeButtonBounds(),layout.tabBounds(0),layout.tabBounds(1),layout.tabBounds(2),
                layout.statusBounds(),layout.outfitEntryBounds(7))) {
            int w=WardrobeTooltipLayout.textWidth(width),h=WardrobeTooltipLayout.textHeight(height,target);
            var placed=WardrobeTooltipLayout.place(width,height,target.centerX(),target.centerY(),w,h,target,
                    List.of(layout.applyButtonBounds(),layout.paginationBounds()));
            var outer=new WardrobeLayout.Bounds(placed.x()-4,placed.y()-4,placed.width()+8,placed.height()+8);
            assertTrue(outer.isWithin(width,height));assertFalse(outer.overlaps(target));
        }
    }

    @Test void longTooltipPagesPreserveEveryLineIncludingLastReason() {
        var lines=IntStream.range(0,99).mapToObj(i -> "完整信息 %s {} 中文长名称 "+i).toList();
        var recovered=new ArrayList<String>();
        for(int page=0;page<25;page++) recovered.addAll(WardrobeTooltipLayout.page(lines,4,page));
        assertEquals(lines,recovered);assertEquals(List.of(lines.getLast()),WardrobeTooltipLayout.page(lines,1,999));
    }
}
