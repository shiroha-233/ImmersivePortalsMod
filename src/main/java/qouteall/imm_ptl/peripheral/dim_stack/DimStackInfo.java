// 本文件仅定义维度堆叠 preset 与 RPC 使用的 Gson 数据结构。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class DimStackInfo {
    public boolean loop;
    public boolean gravityTransform;
    public List<DimStackEntry> entries;

    public DimStackInfo() {
        entries = new ArrayList<>();
        loop = false;
        gravityTransform = false;
    }

    public DimStackInfo(List<DimStackEntry> entries, boolean loop, boolean gravityTransform) {
        this.entries = entries;
        this.loop = loop;
        this.gravityTransform = gravityTransform;
    }

    public boolean hasDimension(ResourceKey<Level> dimension) {
        return entries != null && entries.stream()
            .filter(entry -> entry != null && entry.dimensionIdStr != null)
            .anyMatch(entry -> entry.getDimension().equals(dimension));
    }
}
