package dev.zbw3790.fashion.client.screen;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.client.outfit.ClientOutfitRegistry;
import dev.zbw3790.fashion.client.outfit.ClientOutfitTextureResolver.Availability;

/** 只管理范围、页码和展示；编辑意图交给 Screen 唯一 Session。 */
final class OutfitWardrobeContent {
    enum Panel { GRID, ROOT, DETAIL }
    static final int PAGE_SIZE=8;
    private final WardrobeSelectionSession session;
    final WardrobeOutfitSource source;
    private OutfitScope scope=OutfitScope.ALL;
    private Panel panel=Panel.GRID;
    private int page;
    private List<OutfitRegistrySnapshot.Entry> entries=List.of();
    private final List<OutfitGridEntryWidget> widgets=new ArrayList<>();
    private WardrobeOutfitActionButton original,none;
    private Button scopeButton;
    private ImageButton previous,next;
    OutfitWardrobeContent(WardrobeSelectionSession session,WardrobeOutfitSource source) { this.session=session;this.source=source; }
    OutfitScope scope() { return scope; }
    Panel panel() { return panel; }
    int pageIndex() { return page; }
    int pageCount() { return Math.max(1,(entries.size()+7)/8); }
    int pageNumber() { return page+1; }
    boolean paginationVisible() { return panel==Panel.GRID && session.v2() && pageCount()>1; }
    boolean refresh() {
        var nextEntries=source.entries();
        boolean changed=!entries.equals(nextEntries);
        entries=nextEntries;page=Math.min(page,pageCount()-1);
        widgets.forEach(OutfitGridEntryWidget::refresh);
        if (scopeButton!=null) scopeButton.setMessage(Component.literal(scope.label()+(session.conflicts(scope.targets)?" *":"")));
        boolean canClear=session.v2() && session.canEdit();
        boolean pendingAppearance=session.v2() && session.authorityKnown() && !session.closed() && session.waiting();
        if (original!=null) original.refresh(canClear,pendingAppearance);
        if (none!=null) none.refresh(canClear,pendingAppearance);
        if (previous!=null) { previous.visible=paginationVisible() && page>0; previous.active=previous.visible; }
        if (next!=null) { next.visible=paginationVisible() && page+1<pageCount(); next.active=next.visible; }
        return changed;
    }
    List<OutfitRegistrySnapshot.Entry> pageEntries() {
        int start=page*PAGE_SIZE;return entries.subList(start,Math.min(start+PAGE_SIZE,entries.size()));
    }
    void setScope(OutfitScope value) { scope=Objects.requireNonNull(value);panel=Panel.GRID; }
    void openPanel() { panel=Panel.ROOT; }
    void openDetail() { panel=Panel.DETAIL; }
    void closePanel() { panel=Panel.GRID; }
    void changePage(boolean forward) { page=Math.clamp(page+(forward?1:-1),0,pageCount()-1); }
    boolean canActivate(OutfitId id) {
        return session.v2() && session.canEdit() && resourceSelectable(id);
    }
    boolean resourceSelectable(OutfitId id) {
        return source.find(id).filter(entry -> entry.parts().stream().anyMatch(scope.targets::contains)).isPresent()
                && source.availability(id)==Availability.READY;
    }
    void select(OutfitId id) {
        if (canActivate(id)) session.selectOutfit(scope.targets,id,source.find(id).orElseThrow().parts());
    }
    void clear(OutfitPartSelection value) { session.clearOutfit(scope.targets,value); }
    boolean selected(OutfitId id) {
        return session.fullSnapshot().filter(value -> value.outfit().summarize(scope.targets)
                .equals(new OutfitSelections.Uniform(OutfitPartSelection.outfit(id)))).isPresent();
    }
    boolean partialUse(OutfitId id) {
        return !selected(id) && session.fullSnapshot().filter(value -> scope.targets.stream()
                .anyMatch(part -> value.outfit().get(part).equals(OutfitPartSelection.outfit(id)))).isPresent();
    }
    String summary() {
        if (!session.v2()) return WardrobeText.string("outfit.unsupported");
        if (!session.authorityKnown()) return WardrobeText.string("unknown");
        return session.fullSnapshot().map(value -> {
            var summary=value.outfit().summarize(scope.targets);
            return summary instanceof OutfitSelections.Uniform uniform?label(uniform.selection()):WardrobeText.string("mixed");
        }).orElse(WardrobeText.string("loading"));
    }
    static String label(OutfitPartSelection selection) {
        if (selection instanceof OutfitPartSelection.Outfit custom) return custom.id().value();
        return selection==OutfitPartSelection.NONE?WardrobeText.string("outfit.none"):WardrobeText.string("original");
    }
    String resourceStatus() {
        if (!session.v2()) return WardrobeText.string("outfit.unsupported");
        if (source.state()==ClientOutfitRegistry.State.UNKNOWN) return WardrobeText.string("loading");
        if (source.state()!=ClientOutfitRegistry.State.KNOWN) return WardrobeText.string("outfit.unavailable");
        var draft=session.fullSnapshot();var baseline=session.fullState();
        if (draft.isPresent() && baseline.isPresent()) {
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER) if (scope.targets.contains(part)) {
                var choice=draft.orElseThrow().outfit().get(part);
                if (choice instanceof OutfitPartSelection.Outfit custom) {
                    String status=availabilityText(custom.id());
                    if (!status.isEmpty()) return status;
                    if (choice.equals(baseline.orElseThrow().stored().outfit().get(part))
                            && !choice.equals(baseline.orElseThrow().effective().outfit().get(part)))
                        return WardrobeText.string("outfit.dormant");
                }
            }
        }
        return entries.isEmpty()?WardrobeText.string("outfit.empty"):"";
    }
    private String availabilityText(OutfitId id) {
        return switch(source.availability(id)) {
            case READY->"";case LOADING->WardrobeText.string("loading");case MODEL_MISMATCH->WardrobeText.string("outfit.model_mismatch");
            case MODEL_UNKNOWN->WardrobeText.string("outfit.model_unknown");case UNAVAILABLE->WardrobeText.string("outfit.definition_unavailable");
            case MODEL_INVALID->WardrobeText.string("outfit.model_invalid");case LOAD_FAILED->WardrobeText.string("outfit.failed");
        };
    }
    List<String> tooltip(OutfitId id) {
        var entry=source.find(id);
        if (entry.isEmpty()) return List.of(id.value(),WardrobeText.string("unavailable"));
        var value=entry.orElseThrow();
        String parts=String.join(WardrobeText.string("list_separator"),OutfitPart.CANONICAL_ORDER.stream().filter(value.parts()::contains).map(OutfitScope::partName).toList());
        String reason=availabilityText(id);
        if (value.parts().stream().noneMatch(scope.targets::contains)) reason=WardrobeText.string("outfit.no_scope");
        if (reason.isEmpty()) reason=WardrobeText.string("outfit.select_hint");
        var lines=new ArrayList<String>();
        lines.add(id.value());lines.add(WardrobeText.string("outfit.parts",parts));
        if(value.parts().size()<6) lines.add(WardrobeText.string("outfit.partial",value.parts().size()));
        lines.add(source.model()==null?WardrobeText.string("outfit.model_unknown"):WardrobeText.string("outfit.model",source.model()==OutfitModel.WIDE?WardrobeText.string("outfit.wide"):WardrobeText.string("outfit.slim")));
        if (partialUse(id)) lines.add(WardrobeText.string("outfit.partial_use"));
        lines.add(reason);return List.copyOf(lines);
    }
    List<String> summaryTooltip() {
        return session.fullSnapshot().map(value -> OutfitPart.CANONICAL_ORDER.stream().filter(scope.targets::contains).map(part -> {
            String text=WardrobeText.string("field",OutfitScope.partName(part),label(value.outfit().get(part)));
            if (scope.targets.size()==1 && value.outfit().get(part) instanceof OutfitPartSelection.Outfit selected) {
                var state=session.fullState().orElseThrow();
                if (value.outfit().get(part).equals(state.stored().outfit().get(part))
                        && !value.outfit().get(part).equals(state.effective().outfit().get(part))) text+=WardrobeText.string("separator")+WardrobeText.string("outfit.dormant");
                else { String reason=availabilityText(selected.id());if(!reason.isEmpty()) text+=WardrobeText.string("separator")+reason; }
            }
            return text+(session.conflicts(Set.of(part))?WardrobeText.string("separator")+WardrobeText.string("status.conflict"):"");
        }).toList()).orElse(List.of(summary()));
    }

    void buildWidgets(WardrobeLayout layout,Font font,Consumer<AbstractWidget> add,Runnable rebuild) {
        widgets.clear();original=null;none=null;scopeButton=null;previous=null;next=null;refresh();
        if (!layout.fitsScreen() || !session.v2()) return;
        if (panel==Panel.ROOT) {
            add.accept(button(layout.scopeButtonBounds(),WardrobeText.string("outfit.back_current"),() -> {closePanel();rebuild.run();}));
            var scopes=List.of(OutfitScope.ALL,OutfitScope.HEAD,OutfitScope.UPPER,OutfitScope.LEGS);
            for (int i=0;i<4;i++) {
                OutfitScope item=scopes.get(i);
                add.accept(button(layout.scopeOptionBounds(i),item.label(),() -> {setScope(item);rebuild.run();}));
            }
            add.accept(button(layout.scopeOptionBounds(4),WardrobeText.string("outfit.detail"),() -> {openDetail();rebuild.run();}));
        } else if (panel==Panel.DETAIL) {
            add.accept(button(layout.scopeButtonBounds(),WardrobeText.string("outfit.back_scope"),() -> {openPanel();rebuild.run();}));
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER) add.accept(button(layout.detailOptionBounds(part.ordinal()),
                    OutfitScope.partName(part)+(session.conflicts(Set.of(part))?" *":""),() -> {setScope(OutfitScope.detail(part));rebuild.run();}));
        } else {
            scopeButton=button(layout.scopeButtonBounds(),scope.label(),() -> {openPanel();rebuild.run();});
            add.accept(scopeButton);
            original=new WardrobeOutfitActionButton(layout.originalButtonBounds(),WardrobeText.string("original"),() -> clear(OutfitPartSelection.ORIGINAL));
            none=new WardrobeOutfitActionButton(layout.noneButtonBounds(),WardrobeText.string("outfit.none"),() -> clear(OutfitPartSelection.NONE));
            add.accept(original);add.accept(none);
            var current=pageEntries();
            for (int i=0;i<current.size();i++) {
                var widget=new OutfitGridEntryWidget(layout.outfitEntryBounds(i),current.get(i).id(),this);
                widgets.add(widget);add.accept(widget);
            }
            previous=pageButton(layout.previousPageButtonBounds(),false,() -> {changePage(false);rebuild.run();});
            next=pageButton(layout.nextPageButtonBounds(),true,() -> {changePage(true);rebuild.run();});
            add.accept(previous);add.accept(next);
        }
        refresh();
    }
    private static Button button(WardrobeLayout.Bounds b,String text,Runnable action) {
        return Button.builder(Component.literal(text),button -> action.run()).bounds(b.x(),b.y(),b.width(),b.height()).build();
    }
    private static ImageButton pageButton(WardrobeLayout.Bounds b,boolean forward,Runnable action) {
        String path="recipe_book/page_"+(forward?"forward":"backward");
        return new ImageButton(b.x(),b.y(),b.width(),b.height(),new WidgetSprites(Identifier.withDefaultNamespace(path),
                Identifier.withDefaultNamespace(path+"_highlighted")),button -> action.run(),Component.literal(forward?WardrobeText.string("next"):WardrobeText.string("previous")));
    }
}
