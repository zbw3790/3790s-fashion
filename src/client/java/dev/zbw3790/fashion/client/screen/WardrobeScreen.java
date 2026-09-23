package dev.zbw3790.fashion.client.screen;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import dev.zbw3790.fashion.client.network.ClientFullFashionNetworking;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.client.render.WardrobePreviewAppearance;
import dev.zbw3790.fashion.client.cape.ClientCapeRegistry;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureManager;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureResolver;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;
import dev.zbw3790.fashion.client.network.ClientCapeSelectionRequestTracker;
import dev.zbw3790.fashion.network.SetCapeSelectionPayload;
import dev.zbw3790.fashion.network.SetFullFashionSelectionPayload;
import dev.zbw3790.fashion.fashion.FashionAuthorityRoute;

/** 承载唯一 Draft、玩家预览和应用入口；Cape 内容只管理网格与分页。 */
public final class WardrobeScreen extends Screen {
	private static final Component TITLE = WardrobeText.text("title");
	private static final Component CAPE_TAB = WardrobeText.text("tab.cape");
	private static final Component APPLY = WardrobeText.text("apply");
	private static final Component TOO_SMALL = WardrobeText.text("too_small");
	private static final Component PREVIEW_UNAVAILABLE = WardrobeText.text("preview.unavailable");
	private static final float PLAYER_PREVIEW_OFFSET_Y = 0.0625F;

	private final ClientPlayerFashionRegistry playerFashions;
	private final ClientCapeSelectionRequestTracker requests;
	private final UUID self;
	private final Object connection;
	private final Actions actions;
	private final WardrobeSelectionSession selection = new WardrobeSelectionSession();
	private final CapeWardrobeContent capeContent;
	private final ClientCapeTextureResolver textureResolver;
	private final WardrobePreviewRotation previewRotation = new WardrobePreviewRotation();
	private final WardrobePlayerPreviewRenderer previewRenderer = new WardrobePlayerPreviewRenderer();
	private SelectedTab selectedTab = SelectedTab.CAPE;
	private WardrobePreviewMode previewMode = WardrobePreviewMode.CAPE;
	private WardrobePreviewAppearanceResolver previewAppearanceResolver;
	private List<CapeCosmeticMetadata> previewMetadata;
	private WardrobeLayout layout;
	private Button applyButton;
    private Button reloadButton;
    private final OutfitWardrobeContent outfitContent;
    private final WardrobeOutfitSource outfitSource;
    private final WardrobeArmorSource armorSource;
    private final ArmorWardrobeContent armorContent;
    private final Map<AbstractWidget,String> focusKeys=new LinkedHashMap<>();
    private String rememberedFocus;
    private boolean rebuilding;
    private boolean recoveryShown;
    private int contentWidgetIndex;
    private boolean renderedV2;
    private final Map<SelectedTab,String> tabFocus=new java.util.EnumMap<>(SelectedTab.class);
    private final WardrobeTooltipState tooltipState=new WardrobeTooltipState();
    private boolean keyboardInput;
    private int tooltipAnchorX,tooltipAnchorY;
    private Language renderedLanguage;
    private String tooltipPageTarget;
    private int tooltipPage,tooltipPageCount=1;
    private boolean tooltipVisible;
    private WardrobeLayout.Bounds tooltipTargetBounds;
    private WardrobeLayout.Bounds lastTooltipBounds;

	public WardrobeScreen(ClientCapeRegistry capeRegistry, ClientCapeTextureManager textureManager,
			ClientPlayerFashionRegistry playerFashions, ClientCapeSelectionRequestTracker requests,
			UUID self, Object connection) {
		this(Minecraft.getInstance(), Minecraft.getInstance().font, capeRegistry, textureManager,
				playerFashions, requests, self, connection, runtimeActions(Minecraft.getInstance()),
                dev.zbw3790.fashion.client.Fashion3790Client.wardrobeOutfits(),
                WardrobeArmorSource.current(dev.zbw3790.fashion.client.Fashion3790Client.armorResources(),Minecraft.getInstance().getConnection()));
	}

