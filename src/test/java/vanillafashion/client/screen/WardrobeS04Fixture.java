package vanillafashion.client.screen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.KeyEvent;
import org.slf4j.LoggerFactory;
import vanillafashion.cape.*;
import vanillafashion.client.cape.*;
import vanillafashion.client.outfit.*;
import vanillafashion.client.fashion.*;
import vanillafashion.client.network.*;
import vanillafashion.fashion.*;
import vanillafashion.network.*;
import vanillafashion.outfit.*;

/** 普通 JVM 驱动真实 Screen、Registry 和 Session；发送只收集 Payload，不模拟游戏输入。 */
final class WardrobeS04Fixture {
    static final UUID SELF=new UUID(0,23);
    static final CapeId CAPE_A=new CapeId("cape_a"),CAPE_B=new CapeId("cape_b");
    static final OutfitId LOOK=new OutfitId("look00");
    final Object connection=new Object();
    final ClientPlayerFashionRegistry authority=new ClientPlayerFashionRegistry();
    final ClientCapeSelectionRequestTracker requests=new ClientCapeSelectionRequestTracker();
    final ClientCapeRegistry capes=new ClientCapeRegistry();
    final ClientOutfitRegistry outfits=new ClientOutfitRegistry();
    final ClientOutfitAssetStore store=new ClientOutfitAssetStore();
    final ClientOutfitTextureManager textures=new ClientOutfitTextureManager();
    final ClientOutfitTextureResolver resolver=new ClientOutfitTextureResolver(outfits,store,textures);
    final AtomicReference<OutfitModel> model=new AtomicReference<>(OutfitModel.WIDE);
    final WardrobeOutfitSource source=new WardrobeOutfitSource(outfits,resolver,null,() -> connection,model::get);
    final List<SetFullFashionSelectionPayload> sent=new ArrayList<>();
    final List<SetCapeSelectionPayload> legacySent=new ArrayList<>();
    final byte[] png;
    final String hash;
    boolean canSend=true,receiverReady=true;
    int closeCount;
    WardrobeS04Fixture() { this(ClientOutfitRegistry.State.KNOWN,25,true,FullPlayerFashionState.defaults(0)); }
    WardrobeS04Fixture(ClientOutfitRegistry.State state,int count,boolean ready,FullPlayerFashionState initial) {
        png=png();hash=CapeAssetHash.sha256(png);
        authority.beginConnection(connection);requests.beginConnection(connection);
        authority.receiveFullSnapshot(connection,new FullPlayerFashionSnapshot(true,List.of(new FullPlayerFashionEntry(SELF,initial))));
        capes.replace(new CapeRegistrySnapshot(List.of(new CapeCosmeticMetadata(CAPE_A,"a".repeat(64),Optional.empty()),
                new CapeCosmeticMetadata(CAPE_B,"b".repeat(64),Optional.empty()))));
        outfits.begin(connection);textures.begin(connection);
        if (state==ClientOutfitRegistry.State.KNOWN) {
            List<OutfitRegistrySnapshot.Entry> entries=new ArrayList<>();
            for (int i=count-1;i>=0;i--) {
                Set<OutfitPart> parts=i==1?Set.of(OutfitPart.HEAD):OutfitPart.ALL;
                Set<OutfitModel> models=i==2?Set.of(OutfitModel.WIDE):Set.of(OutfitModel.WIDE,OutfitModel.SLIM);
                Map<OutfitModel,String> hashes=i==3?Map.of():i==2?Map.of(OutfitModel.WIDE,hash):Map.of(OutfitModel.WIDE,hash,OutfitModel.SLIM,hash);
                entries.add(new OutfitRegistrySnapshot.Entry(new OutfitId("look%02d".formatted(i)),parts,models,hashes));
            }
            outfits.replace(connection,new OutfitRegistrySnapshot(true,entries));
        } else if (state==ClientOutfitRegistry.State.UNAVAILABLE) outfits.replace(connection,OutfitRegistrySnapshot.unavailable());
        else if (state==ClientOutfitRegistry.State.UNSUPPORTED) outfits.unsupported(connection);
        if (ready) ready();
    }
    void ready() {
        store.store(hash,png);
        textures.register(connection,store,hash,(id,bytes) -> new ClientOutfitTextureManager.OwnedTexture() {
            public boolean ready() { return true; }
            public void close() { }
        },LoggerFactory.getLogger("衣柜测试"));
    }
    WardrobeScreen open() {
        var screen=new WardrobeScreen(null,new Font(null),capes,new ClientCapeTextureManager(),authority,requests,SELF,connection,
                new WardrobeScreen.Actions(() -> canSend,event -> event.key()==69,legacySent::add,() -> closeCount++,
                        () -> canSend,sent::add,() -> receiverReady),source);
        screen.width=320;screen.height=240;screen.init();return screen;
    }
    void update(FullPlayerFashionState value) { authority.receiveFullUpdate(connection,new FullPlayerFashionEntry(SELF,value)); }
    void result(WardrobeScreen screen,long request,FullFashionSelectionStatus status,FullPlayerFashionState value) {
        ClientFullFashionSelectionResults.apply(connection,connection,SELF,new FullFashionSelectionResultPayload(request,status,Optional.ofNullable(value)),
                authority,() -> screen==null?null:screen.selectionSession());
        if (screen!=null) screen.tick();
    }
    static FullPlayerFashionState state(long revision,PlayerFashionStoredState stored) {
        return new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),stored.outfit()),revision);
    }
    static AbstractButton button(WardrobeScreen screen,String label) {
        return screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    static void press(WardrobeScreen screen,String label) { button(screen,label).onPress(new KeyEvent(257,0,0)); }
    private static byte[] png() {
        try {
            var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            image.setRGB(40,8,0x80806040);
            var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
