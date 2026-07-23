// 本文件在世界创建前校验维度堆叠所需的注册表数据与基岩替换策略。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import qouteall.q_misc_util.Helper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DimensionStackRegistryValidator {
    private DimensionStackRegistryValidator() {}

    public static Validation validate(
        MinecraftServer server,
        DimensionStackPlan plan
    ) {
        List<DimensionStackError> errors = new java.util.ArrayList<>(plan.errors());
        Map<ResourceKey<Level>, BlockState> replacements = new LinkedHashMap<>();
        Registry<LevelStem> levelStems = server.registryAccess()
            .registryOrThrow(Registries.LEVEL_STEM);

        for (DimensionStackDefinition.Entry entry : plan.definition().entries()) {
            ResourceLocation dimensionId;
            try {
                dimensionId = ResourceLocation.parse(entry.dimensionId());
            }
            catch (RuntimeException exception) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.INVALID_DIMENSION_ID,
                    "Invalid dimension id at entry " + entry.index(),
                    entry.index()
                ));
                continue;
            }

            LevelStem levelStem = levelStems.get(ResourceKey.create(
                Registries.LEVEL_STEM, dimensionId
            ));
            if (levelStem == null) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.MISSING_DIMENSION,
                    "Missing dimension " + entry.dimensionId(),
                    entry.index()
                ));
                continue;
            }

            DimensionType dimensionType = levelStem.type().value();
            long maxYLong = (long) dimensionType.minY() + dimensionType.logicalHeight();
            int minY = dimensionType.minY();
            int maxY = maxYLong > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) maxYLong;
            int requestedMinY = entry.bottomY() != null ? entry.bottomY() : minY;
            int requestedMaxY = entry.topY() != null ? entry.topY() : maxY;
            if (maxYLong > Integer.MAX_VALUE
                || requestedMinY < minY
                || requestedMaxY > maxY
                || requestedMinY >= requestedMaxY
            ) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.HEIGHT_OUT_OF_RANGE,
                    "Height range [%d, %d) is outside %s [%d, %d)".formatted(
                        requestedMinY, requestedMaxY, entry.dimensionId(), minY, maxY
                    ),
                    entry.index()
                ));
            }

            DimensionStackLocalValidator.BlockResult parsed =
                DimensionStackLocalValidator.parseBedrockReplacement(
                    entry.bedrockReplacement()
                );
            if (!parsed.valid()) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.INVALID_BEDROCK_BLOCK,
                    "Invalid bedrock replacement '%s' at entry %d".formatted(
                        entry.bedrockReplacement(), entry.index()
                    ),
                    entry.index()
                ));
                continue;
            }

            ResourceKey<Level> dimension = Helper.dimIdToKey(dimensionId);
            if (replacements.containsKey(dimension)
                && !Objects.equals(replacements.get(dimension), parsed.blockState())
            ) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.CONFLICTING_BEDROCK_REPLACEMENT,
                    "Conflicting bedrock replacements for " + dimensionId,
                    entry.index()
                ));
            }
            else {
                replacements.put(dimension, parsed.blockState());
            }
        }

        if (!errors.isEmpty()) {
            return Validation.failure(errors);
        }
        return Validation.success(replacements);
    }

    public record Validation(
        Map<ResourceKey<Level>, BlockState> bedrockReplacements,
        List<DimensionStackError> errors
    ) {
        public Validation {
            bedrockReplacements = Collections.unmodifiableMap(
                new LinkedHashMap<>(bedrockReplacements)
            );
            errors = List.copyOf(errors);
        }

        public static Validation success(Map<ResourceKey<Level>, BlockState> replacements) {
            return new Validation(replacements, List.of());
        }

        public static Validation failure(List<DimensionStackError> errors) {
            return new Validation(Map.of(), errors);
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }
    }
}
