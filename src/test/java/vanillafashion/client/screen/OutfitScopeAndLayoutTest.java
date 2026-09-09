package vanillafashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import static vanillafashion.client.screen.WardrobeS04Fixture.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import net.minecraft.client.gui.components.AbstractWidget;
import vanillafashion.client.fashion.ClientPlayerFashionRegistry;
import vanillafashion.outfit.*;

class OutfitScopeAndLayoutTest {
    @ParameterizedTest @EnumSource(OutfitScope.class)
    void everyProvidedSubsetChangesExactlyIntersectionAndBuiltinsChangeWholeTarget(OutfitScope scope) {
        for (int mask=1;mask<64;mask++) for (String id:List.of("new","original","none")) {
            var provided=EnumSet.noneOf(OutfitPart.class);
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER) if ((mask&(1<<part.ordinal()))!=0) provided.add(part);
            var baseline=new vanillafashion.fashion.PlayerFashionStoredState(Optional.of(CAPE_A),
                    OutfitSelections.original().set(OutfitPart.ALL,OutfitPartSelection.outfit(new OutfitId("old"))).selections());
            var session=new WardrobeSelectionSession();session.observeFull(Optional.of(state(0,baseline)),ClientPlayerFashionRegistry.State.AVAILABLE);
            session.selectOutfit(scope.targets,new OutfitId(id),provided);
            var actual=session.fullSnapshot().orElseThrow();
            assertEquals(Optional.of(CAPE_A),actual.cape());
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER)
                assertEquals(scope.targets.contains(part)&&provided.contains(part)?OutfitPartSelection.outfit(new OutfitId(id)):baseline.outfit().get(part),actual.outfit().get(part));
            for (var builtin:List.of(OutfitPartSelection.ORIGINAL,OutfitPartSelection.NONE)) {
                session.clearOutfit(scope.targets,builtin);
                for (OutfitPart part:scope.targets) assertEquals(builtin,session.fullSnapshot().orElseThrow().outfit().get(part));
                assertEquals(Optional.of(CAPE_A),session.draft());
            }
        }
    }
    @Test void summaryUsesCanonicalPartsOnlyAndNoShadowGroupValue() {
        var f=new WardrobeS04Fixture();var screen=f.open();var content=screen.outfitContent();
        content.select(LOOK);assertEquals("look00",content.summary());
        content.setScope(OutfitScope.HEAD);content.clear(OutfitPartSelection.NONE);assertEquals("无外层",content.summary());
        content.setScope(OutfitScope.ALL);assertEquals("混搭",content.summary());
        assertSame(OutfitScope.HEAD,OutfitScope.detail(OutfitPart.HEAD));
        content.clear(OutfitPartSelection.ORIGINAL);assertEquals("原版",content.summary());
    }
    @Test void registryKeepsAll256InCanonicalOrderAndThirtyTwoPagesAcrossScopes() {
        var f=new WardrobeS04Fixture(vanillafashion.client.outfit.ClientOutfitRegistry.State.KNOWN,256,true,
                vanillafashion.fashion.FullPlayerFashionState.defaults(0));
        var screen=f.open();var content=screen.outfitContent();
        var expected=f.outfits.entries().stream().map(entry -> entry.id()).toList();
        assertEquals(256,expected.size());assertEquals(expected.stream().sorted().toList(),expected);
        assertEquals(32,content.pageCount());var seen=new ArrayList<OutfitId>();
        for (int i=0;i<32;i++) {
            content.setScope(i%2==0?OutfitScope.HEAD:OutfitScope.LEGS);
            assertEquals(i,content.pageIndex());
            seen.addAll(content.pageEntries().stream().map(entry -> entry.id()).toList());
            content.changePage(true);
        }
        assertEquals(expected,seen);assertEquals(31,content.pageIndex());
        assertThrows(UnsupportedOperationException.class,() -> f.outfits.entries().clear());
    }
    @ParameterizedTest @ValueSource(ints={192,200,210,211,320})
    void realWidgetsWithinApprovedFrameAndNoUnexpectedOverlapInEveryPanel(int width) {
        var f=new WardrobeS04Fixture();var screen=f.open();screen.selectTab(WardrobeScreen.SelectedTab.OUTFIT);
        for (OutfitWardrobeContent.Panel panel:OutfitWardrobeContent.Panel.values()) {
            switch(panel) { case GRID->screen.outfitContent().closePanel();case ROOT->screen.outfitContent().openPanel();case DETAIL->screen.outfitContent().openDetail(); }
            screen.resize(width,214);var l=screen.layout();
            assertTrue(l.fitsScreen());assertEquals(width<211?176:195,l.frameBounds().width());
            var controls=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w -> w.visible).toList();
            var bounds=controls.stream().map(w -> new WardrobeLayout.Bounds(w.getX(),w.getY(),w.getWidth(),w.getHeight())).toList();
            for (int i=0;i<bounds.size();i++) {
                var b=bounds.get(i);assertTrue(b.isWithin(width,214));assertTrue(b.width()>=12);assertTrue(b.height()>=16);
                for (int j=i+1;j<bounds.size();j++) assertFalse(b.overlaps(bounds.get(j)),controls.get(i).getMessage()+" / "+controls.get(j).getMessage());
            }
            assertFalse(l.previewModeButtonBounds().overlaps(l.previewDragBounds()));
            assertFalse(l.tooltipBounds().overlaps(l.applyButtonBounds()));assertFalse(l.tooltipBounds().overlaps(l.paginationBounds()));
            assertFalse(l.reloadButtonBounds().overlaps(l.paginationBounds()));assertFalse(l.statusBounds().overlaps(l.applyButtonBounds()));
            assertEquals(88,l.outfitGridBounds().width());assertEquals(68,l.outfitGridBounds().height());
            for (int i=0;i<8;i++) { var b=l.outfitEntryBounds(i);assertTrue(b.x()>=l.gridBounds().x() && b.right()<=l.gridBounds().right());assertTrue(b.bottom()<=l.gridBounds().bottom()); }
        }
    }
    @ParameterizedTest @ValueSource(ints={320,200})
    void selectedTabSeamStaysContinuousForEitherTab(int width) {
        var layout=WardrobeLayout.calculate(width,240,9);
        for (int selected=0;selected<2;selected++) {
            Map<String,Integer> pixels=new HashMap<>();
            WardrobeGuiPainter.frameWithTabs((x,y,r,b,color) -> {
                for (int yy=y;yy<b;yy++) for(int xx=x;xx<r;xx++) pixels.put(xx+":"+yy,color);
            },layout,selected);
            var tab=layout.tabJoinBounds(selected);
            for (int yy=tab.y();yy<tab.bottom();yy++) for(int xx=tab.x()+4;xx<tab.right()-4;xx++)
                assertEquals(WardrobeGuiPainter.FRAME_COLOR,pixels.get(xx+":"+yy));
        }
    }
}