	/** 将屏幕外部的输入绑定、发送和关闭动作集中在一个边界，便于普通 JVM 验证。 */
	WardrobeScreen(Minecraft minecraft, Font font, ClientCapeRegistry capeRegistry,
			ClientCapeTextureManager textureManager, ClientPlayerFashionRegistry playerFashions,
			ClientCapeSelectionRequestTracker requests, UUID self, Object connection, Actions actions) {
        this(minecraft,font,capeRegistry,textureManager,playerFashions,requests,self,connection,actions,WardrobeOutfitSource.empty());
    }
    WardrobeScreen(Minecraft minecraft, Font font, ClientCapeRegistry capeRegistry,
            ClientCapeTextureManager textureManager, ClientPlayerFashionRegistry playerFashions,
            ClientCapeSelectionRequestTracker requests, UUID self, Object connection, Actions actions, WardrobeOutfitSource outfitSource) {
        this(minecraft,font,capeRegistry,textureManager,playerFashions,requests,self,connection,actions,outfitSource,WardrobeArmorSource.empty());
    }
    WardrobeScreen(Minecraft minecraft, Font font, ClientCapeRegistry capeRegistry,
            ClientCapeTextureManager textureManager, ClientPlayerFashionRegistry playerFashions,
            ClientCapeSelectionRequestTracker requests, UUID self, Object connection, Actions actions,
            WardrobeOutfitSource outfitSource, WardrobeArmorSource armorSource) {
        super(minecraft, Objects.requireNonNull(font), TITLE);
		this.playerFashions = Objects.requireNonNull(playerFashions);
		this.requests = Objects.requireNonNull(requests);
		this.self = Objects.requireNonNull(self);
		this.connection = Objects.requireNonNull(connection);
		this.actions = Objects.requireNonNull(actions);
		capeContent = new CapeWardrobeContent(capeRegistry, textureManager, selection);
        this.outfitSource=Objects.requireNonNull(outfitSource);
        outfitContent=new OutfitWardrobeContent(selection,outfitSource);
        this.armorSource=Objects.requireNonNull(armorSource);armorContent=new ArmorWardrobeContent(selection,armorSource);
		textureResolver = new ClientCapeTextureResolver(textureManager);
		previewMetadata = capeContent.metadataEntries();
		previewAppearanceResolver = new WardrobePreviewAppearanceResolver(previewMetadata, textureResolver);
		observeAuthority();
	}

	private static Actions runtimeActions(Minecraft minecraft) {
		return new Actions(
				() -> minecraft.getConnection() != null
						&& ClientPlayNetworking.canSend(SetCapeSelectionPayload.TYPE),
				event -> minecraft.options.keyInventory.matches(event),
				ClientPlayNetworking::send,
				() -> minecraft.gui.setScreen(null),
                () -> minecraft.getConnection()!=null && ClientPlayNetworking.canSend(SetFullFashionSelectionPayload.TYPE),
                ClientPlayNetworking::send, ClientFullFashionNetworking::resultReceiverReady);
	}

