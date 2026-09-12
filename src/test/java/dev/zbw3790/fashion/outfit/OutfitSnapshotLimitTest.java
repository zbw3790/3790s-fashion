package dev.zbw3790.fashion.outfit;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.zbw3790.fashion.testutil.S03TestAssets;
import static org.junit.jupiter.api.Assertions.*;
class OutfitSnapshotLimitTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(ints={255,256,257}) void trustedMetadataCountsEvenWhenAllModelsInvalid(int count)throws Exception{
        for(int i=0;i<count;i++)S03TestAssets.outfit(temp,"robe_"+i,OutfitPart.ALL,false,false);
        var loaded=new OutfitRegistryLoader(4096).load(temp);var snapshot=OutfitRegistrySnapshot.from(loaded);assertEquals(count<=256,snapshot.available());assertEquals(count<=256?count:0,snapshot.entries().size());assertEquals(0,loaded.assets().size());if(count>256)assertFalse(loaded.knowledge().isDefinitelyAbsent(new OutfitId("not_there")));
    }
    @Test void metadataInvalidChildrenDoNotConsumeDefinitionSlots()throws Exception{for(int i=0;i<256;i++)S03TestAssets.outfit(temp,"robe_"+i,OutfitPart.ALL,false,false);Files.createDirectories(temp.resolve("invalid"));Files.writeString(temp.resolve("invalid/outfit.json"),"{}");var result=new OutfitRegistryLoader(4096).load(temp);assertTrue(result.knowledge().trustworthy());assertEquals(256,result.registry().size());assertTrue(result.knowledge().knownExisting(new OutfitId("invalid")));}
    @Test void stopBeforeReading257thDefinitionsModelBytes()throws Exception{for(int i=0;i<257;i++)S03TestAssets.outfit(temp,"robe_%03d".formatted(i),OutfitPart.ALL,true,true);int[] pngReads={0};var access=new OutfitFileAccess(){@Override void beforeOpen(Path path){if(path.toString().endsWith(".png"))pngReads[0]++;}};var loaded=new OutfitRegistryLoader(4096,access).load(temp);assertFalse(loaded.knowledge().trustworthy());assertEquals(512,pngReads[0]);assertEquals(0,loaded.assets().size());}
    @Test void root4097EntriesExceedSeparateEnumerationBudget()throws Exception{for(int i=0;i<4097;i++)Files.writeString(temp.resolve("entry_"+i),"");var loaded=new OutfitRegistryLoader(4096).load(temp);assertFalse(loaded.knowledge().trustworthy());assertFalse(OutfitRegistrySnapshot.from(loaded).available());}
}
