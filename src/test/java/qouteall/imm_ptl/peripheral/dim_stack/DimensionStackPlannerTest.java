// 本文件验证维度堆叠翻转规划的端点、变换和冲突不变量。
package qouteall.imm_ptl.peripheral.dim_stack;

import com.google.gson.Gson;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionStackPlannerTest {
    @Test
    void plansAllFlipCombinations() {
        assertFlipCase(
            false, false,
            DimensionStackPlan.ConnectorFace.FLOOR,
            DimensionStackPlan.ConnectorFace.CEILING,
            false
        );
        assertFlipCase(
            false, true,
            DimensionStackPlan.ConnectorFace.FLOOR,
            DimensionStackPlan.ConnectorFace.FLOOR,
            true
        );
        assertFlipCase(
            true, false,
            DimensionStackPlan.ConnectorFace.CEILING,
            DimensionStackPlan.ConnectorFace.CEILING,
            true
        );
        assertFlipCase(
            true, true,
            DimensionStackPlan.ConnectorFace.CEILING,
            DimensionStackPlan.ConnectorFace.FLOOR,
            false
        );
    }

    @Test
    void derivesScaleAndHorizontalRotationFromOneForwardTransform() {
        DimensionStackDefinition.Entry before = entry(0, "test:before", false, true, true);
        before = new DimensionStackDefinition.Entry(
            before.index(), before.dimensionId(), 2.0, before.flipped(), -170.0,
            before.topY(), before.bottomY(), before.bedrockReplacement(),
            before.connectsPrevious(), before.connectsNext()
        );
        DimensionStackDefinition.Entry after = entry(1, "test:after", true, true, true);
        after = new DimensionStackDefinition.Entry(
            after.index(), after.dimensionId(), 0.5, after.flipped(), 190.0,
            after.topY(), after.bottomY(), after.bedrockReplacement(),
            after.connectsPrevious(), after.connectsNext()
        );

        DimensionStackPlan.Connection connection = plan(List.of(before, after), false)
            .connections().get(0);

        assertEquals(0.25, connection.forwardScale());
        assertEquals(360.0, connection.horizontalRotation());
        assertTrue(connection.inverted());
    }

    @Test
    void preservesIndependentOneWayFlags() {
        for (boolean forward : List.of(false, true)) {
            for (boolean reverse : List.of(false, true)) {
                DimensionStackDefinition.Entry before = entry(
                    0, "test:before", false, true, forward
                );
                DimensionStackDefinition.Entry after = entry(
                    1, "test:after", false, reverse, true
                );

                DimensionStackPlan plan = plan(List.of(before, after), false);
                DimensionStackPlan.Connection connection = plan.connections().get(0);

                assertEquals(forward, connection.forwardEnabled());
                assertEquals(reverse, connection.reverseEnabled());
                assertEquals(
                    forward ? DimensionStackPlan.EndpointStatus.ENABLED
                        : DimensionStackPlan.EndpointStatus.NONE,
                    plan.getEndpointStatus(0, DimensionStackPlan.LogicalDirection.NEXT)
                );
                assertEquals(
                    reverse ? DimensionStackPlan.EndpointStatus.ENABLED
                        : DimensionStackPlan.EndpointStatus.NONE,
                    plan.getEndpointStatus(1, DimensionStackPlan.LogicalDirection.PREVIOUS)
                );
            }
        }
    }

    @Test
    void addsExactlyOneLoopEdge() {
        List<DimensionStackDefinition.Entry> entries = List.of(
            entry(0, "test:a", false, true, true),
            entry(1, "test:b", false, true, true),
            entry(2, "test:c", false, true, true)
        );

        assertEquals(2, plan(entries, false).connections().size());
        assertEquals(3, plan(entries, true).connections().size());
    }

    @Test
    void supportsSingleEntryLoopAsExplicitSelfEdge() {
        DimensionStackPlan plan = plan(List.of(
            entry(0, "test:self", true, true, true)
        ), true);

        assertTrue(plan.isValid());
        assertEquals(1, plan.connections().size());
        assertEquals(
            DimensionStackPlan.ConnectorFace.CEILING,
            plan.connections().get(0).forwardOrigin().face()
        );
        assertEquals(
            DimensionStackPlan.ConnectorFace.FLOOR,
            plan.connections().get(0).reverseOrigin().face()
        );
    }

    @Test
    void rejectsDuplicatePhysicalEndpointSlots() {
        DimensionStackPlan plan = plan(List.of(
            entry(0, "test:same", false, true, true),
            entry(1, "test:same", false, true, true)
        ), true);

        assertFalse(plan.isValid());
        assertTrue(plan.errors().stream().anyMatch(
            error -> error.code() == DimensionStackError.Code.ENDPOINT_CONFLICT
        ));
        assertEquals(
            DimensionStackPlan.EndpointStatus.CONFLICT,
            plan.getEndpointStatus(0, DimensionStackPlan.LogicalDirection.NEXT)
        );
    }

    @Test
    void allowsCeilingAndFloorOfSameDimension() {
        DimensionStackPlan plan = plan(List.of(
            entry(0, "test:same", false, true, true),
            entry(1, "test:same", false, true, true)
        ), false);

        assertTrue(plan.isValid());
        assertEquals(2, plan.endpointUseCounts().size());
    }

    @Test
    void rejectsInvalidNumbersAndStaticHeightRange() {
        DimensionStackDefinition.Entry invalid = new DimensionStackDefinition.Entry(
            0,
            "test:invalid",
            Double.NaN,
            false,
            Double.POSITIVE_INFINITY,
            10,
            10,
            null,
            true,
            true
        );

        DimensionStackPlan plan = plan(List.of(invalid), false);

        assertFalse(plan.isValid());
        assertTrue(hasError(plan, DimensionStackError.Code.INVALID_SCALE));
        assertTrue(hasError(plan, DimensionStackError.Code.INVALID_ROTATION));
        assertTrue(hasError(plan, DimensionStackError.Code.INVALID_HEIGHT_RANGE));
    }

    @Test
    void rejectsMalformedDimensionIdBeforePreparationEvents() {
        DimensionStackPlan plan = plan(List.of(
            entry(0, "not a dimension id", false, true, true)
        ), false);

        assertFalse(plan.isValid());
        assertTrue(hasError(plan, DimensionStackError.Code.INVALID_DIMENSION_ID));
    }

    @Test
    void rejectsUnknownBedrockBlockLocally() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        DimensionStackDefinition.Entry invalid = new DimensionStackDefinition.Entry(
            0,
            "test:world",
            1.0,
            false,
            0.0,
            null,
            null,
            "minecraft:this_block_does_not_exist",
            true,
            true
        );
        DimStackInfo info = new DimensionStackDefinition(
            List.of(invalid), false, false
        ).toInfo();

        DimensionStackLocalValidator.Validation validation =
            DimensionStackLocalValidator.validate(info);

        assertFalse(validation.isValid());
        assertTrue(validation.errors().stream().anyMatch(
            error -> error.code() == DimensionStackError.Code.INVALID_BEDROCK_BLOCK
        ));
    }

    @Test
    void readsAndWritesLegacyFlippedPresetField() {
        DimStackInfo info = new Gson().fromJson(
            "{\"loop\":false,\"gravityTransform\":false,\"entries\":["
                + "{\"dimensionIdStr\":\"test:world\",\"flipped\":true}]}" ,
            DimStackInfo.class
        );

        assertTrue(info.entries.get(0).flipped);
        assertTrue(new Gson().toJson(info).contains("\"flipped\":true"));
    }

    @Test
    void producesDeterministicImmutablePlan() {
        DimensionStackDefinition definition = new DimensionStackDefinition(
            List.of(
                entry(0, "test:a", false, true, true),
                entry(1, "test:b", true, true, true)
            ),
            false,
            true
        );

        assertEquals(
            DimensionStackPlanner.plan(definition),
            DimensionStackPlanner.plan(definition)
        );
    }

    @Test
    void preservesDefinitionFieldsAcrossDtoRoundTrip() {
        DimensionStackDefinition original = new DimensionStackDefinition(
            List.of(new DimensionStackDefinition.Entry(
                0,
                "test:world",
                2.5,
                true,
                45.0,
                200,
                -20,
                "minecraft:obsidian",
                false,
                true
            )),
            true,
            true
        );

        assertEquals(original, DimensionStackDefinition.copyOf(original.toInfo()));
    }

    @Test
    void serializesServerEditorStateWithActiveStack() {
        Gson gson = new Gson();
        DimensionStackEditorState original = new DimensionStackEditorState(
            List.of("test:world"),
            new DimensionStackDefinition(
                List.of(entry(0, "test:world", true, true, true)),
                false,
                true
            ).toInfo()
        );

        DimensionStackEditorState decoded = gson.fromJson(
            gson.toJson(original), DimensionStackEditorState.class
        );

        assertEquals(List.of("test:world"), decoded.dimensionIds());
        assertTrue(decoded.activeStack().entries.get(0).flipped);
        assertTrue(decoded.activeStack().gravityTransform);
    }

    @Test
    void rejectsOversizedRpcDefinition() {
        List<DimensionStackDefinition.Entry> entries = IntStream
            .rangeClosed(0, DimensionStackPlanner.MAX_ENTRY_COUNT)
            .mapToObj(index -> entry(
                index, "test:world_" + index, false, false, false
            ))
            .toList();

        assertTrue(hasError(
            plan(entries, false), DimensionStackError.Code.TOO_MANY_ENTRIES
        ));
    }

    private static void assertFlipCase(
        boolean beforeFlipped,
        boolean afterFlipped,
        DimensionStackPlan.ConnectorFace expectedForward,
        DimensionStackPlan.ConnectorFace expectedReverse,
        boolean expectedInverted
    ) {
        DimensionStackPlan plan = plan(List.of(
            entry(0, "test:before", beforeFlipped, true, true),
            entry(1, "test:after", afterFlipped, true, true)
        ), false);

        assertTrue(plan.isValid());
        DimensionStackPlan.Connection connection = plan.connections().get(0);
        assertEquals(expectedForward, connection.forwardOrigin().face());
        assertEquals(expectedReverse, connection.reverseOrigin().face());
        assertEquals(expectedInverted, connection.inverted());
    }

    private static DimensionStackPlan plan(
        List<DimensionStackDefinition.Entry> entries,
        boolean loop
    ) {
        return DimensionStackPlanner.plan(new DimensionStackDefinition(entries, loop, false));
    }

    private static DimensionStackDefinition.Entry entry(
        int index,
        String dimension,
        boolean flipped,
        boolean connectsPrevious,
        boolean connectsNext
    ) {
        return new DimensionStackDefinition.Entry(
            index,
            dimension,
            1.0,
            flipped,
            0.0,
            null,
            null,
            "minecraft:obsidian",
            connectsPrevious,
            connectsNext
        );
    }

    private static boolean hasError(
        DimensionStackPlan plan,
        DimensionStackError.Code code
    ) {
        return plan.errors().stream().anyMatch(error -> error.code() == code);
    }
}