    @Override
    protected void init() {
        rebuilding=true;renderedLanguage=Language.getInstance();
        layout=WardrobeLayout.calculate(Math.max(1,width),Math.max(1,height),font.lineHeight);
        previewRotation.endDrag(WardrobePreviewRotation.PRIMARY_MOUSE_BUTTON);
        applyButton=null;reloadButton=null;focusKeys.clear();contentWidgetIndex=0;
        observeAuthority();capeContent.refresh(playerFashions.state()==ClientPlayerFashionRegistry.State.UNAVAILABLE);outfitContent.refresh();armorContent.refresh();
        recoveryShown=selection.v2() && selection.hasError();renderedV2=selection.v2();
        if (layout.fitsScreen()) {
            for (SelectedTab tab:SelectedTab.values()) addControl(new TabWidget(layout.tabBounds(tab.ordinal()),tab),"tab:"+tab);
            addControl(new WardrobePreviewModeButton(layout.previewModeButtonBounds(),() -> previewMode,
                    () -> previewMode=previewMode.next()),"preview");
            if (selectedTab==SelectedTab.CAPE) capeContent.buildWidgets(layout,this::addContentControl,this::rebuildPreservingFocus);
            else if (selectedTab==SelectedTab.OUTFIT) outfitContent.buildWidgets(layout,font,this::addContentControl,this::rebuildPreservingFocus);
            else armorContent.buildWidgets(layout,font,this::addContentControl,this::rebuildPreservingFocus);
            var utility=layout.reloadButtonBounds();
            int cancelWidth=recoveryShown?(utility.width()-2)/2:utility.width();
            addControl(new WardrobeArmorButton(new WardrobeLayout.Bounds(utility.x(),utility.y(),cancelWidth,utility.height()),
                    "cancel",font,() -> WardrobeArmorText.text("cancel"),() -> List.of(WardrobeArmorText.string("cancel_hint")),
                    () -> true,() -> false,() -> false,this::onClose),"cancel");
            if (recoveryShown) {
                var b=new WardrobeLayout.Bounds(utility.x()+cancelWidth+2,utility.y(),utility.width()-cancelWidth-2,utility.height());
                reloadButton=addControl(new WardrobeArmorButton(b,"reload",font,() -> WardrobeText.text("reload"),
                        () -> List.of(WardrobeArmorText.string("reload_hint")),selection::canEdit,selection::waiting,
                        () -> false,this::reloadAuthority),"reload");
            }
            var b=layout.applyButtonBounds();
            applyButton=addControl(Button.builder(APPLY,button -> applySelection()).bounds(b.x(),b.y(),b.width(),b.height()).build(),"apply");
        }
        rebuilding=false;refreshSelection();restoreFocus();
    }
    private <T extends AbstractWidget> T addControl(T widget,String key) {
        if (widget instanceof CapeGridEntryWidget cape) cape.useScreenTooltip();
        if (widget instanceof WardrobePreviewModeButton preview) preview.useScreenTooltip();
        focusKeys.put(widget,key);return addRenderableWidget(widget);
    }
    private void addContentControl(AbstractWidget widget) {
        String key=widget instanceof ArmorGridEntryWidget armorGrid?armorGrid.key():widget instanceof WardrobeArmorButton armor?armor.key():widget instanceof OutfitGridEntryWidget entry?"outfit:"+entry.id().value():
                selectedTab+":"+outfitContent.panel()+":"+contentWidgetIndex;
        contentWidgetIndex++;addControl(widget,key);
    }
    private void rememberFocus() {
        if (getFocused() instanceof AbstractWidget widget && focusKeys.containsKey(widget)) {
            rememberedFocus=focusKeys.get(widget);
            if (rememberedFocus.startsWith(selectedTab+":") || rememberedFocus.startsWith("outfit:") || rememberedFocus.startsWith("armor:")) tabFocus.put(selectedTab,rememberedFocus);
        }
    }
    private void restoreFocus() {
        var wanted=focusKeys.entrySet().stream().filter(entry -> entry.getValue().equals(rememberedFocus)
                && entry.getKey().visible && entry.getKey().active).findFirst();
        if (wanted.isPresent()) { setFocused(wanted.orElseThrow().getKey());return; }
        String fallback=selectedTab==SelectedTab.ARMOR?"tab:"+selectedTab:rememberedFocus==null?"tab:"+selectedTab:selectedTab+":"+outfitContent.panel()+":0";
        focusKeys.entrySet().stream().filter(entry -> entry.getValue().equals(fallback) && entry.getKey().visible && entry.getKey().active)
                .findFirst().ifPresent(entry -> setFocused(entry.getKey()));
    }
    private void rebuildPreservingFocus() { rememberFocus();rebuildWidgets(); }
    @Override public void resize(int width,int height) { rememberFocus();super.resize(width,height); }
    void selectTab(SelectedTab tab) {
        if (selectedTab==tab) return;
        rememberFocus();selectedTab=tab;outfitContent.closePanel();
        // 只在真实页签变化时转向；init／resize 和预览模式切换均保留手动朝向。
        previewRotation.orientTo(tab==SelectedTab.CAPE
                ? WardrobePreviewRotation.BACK_FACING_YAW_DEGREES : WardrobePreviewRotation.FRONT_FACING_YAW_DEGREES);
        rememberedFocus=tabFocus.getOrDefault(tab,tab+":"+OutfitWardrobeContent.Panel.GRID+":0");rebuildWidgets();
    }
    void reloadAuthority() { observeAuthority();if (selection.reloadAuthority()) rebuildPreservingFocus(); }
    OutfitWardrobeContent outfitContent() { return outfitContent; }
    ArmorWardrobeContent armorContent() { return armorContent; }

