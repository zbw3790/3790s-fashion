package dev.zbw3790.fashion.client.screen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import dev.zbw3790.fashion.outfit.*;
import dev.zbw3790.fashion.client.render.WardrobePreviewTestSupport;

class OutfitThumbnailTest {
    @BeforeAll static void bootstrap() { WardrobePreviewTestSupport.bootstrap(); }

    @ParameterizedTest @EnumSource(OutfitModel.class)
    void fixedUvMatchesNorthNormalAndEveryOrientedCornerOfActualBakedOuterFace(OutfitModel model) {
        var polygons = fronts(model);
        for (var face : OutfitThumbnail.plan(OutfitPart.ALL, model).faces()) {
            var polygon = polygons.get(face.part());
            assertEquals(0, polygon.normal().x());
            assertEquals(0, polygon.normal().y());
            assertEquals(-1, polygon.normal().z());
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (var vertex : polygon.vertices()) {
                minX = Math.min(minX, vertex.x()); maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y()); maxY = Math.max(maxY, vertex.y());
            }
            Set<List<Integer>> corners = new HashSet<>();
            for (var vertex : polygon.vertices()) {
                int dx = vertex.x() == minX ? 0 : face.width();
                int dy = vertex.y() == minY ? 0 : face.height();
                assertTrue(vertex.x() == minX || vertex.x() == maxX);
                assertTrue(vertex.y() == minY || vertex.y() == maxY);
                assertEquals(face.u() + dx, vertex.u() * 64, 0.0001, face.part().toString());
                assertEquals(face.v() + dy, vertex.v() * 64, 0.0001, face.part().toString());
                corners.add(List.of(dx, dy));
            }
            assertEquals(4, corners.size());
        }
    }

    @ParameterizedTest @EnumSource(OutfitModel.class)
    void everyProvidedSubsetHasExactPositionsNoOverlapAndNoInventedPart(OutfitModel model) {
        for (int mask = 0; mask < 64; mask++) {
            var provided = EnumSet.noneOf(OutfitPart.class);
            for (var part : OutfitPart.values()) if ((mask & (1 << part.ordinal())) != 0) provided.add(part);
            var plan = OutfitThumbnail.plan(provided, model);
            assertEquals(provided.size(), plan.faces().size());
            boolean[][] occupied = new boolean[32][20];
            for (var face : plan.faces()) {
                assertTrue(provided.contains(face.part()));
                assertEquals(List.of(expected(face.part(), model)[0], expected(face.part(), model)[1],
                                expected(face.part(), model)[2], expected(face.part(), model)[3]),
                        List.of(face.x(), face.y(), face.width(), face.height()));
                assertTrue(face.u() >= 0 && face.u() + face.width() <= 64);
                assertTrue(face.v() >= 0 && face.v() + face.height() <= 64);
                for (int y = face.y(); y < face.y() + face.height(); y++)
                    for (int x = face.x(); x < face.x() + face.width(); x++) {
                        assertTrue(x >= 2 && x < 18 && y >= 0 && y < 32);
                        assertFalse(occupied[y][x]); occupied[y][x] = true;
                    }
            }
            assertEquals(plan, OutfitThumbnail.plan(provided, model));
            assertThrows(UnsupportedOperationException.class, () -> plan.faces().clear());
        }
    }

    @ParameterizedTest @EnumSource(OutfitModel.class)
    void asymmetricRgbaFrontPixelsSurviveEverySubsetAndBaseBackSidePoisonIsNeverRead(OutfitModel model) {
        var polygons = fronts(model);
        int[][] texture = new int[64][64];
        // 所有非 FRONT 区域（含 Base、背面、侧面、顶底）均放毒色。
        for (var row : texture) Arrays.fill(row, 0xffff00ff);
        for (var part : OutfitPart.values()) {
            int[] uv = uvBounds(polygons.get(part));
            for (int y = 0; y < uv[3]; y++) for (int x = 0; x < uv[2]; x++)
                texture[uv[1] + y][uv[0] + x] = pixel(part, x, y);
        }
        for (int mask = 1; mask < 64; mask++) {
            var provided = EnumSet.noneOf(OutfitPart.class);
            for (var part : OutfitPart.values()) if ((mask & (1 << part.ordinal())) != 0) provided.add(part);
            int[][] actual = raster(OutfitThumbnail.plan(provided, model), texture);
            int[][] expected = new int[32][20];
            for (var part : provided) {
                int[] box = expected(part, model);
                for (int y = 0; y < box[3]; y++) for (int x = 0; x < box[2]; x++)
                    expected[box[1] + y][box[0] + x] = pixel(part, x, y);
            }
            for (int y = 0; y < 32; y++) assertArrayEquals(expected[y], actual[y], "逐像素保持方向、缺部位空白与 RGBA");
        }
    }

    @ParameterizedTest @EnumSource(OutfitModel.class)
    void transparentFrontStaysEmptyEvenWhenOtherFacesAreOpaqueAndPartRemainsProvided(OutfitModel model) {
        int[][] texture = new int[64][64];
        for (var row : texture) Arrays.fill(row, 0xffff00ff);
        for (var polygon : fronts(model).values()) {
            int[] uv = uvBounds(polygon);
            for (int y = uv[1]; y < uv[1] + uv[3]; y++) Arrays.fill(texture[y], uv[0], uv[0] + uv[2], 0x00123456);
        }
        var plan = OutfitThumbnail.plan(OutfitPart.ALL, model);
        assertEquals(6, plan.faces().size());
        for (var row : raster(plan, texture)) for (int pixel : row) assertEquals(0, pixel >>> 24);
        assertTrue(OutfitThumbnail.plan(OutfitPart.ALL, null).faces().isEmpty());
    }

    @Test void gridHasOnlyTwoDimensionalTextureSubmissionAndNoLegacySampleOrResourceOwner() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle"))) root = Objects.requireNonNull(root.getParent());
        String widget = Files.readString(root.resolve("src/client/java/dev/zbw3790/fashion/client/screen/OutfitGridEntryWidget.java"));
        String plan = Files.readString(root.resolve("src/client/java/dev/zbw3790/fashion/client/screen/OutfitThumbnail.java"));
        for (String forbidden : List.of("PlayerEntityRenderer", "AvatarRenderState", "OutfitRendering", "ModelPart",
                "WardrobePlayerPreviewRenderer", "DynamicTexture", "TextureManager", "NativeImage", "ImageIO",
                "REPRESENTATIVES", "markers()", "sampleTransparent", "graphics.text", "content.scope()")) {
            assertFalse((widget + plan).contains(forbidden), forbidden);
        }
        assertEquals(1, widget.split("graphics\\.blit", -1).length - 1);
        assertTrue(widget.contains("face.width(),face.height(),face.width(),face.height(),64,64"));
        assertTrue(widget.contains("content.source.texture(id).ifPresent"));
    }

    private static int pixel(OutfitPart part, int x, int y) {
        int alpha = new int[]{0, 64, 128, 255}[(x + y) % 4];
        return alpha << 24 | (part.ordinal() + 1) << 16 | x << 8 | y;
    }
    private static int[] expected(OutfitPart part, OutfitModel model) {
        return switch (part) {
            case HEAD -> new int[]{6, 0, 8, 8};
            case BODY -> new int[]{6, 8, 8, 12};
            case RIGHT_ARM -> model == OutfitModel.SLIM ? new int[]{3, 8, 3, 12} : new int[]{2, 8, 4, 12};
            case LEFT_ARM -> model == OutfitModel.SLIM ? new int[]{14, 8, 3, 12} : new int[]{14, 8, 4, 12};
            case RIGHT_LEG -> new int[]{6, 20, 4, 12};
            case LEFT_LEG -> new int[]{10, 20, 4, 12};
        };
    }
    private static int[][] raster(OutfitThumbnail.Plan plan, int[][] texture) {
        int[][] image = new int[32][20];
        for (var face : plan.faces()) for (int y = 0; y < face.height(); y++) for (int x = 0; x < face.width(); x++)
            image[face.y() + y][face.x() + x] = texture[face.v() + y][face.u() + x];
        return image;
    }
    private static int[] uvBounds(ModelPart.Polygon polygon) {
        float minU = 1, minV = 1, maxU = 0, maxV = 0;
        for (var vertex : polygon.vertices()) {
            minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u());
            minV = Math.min(minV, vertex.v()); maxV = Math.max(maxV, vertex.v());
        }
        return new int[]{Math.round(minU * 64), Math.round(minV * 64), Math.round((maxU - minU) * 64), Math.round((maxV - minV) * 64)};
    }
    private static Map<OutfitPart, ModelPart.Polygon> fronts(OutfitModel model) {
        var baked = new PlayerModel(EntityModelSet.vanilla().bakeLayer(model == OutfitModel.SLIM ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), model == OutfitModel.SLIM);
        Map<OutfitPart, ModelPart.Polygon> result = new EnumMap<>(OutfitPart.class);
        for (var part : OutfitPart.values()) {
            ModelPart outer = switch (part) {
                case HEAD -> baked.hat; case BODY -> baked.jacket;
                case LEFT_ARM -> baked.leftSleeve; case RIGHT_ARM -> baked.rightSleeve;
                case LEFT_LEG -> baked.leftPants; case RIGHT_LEG -> baked.rightPants;
            };
            outer.visit(new PoseStack(), (pose, path, index, cube) -> {
                for (var polygon : cube.polygons) if (polygon.normal().z() == -1) {
                    assertNull(result.put(part, polygon), "每个部位只有一个正面");
                    float frontZ = Float.MAX_VALUE;
                    for (var side : cube.polygons) for (var vertex : side.vertices()) frontZ = Math.min(frontZ, vertex.z());
                    for (var vertex : polygon.vertices()) assertEquals(frontZ, vertex.z(), 0.0001);
                }
            });
        }
        assertEquals(6, result.size()); return result;
    }
}
