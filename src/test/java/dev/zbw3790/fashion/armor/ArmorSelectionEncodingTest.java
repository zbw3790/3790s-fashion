package dev.zbw3790.fashion.armor;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ArmorSelectionEncodingTest {
    @Test void all81MixedModesHaveIndependentEncodingAndNoMutableSlots() {
        for(int combination=0;combination<81;combination++) {
            int value=combination,mask=0;var selections=ArmorSelections.original();var expected=new java.io.ByteArrayOutputStream();
            var ids=new ArrayList<String>();
            for(var slot:ArmorSlot.CANONICAL_ORDER) {
                int mode=value%3;value/=3;mask|=mode<<(2*slot.ordinal());
                if(mode==1) selections=selections.with(slot,ArmorSelection.HIDDEN);
                if(mode==2) {String id="slot_"+slot.ordinal();ids.add(id);selections=selections.with(slot,ArmorSelection.custom(new ArmorStyleId(id)));}
            }
            expected.write(mask);for(String id:ids){expected.write(id.length());expected.writeBytes(id.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
            assertArrayEquals(expected.toByteArray(),ArmorSelectionEncoding.encode(selections));assertEquals(selections,ArmorSelectionEncoding.decode(expected.toByteArray()));
            var immutable=selections;assertThrows(UnsupportedOperationException.class,()->immutable.slots().set(0,ArmorSelection.HIDDEN));
        }
    }
    @ParameterizedTest @ValueSource(strings={"","A","../x","a/b","a:b","中文","a b","a\n","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void invalidIds(String id){assertThrows(IllegalArgumentException.class,()->new ArmorStyleId(id));}
    @ParameterizedTest @ValueSource(ints={3,12,48,192,255}) void undefinedModes(int mode){assertThrows(IllegalArgumentException.class,()->ArmorSelectionEncoding.decode(new byte[]{(byte)mode}));}
    @Test void truncatedTrailingAndInvalidAscii() {
        for(byte[] bytes:List.of(new byte[0],new byte[]{0,0},new byte[]{2},new byte[]{2,0},new byte[]{2,33},new byte[]{2,1,(byte)255},new byte[]{2,2,'a'}))
            assertThrows(IllegalArgumentException.class,()->ArmorSelectionEncoding.decode(bytes));
    }
    @Test void maximumHas133Bytes() {
        var selections=ArmorSelections.original();for(var slot:ArmorSlot.CANONICAL_ORDER)selections=selections.with(slot,ArmorSelection.custom(new ArmorStyleId("a".repeat(32))));
        byte[] bytes=ArmorSelectionEncoding.encode(selections);assertEquals(133,bytes.length);assertEquals(selections,ArmorSelectionEncoding.decode(bytes));
    }
}
