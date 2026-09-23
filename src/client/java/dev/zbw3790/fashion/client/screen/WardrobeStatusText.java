package dev.zbw3790.fashion.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import dev.zbw3790.fashion.cape.CapeId;
import dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry;

/** 两行状态的只读表达；不改变选择会话，也不依赖字体或绘制 API。 */
record WardrobeStatusText(Priority priority, String firstLine, String secondLine, List<String> supplementalLines) {
	WardrobeStatusText {
		Objects.requireNonNull(priority);
		Objects.requireNonNull(firstLine);
		Objects.requireNonNull(secondLine);
		supplementalLines = List.copyOf(supplementalLines);
	}

	WardrobeStatusText(Priority priority, String firstLine, String secondLine) {
		this(priority, firstLine, secondLine, List.of());
	}

	static WardrobeStatusText create(WardrobeSelectionSession selection,
			ClientPlayerFashionRegistry.State snapshotState, boolean channelSupported, boolean outstanding,
			Predicate<CapeId> metadataPresent, CapeWardrobeContent.State contentState, String contentStatus) {
		Objects.requireNonNull(selection);
		Objects.requireNonNull(snapshotState);
		Objects.requireNonNull(metadataPresent);
		Objects.requireNonNull(contentState);
		Objects.requireNonNull(contentStatus);
		String sessionStatus = selection.status(channelSupported, outstanding, metadataPresent);
		String contentText = switch (contentState) {
			case READY -> "";
			case EMPTY -> WardrobeText.string("cape.empty");
			case LOADING -> contentStatus.isEmpty() ? WardrobeText.string("cape.registry_loading") : contentStatus;
			case ERROR -> contentStatus.isEmpty() ? WardrobeText.string("cape.registry_unavailable") : contentStatus;
		};
		Priority priority;
		String status;
		// 优先级决定实际第二行；被覆盖的会话提示仍通过完整文本和旁白提供。
		if (selection.hasError()) {
			priority = Priority.ERROR;
			status = sessionStatus;
		} else if (selection.v2() && selection.waiting()) {
            priority=Priority.PENDING;status=selection.pendingStatus();
        } else if (contentState == CapeWardrobeContent.State.ERROR) {
			priority = Priority.ERROR;
			status = contentText;
		} else if (snapshotState == ClientPlayerFashionRegistry.State.UNAVAILABLE) {
			priority = Priority.ERROR;
			status = WardrobeText.string("status.authority_unavailable");
		} else if (!channelSupported) {
			priority = Priority.ERROR;
			status = WardrobeText.string("status.save_unsupported");
		} else if (selection.pendingRequestId() != 0) {
			priority = Priority.PENDING;
			status = sessionStatus;
		} else if (outstanding) {
			priority = Priority.PENDING;
			status = WardrobeText.string("status.previous_pending");
		} else if (selection.dormant() && selection.draft().equals(selection.baseline())) {
			priority = Priority.DORMANT;
			status = sessionStatus;
		} else if (!selection.authorityKnown() || snapshotState != ClientPlayerFashionRegistry.State.AVAILABLE) {
			priority = Priority.LOADING;
			status = WardrobeText.string("status.authority_loading");
		} else if (selection.draft().filter(metadataPresent.negate()).isPresent()) {
			priority = Priority.LOADING;
			status = sessionStatus;
		} else if (contentState == CapeWardrobeContent.State.LOADING) {
			priority = Priority.LOADING;
			status = contentText;
		} else {
			priority = Priority.NORMAL;
			status = sessionStatus.isEmpty() ? contentText : sessionStatus;
		}
		var supplementalLines = new ArrayList<String>(selection.supplementalStatus());
		if (!sessionStatus.isEmpty() && !sessionStatus.equals(status)) {
			supplementalLines.add(sessionStatus);
		}
		if (contentState == CapeWardrobeContent.State.ERROR && !contentText.equals(status)
				&& !supplementalLines.contains(contentText)) {
			supplementalLines.add(contentText);
		}
		return new WardrobeStatusText(priority, selection.selectionLabel(), status, supplementalLines);
	}

	List<String> fullText() {
		var lines = new ArrayList<String>();
		lines.add(firstLine);
		if (!secondLine.isEmpty()) {
			lines.add(secondLine);
		}
		lines.addAll(supplementalLines);
		return List.copyOf(lines);
	}

	String narration() {
		return String.join(WardrobeText.string("separator"), fullText());
	}

	Display clip(int maxWidth, ToIntFunction<String> width) {
		return new Display(fit(firstLine, maxWidth, width), fit(secondLine, maxWidth, width));
	}

	/** 按调用方提供的像素宽度省略，完整文本始终保留在原模型中。 */
	static String fit(String text, int maxWidth, ToIntFunction<String> width) {
		Objects.requireNonNull(text);
		Objects.requireNonNull(width);
		if (maxWidth <= 0 || text.isEmpty()) {
			return "";
		}
		if (width.applyAsInt(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		if (width.applyAsInt(ellipsis) > maxWidth) {
			return "";
		}
		int end = text.length();
		while (end > 0) {
			end = text.offsetByCodePoints(end, -1);
			String candidate = text.substring(0, end) + ellipsis;
			if (width.applyAsInt(candidate) <= maxWidth) {
				return candidate;
			}
		}
		return ellipsis;
	}

	enum Priority { ERROR, PENDING, DORMANT, LOADING, NORMAL }

	record Display(String firstLine, String secondLine) { }
}
