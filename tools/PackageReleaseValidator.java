import java.nio.file.Path;

import dev.zbw3790.fashion.cape.CapeCosmeticDefinition;
import dev.zbw3790.fashion.cape.CapeCosmeticValidationResult;
import dev.zbw3790.fashion.cape.CapeCosmeticValidator;

/** 供发布打包脚本调用，直接复用生产 Cape Cosmetic Validator。 */
public final class PackageReleaseValidator {
    private PackageReleaseValidator() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("用法：PackageReleaseValidator <模板目录> <预期布局>");
        }

        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        String expectedLayout = args[1];
        CapeCosmeticValidationResult result = new CapeCosmeticValidator().validate(directory);

        if (!result.isSuccess()) {
            throw new IllegalArgumentException("模板未通过正式 CapeCosmeticValidator：" + result.issues());
        }

        CapeCosmeticDefinition definition = result.definition().orElseThrow();
        String actualLayout = classify(definition);
        if (!expectedLayout.equals(actualLayout)) {
            throw new IllegalArgumentException(
                    "模板布局不符：预期 " + expectedLayout + "，实际 " + actualLayout + "。"
            );
        }

        System.out.println("正式 Validator 通过：" + definition.id().value() + "，布局=" + actualLayout);
    }

    private static String classify(CapeCosmeticDefinition definition) {
        String capeName = definition.cape().source().getFileName().toString();
        if (definition.elytra().isEmpty()) {
            return capeName.equals("cape.png") ? "CAPE_ONLY" : "UNKNOWN";
        }

        String elytraName = definition.elytra().orElseThrow().source().getFileName().toString();
        if (capeName.equals("cape_elytra.png") && elytraName.equals("cape_elytra.png")
                && definition.cape().source().equals(definition.elytra().orElseThrow().source())) {
            return "SHARED";
        }
        if (capeName.equals("cape.png") && elytraName.equals("elytra.png")) {
            return "SPLIT";
        }
        return "UNKNOWN";
    }
}
