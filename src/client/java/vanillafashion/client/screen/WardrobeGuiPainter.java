package vanillafashion.client.screen;

/** 将已批准的原创像素几何提交给 Minecraft GUI 矩形提取入口。 */
final class WardrobeGuiPainter {
	static final int TEXT_COLOR = 0xFF404040;
	static final int PREVIEW_BACKGROUND_COLOR = 0xFF202020;
	static final int PREVIEW_TEXT_COLOR = 0xFFE0E0E0;
	static final int FRAME_COLOR = 0xFFC6C6C6;
	static final int OUTLINE_COLOR = 0xFF000000;
	static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
	static final int SHADOW_COLOR = 0xFF555555;
	static final int SLOT_COLOR = 0xFF8B8B8B;
	static final int SLOT_SHADOW_COLOR = 0xFF373737;
	static final int HOVER_COLOR = 0x80FFFFFF;
	static final int DISABLED_COLOR = 0x60000000;
	private static final int CAPE_EDGE_COLOR = 0xFF28485F;
	private static final int CAPE_TOP_COLOR = 0xFF72AFCB;
	private static final int CAPE_LEFT_COLOR = 0xFF579DC0;
	private static final int CAPE_BASE_COLOR = 0xFF347CAC;
	private static final int CAPE_RIGHT_COLOR = 0xFF254B78;
	private static final int CAPE_MARK_COLOR = 0xFFACC8D1;
	private static final int CAPE_MARK_INNER_COLOR = 0xFF39688B;
	private static final int ORIGINAL_ARROW_COLOR = 0xFFE6ECEC;
	private static final int ORIGINAL_EDGE_COLOR = 0xFF515A60;
	private static final int ORIGINAL_BASE_COLOR = 0xFFC3C7C8;
	private static final int ELYTRA_EDGE_COLOR = 0xFF4C535A;
	private static final int ELYTRA_HIGHLIGHT_COLOR = 0xFFE0E4E5;
	private static final int ELYTRA_BASE_COLOR = 0xFFB4BCC1;
	private static final int ELYTRA_SHADOW_COLOR = 0xFF7A858D;
	private static final String[][] CORNERS = {
			{"..KK", ".KWW", "KWWW", "KWWW"},
			{"K...", "WK..", "WGK.", "GSSK"},
			{"KWWG", ".KGS", "..KS", "...K"},
			{"SSSK", "SSSK", "SSK.", "KK.."}
	};
	// 原创图标的逻辑像素拓扑，保持已批准的十六乘十六画布与内部留白。
	private static final String[] CAPE_ICON = {
			"................", "................", "....11111111....", "....12222221....",
			"....13444451....", "....13666651....", "....13677651....", "....13677651....",
			"....13677651....", "....13677651....", "....13677651....", "....13666651....",
			"....13444451....", "....13444451....", "....11111111....", "................"
	};
	private static final String[] ORIGINAL_ICON = {
			"................", "................", "..aaaaaaaa......", "..a..bbbbbab....",
			"..a..bccccca....", "aaaaabcccccb....", ".aaa.bcccccb....", ".aaa.bcccccb....",
			"..a..bcccccb....", ".....bcccccb....", ".....bcccccb....", ".....bcccccb....",
			".....bcccccb....", ".....bbbbbbb....", "................", "................"
	};

	// 仅用于预览模式按钮的原创双翼轮廓，不读取或缩放原版物品纹理。
	private static final String[] ELYTRA_ICON = {
			"................", "....dd....dd....", "...defd..dfed...", "..deefgddgfeed..",
			"..deffgddgffed..", "..dffggddggffd..", "...dfggddggfd...", "...dfggddggfd...",
			"....dggddggd....", "....dfgddgfd....", ".....dgddgd.....", ".....dgddgd.....",
			"......dddd......", "......d..d......", "................", "................"
	};

	private WardrobeGuiPainter() { }

	@FunctionalInterface
	interface RectangleSink {
		void fill(int left, int top, int right, int bottom, int color);
	}

	static void frameWithSelectedCapeTab(RectangleSink sink, WardrobeLayout layout) {
		frame(sink, layout.frameBounds());
		// 同一几何入口按原版顺序覆盖共享四行，主框顶边不会穿过选中接缝。
		selectedCapeTab(sink, layout.tabBounds(), layout.tabJoinBounds());
	}