	@Override
	protected void setInitialFocus() {
        restoreFocus();
	}

	@Override
	public void tick() {
		super.tick();
		selection.tick();
		refreshSelection();
	}

	private void observeAuthority() {
        if (!requests.matchesConnection(connection) && !playerFashions.fullRequests().matches(connection)) {
            if (selection.v2()) selection.observeFull(Optional.empty(),ClientPlayerFashionRegistry.State.UNINITIALIZED);
            else selection.observe(Optional.empty(),ClientPlayerFashionRegistry.State.UNINITIALIZED);
            selection.connectionOutstanding(true);return;
        }
        if (playerFashions.route()==FashionAuthorityRoute.V4) selection.observeFull(playerFashions.fullSelfAuthority(self),playerFashions.state());
        else selection.observe(playerFashions.selfAuthority(self),playerFashions.state());
        selection.connectionOutstanding(hasOutstanding());
	}

	private boolean canSendSelection() {
		return playerFashions.route()==FashionAuthorityRoute.V4
                ? playerFashions.fullRequests().matches(connection) && actions.fullChannelSupported().getAsBoolean()
                : requests.matchesConnection(connection) && actions.channelSupported().getAsBoolean();
	}

	private boolean hasOutstanding() {
        return playerFashions.route()==FashionAuthorityRoute.V4?playerFashions.fullRequests().hasOutstanding():requests.hasOutstanding();
    }
    private void refreshSelection() {
		observeAuthority();
		boolean changed = capeContent.refresh(
				playerFashions.state() == ClientPlayerFashionRegistry.State.UNAVAILABLE);
		// 分页或 resize 可能先刷新内容；预览独立核对自己的元数据快照。
		if (!previewMetadata.equals(capeContent.metadataEntries())) {
			previewMetadata = capeContent.metadataEntries();
			previewAppearanceResolver = new WardrobePreviewAppearanceResolver(previewMetadata, textureResolver);
		}
        changed|=outfitContent.refresh();changed|=armorContent.refresh();
        focusKeys.keySet().stream().filter(WardrobeArmorButton.class::isInstance).map(WardrobeArmorButton.class::cast).forEach(WardrobeArmorButton::refreshState);
        boolean recovery=selection.v2() && selection.hasError();
        if ((changed || renderedLanguage!=Language.getInstance() || recovery!=recoveryShown || renderedV2!=selection.v2()) && layout!=null && !rebuilding) { rebuildPreservingFocus();return; }
        if (reloadButton!=null) reloadButton.active=selection.canEdit();
		if (applyButton != null) {
			applyButton.active = canApply();
		}
	}

    boolean canApply() {
        return selection.v2()?selection.canFinishFull(canSendSelection(),actions.resultReceiverReady().getAsBoolean(),
                hasOutstanding(),capeContent::hasMetadata,outfitSource::admitted,armorSource::admitted):
                selection.canFinish(canSendSelection(),hasOutstanding(),capeContent::hasMetadata);
    }

    void applySelection() {
		refreshSelection();
		if (playerFashions.route()==FashionAuthorityRoute.V4) {
            selection.finishFull(playerFashions.fullRequests(),canSendSelection(),actions.resultReceiverReady().getAsBoolean(),
                    capeContent::hasMetadata,outfitSource::admitted,armorSource::admitted).ifPresent(actions.fullSend());
        } else {
            var decision = selection.finish(requests, canSendSelection(), capeContent::hasMetadata);
            decision.request().ifPresent(actions.send());
        }
		refreshSelection();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
        // 文本翻页只消费已显示提示的 PageUp／PageDown，不改变焦点、草稿或下层点击。
        if (!event.isEscape() && !actions.inventoryKey().test(event) && tooltipVisible && tooltipPageCount>1
                && (event.key()==266 || event.key()==267)) {
            tooltipPage=Math.clamp(tooltipPage+(event.key()==267?1:-1),0,tooltipPageCount-1);return true;
        }
        keyboardInput=true;tooltipState.dismiss();
		// 优先关闭，防止 Inventory 被重绑定到 Enter/Space 时误触应用。
		if (event.isEscape() || actions.inventoryKey().test(event)) {
			onClose();
			return true;
		}
        if (event.isSelection() && getFocused() instanceof AbstractButton button && button.active && button.visible) {
            if (minecraft!=null) button.playDownSound(minecraft.getSoundManager());
            button.onPress(event);refreshSelection();return true;
        }
        if (event.key()==258) {
            var controls=focusKeys.keySet().stream().filter(widget -> widget.visible && widget.active).toList();
            if (controls.isEmpty()) return false;
            int current=controls.indexOf(getFocused()),step=(event.modifiers()&1)!=0?-1:1;
            int next=current<0?(step>0?0:controls.size()-1):Math.floorMod(current+step,controls.size());
            setFocused(controls.get(next));return true;
        }
        return super.keyPressed(event);
    }

