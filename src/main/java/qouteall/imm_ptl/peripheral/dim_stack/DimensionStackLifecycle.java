// 本文件协调服务器维度堆叠的准备、提交、启动恢复和基岩替换生命周期。
package qouteall.imm_ptl.peripheral.dim_stack;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DimensionStackLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static volatile DimStackInfo pendingStack;
    @Nullable
    private static volatile DimensionStackRegistryValidator.Validation pendingValidation;
    @Nullable
    private static volatile DimensionStackDefinition activeDefinition;
    private static volatile Map<ResourceKey<Level>, BlockState> bedrockReplacements = Map.of();

    private DimensionStackLifecycle() {}

    public static void init() {
        DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.register(server -> {
            pendingValidation = null;
            if (O_O.isDedicatedServer()) {
                pendingStack = DimensionStackPreset.load();
                if (pendingStack == null) {
                    LOGGER.info("The server has no dimension stack preset");
                }
                else {
                    LOGGER.info("Preparing dedicated server dimension stack preset");
                }
            }

            DimStackInfo pending = pendingStack;
            if (pending == null) {
                return;
            }

            DimensionStackLocalValidator.Validation localValidation =
                DimensionStackLocalValidator.validate(pending);
            DimensionStackPlan topology = localValidation.plan();
            if (localValidation.isValid()) {
                try {
                    pendingStack = topology.definition().toInfo();
                    DimensionStackAPI.DIMENSION_STACK_PRE_UPDATE_EVENT
                        .invoker().run(server, topology.definition().toInfo());
                    pendingValidation = DimensionStackRegistryValidator.validate(
                        server, topology
                    );
                    if (!pendingValidation.isSuccess()) {
                        logErrors(
                            "Cannot validate pending dimension stack",
                            pendingValidation.errors()
                        );
                    }
                }
                catch (RuntimeException exception) {
                    LOGGER.error("Cannot prepare pending dimension stack", exception);
                    pendingValidation = DimensionStackRegistryValidator.Validation.failure(
                        List.of(new DimensionStackError(
                            DimensionStackError.Code.PREPARATION_FAILED,
                            "Dimension stack preparation failed: " + exception.getMessage()
                        ))
                    );
                }
            }
            else {
                pendingValidation = DimensionStackRegistryValidator.Validation.failure(
                    localValidation.errors()
                );
                logErrors("Cannot prepare pending dimension stack", localValidation.errors());
            }
        });
        IPGlobal.SERVER_CLEANUP_EVENT.register(server -> {
            pendingStack = null;
            pendingValidation = null;
            activeDefinition = null;
            bedrockReplacements = Map.of();
        });
    }

    public static void setPendingStack(@Nullable DimStackInfo stack) {
        pendingStack = stack == null
            ? null
            : DimensionStackDefinition.copyOf(stack).toInfo();
        pendingValidation = null;
    }

    public static void onServerEarlyInit(MinecraftServer server) {
        bedrockReplacements = Map.of();
        DimensionStackRegistryValidator.Validation validation = pendingValidation;
        if (pendingStack != null && validation != null && validation.isSuccess()) {
            bedrockReplacements = validation.bedrockReplacements();
        }
    }

    public static void onServerCreatedWorlds(MinecraftServer server) {
        DimStackInfo pending = pendingStack;
        DimensionStackRegistryValidator.Validation validation = pendingValidation;
        pendingStack = null;
        pendingValidation = null;

        if (pending == null) {
            restoreActiveStack(server);
            refreshBedrockReplacementsFromStorage(server);
            return;
        }
        if (validation == null || !validation.isSuccess()) {
            LOGGER.error("Skipping dimension stack initialization after failed preflight");
            refreshBedrockReplacementsFromStorage(server);
            restoreActiveStack(server);
            return;
        }

        UpdateResult result = updateStack(server, pending, false);
        if (result.isSuccess()) {
            LOGGER.info("Dimension stack initialized");
        }
        else {
            logErrors("Cannot initialize dimension stack", result.errors());
            refreshBedrockReplacementsFromStorage(server);
            restoreActiveStack(server);
        }
    }

    public static UpdateResult updateStack(MinecraftServer server, DimStackInfo stack) {
        return updateStack(server, stack, true);
    }

    private static UpdateResult updateStack(
        MinecraftServer server,
        DimStackInfo stack,
        boolean firePreparationEvent
    ) {
        DimensionStackLocalValidator.Validation localValidation =
            DimensionStackLocalValidator.validate(stack);
        DimensionStackPlan topology = localValidation.plan();
        if (!localValidation.isValid()) {
            return UpdateResult.failure(localValidation.errors());
        }

        Set<ResourceKey<Level>> dimensionsBeforeEvent = Set.of();
        if (firePreparationEvent) {
            DimensionStackPortalCompiler.Compilation preflight =
                DimensionStackPortalCompiler.compile(server, topology);
            if (!preflight.isSuccess()
                && preflight.errors().stream().anyMatch(error ->
                    error.code() != DimensionStackError.Code.MISSING_DIMENSION
                )
            ) {
                return UpdateResult.failure(preflight.errors());
            }
            if (preflight.isSuccess()) {
                DimensionStackPortalCommitter.Preparation preparation =
                    DimensionStackPortalCommitter.prepare(server, preflight.plan());
                if (!preparation.isSuccess()) {
                    return UpdateResult.failure(preparation.errors());
                }
            }

            dimensionsBeforeEvent = new LinkedHashSet<>(server.levelKeys());
            try {
                DimensionStackAPI.DIMENSION_STACK_PRE_UPDATE_EVENT
                    .invoker().run(server, topology.definition().toInfo());
            }
            catch (RuntimeException exception) {
                rollbackAddedDimensions(server, dimensionsBeforeEvent);
                return UpdateResult.failure(List.of(new DimensionStackError(
                    DimensionStackError.Code.PREPARATION_FAILED,
                    "Dimension stack preparation failed: " + exception.getMessage()
                )));
            }
        }

        DimensionStackPortalCompiler.Compilation compilation =
            DimensionStackPortalCompiler.compile(server, topology);
        if (!compilation.isSuccess()) {
            if (firePreparationEvent) {
                rollbackAddedDimensions(server, dimensionsBeforeEvent);
            }
            return UpdateResult.failure(compilation.errors());
        }

        DimensionStackPortalCommitter.Preparation preparation =
            DimensionStackPortalCommitter.prepare(server, compilation.plan());
        if (!preparation.isSuccess()) {
            if (firePreparationEvent) {
                rollbackAddedDimensions(server, dimensionsBeforeEvent);
            }
            return UpdateResult.failure(preparation.errors());
        }

        preparation.commit().commit();
        bedrockReplacements = preparation.commit().activeBedrockReplacements();
        setActiveStack(server, topology.definition());
        return UpdateResult.success();
    }

    public static UpdateResult removeStack(
        MinecraftServer server,
        @Nullable DimStackInfo legacyReference
    ) {
        DimensionStackPortalCompiler.CompiledPlan compiledLegacyReference = null;
        if (legacyReference != null) {
            DimensionStackPlan topology = DimensionStackPlanner.plan(legacyReference);
            if (!topology.isValid()) {
                return UpdateResult.failure(topology.errors());
            }

            DimensionStackPortalCompiler.Compilation compilation =
                DimensionStackPortalCompiler.compile(server, topology);
            if (!compilation.isSuccess()) {
                return UpdateResult.failure(compilation.errors());
            }
            compiledLegacyReference = compilation.plan();
        }

        DimensionStackPortalCommitter.Preparation preparation =
            DimensionStackPortalCommitter.prepareRemoval(server, compiledLegacyReference);
        if (!preparation.isSuccess()) {
            return UpdateResult.failure(preparation.errors());
        }

        preparation.commit().commit();
        bedrockReplacements = Map.of();
        setActiveStack(server, null);
        return UpdateResult.success();
    }

    @Nullable
    public static DimStackInfo getActiveStack() {
        DimensionStackDefinition definition = activeDefinition;
        return definition == null ? null : definition.toInfo();
    }

    public static Collection<ResourceKey<Level>> collectCandidates(MinecraftServer server) {
        LinkedHashSet<ResourceKey<Level>> result = new LinkedHashSet<>(server.levelKeys());
        result.addAll(
            DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT
                .invoker().getExtraDimensionKeys(
                    server.registryAccess(), server.getWorldData().worldGenOptions()
                )
        );
        return result;
    }

    public static void replaceBedrock(ServerLevel world, ChunkAccess chunk) {
        BlockState replacement = bedrockReplacements.get(world.dimension());
        if (replacement == null) {
            return;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                    mutable.set(x, y, z);
                    if (chunk.getBlockState(mutable).getBlock() == Blocks.BEDROCK) {
                        chunk.setBlockState(mutable, replacement, false);
                    }
                }
            }
        }
    }

    private static void refreshBedrockReplacementsFromStorage(MinecraftServer server) {
        Map<ResourceKey<Level>, BlockState> result = new LinkedHashMap<>();
        for (ServerLevel world : server.getAllLevels()) {
            BlockState replacement = GlobalPortalStorage.get(world).bedrockReplacement;
            if (replacement != null) {
                result.put(world.dimension(), replacement);
            }
        }
        bedrockReplacements = Collections.unmodifiableMap(result);
    }

    private static void logErrors(String context, List<DimensionStackError> errors) {
        errors.forEach(error -> LOGGER.error("{}: {}", context, error.message()));
    }

    private static void setActiveStack(
        MinecraftServer server,
        @Nullable DimensionStackDefinition definition
    ) {
        activeDefinition = definition;
        DimensionStackStateStorage.getFromServer(server).setActiveStack(definition);
    }

    private static void restoreActiveStack(MinecraftServer server) {
        DimStackInfo stored = DimensionStackStateStorage.getFromServer(server).getActiveStack();
        DimensionStackLocalValidator.Validation validation =
            DimensionStackLocalValidator.validate(stored);
        activeDefinition = stored != null && validation.isValid()
            ? validation.plan().definition()
            : null;
    }

    private static void rollbackAddedDimensions(
        MinecraftServer server,
        Set<ResourceKey<Level>> dimensionsBeforeEvent
    ) {
        if (!server.isRunning()) {
            return;
        }
        for (ResourceKey<Level> dimension : new LinkedHashSet<>(server.levelKeys())) {
            if (dimensionsBeforeEvent.contains(dimension)
                || dimension.equals(Level.OVERWORLD)
                || dimension.equals(Level.NETHER)
                || dimension.equals(Level.END)
            ) {
                continue;
            }
            ServerLevel world = server.getLevel(dimension);
            if (world != null) {
                try {
                    DimensionAPI.removeDimensionDynamically(world);
                }
                catch (RuntimeException exception) {
                    LOGGER.warn("Cannot roll back dimension {}", dimension.location(), exception);
                }
            }
        }
    }

    public record UpdateResult(boolean isSuccess, List<DimensionStackError> errors) {
        public UpdateResult {
            errors = List.copyOf(errors);
        }

        public static UpdateResult success() {
            return new UpdateResult(true, List.of());
        }

        public static UpdateResult failure(List<DimensionStackError> errors) {
            return new UpdateResult(false, errors);
        }
    }
}