	private static void frame(RectangleSink sink, WardrobeLayout.Bounds bounds) {
		int x = bounds.x(), y = bounds.y(), w = bounds.width(), h = bounds.height();
		// 先填十字主体，保留四角透明阶梯，不能用透明色覆盖已提交的矩形。
		rect(sink, x + 4, y, w - 8, h, FRAME_COLOR);
		rect(sink, x, y + 4, w, h - 8, FRAME_COLOR);
		rect(sink, x + 4, y, w - 8, 1, OUTLINE_COLOR);
		rect(sink, x + 4, y + h - 1, w - 8, 1, OUTLINE_COLOR);
		rect(sink, x, y + 4, 1, h - 8, OUTLINE_COLOR);
		rect(sink, x + w - 1, y + 4, 1, h - 8, OUTLINE_COLOR);
		rect(sink, x + 4, y + 1, w - 8, 2, HIGHLIGHT_COLOR);
		rect(sink, x + 1, y + 4, 2, h - 8, HIGHLIGHT_COLOR);
		rect(sink, x + w - 3, y + 4, 2, h - 8, SHADOW_COLOR);
		rect(sink, x + 4, y + h - 3, w - 8, 2, SHADOW_COLOR);
		paintRows(sink, x, y, CORNERS[0]);
		paintRows(sink, x + w - 4, y, CORNERS[1]);
		paintRows(sink, x, y + h - 4, CORNERS[2]);
		paintRows(sink, x + w - 4, y + h - 4, CORNERS[3]);
	}

	private static void selectedCapeTab(RectangleSink sink, WardrobeLayout.Bounds tab,
			WardrobeLayout.Bounds join) {
		int x = tab.x(), y = tab.y(), w = tab.width();
		rect(sink, x + 4, y, w - 8, tab.height(), FRAME_COLOR);
		rect(sink, x, y + 4, w, tab.height() - 4, FRAME_COLOR);
		rect(sink, x + 4, y, w - 8, 1, OUTLINE_COLOR);
		rect(sink, x + 4, y + 1, w - 8, 2, HIGHLIGHT_COLOR);
		rect(sink, x, y + 4, 1, tab.height() - 4, OUTLINE_COLOR);
		rect(sink, x + 1, y + 4, 2, tab.height() - 4, HIGHLIGHT_COLOR);
		rect(sink, x + w - 1, y + 4, 1, join.y() - y - 3, OUTLINE_COLOR);
		rect(sink, x + w - 3, y + 4, 2, join.y() - y - 2, SHADOW_COLOR);
		paintRows(sink, x, y, CORNERS[0]);
		paintRows(sink, x + w - 4, y, CORNERS[1]);
		rect(sink, x + w - 1, join.y() + 1, 1, 1, HIGHLIGHT_COLOR);
		rect(sink, x + w - 3, join.y() + 2, 1, 1, SHADOW_COLOR);
		rect(sink, x + w - 2, join.y() + 2, 2, 1, HIGHLIGHT_COLOR);
	}

	static void slot(RectangleSink sink, WardrobeLayout.Bounds bounds) {
		int x = bounds.x(), y = bounds.y(), w = bounds.width(), h = bounds.height();
		rect(sink, x, y, w, h, SLOT_COLOR);
		rect(sink, x, y, w - 1, 1, SLOT_SHADOW_COLOR);
		rect(sink, x, y, 1, h - 1, SLOT_SHADOW_COLOR);
		rect(sink, x + 1, y + h - 1, w - 1, 1, HIGHLIGHT_COLOR);
		rect(sink, x + w - 1, y + 1, 1, h - 1, HIGHLIGHT_COLOR);
	}