	@Override
	public void onClose() {
		selection.cancel();
		actions.close().run();
	}

	@Override
	public void removed() {
		selection.cancel();
		super.removed();
	}

	public WardrobeSelectionSession selectionSession() {
		return selection;
	}

	WardrobeLayout layout() {
		return layout;
	}

	CapeWardrobeContent capeContent() {
		return capeContent;
	}

	WardrobePreviewRotation previewRotation() {
		return previewRotation;
	}

	WardrobePreviewMode previewMode() {
		return previewMode;
	}

	SelectedTab selectedTab() {
		return selectedTab;
	}

	WardrobeStatusText statusText() {
        if (selectedTab==SelectedTab.CAPE) return WardrobeStatusText.create(selection,playerFashions.state(),canSendSelection(),
                hasOutstanding(),capeContent::hasMetadata,capeContent.state(),capeContent.status());
        var priority=WardrobeStatusText.Priority.NORMAL;
        String detail=selectedTab==SelectedTab.ARMOR?armorContent.resourceStatus():outfitContent.resourceStatus();
        if (selection.hasError()) { priority=WardrobeStatusText.Priority.ERROR;detail=selection.status(canSendSelection(),hasOutstanding(),capeContent::hasMetadata); }
        else if (selection.waiting()) { priority=WardrobeStatusText.Priority.PENDING;detail=selection.pendingStatus(); }
        else if (!selection.authorityKnown()) { priority=WardrobeStatusText.Priority.LOADING;detail=WardrobeText.string("status.authority_unavailable"); }
        else if (!canSendSelection() || (selection.v2() && !actions.resultReceiverReady().getAsBoolean())) { priority=WardrobeStatusText.Priority.ERROR;detail=WardrobeText.string("status.save_unsupported"); }
        return new WardrobeStatusText(priority,selectedTab==SelectedTab.ARMOR?WardrobeText.string("field",armorContent.title(),armorContent.summary()):
                WardrobeText.string("field",outfitContent.scope().label(),outfitContent.summary()),detail,selection.supplementalStatus());
	}

