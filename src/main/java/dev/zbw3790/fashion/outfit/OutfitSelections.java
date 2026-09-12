package dev.zbw3790.fashion.outfit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.EnumSet;

/** 完整六部位不可变值；分组只是编辑目标，不另存一份状态。 */
public final class OutfitSelections {
	private final List<OutfitPartSelection> values;

	private OutfitSelections(List<OutfitPartSelection> values) {
		if (values.size() != OutfitPart.CANONICAL_ORDER.size()) {
			throw new IllegalArgumentException("装束选择必须完整包含六个部位。");
		}
		this.values = List.copyOf(values);
	}

	public static OutfitSelections original() {
		return new OutfitSelections(OutfitPart.CANONICAL_ORDER.stream()
				.map(part -> OutfitPartSelection.ORIGINAL).toList());
	}

	public static OutfitSelections from(Map<OutfitPart, OutfitPartSelection> selections) {
		Objects.requireNonNull(selections, "选择映射不能为 null。");
		if (!selections.keySet().equals(OutfitPart.ALL)) {
			throw new IllegalArgumentException("选择映射不能缺少部位或包含额外键。");
		}
		return new OutfitSelections(OutfitPart.CANONICAL_ORDER.stream().map(selections::get).toList());
	}

	public OutfitPartSelection get(OutfitPart part) {
		return values.get(Objects.requireNonNull(part, "部位不能为 null。").ordinal());
	}

	public OutfitSelections with(OutfitPart part, OutfitPartSelection selection) {
		return set(Set.of(part), selection).selections();
	}

	public Edit applyOutfit(Set<OutfitPart> targets, OutfitId id, Set<OutfitPart> provided) {
		Set<OutfitPart> safeTargets = OutfitPart.immutableSet(targets);
		Set<OutfitPart> safeProvided = OutfitPart.immutableSet(provided);
		if (safeProvided.isEmpty()) throw new IllegalArgumentException("可信提供集合不能为空。");
		EnumSet<OutfitPart> intersection = EnumSet.noneOf(OutfitPart.class);
		intersection.addAll(safeTargets);
		intersection.retainAll(safeProvided);
		return set(intersection, OutfitPartSelection.outfit(id));
	}

	public Edit set(Set<OutfitPart> targets, OutfitPartSelection selection) {
		Objects.requireNonNull(selection, "部位选择不能为 null。");
		Set<OutfitPart> safeTargets = OutfitPart.immutableSet(targets);
		List<OutfitPartSelection> changedValues = new ArrayList<>(values);
		EnumSet<OutfitPart> changedParts = EnumSet.noneOf(OutfitPart.class);
		for (OutfitPart part : safeTargets) {
			if (!get(part).equals(selection)) {
				changedValues.set(part.ordinal(), selection);
				changedParts.add(part);
			}
		}
		return new Edit(changedParts.isEmpty() ? this : new OutfitSelections(changedValues), changedParts);
	}

	public Edit allOriginal() { return set(OutfitPart.ALL, OutfitPartSelection.ORIGINAL); }
	public Edit allNone() { return set(OutfitPart.ALL, OutfitPartSelection.NONE); }

	public Summary summarize(Set<OutfitPart> group) {
		Set<OutfitPart> safe = OutfitPart.immutableSet(group);
		if (safe.isEmpty()) throw new IllegalArgumentException("摘要目标不能为空。");
		OutfitPartSelection first = get(safe.iterator().next());
		return safe.stream().allMatch(part -> get(part).equals(first)) ? new Uniform(first) : Mixed.INSTANCE;
	}

	public record Edit(OutfitSelections selections, Set<OutfitPart> changedParts) {
		public Edit {
			Objects.requireNonNull(selections, "编辑结果不能为 null。");
			changedParts = OutfitPart.immutableSet(changedParts);
		}
		public boolean changed() { return !changedParts.isEmpty(); }
	}
	public sealed interface Summary permits Uniform, Mixed {}
	public record Uniform(OutfitPartSelection selection) implements Summary {
		public Uniform { Objects.requireNonNull(selection, "统一摘要必须有选择。"); }
	}
	public enum Mixed implements Summary { INSTANCE }

	@Override public boolean equals(Object other) {
		return other instanceof OutfitSelections selections && values.equals(selections.values);
	}
	@Override public int hashCode() { return values.hashCode(); }
	@Override public String toString() { return values.toString(); }
}
