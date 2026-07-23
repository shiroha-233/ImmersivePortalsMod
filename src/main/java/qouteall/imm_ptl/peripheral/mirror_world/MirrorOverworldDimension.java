// 本文件注册与当前主世界生成规则一致的独立镜像主世界维度。
package qouteall.imm_ptl.peripheral.mirror_world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackAPI;

import java.util.List;

public final class MirrorOverworldDimension {
    public static final ResourceKey<Level> MIRROR_OVERWORLD = ResourceKey.create(
        Registries.DIMENSION,
        McHelper.newResourceLocation("immersive_portals:mirror_overworld")
    );

    private static final ResourceKey<LevelStem> OVERWORLD_LEVEL_STEM = ResourceKey.create(
        Registries.LEVEL_STEM,
        Level.OVERWORLD.location()
    );

    private MirrorOverworldDimension() {}

    public static void init() {
        DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.register(
            MirrorOverworldDimension::registerIfMissing
        );
        DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT.register(
            (registryAccess, options) -> List.of(MIRROR_OVERWORLD)
        );
        ServerTickEvents.END_SERVER_TICK.register(
            MirrorOverworldDimension::syncWeatherFromOverworld
        );
    }

    private static void registerIfMissing(MinecraftServer server) {
        DimensionAPI.addDimensionIfNotExists(
            server,
            MIRROR_OVERWORLD.location(),
            () -> copyOverworldLevelStem(server)
        );
    }

    private static LevelStem copyOverworldLevelStem(MinecraftServer server) {
        Registry<LevelStem> levelStems = server.registryAccess()
            .registryOrThrow(Registries.LEVEL_STEM);
        LevelStem overworldStem = levelStems.get(OVERWORLD_LEVEL_STEM);
        if (overworldStem == null) {
            throw new IllegalStateException("Cannot find the overworld level stem");
        }

        // ServerLevel 会注入共享世界种子，复制 stem 可保留数据包改过的主世界生成规则。
        return new LevelStem(overworldStem.type(), overworldStem.generator());
    }

    private static void syncWeatherFromOverworld(MinecraftServer server) {
        ServerLevel mirrorWorld = server.getLevel(MIRROR_OVERWORLD);
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (mirrorWorld == null || overworld == null) {
            return;
        }

        ((IEWorld) mirrorWorld).portal_setWeather(
            overworld.getRainLevel(1), overworld.getRainLevel(1),
            overworld.getThunderLevel(1), overworld.getThunderLevel(1)
        );
    }
}
