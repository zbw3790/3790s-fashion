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
        if (scopeButton!=null) scopeButton.setMessage(Component.literal(scope.label+(session.conflicts(scope.targets)?" *":"")));
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
        if (!session.v2()) return "服务器不支持装束";
        if (!session.authorityKnown()) return "尚未同步";
        return session.fullSnapshot().map(value -> {
            var summary=value.outfit().summarize(scope.targets);
            return summary instanceof OutfitSelections.Uniform uniform?label(uniform.selection()):"混搭";
        }).orElse("加载中");
    }
    static String label(OutfitPartSelection selection) {
        if (selection instanceof OutfitPartSelection.Outfit custom) return custom.id().value();
        return selection==OutfitPartSelection.NONE?"无外层":"原版";
    }
    String resourceStatus() {
        if (!session.v2()) return "服务器不支持装束";
        if (source.state()==ClientOutfitRegistry.State.UNKNOWN) return "加载中";
        if (source.state()!=ClientOutfitRegistry.State.KNOWN) return "装束服务不可用";
        var draft=session.fullSnapshot();var baseline=session.fullState();
        if (draft.isPresent() && baseline.isPresent()) {
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER) if (scope.targets.contains(part)) {
                var choice=draft.orElseThrow().outfit().get(part);
                if (choice instanceof OutfitPartSelection.Outfit custom) {
                    String status=availabilityText(custom.id());
                    if (!status.isEmpty()) return status;
                    if (choice.equals(baseline.orElseThrow().stored().outfit().get(part))
                            && !choice.equals(baseline.orElseThrow().effective().outfit().get(part)))
                        return "当前不可用，显示原版";
                }
            }
        }
        return entries.isEmpty()?"暂无装束":"";
    }
    private String availabilityText(OutfitId id) {
        return switch(source.availability(id)) {
            case READY->"";case LOADING->"加载中";case MODEL_MISMATCH->"不支持当前模型";
            case MODEL_UNKNOWN->"玩家模型尚未就绪";case UNAVAILABLE->"当前定义不可用";
            case MODEL_INVALID->"当前模型资源不可用";case LOAD_FAILED->"资源加载失败";
        };
    }
    List<String> tooltip(OutfitId id) {
        var entry=source.find(id);
        if (entry.isEmpty()) return List.of(id.value(),"当前不可用");
        var value=entry.orElseThrow();
        String parts=String.join("、",OutfitPart.CANONICAL_ORDER.stream().filter(value.parts()::contains).map(OutfitScope::partName).toList());
        String reason=availabilityText(id);
        if (value.parts().stream().noneMatch(scope.targets::contains)) reason="不提供当前范围";
        if (reason.isEmpty()) reason="更改当前范围内提供的部位";
        var lines=new ArrayList<String>();
        lines.add(id.value());lines.add("提供："+parts);
        if(value.parts().size()<6) lines.add("部分装束："+value.parts().size()+"/6；其余保留");
        lines.add(source.model()==null?"玩家模型尚未就绪":"当前模型："+(source.model()==OutfitModel.WIDE?"宽臂":"纤细"));
        if (partialUse(id)) lines.add("当前范围部分使用");
        lines.add(reason);return List.copyOf(lines);
    }
    List<String> summaryTooltip() {
        return session.fullSnapshot().map(value -> OutfitPart.CANONICAL_ORDER.stream().filter(scope.targets::contains).map(part -> {
            String text=OutfitScope.partName(part)+"："+label(value.outfit().get(part));
            if (scope.targets.size()==1 && value.outfit().get(part) instanceof OutfitPartSelection.Outfit selected) {
                var state=session.fullState().orElseThrow();
                if (value.outfit().get(part).equals(state.stored().outfit().get(part))
                        && !value.outfit().get(part).equals(state.effective().outfit().get(part))) text+="；当前不可用，显示原版";
                else { String reason=availabilityText(selected.id());if(!reason.isEmpty()) text+="；"+reason; }
            }
            return text+(session.conflicts(Set.of(part))?"；存在外部修改":"");
        }).toList()).orElse(List.of(summary()));
    }

    void buildWidgets(WardrobeLayout layout,Font font,Consumer<AbstractWidget> add,Runnable rebuild) {
        widgets.clear();original=null;none=null;scopeButton=null;previous=null;next=null;refresh();
        if (!layout.fitsScreen() || !session.v2()) return;
        if (panel==Panel.ROOT) {
            add.accept(button(layout.scopeButtonBounds(),"返回当前范围",() -> {closePanel();rebuild.run();}));
            var scopes=List.of(OutfitScope.ALL,OutfitScope.HEAD,OutfitScope.UPPER,OutfitScope.LEGS);
            for (int i=0;i<4;i++) {
                OutfitScope item=scopes.get(i);
                add.accept(button(layout.scopeOptionBounds(i),item.label,() -> {setScope(item);rebuild.run();}));
            }
            add.accept(button(layout.scopeOptionBounds(4),"详细…",() -> {openDetail();rebuild.run();}));
        } else if (panel==Panel.DETAIL) {
            add.accept(button(layout.scopeButtonBounds(),"返回范围",() -> {openPanel();rebuild.run();}));
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER) add.accept(button(layout.detailOptionBounds(part.ordinal()),
                    OutfitScope.partName(part)+(session.conflicts(Set.of(part))?" *":""),() -> {setScope(OutfitScope.detail(part));rebuild.run();}));
        } else {
            scopeButton=button(layout.scopeButtonBounds(),scope.label,() -> {openPanel();rebuild.run();});
            add.accept(scopeButton);
            original=new WardrobeOutfitActionButton(layout.originalButtonBounds(),"原版",() -> clear(OutfitPartSelection.ORIGINAL));
            none=new WardrobeOutfitActionButton(layout.noneButtonBounds(),"无外层",() -> clear(OutfitPartSelection.NONE));
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
                Identifier.withDefaultNamespace(path+"_highlighted")),button -> action.run(),Component.literal(forward?"下一页":"上一页"));
    }
}
