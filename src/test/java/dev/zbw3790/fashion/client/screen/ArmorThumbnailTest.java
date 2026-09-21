package dev.zbw3790.fashion.client.screen;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import dev.zbw3790.fashion.armor.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.ArmorWardrobeFixture.*;

class ArmorThumbnailTest {
    @TempDir Path directory;
    @ParameterizedTest @EnumSource(ArmorSlot.class)
    void samplesStayInFrontAtlasAndNeverOverlapNameOrOtherParts(ArmorSlot slot) {
        var pixels=new HashSet<String>();
        for(var face:ArmorThumbnail.plan(slot)) {
            assertTrue(face.u()>=0 && face.v()>=0 && face.u()+face.width()<=64 && face.v()+face.height()<=32);
            assertTrue(face.x()>=0 && face.y()>=0 && face.x()+face.width()*face.scale()<=20 && face.y()+face.height()*face.scale()<=20);
            for(int y=face.y();y<face.y()+face.height()*face.scale();y++)
                for(int x=face.x();x<face.x()+face.width()*face.scale();x++) assertTrue(pixels.add(x+":"+y));
        }
        assertFalse(pixels.isEmpty());
    }
    @Test void geometryUsesOuterExceptLeggingsAndMirrorsPairedLimbs() {
        assertEquals(ArmorGeometry.INNER,ArmorGeometry.forSlot(ArmorSlot.LEGS));
        for(var slot:List.of(ArmorSlot.HEAD,ArmorSlot.CHEST,ArmorSlot.FEET)) assertEquals(ArmorGeometry.OUTER,ArmorGeometry.forSlot(slot));
        var head=ArmorThumbnail.plan(ArmorSlot.HEAD).getFirst();assertEquals(8,head.u());assertEquals(8,head.v());assertEquals(2,head.scale());
        for(var slot:List.of(ArmorSlot.CHEST,ArmorSlot.LEGS,ArmorSlot.FEET)) assertEquals(1,ArmorThumbnail.plan(slot).stream().filter(ArmorThumbnail.Face::mirror).count());
    }
    @Test void thumbnailSharesTextureOwnerAndRejectsStaleConnection() throws Exception {
        var f=new ArmorWardrobeFixture(directory);assertTrue(f.source.texture(ArmorSlot.HEAD,BLUE).isEmpty());f.ready();
        assertEquals(f.resources.resolve(BLUE,ArmorSlot.HEAD).orElseThrow().texture(),f.source.texture(ArmorSlot.HEAD,BLUE).orElseThrow());
        assertTrue(f.source.texture(ArmorSlot.CHEST,HEAD_ONLY).isEmpty());
        assertTrue(new WardrobeArmorSource(f.resources,new Object()).texture(ArmorSlot.HEAD,BLUE).isEmpty());
        f.resources.sync.begin(new Object());assertTrue(f.source.texture(ArmorSlot.HEAD,BLUE).isEmpty());
    }
    @Test void pagesAreIndependentPerSlotAndResizeDoesNotChangeYawOrPanel() throws Exception {
        var f=new ArmorWardrobeFixture(directory);var screen=f.armor();press(screen,"next");
        slot(screen,ArmorSlot.CHEST);assertEquals(0,screen.armorContent().offset());press(screen,"style:armor00");
        slot(screen,ArmorSlot.HEAD);assertEquals(8,screen.armorContent().offset());
        var draft=stored(screen);press(screen,"scope");var yaw=screen.previewRotation().yawDegrees();
        for(int width:List.of(200,320)) { screen.resize(width,240);assertEquals(ArmorWardrobeContent.Panel.SLOTS,screen.armorContent().panel());assertEquals(yaw,screen.previewRotation().yawDegrees());assertEquals(draft,stored(screen)); }
        assertTrue(f.base.sent.isEmpty());
    }
    @Test void pendingAllowsSlotAndPageBrowsingButNeverMutatesDraft() throws Exception {
        var f=new ArmorWardrobeFixture(directory);var screen=f.armor();press(screen,"style:armor00");screen.applySelection();var draft=stored(screen);
        slot(screen,ArmorSlot.CHEST);press(screen,"next");press(screen,"style:armor09");
        assertEquals(draft,stored(screen));assertEquals(1,f.base.sent.size());assertFalse(screen.canApply());
        assertFalse(button(screen,"original").active);assertFalse(button(screen,"hidden").active);
        f.success(screen);assertEquals(ArmorSlot.CHEST,screen.armorContent().slot());assertEquals(8,screen.armorContent().offset());
        press(screen,"style:armor09");assertTrue(screen.canApply());
    }
    @Test void tooltipRetainsResourceNameIdSlotAndSourceWhileTileIsLoading() throws Exception {
        var f=new ArmorWardrobeFixture(directory);var screen=f.armor();var tile=(ArmorGridEntryWidget)button(screen,"style:armor00");
        var text=String.join("|",tile.tooltip());assertTrue(text.contains("样式 00"));assertTrue(text.contains("armor00"));
        assertTrue(text.contains(WardrobeArmorText.string("texture_source","outer")));assertTrue(tile.canActivate());assertTrue(text.contains(WardrobeArmorText.string("loading")));
        press(screen,"style:armor00");assertEquals("样式 00",screen.armorContent().summary());
        assertTrue(tile.tooltip().contains(WardrobeArmorText.string("selected")));
    }
    @Test void actualTabPixelsHaveNoWhiteOutlineOrBackground() throws Exception {
        int[][] actual=new int[16][16];
        ArmorTabIcon.paint((l,t,r,b,color) -> {for(int y=t;y<b;y++)for(int x=l;x<r;x++)actual[y][x]=color;},new WardrobeLayout.Bounds(0,0,16,16));
        var colors=new HashSet<Integer>();
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){colors.add(actual[y][x]);}
        assertTrue(colors.contains(WardrobeGuiPainter.BRAND_BLUE));assertTrue(colors.contains(0));assertFalse(colors.contains(0xffffffff));assertFalse(colors.contains(0xff26292b));
    }
}
