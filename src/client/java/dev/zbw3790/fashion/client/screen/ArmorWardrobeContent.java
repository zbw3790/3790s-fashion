package dev.zbw3790.fashion.client.screen;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import dev.zbw3790.fashion.armor.*;
import dev.zbw3790.fashion.client.armor.ClientArmorRegistry;

/** 盔甲页只保存浏览位置；四槽值始终来自唯一统一草稿。 */
final class ArmorWardrobeContent {
    enum Panel { GRID, SLOTS }
    private final WardrobeSelectionSession session;
    private final WardrobeArmorSource source;
    private Panel panel=Panel.GRID;
    private ArmorSlot slot=ArmorSlot.HEAD;
    static final int PAGE_SIZE=8;
    private final Map<String,Integer> offsets=new HashMap<>();
    private List<ArmorRegistrySnapshot.Entry> entries=List.of();
    private ClientArmorRegistry.State registryState;
    private long generation=-1;
    private final List<WardrobeArmorButton> widgets=new ArrayList<>();

    ArmorWardrobeContent(WardrobeSelectionSession session,WardrobeArmorSource source) { this.session=session;this.source=source; }
    Panel panel() { return panel; }
    ArmorSlot slot() { return slot; }
    boolean browser() { return panel==Panel.GRID; }
    int capacity() { return PAGE_SIZE; }
    String scopeKey() { return slot.serializedName(); }
    int offset() { return offsets.getOrDefault(scopeKey(),0); }
    List<ArmorRegistrySnapshot.Entry> entries() { return entries; }
    List<ArmorRegistrySnapshot.Entry> filteredEntries() {
        return entries.stream().filter(entry -> entry.slots().contains(slot)).toList();
    }
    List<ArmorRegistrySnapshot.Entry> visibleEntries() {
        var filtered=filteredEntries();int first=Math.min(offset(),filtered.size());
        return filtered.subList(first,Math.min(filtered.size(),first+PAGE_SIZE));
    }
    Set<ArmorSlot> targets() { return Set.of(slot); }
    WardrobeArmorSource source() { return source; }
    boolean canEdit() { return session.canEdit(); }
    void openSlots() { panel=Panel.SLOTS; }
    ArmorSelections draft() { return session.fullSnapshot().map(value -> value.armor()).orElse(ArmorSelections.original()); }
    boolean refresh() {
        var next=source.entries();var state=source.state();long nextGeneration=source.generation();
        boolean changed=!next.equals(entries) || registryState!=state || generation!=nextGeneration;
        entries=next;registryState=state;generation=nextGeneration;clamp();
        widgets.forEach(WardrobeArmorButton::refreshState);
        return changed;
    }
    private void clamp() {
        int last=Math.max(0,(filteredEntries().size()-1)/PAGE_SIZE)*PAGE_SIZE;
        offsets.put(scopeKey(),Math.max(0,Math.min(offset(),last)));
    }
    void openSlot(ArmorSlot value) { slot=Objects.requireNonNull(value);panel=Panel.GRID;clamp(); }
    boolean scroll(int steps) {
        if (!browser() || steps==0) return false;
        int previous=offset();offsets.put(scopeKey(),previous+Integer.signum(steps)*PAGE_SIZE);clamp();return previous!=offset();
    }
    void clear(ArmorSelection value) { if (browser()) session.clearArmor(targets(),value); }
    void choose(ArmorStyleId id) {
        if (!browser()) return;
        source.find(id).ifPresent(entry -> session.selectArmor(targets(),id,entry.slots()));
    }
    boolean paginationVisible() { return session.v2() && browser() && filteredEntries().size()>capacity(); }
    String positionLabel() {
        return (offset()/capacity()+1)+" / "+((filteredEntries().size()+capacity()-1)/capacity());
    }
    boolean selected(ArmorRegistrySnapshot.Entry entry) {
        var affected=targets().stream().filter(entry.slots()::contains).toList();
        return !affected.isEmpty() && affected.stream().allMatch(target -> draft().get(target).equals(ArmorSelection.custom(entry.id())));
    }
    boolean selected(ArmorSelection builtin) { return !targets().isEmpty() && targets().stream().allMatch(target -> draft().get(target).equals(builtin)); }
    String valueLabel(ArmorSelection value) {
        return value instanceof ArmorSelection.Custom custom?source.find(custom.id()).map(ArmorRegistrySnapshot.Entry::name).orElse(custom.id().value()):
                WardrobeArmorText.string(value==ArmorSelection.HIDDEN?"hidden":"original");
    }
    String slotValueLabel(ArmorSlot slot) { return session.fullSnapshot().isEmpty()?WardrobeArmorText.string("unknown_value"):valueLabel(draft().get(slot)); }
    String summary() {
        if (!session.v2() || session.fullSnapshot().isEmpty()) return WardrobeArmorText.string("unknown_value");
        var values=targets().isEmpty()?draft().slots():targets().stream().map(target -> draft().get(target)).toList();
        return values.stream().distinct().count()==1?valueLabel(values.getFirst()):WardrobeArmorText.string("mixed");
    }
    String title() { return WardrobeArmorText.string("slot."+slot.serializedName()); }
    String resourceStatus() {
        if (!session.v2()) return WardrobeArmorText.string("unsupported");
        return switch(source.state()) {
            case UNSUPPORTED -> WardrobeArmorText.string("unsupported");
            case UNKNOWN -> WardrobeArmorText.string("registry_unknown");
            case UNAVAILABLE -> WardrobeArmorText.string("registry_unavailable");
            case KNOWN -> {
                if (hasDormant()) yield WardrobeArmorText.string("dormant");
                if (hasLoading()) yield WardrobeArmorText.string("loading");
                if (ArmorSlot.CANONICAL_ORDER.stream().anyMatch(target -> draft().get(target) instanceof ArmorSelection.Custom custom
                        && source.availability(target,custom.id())==WardrobeArmorSource.Availability.FAILED)) yield WardrobeArmorText.string("failed");
                if (entries.isEmpty()) yield WardrobeArmorText.string("empty");
                yield WardrobeArmorText.string("draft_hint");
            }
        };
    }
    boolean hasDormant() {
        return ArmorSlot.CANONICAL_ORDER.stream().anyMatch(target -> draft().get(target) instanceof ArmorSelection.Custom custom
                && (!source.admitted(target,custom.id()) || session.fullState().filter(state ->
                    state.stored().armor().get(target).equals(draft().get(target))
                    && !state.effective().armor().get(target).equals(draft().get(target))).isPresent()));
    }
    boolean hasLoading() {
        return ArmorSlot.CANONICAL_ORDER.stream().anyMatch(target -> draft().get(target) instanceof ArmorSelection.Custom custom
                && source.availability(target,custom.id())==WardrobeArmorSource.Availability.LOADING);
    }
    List<String> slotTooltip(ArmorSlot target) {
        var lines=new ArrayList<String>();var value=draft().get(target);
        lines.add(WardrobeArmorText.slot(target)+": "+slotValueLabel(target));
        if (value instanceof ArmorSelection.Custom custom) {
            lines.add(custom.id().value());lines.add(availabilityText(source.availability(target,custom.id())));
        }
        if (session.armorConflict(target)) lines.add(WardrobeArmorText.string("conflict"));
        lines.add(WardrobeArmorText.string("actual_equipment"));return List.copyOf(lines);
    }
    List<String> tooltip(ArmorRegistrySnapshot.Entry entry) {
        var lines=new ArrayList<String>();lines.add(entry.name());lines.add(entry.id().value());
        lines.add(WardrobeArmorText.string("supported",String.join(" / ",ArmorSlot.CANONICAL_ORDER.stream().filter(entry.slots()::contains).map(WardrobeArmorText::slot).toList())));
        for (var target:ArmorSlot.CANONICAL_ORDER) if (targets().contains(target) && entry.slots().contains(target))
            lines.add(WardrobeArmorText.slot(target)+": "+availabilityText(source.availability(target,entry.id())));
        if (selected(entry)) lines.add(WardrobeArmorText.string("selected"));
        lines.add(WardrobeArmorText.string("texture_source",ArmorGeometry.forSlot(slot).name().toLowerCase(java.util.Locale.ROOT)));
        return List.copyOf(lines);
    }
    private String availabilityText(WardrobeArmorSource.Availability availability) {
        return WardrobeArmorText.string(switch(availability) {
            case READY -> "ready";case LOADING -> "loading";case FAILED -> "failed";
            case MISSING -> "dormant";case UNSUPPORTED_SLOT -> "unsupported_slot";
        });
    }
    WardrobeLayout.Bounds emptyBounds(WardrobeLayout layout) { return session.v2()?layout.outfitGridBounds():layout.gridBounds(); }
    boolean showEmpty() { return !session.v2() || browser() && filteredEntries().isEmpty(); }
    String emptyText() { return source.state()==ClientArmorRegistry.State.KNOWN && !entries.isEmpty()?WardrobeArmorText.string("empty_slot"):resourceStatus(); }
    void buildWidgets(WardrobeLayout layout,Font font,Consumer<AbstractWidget> add,Runnable rebuild) {
        widgets.clear();
        if (!session.v2()) return;
        add(add,font,layout.scopeButtonBounds(),"scope",() -> WardrobeArmorText.text("edit_slot",WardrobeArmorText.slot(slot)),
                () -> List.of(WardrobeArmorText.string("choose_slot")),() -> true,() -> panel==Panel.SLOTS,
                () -> {panel=browser()?Panel.SLOTS:Panel.GRID;rebuild.run();});
        if (!browser()) {
            for (var target:ArmorSlot.CANONICAL_ORDER) {
                add(add,font,layout.scopeOptionBounds(target.ordinal()),"slot:"+target.serializedName(),
                    () -> Component.literal(WardrobeArmorText.slot(target)+" · "+slotValueLabel(target)),
                    () -> slotTooltip(target),() -> true,() -> slot==target,() -> {openSlot(target);rebuild.run();});
            }
            return;
        }
        for (int index=0;index<2;index++) {
            var builtin=index==0?ArmorSelection.ORIGINAL:ArmorSelection.HIDDEN;String key=index==0?"original":"hidden";
            add(add,font,index==0?layout.originalButtonBounds():layout.noneButtonBounds(),key,() -> WardrobeArmorText.text(key),
                    () -> List.of(WardrobeArmorText.string(key),WardrobeArmorText.string("slot_builtin")),
                    session::canEdit,() -> selected(builtin),() -> clear(builtin));
        }
        int index=0;
        for (var entry:visibleEntries()) add.accept(new ArmorGridEntryWidget(layout.outfitEntryBounds(index++),entry,this,font));
        if (paginationVisible()) {
            add(add,font,layout.previousPageButtonBounds(),"previous",() -> Component.literal("<"),() -> List.of(WardrobeArmorText.string("previous")),
                    () -> offset()>0,() -> false,() -> {scroll(-1);rebuild.run();});
            add(add,font,layout.nextPageButtonBounds(),"next",() -> Component.literal(">"),() -> List.of(WardrobeArmorText.string("next")),
                    () -> offset()+PAGE_SIZE<filteredEntries().size(),() -> false,() -> {scroll(1);rebuild.run();});
        }
    }
    private void add(Consumer<AbstractWidget> add,Font font,WardrobeLayout.Bounds bounds,String key,Supplier<Component> label,
            Supplier<List<String>> tooltip,BooleanSupplier enabled,BooleanSupplier selected,Runnable action) {
        var button=new WardrobeArmorButton(bounds,"armor:"+scopeKey()+":"+key,font,label,
                tooltip==null?() -> List.of(label.get().getString()):tooltip,enabled,
                () -> session.waiting() && session.authorityKnown(),selected,action);
        widgets.add(button);add.accept(button);
    }
    private static WardrobeLayout.Bounds bounds(int x,int y,int width,int height) { return new WardrobeLayout.Bounds(x,y,width,height); }
}
