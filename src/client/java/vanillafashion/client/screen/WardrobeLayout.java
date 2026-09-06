package vanillafashion.client.screen;

final class WardrobeLayout {
	static final int GRID_GAP = 4;
	static final int MIN_ENTRY_WIDTH = 48;
	static final int MAX_ENTRY_WIDTH = 76;
	static final int MIN_ENTRY_HEIGHT = 38;
	static final int MAX_ENTRY_HEIGHT = 52;
	static final int PAGE_BUTTON_WIDTH = 20;
	static final int PAGE_BUTTON_HEIGHT = 20;
	static final int CLOSE_BUTTON_WIDTH = 100;
	static final int CLOSE_BUTTON_HEIGHT = 20;

	private static final int OUTER_MARGIN = 8;
	private static final int MIN_HORIZONTAL_GAP = 8;
	private static final int MAX_HORIZONTAL_GAP = 16;
	private static final int MIN_PREVIEW_WIDTH = 72;
	private static final int MAX_PREVIEW_WIDTH = 104;
	private static final int VERTICAL_GAP = 6;
	private static final int CONTENT_FIXED_HEIGHT = 114;

	private final Bounds contentBounds;
	private final Bounds previewBounds;
	private final Bounds gridBounds;
	private final Bounds previousPageButtonBounds;
	private final Bounds nextPageButtonBounds;
	private final Bounds closeButtonBounds;
	private final int entryWidth;
	private final int entryHeight;
	private final int titleY;
	private final int sectionTitleY;
	private final int pageLabelY;
	private final int selectionLabelY;
	private final int statusLabelY;
	private final int previewEntitySize;

	private WardrobeLayout(
			Bounds contentBounds,
			Bounds previewBounds,
			Bounds gridBounds,
			Bounds previousPageButtonBounds,
			Bounds nextPageButtonBounds,
			Bounds closeButtonBounds,
			int entryWidth,
			int entryHeight,
			int titleY,
			int sectionTitleY,
			int pageLabelY,
			int selectionLabelY,
			int statusLabelY,
			int previewEntitySize
	) {
		this.contentBounds = contentBounds;
		this.previewBounds = previewBounds;
		this.gridBounds = gridBounds;
		this.previousPageButtonBounds = previousPageButtonBounds;
		this.nextPageButtonBounds = nextPageButtonBounds;
		this.closeButtonBounds = closeButtonBounds;
		this.entryWidth = entryWidth;
		this.entryHeight = entryHeight;
		this.titleY = titleY;
		this.sectionTitleY = sectionTitleY;
		this.pageLabelY = pageLabelY;
		this.selectionLabelY = selectionLabelY;
		this.statusLabelY = statusLabelY;
		this.previewEntitySize = previewEntitySize;
	}

