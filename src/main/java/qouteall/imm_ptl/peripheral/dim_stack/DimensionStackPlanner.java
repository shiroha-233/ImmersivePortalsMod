// 本文件以纯函数方式把维度堆叠定义编译为连接计划。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DimensionStackPlanner {
    public static final int MAX_ENTRY_COUNT = 64;

    private DimensionStackPlanner() {}

    public static DimensionStackPlan plan(DimStackInfo source) {
        if (source == null) {
            return plan(new DimensionStackDefinition(List.of(), false, false));
        }
        return plan(DimensionStackDefinition.copyOf(source));
    }

    public static DimensionStackPlan plan(DimensionStackDefinition definition) {
        List<DimensionStackError> errors = new ArrayList<>();
        List<DimensionStackPlan.Connection> connections = new ArrayList<>();
        Map<DimensionStackPlan.Endpoint, Integer> endpointUseCounts = new LinkedHashMap<>();

        if (definition.entries().isEmpty()) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.EMPTY_STACK,
                "Dimension stack must contain at least one entry"
            ));
        }
        if (definition.entries().size() > MAX_ENTRY_COUNT) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.TOO_MANY_ENTRIES,
                "Dimension stack cannot contain more than " + MAX_ENTRY_COUNT + " entries"
            ));
        }

        for (DimensionStackDefinition.Entry entry : definition.entries()) {
            validateEntry(entry, errors);
        }

        for (int index = 0; index + 1 < definition.entries().size(); index++) {
            addConnection(
                definition, index, index, index + 1,
                connections, endpointUseCounts, errors
            );
        }
        if (definition.loop() && !definition.entries().isEmpty()) {
            int lastIndex = definition.entries().size() - 1;
            addConnection(
                definition,
                connections.size(),
                lastIndex,
                0,
                connections,
                endpointUseCounts,
                errors
            );
        }

        endpointUseCounts.forEach((endpoint, count) -> {
            if (count > 1) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.ENDPOINT_CONFLICT,
                    "Multiple connections use %s %s"
                        .formatted(endpoint.dimensionId(), endpoint.face())
                ));
            }
        });

        return new DimensionStackPlan(definition, connections, endpointUseCounts, errors);
    }

    private static void validateEntry(
        DimensionStackDefinition.Entry entry,
        List<DimensionStackError> errors
    ) {
        if (entry.dimensionId() == null || entry.dimensionId().isBlank()) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.EMPTY_DIMENSION_ID,
                "Dimension id is missing at entry " + entry.index(),
                entry.index()
            ));
        }
        else {
            try {
                ResourceLocation.parse(entry.dimensionId());
            }
            catch (RuntimeException exception) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.INVALID_DIMENSION_ID,
                    "Invalid dimension id at entry " + entry.index(),
                    entry.index()
                ));
            }
        }
        if (!Double.isFinite(entry.scale()) || entry.scale() <= 0) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.INVALID_SCALE,
                "Scale must be finite and positive at entry " + entry.index(),
                entry.index()
            ));
        }
        if (!Double.isFinite(entry.horizontalRotation())) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.INVALID_ROTATION,
                "Horizontal rotation must be finite at entry " + entry.index(),
                entry.index()
            ));
        }
        if (entry.bottomY() != null && entry.topY() != null && entry.bottomY() >= entry.topY()) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.INVALID_HEIGHT_RANGE,
                "Bottom Y must be lower than top Y at entry " + entry.index(),
                entry.index()
            ));
        }
    }

    private static void addConnection(
        DimensionStackDefinition definition,
        int edgeIndex,
        int beforeIndex,
        int afterIndex,
        List<DimensionStackPlan.Connection> connections,
        Map<DimensionStackPlan.Endpoint, Integer> endpointUseCounts,
        List<DimensionStackError> errors
    ) {
        DimensionStackDefinition.Entry before = definition.entries().get(beforeIndex);
        DimensionStackDefinition.Entry after = definition.entries().get(afterIndex);

        DimensionStackPlan.Endpoint forwardOrigin = new DimensionStackPlan.Endpoint(
            before.dimensionId(), DimensionStackPlan.nextFace(before.flipped())
        );
        DimensionStackPlan.Endpoint reverseOrigin = new DimensionStackPlan.Endpoint(
            after.dimensionId(), DimensionStackPlan.previousFace(after.flipped())
        );

        boolean forwardEnabled = before.connectsNext();
        boolean reverseEnabled = after.connectsPrevious();
        if (forwardEnabled) {
            endpointUseCounts.merge(forwardOrigin, 1, Integer::sum);
        }
        if (reverseEnabled) {
            endpointUseCounts.merge(reverseOrigin, 1, Integer::sum);
        }

        double scale = isUsableScale(before.scale()) && isUsableScale(after.scale())
            ? after.scale() / before.scale()
            : 1.0;
        if (!Double.isFinite(scale) || scale <= 0) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.INVALID_SCALE,
                "Connection scale is not finite at edge " + edgeIndex,
                beforeIndex
            ));
            scale = 1.0;
        }
        double rotation = Double.isFinite(before.horizontalRotation())
            && Double.isFinite(after.horizontalRotation())
            ? after.horizontalRotation() - before.horizontalRotation()
            : 0.0;
        if (!Double.isFinite(rotation)) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.INVALID_ROTATION,
                "Connection rotation is not finite at edge " + edgeIndex,
                beforeIndex
            ));
            rotation = 0.0;
        }

        connections.add(new DimensionStackPlan.Connection(
            edgeIndex,
            beforeIndex,
            afterIndex,
            forwardOrigin,
            reverseOrigin,
            scale,
            before.flipped() ^ after.flipped(),
            rotation,
            forwardEnabled,
            reverseEnabled
        ));
    }

    private static boolean isUsableScale(double scale) {
        return Double.isFinite(scale) && scale > 0;
    }
}
