package vanillafashion.fashion;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import vanillafashion.cape.CapeId;

/** 仅保存非 Vanilla 选择；所有业务修改通过服务器服务完成。 */
public final class PlayerFashionSavedData extends SavedData {
	public static final int DATA_VERSION = 1;
	public static final int MAX_PERSISTED_PLAYER_FASHION_ENTRIES = 16384;
	public static final Codec<PlayerFashionSavedData> CODEC = CompoundTag.CODEC.flatXmap(
			PlayerFashionSavedData::decode, data -> DataResult.success(data.encode()));
	public static final SavedDataType<PlayerFashionSavedData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("vanilla_fashion", "player_fashion"),
			PlayerFashionSavedData::new, CODEC, null);

	private final Map<UUID, CapeId> selections = new HashMap<>();
	private int rejectedEntryCount;

	public PlayerFashionSavedData() {
	}

	Optional<CapeId> selection(UUID playerId) {
		return Optional.ofNullable(selections.get(playerId));
	}

	Map<UUID, CapeId> snapshot() {
		return Map.copyOf(selections);
	}

	int size() {
		return selections.size();
	}

	int rejectedEntryCount() {
		return rejectedEntryCount;
	}

	boolean setSelection(UUID playerId, Optional<CapeId> selection) {
		if (selection(playerId).equals(selection)) {
			return false;
		}
		if (selection.isPresent()) {
			selections.put(playerId, selection.orElseThrow());
		} else {
			selections.remove(playerId);
		}
		setDirty();
		return true;
	}

	private static DataResult<PlayerFashionSavedData> decode(CompoundTag tag) {
		if (!(tag.get("schema_version") instanceof IntTag)
				|| tag.getIntOr("schema_version", -1) != DATA_VERSION) {
			return DataResult.error(() -> "玩家时装存档版本缺失、类型错误或不受支持，禁止覆盖。");
		}
		if (!(tag.get("entries") instanceof ListTag entries)) {
			return DataResult.error(() -> "玩家时装存档 entries 不是列表，禁止覆盖。");
		}
		// 在解析条目前检查原始数量，超限时不返回截断的 partial result。
		if (entries.size() > MAX_PERSISTED_PLAYER_FASHION_ENTRIES) {
			return DataResult.error(() -> "玩家时装存档条目超过 16384 安全上限，禁止截断或覆盖。");
		}
		PlayerFashionSavedData data = new PlayerFashionSavedData();
		for (Tag entry : entries) {
			try {
				if (!(entry instanceof CompoundTag compound)) {
					throw new IllegalArgumentException("存档条目不是复合标签。");
				}
				String rawUuid = compound.getString("uuid").orElseThrow();
				UUID playerId = UUID.fromString(rawUuid);
				if (!playerId.toString().equalsIgnoreCase(rawUuid)) {
					throw new IllegalArgumentException("UUID 必须使用完整标准格式。");
				}
				CapeId capeId = new CapeId(compound.getString("cape_id").orElseThrow());
				if (data.selections.putIfAbsent(playerId, capeId) != null) {
					data.rejectedEntryCount++;
				}
			} catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
				data.rejectedEntryCount++;
			}
		}
		// 单条坏项的规范化改变了持久化内容，保留全部合法项后才标脏。
		data.setDirty(data.rejectedEntryCount > 0);
		return DataResult.success(data);
	}

	private CompoundTag encode() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("schema_version", DATA_VERSION);
		ListTag entries = new ListTag();
		selections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(selection -> {
			CompoundTag entry = new CompoundTag();
			entry.putString("uuid", selection.getKey().toString());
			entry.putString("cape_id", selection.getValue().value());
			entries.add(entry);
		});
		tag.put("entries", entries);
		return tag;
	}
}