	static WardrobeLayout calculate(int screenWidth, int screenHeight, int fontLineHeight) {
		if (screenWidth <= 0 || screenHeight <= 0 || fontLineHeight <= 0) {
			throw new IllegalArgumentException("衣柜布局尺寸必须为正数。");
		}

		int horizontalGap = clamp(screenWidth / 32, MIN_HORIZONTAL_GAP, MAX_HORIZONTAL_GAP);
		int previewWidth = clamp(screenWidth / 4, MIN_PREVIEW_WIDTH, MAX_PREVIEW_WIDTH);
		int availableGridWidth = screenWidth - 2 * OUTER_MARGIN - previewWidth - horizontalGap;
		int entryWidth = clamp(
				(availableGridWidth - (WardrobeCapeCatalog.PAGE_COLUMNS - 1) * GRID_GAP)
						/ WardrobeCapeCatalog.PAGE_COLUMNS,
				MIN_ENTRY_WIDTH,
				MAX_ENTRY_WIDTH
		);
		int gridWidth = WardrobeCapeCatalog.PAGE_COLUMNS * entryWidth
				+ (WardrobeCapeCatalog.PAGE_COLUMNS - 1) * GRID_GAP;
		int contentWidth = previewWidth + horizontalGap + gridWidth;
		int contentX = (screenWidth - contentWidth) / 2;

		int entryHeight = clamp(
				(screenHeight - 126) / WardrobeCapeCatalog.PAGE_ROWS,
				MIN_ENTRY_HEIGHT,
				MAX_ENTRY_HEIGHT
		);
		int gridHeight = WardrobeCapeCatalog.PAGE_ROWS * entryHeight
				+ (WardrobeCapeCatalog.PAGE_ROWS - 1) * GRID_GAP;
		int contentHeight = CONTENT_FIXED_HEIGHT + WardrobeCapeCatalog.PAGE_ROWS * entryHeight;
		int contentY = Math.max(6, (screenHeight - contentHeight) / 2);
		int titleY = contentY;
		int sectionTitleY = titleY + fontLineHeight + VERTICAL_GAP;
		int gridY = sectionTitleY + fontLineHeight + VERTICAL_GAP;

		Bounds previewBounds = new Bounds(contentX, gridY, previewWidth, gridHeight);
		Bounds gridBounds = new Bounds(previewBounds.right() + horizontalGap, gridY, gridWidth, gridHeight);
		int pageButtonY = gridBounds.bottom() + VERTICAL_GAP;
		int gridCenterX = gridBounds.centerX();
		Bounds previousPageButtonBounds = new Bounds(
				gridCenterX - 72,
				pageButtonY,
				PAGE_BUTTON_WIDTH,
				PAGE_BUTTON_HEIGHT
		);
		Bounds nextPageButtonBounds = new Bounds(
				gridCenterX + 52,
				pageButtonY,
				PAGE_BUTTON_WIDTH,
				PAGE_BUTTON_HEIGHT
		);
		int pageLabelY = pageButtonY + (PAGE_BUTTON_HEIGHT - fontLineHeight) / 2;
		int selectionLabelY = pageButtonY + PAGE_BUTTON_HEIGHT + VERTICAL_GAP;
		int statusLabelY = selectionLabelY + fontLineHeight;
		int closeButtonY = statusLabelY + fontLineHeight + VERTICAL_GAP;
		Bounds closeButtonBounds = new Bounds(
				(screenWidth - CLOSE_BUTTON_WIDTH) / 2,
				closeButtonY,
				CLOSE_BUTTON_WIDTH,
				CLOSE_BUTTON_HEIGHT
		);
		Bounds contentBounds = new Bounds(contentX, contentY, contentWidth, contentHeight);
		int previewEntitySize = clamp(gridHeight * 3 / 7, 24, 60);

		return new WardrobeLayout(
				contentBounds,
				previewBounds,
				gridBounds,
				previousPageButtonBounds,
				nextPageButtonBounds,
				closeButtonBounds,
				entryWidth,
				entryHeight,
				titleY,
				sectionTitleY,
				pageLabelY,
				selectionLabelY,
				statusLabelY,
				previewEntitySize
		);
	}

	Bounds entryBounds(int pageEntryIndex) {
		if (pageEntryIndex < 0 || pageEntryIndex >= WardrobeCapeCatalog.PAGE_SIZE) {
			throw new IllegalArgumentException("衣柜页面条目索引超出 4×3 网格范围。");
		}

		int column = pageEntryIndex % WardrobeCapeCatalog.PAGE_COLUMNS;
		int row = pageEntryIndex / WardrobeCapeCatalog.PAGE_COLUMNS;
		return new Bounds(
				gridBounds.x() + column * (entryWidth + GRID_GAP),
				gridBounds.y() + row * (entryHeight + GRID_GAP),
				entryWidth,
				entryHeight
		);
	}

	Bounds contentBounds() {
		return contentBounds;
	}

	Bounds previewBounds() {
		return previewBounds;
	}

	Bounds gridBounds() {
		return gridBounds;
	}

	Bounds previousPageButtonBounds() {
		return previousPageButtonBounds;
	}

	Bounds nextPageButtonBounds() {
		return nextPageButtonBounds;
	}

	Bounds finishButtonBounds() {
		return new Bounds(closeButtonBounds.x() - 54, closeButtonBounds.y(), CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
	}

	Bounds cancelButtonBounds() {
		return new Bounds(closeButtonBounds.x() + 54, closeButtonBounds.y(), CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
	}

	int titleY() {
		return titleY;
	}

	int sectionTitleY() {
		return sectionTitleY;
	}

	int pageLabelY() {
		return pageLabelY;
	}

	int selectionLabelY() {
		return selectionLabelY;
	}

	int statusLabelY() {
		return statusLabelY;
	}

	int previewEntitySize() {
		return previewEntitySize;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	record Bounds(int x, int y, int width, int height) {
		Bounds {
			if (width <= 0 || height <= 0) {
				throw new IllegalArgumentException("衣柜布局区域尺寸必须为正数。");
			}
		}

		int right() {
			return x + width;
		}

		int bottom() {
			return y + height;
		}

		int centerX() {
			return x + width / 2;
		}

		int centerY() {
			return y + height / 2;
		}

		boolean contains(double pointX, double pointY) {
			return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
		}

		boolean overlaps(Bounds other) {
			return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
		}

		boolean isWithin(int outerWidth, int outerHeight) {
			return x >= 0 && y >= 0 && right() <= outerWidth && bottom() <= outerHeight;
		}
	}
}
