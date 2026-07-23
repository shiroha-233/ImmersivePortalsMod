// 本文件把可变的 Gson 配置快照转换为不可变的维度堆叠定义。
package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.ArrayList;
import java.util.List;

public record DimensionStackDefinition(
    List<Entry> entries,
    boolean loop,
    boolean gravityTransform
) {
    public DimensionStackDefinition {
        entries = List.copyOf(entries);
    }

    public static DimensionStackDefinition copyOf(DimStackInfo source) {
        List<Entry> entries = new ArrayList<>();
        if (source.entries != null) {
            for (int index = 0; index < source.entries.size(); index++) {
                DimStackEntry entry = source.entries.get(index);
                if (entry == null) {
                    entries.add(Entry.missing(index));
                }
                else {
                    entries.add(new Entry(
                        index,
                        entry.dimensionIdStr,
                        entry.scale,
                        entry.flipped,
                        entry.horizontalRotation,
                        entry.topY,
                        entry.bottomY,
                        entry.bedrockReplacementStr,
                        entry.connectsPrevious,
                        entry.connectsNext
                    ));
                }
            }
        }
        return new DimensionStackDefinition(entries, source.loop, source.gravityTransform);
    }

    public DimStackInfo toInfo() {
        List<DimStackEntry> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            DimStackEntry target = new DimStackEntry();
            target.dimensionIdStr = entry.dimensionId();
            target.scale = entry.scale();
            target.flipped = entry.flipped();
            target.horizontalRotation = entry.horizontalRotation();
            target.topY = entry.topY();
            target.bottomY = entry.bottomY();
            target.bedrockReplacementStr = entry.bedrockReplacement();
            target.connectsPrevious = entry.connectsPrevious();
            target.connectsNext = entry.connectsNext();
            result.add(target);
        }
        return new DimStackInfo(result, loop, gravityTransform);
    }

    public record Entry(
        int index,
        String dimensionId,
        double scale,
        boolean flipped,
        double horizontalRotation,
        Integer topY,
        Integer bottomY,
        String bedrockReplacement,
        boolean connectsPrevious,
        boolean connectsNext
    ) {
        private static Entry missing(int index) {
            return new Entry(
                index, null, Double.NaN, false, Double.NaN,
                null, null, null, false, false
            );
        }
    }
}
