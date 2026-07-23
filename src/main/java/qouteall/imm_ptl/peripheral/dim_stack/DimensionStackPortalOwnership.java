// 本文件集中定义维度堆叠门户的归属标签、槽位识别和旧门户精确匹配规则。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.shape.PortalShapeSerialization;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class DimensionStackPortalOwnership {
    private static final String TAG_PREFIX = "imm_ptl:dimension_stack/v2/";

    private DimensionStackPortalOwnership() {}

    public static String tag(DimensionStackPlan.Endpoint endpoint) {
        return TAG_PREFIX
            + endpoint.dimensionId()
            + "/"
            + endpoint.face().name().toLowerCase(Locale.ROOT);
    }

    public static boolean isOwned(Portal portal) {
        return portal.portalTag != null && portal.portalTag.startsWith(TAG_PREFIX);
    }

    public static Optional<DimensionStackPlan.Endpoint> findEndpoint(
        ServerLevel world,
        Portal portal
    ) {
        if (!(portal instanceof VerticalConnectingPortal)) {
            return Optional.empty();
        }
        double normalY;
        try {
            normalY = portal.getNormal().y;
        }
        catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (Math.abs(normalY) < 0.9) {
            return Optional.empty();
        }
        DimensionStackPlan.ConnectorFace face = normalY > 0
            ? DimensionStackPlan.ConnectorFace.FLOOR
            : DimensionStackPlan.ConnectorFace.CEILING;
        return Optional.of(new DimensionStackPlan.Endpoint(
            world.dimension().location().toString(), face
        ));
    }

    public static boolean structurallyMatches(Portal existing, Portal expected) {
        if (existing.getType() != expected.getType() || existing.level() != expected.level()) {
            return false;
        }
        try {
            if (normalizedNbt(existing).equals(normalizedNbt(expected))) {
                return true;
            }

            PortalExtension existingExtension = PortalExtension.get(existing);
            PortalExtension expectedExtension = PortalExtension.get(expected);
            return Objects.equals(existing.getDestDim(), expected.getDestDim())
                && close(existing.getOriginPos(), expected.getOriginPos())
                && close(existing.getDestPos(), expected.getDestPos())
                && close(existing.getAxisW(), expected.getAxisW())
                && close(existing.getAxisH(), expected.getAxisH())
                && close(existing.getWidth(), expected.getWidth())
                && close(existing.getHeight(), expected.getHeight())
                && close(existing.getThickness(), expected.getThickness())
                && close(existing.getScale(), expected.getScale())
                && DQuaternion.isClose(existing.getRotationD(), expected.getRotationD())
                && existing.isFuseView() == expected.isFuseView()
                && existing.isTeleportable() == expected.isTeleportable()
                && existing.isInteractable() == expected.isInteractable()
                && existing.isCrossPortalCollisionEnabled()
                    == expected.isCrossPortalCollisionEnabled()
                && existing.isDoRenderPlayer() == expected.isDoRenderPlayer()
                && existing.isVisible() == expected.isVisible()
                && existing.isRenderingMergable() == expected.isRenderingMergable()
                && existing.getTeleportChangesScale() == expected.getTeleportChangesScale()
                && existing.getTeleportChangesGravity() == expected.getTeleportChangesGravity()
                && Objects.equals(existing.specificPlayerId, expected.specificPlayerId)
                && Objects.equals(existing.getCommandsOnTeleported(), expected.getCommandsOnTeleported())
                && PortalShapeSerialization.serialize(existing.getPortalShape()).equals(
                    PortalShapeSerialization.serialize(expected.getPortalShape())
                )
                && existingExtension.adjustPositionAfterTeleport
                    == expectedExtension.adjustPositionAfterTeleport
                && existingExtension.bindCluster == expectedExtension.bindCluster
                && close(existingExtension.motionAffinity, expectedExtension.motionAffinity)
                && Objects.equals(existingExtension.reversePortalId, expectedExtension.reversePortalId)
                && Objects.equals(existingExtension.flippedPortalId, expectedExtension.flippedPortalId)
                && Objects.equals(existingExtension.parallelPortalId, expectedExtension.parallelPortalId)
                && animationNbt(existing).equals(animationNbt(expected));
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean close(net.minecraft.world.phys.Vec3 first, net.minecraft.world.phys.Vec3 second) {
        return first.distanceToSqr(second) < 1.0e-8;
    }

    private static boolean close(double first, double second) {
        double magnitude = Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= magnitude * 1.0e-8;
    }

    private static CompoundTag animationNbt(Portal portal) {
        CompoundTag tag = new CompoundTag();
        portal.animation.writeToTag(tag);
        return tag;
    }

    private static CompoundTag normalizedNbt(Portal portal) {
        CompoundTag tag = portal.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        tag.remove("portalTag");
        return tag;
    }
}
