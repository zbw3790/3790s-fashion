package vanillafashion.client.screen;

enum WardrobePreviewMode {
	CAPE,
	ELYTRA;

	WardrobePreviewMode next() {
		return this == CAPE ? ELYTRA : CAPE;
	}
}
