package dev.zbw3790.fashion.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 只定位衣柜提示文字；优先避开目标，再避开应用与分页，最后按锚点距离选择。 */
final class WardrobeTooltipLayout {
    private static final int MARGIN = 8;
    private static final int PAD = 4;
    private WardrobeTooltipLayout() { }

    static int textWidth(int screenWidth) { return Math.max(1, Math.min(220, screenWidth - MARGIN * 2)); }

    static int textHeight(int screenHeight, WardrobeLayout.Bounds target) {
        int gap = Math.max(target.y() - MARGIN, screenHeight - MARGIN - target.bottom());
        return Math.max(30, Math.min(140, gap - PAD * 2));
    }

    static WardrobeLayout.Bounds place(int width, int height, int anchorX, int anchorY,
            int textWidth, int textHeight, WardrobeLayout.Bounds target, List<WardrobeLayout.Bounds> important) {
        int w = Math.min(textWidth, width - MARGIN * 2);
        int h = Math.min(textHeight, height - MARGIN * 2);
        var candidates = new ArrayList<WardrobeLayout.Bounds>();
        int[] xs = {anchorX + 12, anchorX - w - 12, target.right() + 8,
                target.x() - w - 8, target.centerX() - w / 2, MARGIN, width - MARGIN - w};
        int[] ys = {anchorY + 12, target.bottom() + 8, target.y() - h - 8, MARGIN, height - MARGIN - h};
        for (int x : xs) for (int y : ys) candidates.add(new WardrobeLayout.Bounds(
                Math.clamp(x, MARGIN, width - MARGIN - w),
                Math.clamp(y, MARGIN, height - MARGIN - h), w, h));
        return candidates.stream().min(Comparator.comparingLong(b -> {
            var outer = new WardrobeLayout.Bounds(b.x() - PAD, b.y() - PAD, b.width() + PAD * 2, b.height() + PAD * 2);
            long targetOverlap = overlap(outer, target);
            long importantOverlap = important.stream().mapToLong(rect -> overlap(outer, rect)).sum();
            long distance = Math.abs(b.x() - anchorX) + Math.abs(b.y() - anchorY);
            return targetOverlap * 1_000_000L + importantOverlap * 1_000L + distance;
        })).orElseThrow();
    }

    private static long overlap(WardrobeLayout.Bounds a, WardrobeLayout.Bounds b) {
        return (long) Math.max(0, Math.min(a.right(), b.right()) - Math.max(a.x(), b.x()))
                * Math.max(0, Math.min(a.bottom(), b.bottom()) - Math.max(a.y(), b.y()));
    }

    /** 全部换行结果保留；仅改变当前展示切片，不截断任何资源数据。 */
    static <T> List<T> page(List<T> lines, int capacity, int page) {
        int count = Math.max(1, (lines.size() + capacity - 1) / capacity);
        int start = Math.clamp(page, 0, count - 1) * capacity;
        return List.copyOf(lines.subList(start, Math.min(start + capacity, lines.size())));
    }
}
