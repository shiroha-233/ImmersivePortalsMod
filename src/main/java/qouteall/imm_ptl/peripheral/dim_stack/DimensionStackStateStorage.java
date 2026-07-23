// 本文件把当前维度堆叠定义持久化到存档，供重启后的服务端编辑器恢复状态。
package qouteall.imm_ptl.peripheral.dim_stack;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;

public final class DimensionStackStateStorage extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "imm_ptl_dimension_stack";
    private static final String ACTIVE_STACK = "activeStack";

    @Nullable
    private DimensionStackDefinition activeDefinition;

    private DimensionStackStateStorage() {}

    public static DimensionStackStateStorage get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(
            new Factory<>(
                DimensionStackStateStorage::new,
                (tag, ignored) -> fromTag(tag),
                null
            ),
            DATA_NAME
        );
    }

    @Nullable
    public DimStackInfo getActiveStack() {
        return activeDefinition == null ? null : activeDefinition.toInfo();
    }

    public void setActiveStack(@Nullable DimensionStackDefinition definition) {
        if (java.util.Objects.equals(activeDefinition, definition)) {
            return;
        }
        activeDefinition = definition;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (activeDefinition != null) {
            tag.putString(ACTIVE_STACK, IPGlobal.gson.toJson(activeDefinition.toInfo()));
        }
        return tag;
    }

    private static DimensionStackStateStorage fromTag(CompoundTag tag) {
        DimensionStackStateStorage storage = new DimensionStackStateStorage();
        if (!tag.contains(ACTIVE_STACK)) {
            return storage;
        }
        try {
            DimStackInfo info = IPGlobal.gson.fromJson(
                tag.getString(ACTIVE_STACK), DimStackInfo.class
            );
            DimensionStackLocalValidator.Validation validation =
                DimensionStackLocalValidator.validate(info);
            if (validation.isValid()) {
                storage.activeDefinition = validation.plan().definition();
            }
            else {
                LOGGER.warn("Ignoring invalid persisted dimension stack state");
                storage.setDirty();
            }
        }
        catch (RuntimeException exception) {
            LOGGER.warn("Cannot read persisted dimension stack state", exception);
            storage.setDirty();
        }
        return storage;
    }

    public static DimensionStackStateStorage getFromServer(
        net.minecraft.server.MinecraftServer server
    ) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld is not available");
        }
        return get(overworld);
    }
}
