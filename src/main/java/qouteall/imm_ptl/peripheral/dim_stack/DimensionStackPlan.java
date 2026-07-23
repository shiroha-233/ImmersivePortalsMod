// 本文件保存纯规划得到的逻辑连接、物理端点占用和校验结果。
package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DimensionStackPlan(
    DimensionStackDefinition definition,
    List<Connection> connections,
    Map<Endpoint, Integer> endpointUseCounts,
    List<DimensionStackError> errors
) {
    public DimensionStackPlan {
        connections = List.copyOf(connections);
        endpointUseCounts = Collections.unmodifiableMap(new LinkedHashMap<>(endpointUseCounts));
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public EndpointStatus getEndpointStatus(int entryIndex, LogicalDirection direction) {
        if (entryIndex < 0 || entryIndex >= definition.entries().size()) {
            return EndpointStatus.NONE;
        }

        DimensionStackDefinition.Entry entry = definition.entries().get(entryIndex);
        boolean hasNeighbour = definition.loop() || switch (direction) {
            case PREVIOUS -> entryIndex > 0;
            case NEXT -> entryIndex + 1 < definition.entries().size();
        };
        boolean enabled = hasNeighbour && switch (direction) {
            case PREVIOUS -> entry.connectsPrevious();
            case NEXT -> entry.connectsNext();
        };
        if (!enabled) {
            return EndpointStatus.NONE;
        }

        Endpoint endpoint = new Endpoint(
            entry.dimensionId(),
            direction == LogicalDirection.PREVIOUS
                ? previousFace(entry.flipped())
                : nextFace(entry.flipped())
        );
        return endpointUseCounts.getOrDefault(endpoint, 0) > 1
            ? EndpointStatus.CONFLICT
            : EndpointStatus.ENABLED;
    }

    public static ConnectorFace previousFace(boolean flipped) {
        return flipped ? ConnectorFace.FLOOR : ConnectorFace.CEILING;
    }

    public static ConnectorFace nextFace(boolean flipped) {
        return flipped ? ConnectorFace.CEILING : ConnectorFace.FLOOR;
    }

    public enum ConnectorFace {
        CEILING,
        FLOOR
    }

    public enum LogicalDirection {
        PREVIOUS,
        NEXT
    }

    public enum EndpointStatus {
        NONE,
        ENABLED,
        CONFLICT
    }

    public record Endpoint(String dimensionId, ConnectorFace face) {}

    public record Connection(
        int edgeIndex,
        int beforeEntryIndex,
        int afterEntryIndex,
        Endpoint forwardOrigin,
        Endpoint reverseOrigin,
        double forwardScale,
        boolean inverted,
        double horizontalRotation,
        boolean forwardEnabled,
        boolean reverseEnabled
    ) {}
}