	@Override
	public Component getNarrationMessage() {
		return WardrobeText.text("narration",selectedTab.label(),WardrobeText.string(previewMode == WardrobePreviewMode.CAPE ? "preview.name.cape" : "preview.name.elytra"),statusText().narration());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		if (layout == null || !layout.fitsScreen()) {
			return;
		}
		// Frame 与 selected seam 由同一几何入口计算并绘制。
        WardrobeGuiPainter.frameWithTabs(graphics::fill,layout,selectedTab.ordinal());
        WardrobeGuiIcons.drawTabs(graphics, layout);
		WardrobeGuiPainter.previewFrame(graphics::fill, layout);
        if (selectedTab==SelectedTab.CAPE) {
            for (int index=0;index<12;index++) WardrobeGuiPainter.slot(graphics::fill,layout.entryBounds(index));
        } else if (selection.v2() && (selectedTab==SelectedTab.OUTFIT && outfitContent.panel()==OutfitWardrobeContent.Panel.GRID || selectedTab==SelectedTab.ARMOR && armorContent.browser() && !armorContent.showEmpty())) {
            for (int index=0;index<8;index++) WardrobeGuiPainter.slot(graphics::fill,layout.outfitEntryBounds(index));
        }
		extractPlayerPreview(graphics, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (layout == null || !layout.fitsScreen()) {
            var lines=font.split(TOO_SMALL,Math.max(1,width-16));
            int y=Math.max(0,(height-lines.size()*font.lineHeight)/2);
            for (var line:lines) {
                graphics.text(font,line,Math.max(0,(width-font.width(line))/2),y,WardrobeGuiPainter.TEXT_COLOR,false);
                y+=font.lineHeight;
            }
			return;
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, title, layout.titleX(), layout.titleY(), WardrobeGuiPainter.TEXT_COLOR, false);
        boolean pagination=switch(selectedTab) {
            case CAPE -> capeContent.paginationVisible();case OUTFIT -> outfitContent.paginationVisible();case ARMOR -> armorContent.paginationVisible();
        };
        if (pagination) {
            String page=switch(selectedTab) {
                case CAPE -> capeContent.pageNumber()+" / "+capeContent.pageCount();
                case OUTFIT -> outfitContent.pageNumber()+" / "+outfitContent.pageCount();
                case ARMOR -> armorContent.positionLabel();
            };
            graphics.text(font,page,layout.paginationBounds().centerX()-font.width(page)/2,layout.pageLabelY(),WardrobeGuiPainter.TEXT_COLOR,false);
        }
        if (selectedTab==SelectedTab.ARMOR && armorContent.showEmpty()) drawWrapped(graphics,armorContent.emptyText(),armorContent.emptyBounds(layout));
        if (selectedTab==SelectedTab.OUTFIT && (!selection.v2() || outfitSource.entries().isEmpty())) {
            var area=selection.v2()?layout.outfitGridBounds():layout.gridBounds();
            drawWrapped(graphics,outfitContent.resourceStatus(),area);
        }
        var bounds = layout.statusBounds();
		var status = statusText();
		var display = status.clip(bounds.width(), font::width);
		graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
		graphics.text(font, display.firstLine(), bounds.x(), bounds.y(), WardrobeGuiPainter.TEXT_COLOR, false);
		graphics.text(font, display.secondLine(), bounds.x(), bounds.y() + 10, WardrobeGuiPainter.TEXT_COLOR, false);
		graphics.disableScissor();
        AbstractWidget target=keyboardInput && getFocused() instanceof AbstractWidget focused?focused:
                focusKeys.keySet().stream().filter(widget -> widget.visible && widget.isMouseOver(mouseX,mouseY)).findFirst().orElse(null);
        String emptyText=selectedTab==SelectedTab.ARMOR && armorContent.showEmpty()?armorContent.emptyText():
                selectedTab==SelectedTab.OUTFIT && (!selection.v2() || outfitSource.entries().isEmpty())?outfitContent.resourceStatus():"";
        var emptyArea=selectedTab==SelectedTab.ARMOR?armorContent.emptyBounds(layout):layout.gridBounds();
        String targetKey=target==null?(!keyboardInput && bounds.contains(mouseX,mouseY)?"status":
                !keyboardInput && !emptyText.isEmpty() && emptyArea.contains(mouseX,mouseY)?"empty":null):focusKeys.get(target);
        if (!Objects.equals(targetKey,tooltipPageTarget)) { tooltipPageTarget=targetKey;tooltipPage=0; }
        tooltipVisible=false;lastTooltipBounds=null;
        tooltipTargetBounds=target==null?("empty".equals(targetKey)?emptyArea:bounds):
                new WardrobeLayout.Bounds(target.getX(),target.getY(),target.getWidth(),target.getHeight());
        tooltipAnchorX=keyboardInput && target!=null?target.getX()+target.getWidth()/2:mouseX;
        tooltipAnchorY=keyboardInput && target!=null?target.getY()+target.getHeight()/2:mouseY;
        if (tooltipState.visible(targetKey,keyboardInput,System.nanoTime())) {
            List<String> lines=List.of();
            if ("status".equals(targetKey)) lines=status.fullText();
            else if ("empty".equals(targetKey)) lines=List.of(emptyText);
            else if (target instanceof ArmorGridEntryWidget armorGrid) lines=armorGrid.tooltip();
            else if (target instanceof WardrobeArmorButton armor) lines=armor.tooltip();
            else if (target instanceof OutfitGridEntryWidget entry) lines=outfitContent.tooltip(entry.id());
            else if (target instanceof CapeGridEntryWidget entry) lines=List.of(entry.tooltipText());
            else if (target instanceof WardrobePreviewModeButton) lines=List.of(target.getMessage().getString());
            else if (target==reloadButton && reloadButton!=null) lines=List.of(WardrobeText.string("reload.latest"),WardrobeText.string("reload.discard"));
            else if (target==applyButton) {
                var detail=new ArrayList<String>();detail.add(WardrobeText.string("apply.hint"));
                if (!applyButton.active) detail.add(selection.dirty()?
                        (status.secondLine().isEmpty()?WardrobeText.string("status.not_ready"):status.secondLine()):WardrobeText.string("status.unchanged"));
                if (!selection.supplementalStatus().isEmpty()) detail.addAll(selection.supplementalStatus());
                lines=detail;
            }
            else if (target!=null && selectedTab==SelectedTab.OUTFIT && layout.scopeButtonBounds().contains(target.getX(),target.getY())
                    && outfitContent.panel()==OutfitWardrobeContent.Panel.GRID) lines=outfitContent.summaryTooltip();
            else if (target!=null && targetKey!=null && targetKey.equals("tab:"+selectedTab.name()) && !emptyText.isEmpty())
                lines=List.of(target.getMessage().getString(),emptyText);
            else if (target!=null) lines=List.of(target.getMessage().getString());
            if (!lines.isEmpty()) boundedTooltip(graphics,lines);
        }
    }

    private void drawWrapped(GuiGraphicsExtractor graphics,String text,WardrobeLayout.Bounds area) {
        var lines=font.split(Component.literal(text),area.width()-6);
        int y=area.y()+4;
        for (var line:lines) {
            if (y+font.lineHeight>area.bottom()) break;
            graphics.text(font,line,area.x()+3,y,WardrobeGuiPainter.TEXT_COLOR,false);y+=font.lineHeight;
        }
    }
    private void boundedTooltip(GuiGraphicsExtractor graphics,List<String> text) {
        int textWidth=WardrobeTooltipLayout.textWidth(width);
        var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String line:text) lines.addAll(font.split(Component.literal(line),textWidth));
        // Vanilla 文本 Tooltip 每行比字体行高多一像素，首行后另有间距。
        int capacity=Math.max(3,(WardrobeTooltipLayout.textHeight(height,tooltipTargetBounds)-2)/(font.lineHeight+1));
        var shown=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        if (lines.size()>capacity) {
            var footer=font.split(WardrobeText.text("tooltip.more",1,999),textWidth);
            int contentCapacity=Math.max(1,capacity-footer.size());
            tooltipPageCount=(lines.size()+contentCapacity-1)/contentCapacity;
            tooltipPage=Math.clamp(tooltipPage,0,tooltipPageCount-1);
            shown.addAll(WardrobeTooltipLayout.page(lines,contentCapacity,tooltipPage));
            shown.addAll(font.split(WardrobeText.text("tooltip.more",tooltipPage+1,tooltipPageCount),textWidth));
        } else { tooltipPage=0;tooltipPageCount=1;shown.addAll(lines); }
        tooltipVisible=true;
        graphics.setTooltipForNextFrame(font,List.copyOf(shown),(screenWidth,screenHeight,x,y,tipWidth,tipHeight) -> {
            lastTooltipBounds=WardrobeTooltipLayout.place(screenWidth,screenHeight,tooltipAnchorX,tooltipAnchorY,tipWidth,tipHeight,
                    tooltipTargetBounds,List.of(layout.applyButtonBounds(),layout.paginationBounds()));
            return new org.joml.Vector2i(lastTooltipBounds.x(),lastTooltipBounds.y());
        },tooltipAnchorX,tooltipAnchorY,true);
    }