	static void previewFrame(RectangleSink sink, WardrobeLayout layout) {
		if (!layout.fitsScreen()) {
			return;
		}
		var bounds = layout.previewBounds();
		int x = bounds.x(), y = bounds.y(), w = bounds.width(), h = bounds.height();
		// 单像素下沉边沿用容器灰阶，交叉角为中性灰；深灰底内不添加装饰。
		rect(sink, x, y, w, h, FRAME_COLOR);
		rect(sink, x, y, w - 1, 1, SLOT_SHADOW_COLOR);
		rect(sink, x, y, 1, h - 1, SLOT_SHADOW_COLOR);
		rect(sink, x + 1, y + h - 1, w - 1, 1, HIGHLIGHT_COLOR);
		rect(sink, x + w - 1, y + 1, 1, h - 1, HIGHLIGHT_COLOR);
		var inner = layout.previewInnerBounds();
		rect(sink, inner.x(), inner.y(), inner.width(), inner.height(), PREVIEW_BACKGROUND_COLOR);
	}

	static void capeIcon(RectangleSink sink, WardrobeLayout.Bounds bounds) {
		icon(sink, bounds, CAPE_ICON);
	}

	static void originalIcon(RectangleSink sink, WardrobeLayout.Bounds bounds) {
		icon(sink, bounds, ORIGINAL_ICON);
	}

	static void elytraIcon(RectangleSink sink, WardrobeLayout.Bounds bounds) {
		icon(sink, bounds, ELYTRA_ICON);
	}

	private static void icon(RectangleSink sink, WardrobeLayout.Bounds bounds, String[] rows) {
		if (bounds.width() != WardrobeLayout.ICON_SIZE || bounds.height() != WardrobeLayout.ICON_SIZE) {
			throw new IllegalArgumentException("衣柜原创图标必须保持十六乘十六逻辑像素。");
		}
		paintRows(sink, bounds.x(), bounds.y(), rows);
	}

	static void slotOverlay(RectangleSink sink, WardrobeLayout.Bounds bounds,
			boolean hovered, boolean selected, boolean disabled) {
		int x = bounds.x() + 1, y = bounds.y() + 1;
		int w = bounds.width() - 2, h = bounds.height() - 2;
		if (disabled) {
			rect(sink, x, y, w, h, DISABLED_COLOR);
		} else if (hovered) {
			rect(sink, x, y, w, h, HOVER_COLOR);
		}
		if (selected) {
			rect(sink, x, y, w, 1, HIGHLIGHT_COLOR);
			rect(sink, x, y + h - 1, w, 1, HIGHLIGHT_COLOR);
			rect(sink, x, y + 1, 1, h - 2, HIGHLIGHT_COLOR);
			rect(sink, x + w - 1, y + 1, 1, h - 2, HIGHLIGHT_COLOR);
		}
	}

	private static void paintRows(RectangleSink sink, int x, int y, String[] rows) {
		for (int row = 0; row < rows.length; row++) {
			String pixels = rows[row];
			for (int start = 0; start < pixels.length();) {
				char pixel = pixels.charAt(start);
				int end = start + 1;
				while (end < pixels.length() && pixels.charAt(end) == pixel) { end++; }
				if (pixel != '.') { rect(sink, x + start, y + row, end - start, 1, color(pixel)); }
				start = end;
			}
		}
	}

	private static int color(char pixel) {
		return switch (pixel) {
			case 'K' -> OUTLINE_COLOR;
			case 'W' -> HIGHLIGHT_COLOR;
			case 'G' -> FRAME_COLOR;
			case 'S' -> SHADOW_COLOR;
			case '1' -> CAPE_EDGE_COLOR;
			case '2' -> CAPE_TOP_COLOR;
			case '3' -> CAPE_LEFT_COLOR;
			case '4' -> CAPE_BASE_COLOR;
			case '5' -> CAPE_RIGHT_COLOR;
			case '6' -> CAPE_MARK_COLOR;
			case '7' -> CAPE_MARK_INNER_COLOR;
			case 'a' -> ORIGINAL_ARROW_COLOR;
			case 'b' -> ORIGINAL_EDGE_COLOR;
			case 'c' -> ORIGINAL_BASE_COLOR;
			case 'd' -> ELYTRA_EDGE_COLOR;
			case 'e' -> ELYTRA_HIGHLIGHT_COLOR;
			case 'f' -> ELYTRA_BASE_COLOR;
			case 'g' -> ELYTRA_SHADOW_COLOR;
			default -> throw new IllegalArgumentException("未知衣柜像素标记。");
		};
	}

	private static void rect(RectangleSink sink, int x, int y, int width, int height, int color) {
		sink.fill(x, y, x + width, y + height, color);
	}
}
