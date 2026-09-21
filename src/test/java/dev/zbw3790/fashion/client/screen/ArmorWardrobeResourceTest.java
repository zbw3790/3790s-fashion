package dev.zbw3790.fashion.client.screen;

import java.nio.file.Path;
import java.util.*;
import java.io.IOException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.helpers.NOPLogger;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.client.armor.*;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.network.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.zbw3790.fashion.client.screen.ArmorWardrobeFixture.*;

class ArmorWardrobeResourceTest {
    @TempDir Path directory;
    ArmorWardrobeFixture f;
    @BeforeEach void setup() throws Exception { f=new ArmorWardrobeFixture(directory); }
    @Test void metadataSelectableBeforePixelsThenReadyDoesNotEditOrSend() {
        var screen=f.armor();assertEquals(WardrobeArmorSource.Availability.LOADING,f.source.availability(ArmorSlot.HEAD,BLUE));
        press(screen,"style:armor00");assertTrue(screen.canApply());assertTrue(screen.armorContent().hasLoading());var before=stored(screen);
        f.ready();screen.tick();assertEquals(WardrobeArmorSource.Availability.READY,f.source.availability(ArmorSlot.HEAD,BLUE));
        assertFalse(screen.armorContent().hasLoading());assertEquals(before,stored(screen));assertTrue(f.base.sent.isEmpty());
    }
    @Test void vanishedSavedStyleRemainsDormantAndUnrelatedCapeApplyPreservesIt() {
        var saved=PlayerFashionStoredState.DEFAULT.withArmor(ArmorSelections.original().with(ArmorSlot.HEAD,ArmorSelection.custom(BLUE)));
        f.base.update(state(1,saved));var screen=f.armor();f.install(List.of());screen.tick();
        assertTrue(screen.armorContent().hasDormant());assertEquals(saved,stored(screen));assertTrue(screen.armorContent().slotTooltip(ArmorSlot.HEAD).contains(BLUE.value()));
        screen.selectionSession().select(Optional.of(WardrobeS04Fixture.CAPE_A));assertTrue(screen.canApply());screen.applySelection();assertEquals(saved.armor(),f.base.sent.getFirst().stored().armor());
        f.success(screen);f.install(entries(14,f.asset.hash()));f.ready();screen.tick();assertFalse(screen.armorContent().hasDormant());assertEquals(saved.armor(),armor(screen));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void unavailableRegistryAllowsClearAndOtherFieldsButNoNewCustom(boolean unavailable) {
        if(unavailable) f.install(ArmorRegistrySnapshot.unavailable());else f.resources.sync.begin(f.base.connection);
        var old=PlayerFashionStoredState.DEFAULT.withArmor(ArmorSelections.original().with(ArmorSlot.HEAD,ArmorSelection.custom(BLUE)));f.base.update(state(1,old));
        var screen=f.armor();assertEquals(unavailable?ClientArmorRegistry.State.UNAVAILABLE:ClientArmorRegistry.State.UNKNOWN,f.source.state());
        assertTrue(screen.armorContent().entries().isEmpty());assertFalse(f.source.admitted(ArmorSlot.HEAD,ORANGE));
        screen.selectionSession().select(Optional.of(WardrobeS04Fixture.CAPE_A));assertTrue(screen.canApply());
        screen.selectionSession().selectArmor(Set.of(ArmorSlot.HEAD),ORANGE,Set.of(ArmorSlot.HEAD));assertFalse(screen.canApply());
        press(screen,"original");assertTrue(screen.canApply());assertEquals(ArmorSelections.original(),armor(screen));
        press(screen,"hidden");assertTrue(screen.canApply());assertEquals(1,armor(screen).hiddenMask());
    }
    @Test void authorityUnknownLocksMutationsAndDoesNotEraseDraft() {
        var screen=f.armor();press(screen,"style:armor00");var before=stored(screen);
        f.base.authority.receiveFullSnapshot(f.base.connection,FullPlayerFashionSnapshot.unavailable());screen.tick();
        assertFalse(screen.canApply());assertFalse(button(screen,"hidden").active);assertEquals(before,stored(screen));
        assertTrue(button(screen,"scope").active);
    }
    @Test void noAuthorityNeverLabelsFourUnknownSlotsAsConfirmedOriginal() {
        f.base.authority.receiveFullSnapshot(f.base.connection,FullPlayerFashionSnapshot.unavailable());
        var screen=f.armor();press(screen,"scope");
        for(var slot:ArmorSlot.CANONICAL_ORDER) assertEquals(WardrobeArmorText.string("unknown_value"),screen.armorContent().slotValueLabel(slot));
        press(screen,"slot:head");assertFalse(button(screen,"original").active);assertFalse(screen.canApply());
    }
    @Test void knownEmptyStillOffersBothBuiltins() {
        f.install(List.of());var screen=f.armor();assertEquals(ClientArmorRegistry.State.KNOWN,f.source.state());
        assertTrue(button(screen,"original").active);assertTrue(button(screen,"hidden").active);assertTrue(screen.armorContent().showEmpty());
        press(screen,"hidden");assertTrue(screen.canApply());
    }
    @Test void generationRefreshKeepsScreenDraftScopeAndPositionWhileClampingRemovedTail() {
        var screen=f.armor();press(screen,"next");assertEquals(8,screen.armorContent().offset());
        press(screen,"style:armor08");var before=stored(screen);f.install(entries(12,f.asset.hash()));screen.tick();
        assertEquals(8,screen.armorContent().offset());assertEquals(before,stored(screen));
        f.install(entries(4,f.asset.hash()));screen.tick();assertEquals(0,screen.armorContent().offset());assertEquals(before,stored(screen));assertTrue(screen.armorContent().hasDormant());
    }
    @Test void hashChangeDropsOldReadinessWithoutChangingDraftAndLateReadyRestores() throws Exception {
        f.ready();var screen=f.armor();press(screen,"style:armor00");var before=stored(screen);
        var changed=ArmorAsset.fromBytes(ArmorTextureFixture.png(0xffe09037));f.install(entries(14,changed.hash()));screen.tick();
        assertTrue(screen.armorContent().hasLoading());assertEquals(before,stored(screen));assertTrue(f.base.sent.isEmpty());
        f.resources.store.store(changed.hash(),changed.bytes());f.resources.textures.register(f.base.connection,f.resources.store,changed.hash(),
                (id,png) -> new ClientArmorTextureManager.OwnedTexture(){public boolean ready(){return true;}public void close(){}},NOPLogger.NOP_LOGGER);
        screen.tick();assertFalse(screen.armorContent().hasLoading());assertEquals(before,stored(screen));assertTrue(f.base.sent.isEmpty());
    }
    @Test void registrationFailureIsTruthfulReadOnlyStatusAndOldConnectionNeverSuppliesNewScreen() {
        f.resources.store.store(f.asset.hash(),f.asset.bytes());f.resources.textures.register(f.base.connection,f.resources.store,f.asset.hash(),
                (id,png) -> {throw new IOException("受控注册失败");},NOPLogger.NOP_LOGGER);
        assertEquals(WardrobeArmorSource.Availability.FAILED,f.source.availability(ArmorSlot.HEAD,BLUE));
        assertTrue(f.source.admitted(ArmorSlot.HEAD,BLUE));assertFalse(f.resources.textures.failed(new Object(),f.asset.hash()));
        var source=new WardrobeArmorSource(f.resources,new Object());assertEquals(ClientArmorRegistry.State.UNSUPPORTED,source.state());assertTrue(source.entries().isEmpty());
    }
    @Test void previewUnchangedDormantUsesEffectiveButNewDraftDoesNotModifyAuthority() {
        var stored=PlayerFashionStoredState.DEFAULT.withArmor(ArmorSelections.original().with(ArmorSlot.HEAD,ArmorSelection.custom(BLUE)));
        var baseline=new FullPlayerFashionState(stored,new PlayerFashionEffectiveState(stored.cape(),stored.outfit(),ArmorSelections.original()),4);
        var before=new WardrobePreviewDraft(stored,baseline);assertEquals(ArmorSelections.original(),before.armor());
        var changed=stored.withArmor(stored.armor().with(ArmorSlot.CHEST,ArmorSelection.HIDDEN));
        var preview=new WardrobePreviewDraft(changed,baseline);assertEquals(ArmorSelection.HIDDEN,preview.armor().get(ArmorSlot.CHEST));assertEquals(ArmorSelection.ORIGINAL,preview.armor().get(ArmorSlot.HEAD));
        assertEquals(stored,baseline.stored());assertEquals(ArmorSelections.original(),baseline.effective().armor());
    }
    @Test void invalidArmorResultRequiresDeliberateCorrectionAndNeverAutoRetries() {
        var screen=f.armor();press(screen,"style:armor00");screen.applySelection();
        f.base.result(screen,1,FullFashionSelectionStatus.INVALID_ARMOR_SELECTION,FullPlayerFashionState.defaults(0));
        assertFalse(screen.canApply());screen.tick();screen.applySelection();assertEquals(1,f.base.sent.size());
        press(screen,"hidden");assertTrue(screen.canApply());screen.applySelection();assertEquals(2,f.base.sent.size());
    }
    @Test void resourceHandlerAndAuthorityTransportIdentitiesMustNotBeInterchanged() {
        Object transport=new Object();
        assertEquals(ClientArmorRegistry.State.KNOWN,new WardrobeArmorSource(f.resources,f.base.connection).state());
        assertEquals(ClientArmorRegistry.State.UNSUPPORTED,new WardrobeArmorSource(f.resources,transport).state());
        assertEquals(ClientArmorRegistry.State.UNSUPPORTED,WardrobeArmorSource.current(f.resources,null).state());
        assertEquals(14,f.source.entries().size());
    }
    @Test void translationsCoverSameKeysAndKeepBothLanguagesNonempty() throws Exception {
        var gson=new com.google.gson.Gson();var root="/assets/fashion_3790/lang/";
        var zh=gson.fromJson(new java.io.InputStreamReader(getClass().getResourceAsStream(root+"zh_cn.json"),java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
        var en=gson.fromJson(new java.io.InputStreamReader(getClass().getResourceAsStream(root+"en_us.json"),java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
        assertEquals(zh.keySet(),en.keySet());for(String key:zh.keySet()){assertFalse(zh.get(key).getAsString().isBlank());assertFalse(en.get(key).getAsString().isBlank());}
    }
}
