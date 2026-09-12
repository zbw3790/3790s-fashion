package dev.zbw3790.fashion.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import dev.zbw3790.fashion.outfit.OutfitModel;
import dev.zbw3790.fashion.outfit.OutfitPart;

/** 固定正面二维投影；只引用现有外层纹理，不持有图像、模型或 GPU 资源。 */
final class OutfitThumbnail {
    record Face(OutfitPart part, int x, int y, int width, int height, int u, int v) { }
    record Plan(List<Face> faces) {
        Plan { faces = List.copyOf(faces); }
    }

    private OutfitThumbnail() { }

    static Plan plan(Set<OutfitPart> provided, OutfitModel model) {
        List<Face> faces = new ArrayList<>(6);
        if (model != null) {
            for (OutfitPart part : OutfitPart.CANONICAL_ORDER) {
                if (provided.contains(part)) faces.add(front(part, model));
            }
        }
        return new Plan(faces);
    }

    private static Face front(OutfitPart part, OutfitModel model) {
        int armWidth = model == OutfitModel.SLIM ? 3 : 4;
        // 与 PLAYER / PLAYER_SLIM 外层 Cube.NORTH 的法向及四角 UV 对照；左右按玩家自身定义。
        // 目的坐标已包含二十像素槽内图与十六像素正面投影之间的两像素水平留白。
        return switch (part) {
            case HEAD -> new Face(part, 6, 0, 8, 8, 40, 8);
            case BODY -> new Face(part, 6, 8, 8, 12, 20, 36);
            case RIGHT_ARM -> new Face(part, 6 - armWidth, 8, armWidth, 12, 44, 36);
            case LEFT_ARM -> new Face(part, 14, 8, armWidth, 12, 52, 52);
            case RIGHT_LEG -> new Face(part, 6, 20, 4, 12, 4, 36);
            case LEFT_LEG -> new Face(part, 10, 20, 4, 12, 4, 52);
        };
    }
}
