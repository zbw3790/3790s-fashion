package dev.zbw3790.fashion.network;

import java.util.*;
import java.util.stream.Stream;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import dev.zbw3790.fashion.cape.*;
import dev.zbw3790.fashion.fashion.*;
import static org.junit.jupiter.api.Assertions.*;

/** 旧十种 family 的固定字段字节；预期值不由待测 Codec 生成。 */
@SuppressWarnings({"rawtypes","unchecked"})
class LegacyPayloadGoldenTest {
    record Golden(String name,StreamCodec codec,Object value,String hex){@Override public String toString(){return name;}}
    static Stream<Golden> golden(){
        var cape=new CapeId("founder");var id=new UUID(0,1);var state=PlayerFashionAuthoritativeState.active(cape);
        String uuid="00000000000000000000000000000001", selection="0107666f756e646572", authority=selection+selection;
        String hashA="40"+"61".repeat(64),hashB="40"+"62".repeat(64);
        return Stream.of(
            new Golden("open_wardrobe",OpenWardrobePayload.CODEC,OpenWardrobePayload.INSTANCE,""),
            new Golden("wardrobe_available",WardrobeAvailablePayload.CODEC,WardrobeAvailablePayload.INSTANCE,""),
            new Golden("cape_registry_snapshot",CapeRegistrySnapshotPayload.CODEC,new CapeRegistrySnapshotPayload(new CapeRegistrySnapshot(List.of(new CapeCosmeticMetadata(cape,"a".repeat(64),Optional.of("b".repeat(64)))))),"0107666f756e646572"+hashA+"01"+hashB),
            new Golden("cape_asset_data",CapeAssetDataPayload.CODEC,new CapeAssetDataPayload("a".repeat(64),new byte[]{1,2,3}),hashA+"03010203"),
            new Golden("player_fashion_snapshot",PlayerFashionSnapshotPayload.CODEC,new PlayerFashionSnapshotPayload(new PlayerFashionSnapshot(true,List.of(new PlayerFashionEntry(id,state)))),"0101"+uuid+authority),
            new Golden("player_fashion_update",PlayerFashionUpdatePayload.CODEC,new PlayerFashionUpdatePayload(new PlayerFashionEntry(id,state)),uuid+authority),
            new Golden("player_fashion_remove",PlayerFashionRemovePayload.CODEC,new PlayerFashionRemovePayload(id),uuid),
            new Golden("cape_selection_result",CapeSelectionResultPayload.CODEC,new CapeSelectionResultPayload(1,true,state,CapeSelectionReason.APPLIED),"000000000000000101"+authority+"00"),
            new Golden("cape_asset_request",CapeAssetRequestPayload.CODEC,new CapeAssetRequestPayload(List.of("a".repeat(64))),"01"+hashA),
            new Golden("set_cape_selection",SetCapeSelectionPayload.CODEC,new SetCapeSelectionPayload(1,Optional.of(cape)),"0000000000000001"+selection));
    }
    @ParameterizedTest @MethodSource("golden") void oldTenCodecsAreByteStableAndKeepDecodeSemantics(Golden fixture){
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{fixture.codec.encode(b,fixture.value);byte[] encoded=new byte[b.readableBytes()];b.readBytes(encoded);assertArrayEquals(HexFormat.of().parseHex(fixture.hex),encoded);
            b.clear();b.writeBytes(HexFormat.of().parseHex(fixture.hex));Object decoded=fixture.codec.decode(b);assertFalse(b.isReadable());
            if(decoded instanceof CapeAssetDataPayload asset){var expected=(CapeAssetDataPayload)fixture.value;assertEquals(expected.sha256(),asset.sha256());assertArrayEquals(expected.pngBytes(),asset.pngBytes());}
            else if(decoded instanceof CapeRegistrySnapshotPayload snapshot)assertEquals(((CapeRegistrySnapshotPayload)fixture.value).snapshot().entries(),snapshot.snapshot().entries());
            else assertEquals(fixture.value,decoded);
        }finally{b.release();}
    }
}
