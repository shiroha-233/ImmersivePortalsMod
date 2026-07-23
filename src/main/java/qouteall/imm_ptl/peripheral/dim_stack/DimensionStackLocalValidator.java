// 本文件统一校验不依赖具体服务器世界的维度堆叠字段与方块注册表引用。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DimensionStackLocalValidator {
    private DimensionStackLocalValidator() {}

    public static Validation validate(DimStackInfo source) {
        DimensionStackPlan plan = DimensionStackPlanner.plan(source);
        List<DimensionStackError> errors = new ArrayList<>(plan.errors());
        for (DimensionStackDefinition.Entry entry : plan.definition().entries()) {
            BlockResult block = parseBedrockReplacement(entry.bedrockReplacement());
            if (!block.valid()) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.INVALID_BEDROCK_BLOCK,
                    "Invalid bedrock replacement '%s' at entry %d".formatted(
                        entry.bedrockReplacement(), entry.index()
                    ),
                    entry.index()
                ));
            }
        }
        return new Validation(plan, errors);
    }

    static BlockResult parseBedrockReplacement(String value) {
        if (value == null || value.isBlank()) {
            return new BlockResult(true, null);
        }
        try {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse(value)
            );
            return block
                .map(found -> new BlockResult(true, found.defaultBlockState()))
                .orElseGet(() -> new BlockResult(false, null));
        }
        catch (RuntimeException exception) {
            return new BlockResult(false, null);
        }
    }

    record BlockResult(boolean valid, @Nullable BlockState blockState) {}

    public record Validation(
        DimensionStackPlan plan,
        List<DimensionStackError> errors
    ) {
        public Validation {
            errors = List.copyOf(errors);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}
