package vanillafashion.outfit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static vanillafashion.outfit.OutfitDiagnostic.Code.*;

public final class OutfitRegistryLoader {
	private final int maxDirectoryEntries;
	private final OutfitFileAccess access;

	/** 目录枚举预算与 S03 的 256 个可信定义上限分别检查。 */
	public OutfitRegistryLoader(int maxDirectoryEntries) { this(maxDirectoryEntries, new OutfitFileAccess()); }
	OutfitRegistryLoader(int maxDirectoryEntries, OutfitFileAccess access) {
		if (maxDirectoryEntries < 1 || maxDirectoryEntries == Integer.MAX_VALUE) {
			throw new IllegalArgumentException("必须提供有限且为正的目录枚举预算。");
		}
		this.maxDirectoryEntries = maxDirectoryEntries;
		this.access = Objects.requireNonNull(access);
	}
	public static Path rootUnder(Path configDirectory) {
		return configDirectory.resolve("vanilla-fashion").resolve("outfits");
	}

	public OutfitRegistryLoadResult load(Path rootPath) {
		Objects.requireNonNull(rootPath, "装束根目录不能为 null。");
		List<OutfitDiagnostic> diagnostics = new ArrayList<>();
		Set<OutfitId> known = new HashSet<>();
		List<OutfitRegistryEntry> entries = new ArrayList<>();
		OutfitFileAccess.Stamp root;
		List<Path> children;
		try {
			root = access.root(rootPath);
			children = access.children(root, maxDirectoryEntries);
		} catch (IOException | SecurityException exception) {
			diagnostics.add(new OutfitDiagnostic("", "root", exception instanceof OutfitFileAccess.Failure
					? OutfitFileAccess.reason(exception) : ROOT_UNAVAILABLE));
			return result(null, false, known, entries, diagnostics);
		}
		for (Path child : children) {
			String name = child.getFileName().toString();
			OutfitId id;
			try { id = new OutfitId(name); }
			catch (IllegalArgumentException exception) {
				diagnostics.add(new OutfitDiagnostic(name, "directory", INVALID_ID)); continue;
			}
			known.add(id);
			OutfitFileAccess.Stamp directory;
			Map<String, Path> files = new HashMap<>();
			try {
				directory = access.directory(child, root);
				for (Path path : access.children(directory, maxDirectoryEntries)) files.put(path.getFileName().toString(), path);
			} catch (IOException | SecurityException exception) {
				diagnostics.add(new OutfitDiagnostic(name, "directory", OutfitFileAccess.reason(exception))); continue;
			}
			OutfitMetadata metadata;
			try {
				Path metadataPath = required(files, "outfit.json");
				metadata = OutfitMetadataParser.parse(access.read(metadataPath, directory, root, OutfitMetadataParser.MAX_METADATA_BYTES));
			} catch (IOException | SecurityException exception) {
				diagnostics.add(new OutfitDiagnostic(name, "outfit.json", OutfitFileAccess.reason(exception))); continue;
			} catch (IllegalArgumentException exception) {
				diagnostics.add(new OutfitDiagnostic(name, "outfit.json", INVALID_METADATA)); continue;
			}
			if (entries.size() >= OutfitRegistrySnapshot.MAX_OUTFITS) {
				diagnostics.add(new OutfitDiagnostic("", "root", SCAN_LIMIT));
				return result(root, false, known, List.of(), diagnostics);
			}
			Map<OutfitModel, OutfitModelResource> models = new EnumMap<>(OutfitModel.class);
			for (OutfitModel model : OutfitModel.CANONICAL_ORDER) {
				if (!metadata.models().contains(model)) {
					if (files.containsKey(model.fileName())) diagnostics.add(new OutfitDiagnostic(name, model.fileName(), UNDECLARED_MODEL_FILE));
					continue;
				}
				OutfitDiagnostic.Code failure = null;
				try {
					byte[] bytes = access.read(required(files, model.fileName()), directory, root, OutfitPngValidator.MAX_ASSET_BYTES);
					OutfitPngValidator.Result validation = OutfitPngValidator.validate(bytes);
					if (validation == OutfitPngValidator.Result.VALID) {
						models.put(model, new OutfitModelResource.Valid(OutfitAsset.fromBytes(bytes)));
					} else {
						failure = switch (validation) {
							case TOO_LARGE -> TOO_LARGE;
							case INVALID_DIMENSIONS -> INVALID_DIMENSIONS;
							default -> INVALID_PNG;
						};
					}
				} catch (IOException | SecurityException exception) { failure = OutfitFileAccess.reason(exception); }
				if (failure != null) {
					models.put(model, new OutfitModelResource.Invalid(failure));
					diagnostics.add(new OutfitDiagnostic(name, model.fileName(), failure));
				}
			}
			try { access.check(directory, true); access.check(root, false); }
			catch (IOException | SecurityException exception) {
				diagnostics.add(new OutfitDiagnostic(name, "directory", OutfitFileAccess.reason(exception))); continue;
			}
			entries.add(new OutfitRegistryEntry(id, metadata, models));
		}
		try { access.check(root, true); }
		catch (IOException | SecurityException exception) {
			diagnostics.add(new OutfitDiagnostic("", "root", CHANGED_DURING_READ));
			return result(root, false, known, List.of(), diagnostics);
		}
		return result(root, true, known, entries, diagnostics);
	}
	private static Path required(Map<String, Path> files, String name) throws OutfitFileAccess.Failure {
		Path path = files.get(name);
		if (path == null) throw new OutfitFileAccess.Failure(MISSING_FILE);
		return path;
	}
	private OutfitRegistryLoadResult result(OutfitFileAccess.Stamp root, boolean trustworthy, Set<OutfitId> known,
			List<OutfitRegistryEntry> entries, List<OutfitDiagnostic> diagnostics) {
		OutfitRegistry registry = new OutfitRegistry(entries);
		return new OutfitRegistryLoadResult(registry, new OutfitRegistryKnowledge(access, root, trustworthy, known, registry),
				OutfitAssetIndex.from(registry), diagnostics);
	}
}
