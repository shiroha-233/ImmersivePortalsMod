// 本文件预演并提交维度堆叠拥有的全局门户差量，保护其他来源的门户。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DimensionStackPortalCommitter {
    private DimensionStackPortalCommitter() {}

    public static Preparation prepare(
        MinecraftServer server,
        DimensionStackPortalCompiler.CompiledPlan compiledPlan
    ) {
        List<DimensionStackError> errors = new ArrayList<>();
        Map<ServerLevel, WorldDelta> deltas = new LinkedHashMap<>();

        for (ServerLevel world : server.getAllLevels()) {
            List<DimensionStackPortalCompiler.DesiredPortal> desired =
                compiledPlan.desiredPortals().getOrDefault(world, List.of());
            WorldDelta delta = prepareWorldDelta(
                world,
                desired,
                compiledPlan.bedrockReplacements().get(world.dimension()),
                errors
            );
            deltas.put(world, delta);
        }

        if (!errors.isEmpty()) {
            return Preparation.failure(errors);
        }
        return Preparation.success(new PreparedCommit(
            deltas,
            onlyActiveReplacements(compiledPlan.bedrockReplacements())
        ));
    }

    public static Preparation prepareRemoval(
        MinecraftServer server,
        DimensionStackPortalCompiler.CompiledPlan legacyReference
    ) {
        List<DimensionStackError> errors = new ArrayList<>();
        Map<ServerLevel, WorldDelta> deltas = new LinkedHashMap<>();

        for (ServerLevel world : server.getAllLevels()) {
            GlobalPortalStorage storage = GlobalPortalStorage.get(world);
            List<Portal> removals = storage.data.stream()
                .filter(DimensionStackPortalOwnership::isOwned)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            if (legacyReference != null) {
                for (DimensionStackPortalCompiler.DesiredPortal desired :
                    legacyReference.desiredPortals().getOrDefault(world, List.of())
                ) {
                    List<Portal> exactLegacyMatches = storage.data.stream()
                        .filter(portal -> portal.portalTag == null)
                        .filter(portal -> DimensionStackPortalOwnership.structurallyMatches(
                            portal, desired.portal()
                        ))
                        .toList();
                    if (exactLegacyMatches.size() > 1) {
                        errors.add(occupiedEndpoint(desired.endpoint()));
                    }
                    else if (exactLegacyMatches.size() == 1) {
                        removals.add(exactLegacyMatches.get(0));
                    }
                }
            }

            deltas.put(world, new WorldDelta(
                storage, removals, List.of(), List.of(), null
            ));
        }

        if (!errors.isEmpty()) {
            return Preparation.failure(errors);
        }
        return Preparation.success(new PreparedCommit(deltas, Map.of()));
    }

    public static boolean hasOwnedPortals(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            if (GlobalPortalStorage.get(world).data.stream()
                .anyMatch(DimensionStackPortalOwnership::isOwned)
            ) {
                return true;
            }
        }
        return false;
    }

    private static WorldDelta prepareWorldDelta(
        ServerLevel world,
        List<DimensionStackPortalCompiler.DesiredPortal> desiredPortals,
        BlockState bedrockReplacement,
        List<DimensionStackError> errors
    ) {
        GlobalPortalStorage storage = GlobalPortalStorage.get(world);
        Map<String, List<Portal>> ownedByTag = new LinkedHashMap<>();
        Map<DimensionStackPlan.Endpoint, List<Portal>> unownedByEndpoint = new LinkedHashMap<>();

        for (Portal existing : storage.data) {
            if (DimensionStackPortalOwnership.isOwned(existing)) {
                ownedByTag.computeIfAbsent(existing.portalTag, ignored -> new ArrayList<>())
                    .add(existing);
            }
            else {
                DimensionStackPortalOwnership.findEndpoint(world, existing)
                    .ifPresent(endpoint -> unownedByEndpoint
                        .computeIfAbsent(endpoint, ignored -> new ArrayList<>())
                        .add(existing));
            }
        }

        List<Portal> removals = new ArrayList<>();
        List<Portal> additions = new ArrayList<>();
        List<Adoption> adoptions = new ArrayList<>();

        for (DimensionStackPortalCompiler.DesiredPortal desired : desiredPortals) {
            String expectedTag = DimensionStackPortalOwnership.tag(desired.endpoint());
            List<Portal> owned = ownedByTag.remove(expectedTag);
            List<Portal> unowned = unownedByEndpoint.getOrDefault(desired.endpoint(), List.of());

            if (owned != null && !owned.isEmpty()) {
                if (!unowned.isEmpty()) {
                    errors.add(occupiedEndpoint(desired.endpoint()));
                    continue;
                }

                if (owned.size() == 1 && DimensionStackPortalOwnership.structurallyMatches(
                    owned.get(0), desired.portal()
                )) {
                    continue;
                }

                removals.addAll(owned);
                if (owned.size() == 1) {
                    desired.portal().setUUID(owned.get(0).getUUID());
                }
                additions.add(desired.portal());
                continue;
            }

            if (unowned.isEmpty()) {
                additions.add(desired.portal());
            }
            else if (unowned.size() == 1
                && unowned.get(0).portalTag == null
                && DimensionStackPortalOwnership.structurallyMatches(
                    unowned.get(0), desired.portal()
                )
            ) {
                adoptions.add(new Adoption(unowned.get(0), expectedTag));
            }
            else {
                errors.add(occupiedEndpoint(desired.endpoint()));
            }
        }

        ownedByTag.values().forEach(removals::addAll);
        return new WorldDelta(
            storage, removals, additions, adoptions, bedrockReplacement
        );
    }

    private static DimensionStackError occupiedEndpoint(DimensionStackPlan.Endpoint endpoint) {
        return new DimensionStackError(
            DimensionStackError.Code.OCCUPIED_ENDPOINT,
            "A non-stack portal occupies %s %s".formatted(
                endpoint.dimensionId(), endpoint.face()
            )
        );
    }

    private static Map<ResourceKey<Level>, BlockState> onlyActiveReplacements(
        Map<ResourceKey<Level>, BlockState> replacements
    ) {
        Map<ResourceKey<Level>, BlockState> result = new LinkedHashMap<>();
        replacements.forEach((dimension, blockState) -> {
            if (blockState != null) {
                result.put(dimension, blockState);
            }
        });
        return result;
    }

    private record Adoption(Portal portal, String tag) {}

    private record WorldDelta(
        GlobalPortalStorage storage,
        List<Portal> removals,
        List<Portal> additions,
        List<Adoption> adoptions,
        BlockState bedrockReplacement
    ) {
        private WorldDelta {
            removals = List.copyOf(removals);
            additions = List.copyOf(additions);
            adoptions = List.copyOf(adoptions);
        }

        private void commit() {
            boolean bedrockChanged = !Objects.equals(
                storage.bedrockReplacement, bedrockReplacement
            );
            storage.bedrockReplacement = bedrockReplacement;
            adoptions.forEach(adoption -> adoption.portal().portalTag = adoption.tag());

            boolean portalsChanged = storage.replacePortals(removals, additions);
            if (!portalsChanged && (bedrockChanged || !adoptions.isEmpty())) {
                storage.onDataChanged();
            }
        }
    }

    public static final class PreparedCommit {
        private final Map<ServerLevel, WorldDelta> deltas;
        private final Map<ResourceKey<Level>, BlockState> activeBedrockReplacements;

        private PreparedCommit(
            Map<ServerLevel, WorldDelta> deltas,
            Map<ResourceKey<Level>, BlockState> activeBedrockReplacements
        ) {
            this.deltas = Collections.unmodifiableMap(new LinkedHashMap<>(deltas));
            this.activeBedrockReplacements = Collections.unmodifiableMap(
                new LinkedHashMap<>(activeBedrockReplacements)
            );
        }

        public Map<ResourceKey<Level>, BlockState> activeBedrockReplacements() {
            return activeBedrockReplacements;
        }

        public void commit() {
            McHelper.validateOnServerThread();
            deltas.values().forEach(WorldDelta::commit);
        }
    }

    public record Preparation(PreparedCommit commit, List<DimensionStackError> errors) {
        public Preparation {
            errors = List.copyOf(errors);
        }

        public static Preparation success(PreparedCommit commit) {
            return new Preparation(commit, List.of());
        }

        public static Preparation failure(List<DimensionStackError> errors) {
            return new Preparation(null, errors);
        }

        public boolean isSuccess() {
            return commit != null && errors.isEmpty();
        }
    }
}
