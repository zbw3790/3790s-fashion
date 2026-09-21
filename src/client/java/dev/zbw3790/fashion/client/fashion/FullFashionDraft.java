package dev.zbw3790.fashion.client.fashion;

import java.util.*;
import dev.zbw3790.fashion.armor.ArmorSlot;
import dev.zbw3790.fashion.fashion.*;
import dev.zbw3790.fashion.outfit.*;

/** S04 可复用的逐字段三路合并；冲突是数据，不隐式覆盖用户草稿。 */
public final class FullFashionDraft {
    public enum Field { CAPE, HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET }
    private FullPlayerFashionState baseline;
    private PlayerFashionStoredState draft;
    private final Set<Field> conflicts=EnumSet.noneOf(Field.class);
    public FullFashionDraft(FullPlayerFashionState initial) { baseline=Objects.requireNonNull(initial); draft=initial.stored(); }
    public void edit(PlayerFashionStoredState value) { draft=Objects.requireNonNull(value); }
    public void observe(FullPlayerFashionState next) {
        if (next.revision()<baseline.revision()) return;
        var old=baseline.stored(); var incoming=next.stored(); var merged=draft;
        if (draft.cape().equals(old.cape()) && !conflicts.contains(Field.CAPE)) merged=merged.withCape(incoming.cape());
        else if (!incoming.cape().equals(old.cape())) conflicts.add(Field.CAPE);
        var parts=merged.outfit();
        for (int i=0; i<6; i++) {
            var part=OutfitPart.CANONICAL_ORDER.get(i); var field=Field.values()[i+1];
            if (draft.outfit().get(part).equals(old.outfit().get(part)) && !conflicts.contains(field)) parts=parts.with(part,incoming.outfit().get(part));
            else if (!incoming.outfit().get(part).equals(old.outfit().get(part))) conflicts.add(field);
        }
        var armor=merged.armor();
        for (var slot : ArmorSlot.CANONICAL_ORDER) {
            var field=Field.values()[7+slot.ordinal()];
            if (draft.armor().get(slot).equals(old.armor().get(slot)) && !conflicts.contains(field)) armor=armor.with(slot,incoming.armor().get(slot));
            else if (!incoming.armor().get(slot).equals(old.armor().get(slot))) conflicts.add(field);
        }
        draft=merged.withOutfit(parts).withArmor(armor); baseline=next;
    }
    /** 自己成功的 ACK 是明确确认；随后合并更高版本，不把旧 ACK 留作当前 baseline。 */
    public void acceptSuccess(FullPlayerFashionState confirmed, Optional<FullPlayerFashionState> latest) {
        baseline=confirmed; draft=confirmed.stored(); conflicts.clear();
        latest.filter(value -> value.revision()>confirmed.revision()).ifPresent(this::observe);
    }
    public FullPlayerFashionState baseline() { return baseline; }
    public PlayerFashionStoredState draft() { return draft; }
    public Set<Field> conflicts() { return Set.copyOf(conflicts); }
    public boolean dirty() { return !draft.equals(baseline.stored()); }
    public boolean canSubmit() { return dirty() && conflicts.isEmpty(); }
}
