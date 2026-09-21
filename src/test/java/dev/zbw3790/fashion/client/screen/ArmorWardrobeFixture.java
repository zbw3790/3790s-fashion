package dev.zbw3790.fashion.client.screen;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.client.armor.*;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureManager;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.network.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Screen 输入与真实统一草稿；仅 GPU 所有权用普通 JVM 后端代替。 */
final class ArmorWardrobeFixture {
    static final ArmorStyleId BLUE=new ArmorStyleId("armor00"),HEAD_ONLY=new ArmorStyleId("armor01"),ORANGE=new ArmorStyleId("armor02");
    final WardrobeS04Fixture base=new WardrobeS04Fixture();
    final ClientArmorResources resources;
    final WardrobeArmorSource source;
    final ArmorAsset asset;
    long generation;
    ArmorWardrobeFixture(Path directory) throws Exception {
        asset=ArmorAsset.fromBytes(ArmorTextureFixture.png(0xff3790ff));
        resources=new ClientArmorResources(directory,NOPLogger.NOP_LOGGER);
        resources.sync.begin(base.connection);resources.textures.begin(base.connection);
        source=new WardrobeArmorSource(resources,base.connection);
        install(entries(14,asset.hash()));
    }
    static List<ArmorRegistrySnapshot.Entry> entries(int count,String hash) {
        var result=new ArrayList<ArmorRegistrySnapshot.Entry>();
        for(int i=count-1;i>=0;i--) result.add(new ArmorRegistrySnapshot.Entry(new ArmorStyleId("armor%02d".formatted(i)),"样式 %02d".formatted(i),
                i==1?Set.of(ArmorSlot.HEAD):EnumSet.allOf(ArmorSlot.class),i==1?Map.of(ArmorGeometry.OUTER,hash):Map.of(ArmorGeometry.OUTER,hash,ArmorGeometry.INNER,hash)));
        return result;
    }
    void install(List<ArmorRegistrySnapshot.Entry> entries) { install(new ArmorRegistrySnapshot(true,entries)); }
    void install(ArmorRegistrySnapshot snapshot) { assertEquals(ClientArmorRegistry.Result.APPLIED,resources.sync.snapshot(base.connection,generation++,snapshot)); }
    void ready() {
        resources.store.store(asset.hash(),asset.bytes());
        resources.textures.register(base.connection,resources.store,asset.hash(),(id,png) -> new ClientArmorTextureManager.OwnedTexture() {
            boolean closed;public boolean ready(){return !closed;}public void close(){closed=true;}
        },NOPLogger.NOP_LOGGER);
    }
    WardrobeScreen open() {
        var screen=new WardrobeScreen(null,new Font(null),base.capes,new ClientCapeTextureManager(),base.authority,base.requests,
                WardrobeS04Fixture.SELF,base.connection,new WardrobeScreen.Actions(() -> base.canSend,event -> event.key()==69,
                    base.legacySent::add,() -> base.closeCount++,() -> base.canSend,base.sent::add,() -> base.receiverReady),base.source,source);
        screen.width=320;screen.height=240;screen.init();return screen;
    }
    WardrobeScreen armor() { var screen=open();tab(screen,WardrobeScreen.SelectedTab.ARMOR);return screen; }
    static void tab(WardrobeScreen screen,WardrobeScreen.SelectedTab tab) {
        var bounds=screen.layout().tabBounds(tab.ordinal());
        var widget=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(value -> value.getX()==bounds.x() && value.getY()==bounds.y()).findFirst().orElseThrow();
        screen.setFocused(widget);assertTrue(screen.keyPressed(new KeyEvent(257,0,0)));
    }
    static AbstractWidget button(WardrobeScreen screen,String suffix) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(value -> {
                    String key=value instanceof WardrobeArmorButton b?b.key():value instanceof ArmorGridEntryWidget b?b.key():"";
                    return key.equals(suffix) || key.endsWith(":"+suffix);
                }).findFirst().orElseThrow();
    }
    static void slot(WardrobeScreen screen,ArmorSlot slot) { press(screen,"scope");press(screen,"slot:"+slot.serializedName()); }
    static void press(WardrobeScreen screen,String suffix) {
        var target=button(screen,suffix);assertTrue(target.active,"测试应通过可用输入进入生产操作："+suffix);
        screen.setFocused(target);assertTrue(screen.keyPressed(new KeyEvent(257,0,0)));
    }
    static PlayerFashionStoredState stored(WardrobeScreen screen) { return screen.selectionSession().fullSnapshot().orElseThrow(); }
    static ArmorSelections armor(WardrobeScreen screen) { return stored(screen).armor(); }
    static FullPlayerFashionState state(long revision,PlayerFashionStoredState stored) {
        return new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),stored.outfit(),stored.armor()),revision);
    }
    void success(WardrobeScreen screen) {
        var request=base.sent.getLast();base.result(screen,request.requestId(),FullFashionSelectionStatus.SUCCESS,state(request.expectedRevision()+1,request.stored()));
    }
}
