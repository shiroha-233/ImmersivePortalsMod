// 本文件定义服务端下发给维度堆叠编辑器的候选维度与当前配置快照。
package qouteall.imm_ptl.peripheral.dim_stack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record DimensionStackEditorState(
    List<String> dimensionIds,
    @Nullable DimStackInfo activeStack
) {
    public DimensionStackEditorState {
        dimensionIds = List.copyOf(dimensionIds);
    }
}
