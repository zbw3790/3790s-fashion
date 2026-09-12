package dev.zbw3790.fashion.fashion;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.cape.CapeRegistryKnowledge;

final class FashionTestSupport {
	static final UUID FIRST = new UUID(0, 1);
	static final UUID SECOND = new UUID(0, 2);
	static final CapeId FOUNDER = new CapeId("founder");
	static final CapeId BUILDER = new CapeId("builder");

	private FashionTestSupport() {
	}

	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static SavedDataStorage storage(Path directory) {
		return new SavedDataStorage(directory, DataFixers.getDataFixer(), HolderLookup.Provider.create(Stream.empty()));
	}

	static PlayerFashionSavedData data(Map<UUID, CapeId> entries) {
		PlayerFashionSavedData data = new PlayerFashionSavedData();
		entries.forEach((id, cape) -> data.setSelection(id, Optional.of(cape)));
		data.setDirty(false);
		return data;
	}

	static PlayerFashionService service(PlayerFashionSavedData data, CapeRegistryKnowledge knowledge) {
		return new PlayerFashionService(new PlayerFashionPersistence.LoadResult(data, Optional.empty()), knowledge);
	}

	static CapeRegistryKnowledge valid(Path root) {
		return new CapeRegistryKnowledge(root, true, Set.of(FOUNDER, BUILDER), Set.of(FOUNDER, BUILDER));
	}

	static CompoundTag entry(String uuid, String cape) {
		CompoundTag entry = new CompoundTag();
		entry.putString("uuid", uuid);
		entry.putString("cape_id", cape);
		return entry;
	}

	static CompoundTag encoded(CompoundTag... entries) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("schema_version", 1);
		ListTag list = new ListTag();
		for (CompoundTag entry : entries) {
			list.add(entry);
		}
		tag.put("entries", list);
		return tag;
	}
}
