package dev.zbw3790.fashion.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;

final class WardrobeCapeCatalog {
	static final int PAGE_COLUMNS = 4;
	static final int PAGE_ROWS = 3;
	static final int PAGE_SIZE = PAGE_COLUMNS * PAGE_ROWS;

	private List<Entry> entries = List.of(Entry.vanilla());
	private int pageIndex;

	WardrobeCapeCatalog(List<CapeCosmeticMetadata> metadataEntries) {
		replace(metadataEntries);
	}

	void replace(List<CapeCosmeticMetadata> metadataEntries) {
		Objects.requireNonNull(metadataEntries, "衣柜 Cape 元数据列表不能为 null。");
		var sortedEntries = new TreeMap<String, CapeCosmeticMetadata>();

		for (CapeCosmeticMetadata metadata : metadataEntries) {
			CapeCosmeticMetadata nonNullMetadata = Objects.requireNonNull(
					metadata,
					"衣柜 Cape 元数据不能为 null。"
			);
			sortedEntries.putIfAbsent(nonNullMetadata.id().value(), nonNullMetadata);
		}

		var replacement = new ArrayList<Entry>(sortedEntries.size() + 1);
		replacement.add(Entry.vanilla());
		sortedEntries.values().stream().map(Entry::cape).forEach(replacement::add);
		entries = List.copyOf(replacement);

		pageIndex = Math.min(pageIndex, pageCount() - 1);
	}

	int capeCount() {
		return entries.size() - 1;
	}

	int pageCount() {
		return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	int pageIndex() {
		return pageIndex;
	}

	int pageNumber() {
		return pageIndex + 1;
	}

	List<Entry> pageEntries() {
		int start = pageIndex * PAGE_SIZE;
		int end = Math.min(start + PAGE_SIZE, entries.size());
		return List.copyOf(entries.subList(start, end));
	}

	boolean canGoToPreviousPage() {
		return pageIndex > 0;
	}

	boolean canGoToNextPage() {
		return pageIndex + 1 < pageCount();
	}

	void goToPreviousPage() {
		pageIndex = Math.max(0, pageIndex - 1);
	}

	void goToNextPage() {
		pageIndex = Math.min(pageCount() - 1, pageIndex + 1);
	}

	record Entry(Optional<CapeCosmeticMetadata> metadata) {
		Entry {
			metadata = Objects.requireNonNull(metadata, "衣柜条目元数据状态不能为 null。");
		}

		static Entry vanilla() {
			return new Entry(Optional.empty());
		}

		static Entry cape(CapeCosmeticMetadata metadata) {
			return new Entry(Optional.of(Objects.requireNonNull(metadata, "Cape 元数据不能为 null。")));
		}

		boolean isVanilla() {
			return metadata.isEmpty();
		}

		Optional<CapeId> capeId() {
			return metadata.map(CapeCosmeticMetadata::id);
		}

		String displayName() {
			return capeId().map(CapeId::value).orElse(WardrobeText.string("original"));
		}
	}
}
