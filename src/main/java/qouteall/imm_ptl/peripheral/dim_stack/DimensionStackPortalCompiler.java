// 本文件把纯连接计划编译为已校验但尚未注册的 Minecraft 全局门户。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DimensionStackPortalCompiler {
    private DimensionStackPortalCompiler() {}

    public static Compilation compile(MinecraftServer server, DimensionStackPlan topology) {
        List<DimensionStackError> errors = new ArrayList<>(topology.errors());
        if (!errors.isEmpty()) {
            return Compilation.failure(errors);
        }

        Map<Integer, ResolvedEntry> resolvedEntries = resolveEntries(server, topology, errors);
        Map<ResourceKey<Level>, BlockState> bedrockReplacements =
            resolveBedrockReplacements(topology, resolvedEntries, errors);
        if (!errors.isEmpty()) {
            return Compilation.failure(errors);
        }

        Map<ServerLevel, List<DesiredPortal>> desiredPortals = new LinkedHashMap<>();
        Set<DimensionStackPlan.Endpoint> usedEndpoints = new LinkedHashSet<>();
        for (DimensionStackPlan.Connection connection : topology.connections()) {
            ResolvedEntry before = resolvedEntries.get(connection.beforeEntryIndex());
            ResolvedEntry after = resolvedEntries.get(connection.afterEntryIndex());
            DimensionStackPlan.Endpoint forwardEndpoint = canonicalEndpoint(
                before.world(), connection.forwardOrigin().face()
            );
            DimensionStackPlan.Endpoint reverseEndpoint = canonicalEndpoint(
                after.world(), connection.reverseOrigin().face()
            );

            VerticalConnectingPortal forward = VerticalConnectingPortal.createConnectingPortal(
                before.world(),
                toConnectorType(connection.forwardOrigin().face()),
                after.world(),
                connection.forwardScale(),
                connection.inverted(),
                connection.horizontalRotation(),
                before.minY(), before.maxY(),
                after.minY(), after.maxY()
            );
            VerticalConnectingPortal reverse = PortalAPI.createReversePortal(forward);

            forward.setFuseView(true);
            reverse.setFuseView(true);
            forward.setTeleportChangesGravity(topology.definition().gravityTransform());
            reverse.setTeleportChangesGravity(topology.definition().gravityTransform());
            forward.portalTag = DimensionStackPortalOwnership.tag(forwardEndpoint);
            reverse.portalTag = DimensionStackPortalOwnership.tag(reverseEndpoint);

            if (connection.forwardEnabled()) {
                addDesiredPortal(
                    desiredPortals, usedEndpoints, forwardEndpoint, forward, errors
                );
            }
            if (connection.reverseEnabled()) {
                addDesiredPortal(
                    desiredPortals, usedEndpoints, reverseEndpoint, reverse, errors
                );
            }
        }

        if (!errors.isEmpty()) {
            return Compilation.failure(errors);
        }

        desiredPortals.values().stream()
            .flatMap(List::stream)
            .filter(desired -> !desired.portal().isPortalValid())
            .forEach(desired -> errors.add(new DimensionStackError(
                DimensionStackError.Code.INVALID_PORTAL,
                "Generated portal is invalid at %s %s".formatted(
                    desired.endpoint().dimensionId(), desired.endpoint().face()
                )
            )));
        if (!errors.isEmpty()) {
            return Compilation.failure(errors);
        }

        return Compilation.success(new CompiledPlan(
            topology, desiredPortals, bedrockReplacements
        ));
    }

    private static Map<Integer, ResolvedEntry> resolveEntries(
        MinecraftServer server,
        DimensionStackPlan topology,
        List<DimensionStackError> errors
    ) {
        Map<Integer, ResolvedEntry> result = new LinkedHashMap<>();
        for (int position = 0; position < topology.definition().entries().size(); position++) {
            DimensionStackDefinition.Entry entry = topology.definition().entries().get(position);
            ResourceKey<Level> dimension;
            try {
                dimension = Helper.dimIdToKey(ResourceLocation.parse(entry.dimensionId()));
            }
            catch (RuntimeException exception) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.INVALID_DIMENSION_ID,
                    "Invalid dimension id at entry " + entry.index(),
                    entry.index()
                ));
                continue;
            }

            ServerLevel world = server.getLevel(dimension);
            if (world == null) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.MISSING_DIMENSION,
                    "Missing dimension " + entry.dimensionId(),
                    entry.index()
                ));
                continue;
            }

            int worldMinY = McHelper.getMinY(world);
            int worldMaxY = McHelper.getMaxContentYExclusive(world);
            int minY = entry.bottomY() != null ? entry.bottomY() : worldMinY;
            int maxY = entry.topY() != null ? entry.topY() : worldMaxY;
            if (minY < worldMinY || maxY > worldMaxY || minY >= maxY) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.HEIGHT_OUT_OF_RANGE,
                    "Height range [%d, %d) is outside %s [%d, %d)".formatted(
                        minY, maxY, entry.dimensionId(), worldMinY, worldMaxY
                    ),
                    entry.index()
                ));
                continue;
            }

            result.put(position, new ResolvedEntry(entry, world, minY, maxY));
        }
        return result;
    }

    private static Map<ResourceKey<Level>, BlockState> resolveBedrockReplacements(
        DimensionStackPlan topology,
        Map<Integer, ResolvedEntry> resolvedEntries,
        List<DimensionStackError> errors
    ) {
        Map<ResourceKey<Level>, BlockState> result = new LinkedHashMap<>();
        for (int position = 0; position < topology.definition().entries().size(); position++) {
            DimensionStackDefinition.Entry entry = topology.definition().entries().get(position);
            ResolvedEntry resolvedEntry = resolvedEntries.get(position);
            if (resolvedEntry == null) {
                continue;
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

            ResourceKey<Level> dimension = resolvedEntry.world().dimension();
            if (result.containsKey(dimension)
                && !Objects.equals(result.get(dimension), parsed.blockState())
            ) {
                errors.add(new DimensionStackError(
                    DimensionStackError.Code.CONFLICTING_BEDROCK_REPLACEMENT,
                    "Conflicting bedrock replacements for " + dimension.location(),
                    entry.index()
                ));
                continue;
            }
            result.put(dimension, parsed.blockState());
        }
        return result;
    }

    private static VerticalConnectingPortal.ConnectorType toConnectorType(
        DimensionStackPlan.ConnectorFace face
    ) {
        return face == DimensionStackPlan.ConnectorFace.CEILING
            ? VerticalConnectingPortal.ConnectorType.ceil
            : VerticalConnectingPortal.ConnectorType.floor;
    }

    private static void addDesiredPortal(
        Map<ServerLevel, List<DesiredPortal>> desiredPortals,
        Set<DimensionStackPlan.Endpoint> usedEndpoints,
        DimensionStackPlan.Endpoint endpoint,
        VerticalConnectingPortal portal,
        List<DimensionStackError> errors
    ) {
        if (!usedEndpoints.add(endpoint)) {
            errors.add(new DimensionStackError(
                DimensionStackError.Code.ENDPOINT_CONFLICT,
                "Multiple connections resolve to %s %s".formatted(
                    endpoint.dimensionId(), endpoint.face()
                )
            ));
            return;
        }
        desiredPortals.computeIfAbsent((ServerLevel) portal.level(), ignored -> new ArrayList<>())
            .add(new DesiredPortal(endpoint, portal));
    }

    private static DimensionStackPlan.Endpoint canonicalEndpoint(
        ServerLevel world,
        DimensionStackPlan.ConnectorFace face
    ) {
        return new DimensionStackPlan.Endpoint(
            world.dimension().location().toString(), face
        );
    }

    private record ResolvedEntry(
        DimensionStackDefinition.Entry definition,
        ServerLevel world,
        int minY,
        int maxY
    ) {}

    public record DesiredPortal(
        DimensionStackPlan.Endpoint endpoint,
        VerticalConnectingPortal portal
    ) {}

    public record CompiledPlan(
        DimensionStackPlan topology,
        Map<ServerLevel, List<DesiredPortal>> desiredPortals,
        Map<ResourceKey<Level>, BlockState> bedrockReplacements
    ) {
        public CompiledPlan {
            Map<ServerLevel, List<DesiredPortal>> portalsCopy = new LinkedHashMap<>();
            desiredPortals.forEach((world, portals) -> portalsCopy.put(world, List.copyOf(portals)));
            desiredPortals = Collections.unmodifiableMap(portalsCopy);
            bedrockReplacements = Collections.unmodifiableMap(
                new LinkedHashMap<>(bedrockReplacements)
            );
        }
    }

    public record Compilation(CompiledPlan plan, List<DimensionStackError> errors) {
        public Compilation {
            errors = List.copyOf(errors);
        }

        public static Compilation success(CompiledPlan plan) {
            return new Compilation(plan, List.of());
        }

        public static Compilation failure(List<DimensionStackError> errors) {
            return new Compilation(null, errors);
        }

        public boolean isSuccess() {
            return plan != null && errors.isEmpty();
        }
    }
}
