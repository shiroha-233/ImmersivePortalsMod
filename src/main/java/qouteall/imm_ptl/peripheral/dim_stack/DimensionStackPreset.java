// 本文件负责维度堆叠 preset 的 UTF-8 JSON 配置读写。
package qouteall.imm_ptl.peripheral.dim_stack;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

public final class DimensionStackPreset {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DimensionStackPreset() {}

    @Nullable
    public static DimStackInfo load() {
        DimStackInfo preset = loadRaw();
        if (preset == null) {
            return null;
        }
        DimensionStackLocalValidator.Validation validation =
            DimensionStackLocalValidator.validate(preset);
        if (!validation.isValid()) {
            LOGGER.error(
                "Ignoring invalid dimension stack preset: {}",
                validation.errors().get(0).message()
            );
            return null;
        }
        return validation.plan().definition().toInfo();
    }

    @Nullable
    static DimStackInfo loadRaw() {
        JsonObject json = IPConfig.getConfig().dimStackPreset;
        if (json == null) {
            return null;
        }

        try {
            return IPGlobal.gson.fromJson(json, DimStackInfo.class);
        }
        catch (RuntimeException exception) {
            LOGGER.error("Cannot parse dimension stack preset JSON {}", json, exception);
            return null;
        }
    }

    public static void save(@Nullable DimStackInfo preset) {
        if (preset != null) {
            DimensionStackLocalValidator.Validation validation =
                DimensionStackLocalValidator.validate(preset);
            if (!validation.isValid()) {
                LOGGER.error(
                    "Refusing to save invalid dimension stack preset: {}",
                    validation.errors().get(0).message()
                );
                return;
            }
            preset = validation.plan().definition().toInfo();
        }
        IPConfig config = IPConfig.getConfig();
        config.dimStackPreset = preset == null
            ? null
            : IPGlobal.gson.toJsonTree(preset).getAsJsonObject();
        config.saveConfigFile();
    }
}
