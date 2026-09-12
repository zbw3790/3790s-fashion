package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import dev.zbw3790.fashion.cape.CapeCosmeticMetadata;
import dev.zbw3790.fashion.cape.CapeId;

class WardrobePreviewRotationTest {
	private static final WardrobeLayout.Bounds PREVIEW_BOUNDS =
			new WardrobeLayout.Bounds(10, 20, 100, 160);
	private static final String CAPE_HASH = "a".repeat(64);

	@Test
	void newScreenRotationStartsAtExactBackAngle() {
		WardrobePreviewRotation rotation = new WardrobePreviewRotation();

		assertEquals(180.0F, rotation.yawDegrees());
		assertEquals(
				WardrobePreviewRotation.BACK_FACING_YAW_DEGREES,
				rotation.yawDegrees()
		);
	}

	@Test
	void newScreenRotationIsNotDragging() {
		assertFalse(new WardrobePreviewRotation().isDragging());
	}

	@Test
	void primaryClickInsidePreviewStartsDrag() {
		WardrobePreviewRotation rotation = new WardrobePreviewRotation();

		assertTrue(rotation.beginDrag(10, 20, 0, PREVIEW_BOUNDS));
		assertTrue(rotation.isDragging());
	}

	@Test
	void previewRightAndBottomEdgesAreExclusive() {
		WardrobePreviewRotation rotation = new WardrobePreviewRotation();

		assertFalse(rotation.beginDrag(110, 30, 0, PREVIEW_BOUNDS));
		assertFalse(rotation.beginDrag(30, 180, 0, PREVIEW_BOUNDS));
	}

	@Test
	void primaryClickOutsidePreviewDoesNotStartDrag() {
		WardrobePreviewRotation rotation = new WardrobePreviewRotation();

		assertFalse(rotation.beginDrag(9, 20, 0, PREVIEW_BOUNDS));
		assertFalse(rotation.isDragging());
	}

	@Test
	void secondaryClickInsidePreviewDoesNotStartDrag() {
		WardrobePreviewRotation rotation = new WardrobePreviewRotation();

		assertFalse(rotation.beginDrag(50, 50, 1, PREVIEW_BOUNDS));
		assertFalse(rotation.isDragging());
	}

	@Test
	void dragWithoutPrimaryPressDoesNothing() {
		WardrobePreviewRotation rotation = new WardrobePreviewRotation();

		assertFalse(rotation.drag(0, 30));
		assertEquals(180.0F, rotation.yawDegrees());
	}

	@Test
	void horizontalDeltaRotatesOneDegreePerPixel() {
		WardrobePreviewRotation rotation = draggingRotation();

		assertTrue(rotation.drag(0, 45));
		assertEquals(225.0F, rotation.yawDegrees());
	}

	@Test
	void negativeHorizontalDeltaRotatesInOppositeDirection() {
		WardrobePreviewRotation rotation = draggingRotation();

		rotation.drag(0, -50);

		assertEquals(130.0F, rotation.yawDegrees());
	}

	@Test
	void rotationWrapsAcrossBothFullTurnEdges() {
		WardrobePreviewRotation rotation = draggingRotation();

		rotation.drag(0, 200);
		assertEquals(20.0F, rotation.yawDegrees());
		rotation.drag(0, -50);
		assertEquals(330.0F, rotation.yawDegrees());
	}

	@Test
	void nonPrimaryDragCannotChangeActiveRotation() {
		WardrobePreviewRotation rotation = draggingRotation();

		assertFalse(rotation.drag(1, 90));
		assertEquals(180.0F, rotation.yawDegrees());
		assertTrue(rotation.isDragging());
	}

	@Test
	void primaryReleaseEndsDragAndStopsFurtherMovement() {
		WardrobePreviewRotation rotation = draggingRotation();
		rotation.drag(0, 25);

		assertTrue(rotation.endDrag(0));
		assertFalse(rotation.isDragging());
		assertFalse(rotation.drag(0, 25));
		assertEquals(205.0F, rotation.yawDegrees());
	}

	@Test
	void resizeDoesNotResetScreenLocalRotation() {
		WardrobePreviewRotation rotation = draggingRotation();
		rotation.drag(0, 60);

		WardrobeLayout.calculate(320, 240, 9);
		WardrobeLayout.calculate(854, 480, 9);

		assertEquals(240.0F, rotation.yawDegrees());
	}

	@Test
	void reopenedScreenGetsFreshBackFacingRotation() {
		WardrobePreviewRotation oldScreenRotation = draggingRotation();
		oldScreenRotation.drag(0, 75);

		WardrobePreviewRotation reopenedScreenRotation = new WardrobePreviewRotation();

		assertEquals(255.0F, oldScreenRotation.yawDegrees());
		assertEquals(180.0F, reopenedScreenRotation.yawDegrees());
	}

	@Test
	void draftSelectionAndRotationRemainIndependent() {
		var selection = knownVanillaSession();
		WardrobeCapeCatalog catalog = new WardrobeCapeCatalog(List.of(metadata("alpha")));
		WardrobePreviewRotation rotation = draggingRotation();
		rotation.drag(0, 40);

		selection.select(catalog.pageEntries().get(1).capeId());

		assertEquals(Optional.of(new CapeId("alpha")), selection.draft());
		assertEquals(220.0F, rotation.yawDegrees());
	}

	private static WardrobePreviewRotation draggingRotation() {
		WardrobePreviewRotation rotation = new WardrobePreviewRotation();
		rotation.beginDrag(50, 50, 0, PREVIEW_BOUNDS);
		return rotation;
	}

	private static CapeCosmeticMetadata metadata(String id) {
		return new CapeCosmeticMetadata(new CapeId(id), CAPE_HASH, Optional.empty());
	}
	private static WardrobeSelectionSession knownVanillaSession() {
		var session = new WardrobeSelectionSession();
		session.observe(Optional.of(dev.zbw3790.fashion.fashion.PlayerFashionAuthoritativeState.vanilla()),
				dev.zbw3790.fashion.client.fashion.ClientPlayerFashionRegistry.State.AVAILABLE);
		return session;
	}

}
