package dev.zbw3790.fashion.fashion;

import java.util.*;
import dev.zbw3790.fashion.armor.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.outfit.*;

/** 聚合意图的唯一持久化；v1 读取迁移不标脏，任何坏条目拒绝整份。 */
public final class PlayerFashionSavedData extends SavedData {
    public static final int DATA_VERSION = 4;
    public static final int MAX_PERSISTED_PLAYER_FASHION_ENTRIES = 16384;
    public static final Codec<PlayerFashionSavedData> CODEC = CompoundTag.CODEC.flatXmap(PlayerFashionSavedData::decode, value -> DataResult.success(value.encode()));
    public static final SavedDataType<PlayerFashionSavedData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("fashion_3790", "player_fashion"), PlayerFashionSavedData::new, CODEC, null);
    private final Map<UUID, PlayerFashionStoredState> states = new HashMap<>();

    public PlayerFashionStoredState storedState(UUID id) { return states.getOrDefault(Objects.requireNonNull(id), PlayerFashionStoredState.DEFAULT); }
    public Map<UUID, PlayerFashionStoredState> storedSnapshot() { return Map.copyOf(states); }
    Optional<CapeId> selection(UUID id) { return storedState(id).cape(); }
    /** 只读 legacy 投影，不保留第二份 Cape 存储。 */
    Map<UUID, CapeId> snapshot() { Map<UUID,CapeId> result = new HashMap<>(); states.forEach((id,s) -> s.cape().ifPresent(c -> result.put(id,c))); return Map.copyOf(result); }
    int size() { return states.size(); }
    int rejectedEntryCount() { return 0; }
    boolean setSelection(UUID id, Optional<CapeId> cape) { return setState(id, storedState(id).withCape(cape)); }
    boolean setState(UUID id, PlayerFashionStoredState state) {
        Objects.requireNonNull(id); Objects.requireNonNull(state);
        if (storedState(id).equals(state)) return false;
        if (!state.isDefault() && !states.containsKey(id) && states.size() >= MAX_PERSISTED_PLAYER_FASHION_ENTRIES) throw new IllegalStateException("持久化玩家已达上限。");
        if (state.isDefault()) states.remove(id); else states.put(id, state);
        setDirty(); return true;
    }
    private static void fields(CompoundTag tag, Set<String> allowed) {
        if (!allowed.containsAll(tag.keySet())) throw new IllegalArgumentException("存档包含未知字段。");
    }
    private static String string(CompoundTag tag, String name) { return tag.getString(name).orElseThrow(() -> new IllegalArgumentException("存档字符串缺失或类型错误。")); }
    private static DataResult<PlayerFashionSavedData> decode(CompoundTag tag) {
        try {
            fields(tag, Set.of("schema_version", "entries"));
            if (!(tag.get("schema_version") instanceof IntTag)) throw new IllegalArgumentException("存档版本类型错误。");
            int version=tag.getIntOr("schema_version", -1);
            if (version != 1 && version != 2 && version != 3 && version != 4) throw new IllegalArgumentException("存档版本不受支持。");
            if (!(tag.get("entries") instanceof ListTag entries) || entries.size() > MAX_PERSISTED_PLAYER_FASHION_ENTRIES) throw new IllegalArgumentException("存档条目列表无效或超限。");
            var data = new PlayerFashionSavedData(); Set<UUID> seen = new HashSet<>();
            for (Tag value : entries) {
                if (!(value instanceof CompoundTag entry)) throw new IllegalArgumentException("玩家条目必须为复合标签。");
                fields(entry, version==1 ? Set.of("uuid", "cape_id") : version==2 ? Set.of("uuid", "cape_id", "outfit_parts") : version==3 ? Set.of("uuid", "cape_id", "outfit_parts", "armor_hidden") : Set.of("uuid", "cape_id", "outfit_parts", "armor"));
                String raw=string(entry,"uuid"); UUID id=UUID.fromString(raw);
                if (!id.toString().equalsIgnoreCase(raw) || !seen.add(id)) throw new IllegalArgumentException("玩家 UUID 无效或重复。");
                Optional<CapeId> cape=version==1 || entry.contains("cape_id") ? Optional.of(new CapeId(string(entry,"cape_id"))) : Optional.empty();
                OutfitSelections outfit=OutfitSelections.original();
                if (entry.contains("outfit_parts")) {
                    if (!(entry.get("outfit_parts") instanceof ListTag parts) || parts.size()>6) throw new IllegalArgumentException("装束部位列表无效或超限。");
                    Set<OutfitPart> used=EnumSet.noneOf(OutfitPart.class);
                    for (Tag partValue:parts) {
                        if (!(partValue instanceof CompoundTag partTag)) throw new IllegalArgumentException("部位必须为复合标签。");
                        fields(partTag,Set.of("part","mode","outfit_id"));
                        OutfitPart part=OutfitPart.fromName(string(partTag,"part"));
                        if (!used.add(part)) throw new IllegalArgumentException("保存部位重复。");
                        String mode=string(partTag,"mode"); OutfitPartSelection selection;
                        if (mode.equals("none") && !partTag.contains("outfit_id")) selection=OutfitPartSelection.NONE;
                        else if (mode.equals("outfit")) selection=OutfitPartSelection.outfit(new OutfitId(string(partTag,"outfit_id")));
                        else throw new IllegalArgumentException("部位模式或 ID 组合不合法。");
                        outfit=outfit.with(part,selection);
                    }
                }
                ArmorSelections armor=ArmorSelections.original();
                if (entry.contains("armor_hidden")) {
                    if (!(entry.get("armor_hidden") instanceof ByteTag armorTag)) throw new IllegalArgumentException("盔甲显示掩码必须为字节。");
                    armor=new ArmorSelections(Byte.toUnsignedInt(armorTag.value()));
                }
                if (entry.contains("armor")) {
                    if (!(entry.get("armor") instanceof ByteArrayTag)) throw new IllegalArgumentException("盔甲三态必须为字节数组。");
                    armor=ArmorSelectionEncoding.decode(entry.getByteArray("armor").orElseThrow());
                }
                var state=new PlayerFashionStoredState(cape,outfit,armor); if (!state.isDefault()) data.states.put(id,state);
            }
            data.setDirty(false); return DataResult.success(data);
        } catch (IllegalArgumentException exception) { return DataResult.error(() -> "玩家时装存档结构不可信，禁止覆盖："+exception.getMessage()); }
    }
    private CompoundTag encode() {
        CompoundTag result=new CompoundTag(); result.putInt("schema_version",DATA_VERSION); ListTag entries=new ListTag();
        states.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(item -> {
            CompoundTag entry=new CompoundTag();entry.putString("uuid",item.getKey().toString());var state=item.getValue();
            state.cape().ifPresent(id -> entry.putString("cape_id",id.value()));ListTag parts=new ListTag();
            for (OutfitPart part:OutfitPart.CANONICAL_ORDER) {
                var selection=state.outfit().get(part);if (selection==OutfitPartSelection.ORIGINAL) continue;
                CompoundTag value=new CompoundTag();value.putString("part",part.serializedName());
                if (selection==OutfitPartSelection.NONE) value.putString("mode","none");
                else { value.putString("mode","outfit");value.putString("outfit_id",((OutfitPartSelection.Outfit)selection).id().value()); }
                parts.add(value);
            }
            if (!parts.isEmpty()) entry.put("outfit_parts",parts);
            if (!state.armor().equals(ArmorSelections.original())) entry.putByteArray("armor",ArmorSelectionEncoding.encode(state.armor()));
            entries.add(entry);
        });
        result.put("entries",entries);return result;
    }
}