    WardrobePreviewAppearance previewAppearance() {
		return previewAppearanceResolver.resolve(selection.previewSelection());
	}

	private void extractPlayerPreview(GuiGraphicsExtractor graphics, int mouseY) {
		var bounds = layout.previewModelBounds();
		if (minecraft == null || minecraft.player == null) {
			extractPreviewMessage(graphics, bounds, PREVIEW_UNAVAILABLE.getString());
			return;
		}
		if (!selection.authorityKnown()) {
			extractPreviewMessage(graphics, bounds, WardrobeText.string("status.authority_loading"));
			return;
		}
        var fullPreview=selection.previewDraft();
        var appearance=fullPreview.map(value -> previewAppearanceResolver.resolve(value.cape())).orElseGet(this::previewAppearance);
		if (!previewRenderer.extract(graphics, bounds, layout.previewEntitySize(),
				PLAYER_PREVIEW_OFFSET_Y, mouseY, previewRotation.yawDegrees(), minecraft.player, appearance,
                previewMode,fullPreview,outfitSource)) {
			extractPreviewMessage(graphics, bounds, PREVIEW_UNAVAILABLE.getString());
		}
	}

	private void extractPreviewMessage(GuiGraphicsExtractor graphics, WardrobeLayout.Bounds bounds, String message) {
		String fitted = WardrobeStatusText.fit(message, bounds.width(), font::width);
		graphics.text(font, fitted, bounds.centerX() - font.width(fitted) / 2,
				bounds.centerY() - font.lineHeight / 2, WardrobeGuiPainter.PREVIEW_TEXT_COLOR, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        keyboardInput=false;tooltipState.dismiss();
		// Widget 先处理命中；旋转区还在几何上排除预览模式按钮和边框。
		if (super.mouseClicked(event, doubleClick)) {
            if (getFocused()!=null && !children().contains(getFocused())) restoreFocus();
			return true;
		}
		return layout != null && layout.fitsScreen() && previewRotation.beginDrag(
				event.x(), event.y(), event.button(), layout.previewDragBounds());
	}

    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if (selectedTab==SelectedTab.ARMOR && layout!=null && layout.fitsScreen()
                && layout.gridBounds().contains(x,y) && vertical!=0 && armorContent.scroll(vertical>0?-1:1)) {
            tooltipState.dismiss();rebuildPreservingFocus();return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void mouseMoved(double x,double y) { keyboardInput=false;super.mouseMoved(x,y); }

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (layout != null && layout.fitsScreen() && previewRotation.drag(event.button(), deltaX)) {
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (previewRotation.endDrag(event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	enum SelectedTab {
        CAPE("tab.cape"),OUTFIT("tab.outfit"),ARMOR("armor.tab");
        final String label;SelectedTab(String label) { this.label=label; }
        String label() { return WardrobeText.string(label); }
    }

	record Actions(BooleanSupplier channelSupported, Predicate<KeyEvent> inventoryKey,
			Consumer<SetCapeSelectionPayload> send, Runnable close, BooleanSupplier fullChannelSupported, Consumer<SetFullFashionSelectionPayload> fullSend, BooleanSupplier resultReceiverReady) {
        Actions(BooleanSupplier channelSupported,Predicate<KeyEvent> inventoryKey,Consumer<SetCapeSelectionPayload> send,Runnable close,
                BooleanSupplier fullChannelSupported,Consumer<SetFullFashionSelectionPayload> fullSend) {
            this(channelSupported,inventoryKey,send,close,fullChannelSupported,fullSend,() -> true);
        }
        Actions(BooleanSupplier channelSupported, Predicate<KeyEvent> inventoryKey, Consumer<SetCapeSelectionPayload> send, Runnable close) {
            this(channelSupported,inventoryKey,send,close,() -> false,request -> { });
        }
		Actions {
			Objects.requireNonNull(channelSupported);
			Objects.requireNonNull(inventoryKey);
			Objects.requireNonNull(send);
			Objects.requireNonNull(close);
            Objects.requireNonNull(fullChannelSupported); Objects.requireNonNull(fullSend);
		}
	}

    private final class TabWidget extends AbstractButton {
        private final SelectedTab tab;
        TabWidget(WardrobeLayout.Bounds b,SelectedTab tab) {
            super(b.x(),b.y(),b.width(),b.height(),Component.literal(tab.label()));this.tab=tab;
        }
        @Override public void onPress(InputWithModifiers input) { if (visible && active) selectTab(tab); }
        @Override protected void extractContents(GuiGraphicsExtractor graphics,int x,int y,float tick) {
            if (isHoveredOrFocused()) graphics.fill(getX()+4,getY()+3,getX()+getWidth()-4,getY()+4,0xffffffff);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE,WardrobeText.string(selectedTab==tab?"tab.selected":"tab.switch",tab.label()));
        }
    }
}
