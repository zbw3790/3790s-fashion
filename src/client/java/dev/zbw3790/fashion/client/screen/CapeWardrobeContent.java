package dev.zbw3790.fashion.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.client.cape.ClientCapeRegistry;
import dev.zbw3790.fashion.client.cape.ClientCapeTextureManager;

/** Cape 内容与控件；只修改共享 Draft，不持有网络发送器或世界状态。 */
final class CapeWardrobeContent {
	private static final WidgetSprites PREVIOUS_SPRITES = sprites("page_backward");
	private static final WidgetSprites NEXT_SPRITES = sprites("page_forward");

	private final Supplier<List<CapeCosmeticMetadata>> metadataSource;
	private final Function<CapeCosmeticMetadata, Optional<Identifier>> textureLookup;
	private final WardrobeSelectionSession selection;
	private final WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of());
	private final List<CapeGridEntryWidget> widgets = new ArrayList<>();
	private final Map<Optional<CapeId>, SlotModel> slotModels = new HashMap<>();
	private List<CapeCosmeticMetadata> metadataEntries = List.of();
	private ImageButton previousButton;
	private ImageButton nextButton;
	private State state = State.LOADING;
	private boolean authorityUnavailable;

	CapeWardrobeContent(ClientCapeRegistry registry, ClientCapeTextureManager textures,
			WardrobeSelectionSession selection) {
		this(Objects.requireNonNull(registry)::entries,
				metadata -> Objects.requireNonNull(textures).find(metadata.capeSha256()), selection);
	}

	CapeWardrobeContent(Supplier<List<CapeCosmeticMetadata>> metadataSource,
			Function<CapeCosmeticMetadata, Optional<Identifier>> textureLookup,
			WardrobeSelectionSession selection) {
		this.metadataSource = Objects.requireNonNull(metadataSource);
		this.textureLookup = Objects.requireNonNull(textureLookup);
		this.selection = Objects.requireNonNull(selection);
		refresh(false);
	}

	/** 返回 Catalog 是否改变；纹理、门禁和控件状态每次都会刷新。 */
	boolean refresh(boolean authorityUnavailable) {
		this.authorityUnavailable = authorityUnavailable;
		boolean changed = false;
		boolean failed = authorityUnavailable;
		try {
			var current = List.copyOf(metadataSource.get());
			if (!current.equals(metadataEntries)) {
				catalog.replace(current);
				metadataEntries = current;
				changed = true;
			}
		} catch (RuntimeException exception) {
			// 保留已有条目与页码，关闭自定义条目的激活，等待下次同步恢复。
			failed = true;
		}

		slotModels.clear();
		for (var entry : catalog.pageEntries()) {
			SlotModel model;
			if (entry.isVanilla()) {
				model = new SlotModel(entry, Optional.empty(), Availability.READY);
			} else if (failed) {
				model = new SlotModel(entry, Optional.empty(), Availability.UNAVAILABLE);
			} else {
				try {
					var texture = Objects.requireNonNull(textureLookup.apply(entry.metadata().orElseThrow()));
					model = new SlotModel(entry, texture,
							texture.isPresent() ? Availability.READY : Availability.LOADING);
				} catch (RuntimeException exception) {
					model = new SlotModel(entry, Optional.empty(), Availability.UNAVAILABLE);
				}
			}
			slotModels.put(entry.capeId(), model);
		}
		if (failed || slotModels.values().stream().anyMatch(model -> model.availability() == Availability.UNAVAILABLE)) {
			state = State.ERROR;
		} else if (!selection.authorityKnown()
				|| slotModels.values().stream().anyMatch(model -> model.availability() == Availability.LOADING)) {
			state = State.LOADING;
		} else {
			state = catalog.capeCount() == 0 ? State.EMPTY : State.READY;
		}
		widgets.forEach(CapeGridEntryWidget::refreshAvailability);
		refreshPagination();
		return changed;
	}

	void buildWidgets(WardrobeLayout layout, Consumer<AbstractWidget> add, Runnable rebuild) {
		widgets.clear();
		previousButton = null;
		nextButton = null;
		if (!layout.fitsScreen()) {
			return;
		}
		refresh(authorityUnavailable);
		var entries = catalog.pageEntries();
		for (int index = 0; index < entries.size(); index++) {
			var entry = entries.get(index);
			var widget = new CapeGridEntryWidget(layout.entryBounds(index), () -> modelFor(entry),
					() -> selection.authorityKnown() && entry.capeId().equals(selection.draft()),
					selection::canEdit,
					() -> selection.authorityKnown() && !selection.closed() && selection.waiting(),
					() -> select(entry));
			widgets.add(widget);
			add.accept(widget);
		}
		previousButton = pageButton(layout.previousPageButtonBounds(), false, rebuild);
		nextButton = pageButton(layout.nextPageButtonBounds(), true, rebuild);
		add.accept(previousButton);
		add.accept(nextButton);
		refreshPagination();
	}

	private ImageButton pageButton(WardrobeLayout.Bounds bounds, boolean next, Runnable rebuild) {
		var label = Component.literal(next ? "下一页" : "上一页");
		var button = new ImageButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
				next ? NEXT_SPRITES : PREVIOUS_SPRITES, pressed -> {
					if (pressed.active && pressed.visible && changePage(next)) {
						rebuild.run();
					}
				}, label);
		button.setTooltip(Tooltip.create(label));
		return button;
	}

	private void refreshPagination() {
		if (previousButton != null) {
			previousButton.visible = paginationVisible() && catalog.canGoToPreviousPage();
			previousButton.active = previousButton.visible;
		}
		if (nextButton != null) {
			nextButton.visible = paginationVisible() && catalog.canGoToNextPage();
			nextButton.active = nextButton.visible;
		}
	}

	boolean changePage(boolean next) {
		int before = catalog.pageIndex();
		if (next) {
			catalog.goToNextPage();
		} else {
			catalog.goToPreviousPage();
		}
		if (before == catalog.pageIndex()) {
			return false;
		}
		refresh(authorityUnavailable);
		return true;
	}

	boolean activateSlot(int index) {
		var entries = catalog.pageEntries();
		return index >= 0 && index < entries.size() && select(entries.get(index));
	}

	private boolean select(WardrobeCapeCatalog.Entry entry) {
		var current = slotModels.get(entry.capeId());
		if (!selection.canEdit() || current == null || !current.selectable()) {
			return false;
		}
		selection.select(entry.capeId());
		return true;
	}

	private SlotModel modelFor(WardrobeCapeCatalog.Entry entry) {
		return slotModels.getOrDefault(entry.capeId(),
				new SlotModel(entry, Optional.empty(), Availability.UNAVAILABLE));
	}

	List<SlotModel> slots() {
		return catalog.pageEntries().stream().map(this::modelFor).toList();
	}

	List<CapeCosmeticMetadata> metadataEntries() { return metadataEntries; }
	boolean hasMetadata(CapeId id) { return metadataEntries.stream().anyMatch(metadata -> metadata.id().equals(id)); }
	int pageIndex() { return catalog.pageIndex(); }
	int pageNumber() { return catalog.pageNumber(); }
	int pageCount() { return catalog.pageCount(); }
	boolean paginationVisible() { return catalog.pageCount() > 1; }
	State state() { return state; }

	String status() {
		return switch (state) {
			case READY -> "";
			case LOADING -> "披风内容正在加载";
			case EMPTY -> "服务器暂无可用披风";
			case ERROR -> "披风内容当前不可用";
		};
	}

	private static WidgetSprites sprites(String name) {
		return new WidgetSprites(Identifier.withDefaultNamespace("recipe_book/" + name),
				Identifier.withDefaultNamespace("recipe_book/" + name + "_highlighted"));
	}

	enum State { READY, LOADING, EMPTY, ERROR }
	enum Availability { READY, LOADING, UNAVAILABLE }
	enum Visual { ORIGINAL_ICON, CAPE_THUMBNAIL, EMPTY }

	record SlotModel(WardrobeCapeCatalog.Entry entry, Optional<Identifier> texture, Availability availability) {
		SlotModel {
			Objects.requireNonNull(entry);
			Objects.requireNonNull(texture);
			Objects.requireNonNull(availability);
			if (entry.isVanilla() || availability != Availability.READY) {
				texture = Optional.empty();
			}
		}

		boolean selectable() { return entry.isVanilla() || availability == Availability.READY; }

		Visual visual() {
			return entry.isVanilla() ? Visual.ORIGINAL_ICON
					: texture.isPresent() ? Visual.CAPE_THUMBNAIL : Visual.EMPTY;
		}

		String tooltipText() {
			if (entry.isVanilla()) {
				return "原版";
			}
			String elytra = entry.metadata().orElseThrow().hasElytra() ? "有" : "无";
			String status = switch (availability) {
				case READY -> "";
				case LOADING -> "\n纹理正在加载";
				case UNAVAILABLE -> "\n当前不可用";
			};
			return "披风 ID：" + entry.displayName() + "\n自定义鞘翅：" + elytra + status;
		}
	}
}
