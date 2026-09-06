package vanillafashion.client.screen;

/** 已批准衣柜的逻辑像素布局；重算布局不持有或改变会话状态。 */
final class WardrobeLayout {
	static final int STANDARD_WIDTH = 195;
	static final int COMPACT_WIDTH = 176;
	static final int FRAME_HEIGHT = 170;
	static final int TAB_WIDTH = 32;
	static final int TAB_HEIGHT = 32;
	static final int TAB_PITCH = 33;
	static final int TAB_OVERLAP = 4;
	static final int ICON_SIZE = 16;
	static final int ENTRY_WIDTH = 22;
	static final int ENTRY_HEIGHT = 34;
	static final int THUMBNAIL_WIDTH = 20;
	static final int THUMBNAIL_HEIGHT = 32;
	static final int PAGE_BUTTON_WIDTH = 12;
	static final int PAGE_BUTTON_HEIGHT = 17;
	static final int APPLY_BUTTON_WIDTH = 56;
	static final int APPLY_BUTTON_HEIGHT = 20;
	static final int OUTER_MARGIN = 8;
	static final int TOTAL_HEIGHT = FRAME_HEIGHT + TAB_HEIGHT - TAB_OVERLAP;

	enum Mode { STANDARD, COMPACT, TOO_SMALL }

	private final Mode mode;
	private final Bounds frameBounds;

	private WardrobeLayout(Mode mode, Bounds frameBounds) {
		this.mode = mode;
		this.frameBounds = frameBounds;
	}

	static WardrobeLayout calculate(int screenWidth, int screenHeight, int fontLineHeight) {
		if (screenWidth <= 0 || screenHeight <= 0 || fontLineHeight <= 0) {
			throw new IllegalArgumentException("衣柜布局尺寸必须为正数。");
		}
		Mode mode;
		if (screenHeight < TOTAL_HEIGHT + OUTER_MARGIN * 2
				|| screenWidth < COMPACT_WIDTH + OUTER_MARGIN * 2) {
			mode = Mode.TOO_SMALL;
		} else if (screenWidth >= STANDARD_WIDTH + OUTER_MARGIN * 2) {
			mode = Mode.STANDARD;
		} else {
			mode = Mode.COMPACT;
		}
		int frameWidth = mode == Mode.STANDARD ? STANDARD_WIDTH : COMPACT_WIDTH;
		int left = Math.floorDiv(screenWidth - frameWidth, 2);
		int top = Math.floorDiv(screenHeight - TOTAL_HEIGHT, 2) + TAB_HEIGHT - TAB_OVERLAP;
		return new WardrobeLayout(mode, new Bounds(left, top, frameWidth, FRAME_HEIGHT));
	}

	Mode mode() { return mode; }
	boolean fitsScreen() { return mode != Mode.TOO_SMALL; }
	Bounds frameBounds() { return frameBounds; }
	Bounds contentBounds() { return frameBounds; }
	Bounds tabBounds() { return relative(0, -28, TAB_WIDTH, TAB_HEIGHT); }
	Bounds tabIconBounds() { return relative(8, -19, ICON_SIZE, ICON_SIZE); }
	Bounds tabJoinBounds() { return relative(0, 0, TAB_WIDTH, TAB_OVERLAP); }
	Bounds previewBounds() { return relative(8, 18, frameBounds.width() - 114, 102); }
	Bounds gridBounds() { return relative(frameBounds.width() - 96, 18, 88, 102); }
	Bounds paginationBounds() { return relative(frameBounds.width() - 96, 122, 88, 17); }
	Bounds previousPageButtonBounds() { return relative(frameBounds.width() - 96, 122, 12, 17); }
	Bounds nextPageButtonBounds() { return relative(frameBounds.width() - 20, 122, 12, 17); }
	Bounds statusBounds() { return relative(8, 144, frameBounds.width() - 80, 20); }
	Bounds applyButtonBounds() { return relative(frameBounds.width() - 64, 142, 56, 20); }
	Bounds finishButtonBounds() { return applyButtonBounds(); }
	int titleX() { return frameBounds.x() + 8; }
	int titleY() { return frameBounds.y() + 6; }
	int pageLabelY() { return frameBounds.y() + 126; }
	int selectionLabelY() { return frameBounds.y() + 144; }
	int statusLabelY() { return frameBounds.y() + 154; }
	int previewEntitySize() { return mode == Mode.STANDARD ? 40 : 34; }

	Bounds entryBounds(int pageEntryIndex) {
		if (pageEntryIndex < 0 || pageEntryIndex >= WardrobeCapeCatalog.PAGE_SIZE) {
			throw new IllegalArgumentException("衣柜页面条目索引超出 4×3 网格范围。");
		}
		int column = pageEntryIndex % WardrobeCapeCatalog.PAGE_COLUMNS;
		int row = pageEntryIndex / WardrobeCapeCatalog.PAGE_COLUMNS;
		return relative(frameBounds.width() - 96 + column * ENTRY_WIDTH,
				18 + row * ENTRY_HEIGHT, ENTRY_WIDTH, ENTRY_HEIGHT);
	}

	Bounds thumbnailBounds(int pageEntryIndex) {
		Bounds entry = entryBounds(pageEntryIndex);
		return new Bounds(entry.x() + 1, entry.y() + 1, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
	}

	private Bounds relative(int x, int y, int width, int height) {
		return new Bounds(frameBounds.x() + x, frameBounds.y() + y, width, height);
	}

	record Bounds(int x, int y, int width, int height) {
		Bounds {
			if (width <= 0 || height <= 0) {
				throw new IllegalArgumentException("衣柜布局区域尺寸必须为正数。");
			}
		}
		int right() { return x + width; }
		int bottom() { return y + height; }
		int centerX() { return x + width / 2; }
		int centerY() { return y + height / 2; }
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
