package vanillafashion.client.screen;

final class WardrobePreviewRotation {
	static final float BACK_FACING_YAW_DEGREES = 180.0F;
	static final float ROTATION_DEGREES_PER_PIXEL = 1.0F;
	static final int PRIMARY_MOUSE_BUTTON = 0;

	private float yawDegrees = BACK_FACING_YAW_DEGREES;
	private boolean dragging;

	boolean beginDrag(
			double mouseX,
			double mouseY,
			int mouseButton,
			WardrobeLayout.Bounds previewBounds
	) {
		if (mouseButton != PRIMARY_MOUSE_BUTTON || !previewBounds.contains(mouseX, mouseY)) {
			return false;
		}

		dragging = true;
		return true;
	}

	boolean drag(int mouseButton, double deltaX) {
		if (!dragging || mouseButton != PRIMARY_MOUSE_BUTTON) {
			return false;
		}

		yawDegrees = wrapToFullTurn(yawDegrees + (float) deltaX * ROTATION_DEGREES_PER_PIXEL);
		return true;
	}

	boolean endDrag(int mouseButton) {
		if (!dragging || mouseButton != PRIMARY_MOUSE_BUTTON) {
			return false;
		}

		dragging = false;
		return true;
	}

	float yawDegrees() {
		return yawDegrees;
	}

	boolean isDragging() {
		return dragging;
	}

	private static float wrapToFullTurn(float degrees) {
		float wrapped = degrees % 360.0F;
		return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
	}
}
