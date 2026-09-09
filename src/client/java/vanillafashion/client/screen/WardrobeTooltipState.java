package vanillafashion.client.screen;

import java.util.Objects;

/** 鼠标停留后显示；输入会隐藏当前提示，焦点或悬停目标变化后才重新展示。 */
final class WardrobeTooltipState {
    private Object target,suppressed;
    private long since;
    boolean visible(Object next,boolean keyboard,long now) {
        if (!Objects.equals(next,target)) { target=next;since=now;suppressed=null; }
        return target!=null && !Objects.equals(target,suppressed) && (keyboard || now-since>=300_000_000L);
    }
    void dismiss() { suppressed=target; }
}
