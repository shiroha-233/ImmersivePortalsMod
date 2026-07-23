// 本文件定义维度堆叠规划与提交阶段共享的结构化错误。
package qouteall.imm_ptl.peripheral.dim_stack;

public record DimensionStackError(
    Code code,
    String message,
    int entryIndex
) {
    public static final int NO_ENTRY = -1;

    public DimensionStackError(Code code, String message) {
        this(code, message, NO_ENTRY);
    }

    public enum Code {
        EMPTY_STACK,
        TOO_MANY_ENTRIES,
        EMPTY_DIMENSION_ID,
        INVALID_SCALE,
        INVALID_ROTATION,
        INVALID_HEIGHT_RANGE,
        ENDPOINT_CONFLICT,
        INVALID_DIMENSION_ID,
        MISSING_DIMENSION,
        HEIGHT_OUT_OF_RANGE,
        INVALID_BEDROCK_BLOCK,
        CONFLICTING_BEDROCK_REPLACEMENT,
        INVALID_PORTAL,
        OCCUPIED_ENDPOINT,
        PREPARATION_FAILED
    }
}
