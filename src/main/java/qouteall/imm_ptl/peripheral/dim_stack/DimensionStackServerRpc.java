// 本文件处理维度堆叠命令的服务端鉴权、RPC 调度和结果反馈。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

import java.util.List;

public final class DimensionStackServerRpc {
    private DimensionStackServerRpc() {}

    public static void openEditor(ServerPlayer player) {
        List<String> dimensionIds = DimensionStackLifecycle.collectCandidates(player.server)
            .stream()
            .map(key -> key.location().toString())
            .toList();
        DimStackInfo activeStack = DimensionStackLifecycle.getActiveStack();
        if (activeStack == null) {
            activeStack = DimensionStackPreset.load();
        }
        DimensionStackEditorState editorState = new DimensionStackEditorState(
            dimensionIds, activeStack
        );
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.imm_ptl.peripheral.dim_stack.DimensionStackClientRpc.RemoteCallables.openScreen",
            editorState
        );
    }

    public static final class RemoteCallables {
        private RemoteCallables() {}

        public static void setup(ServerPlayer player, DimStackInfo stack) {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(
                    "You don't have permission to change dimension stack"
                ).withStyle(ChatFormatting.RED));
                return;
            }

            DimensionStackLifecycle.UpdateResult result =
                DimensionStackLifecycle.updateStack(player.server, stack);
            if (!result.isSuccess()) {
                sendFailure(player, result);
                return;
            }

            if (O_O.isDedicatedServer()) {
                DimensionStackPreset.save(stack);
            }
            player.displayClientMessage(
                Component.translatable("imm_ptl.dim_stack_established"), false
            );
        }

        public static void remove(ServerPlayer player) {
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(
                    "You don't have permission to change dimension stack"
                ).withStyle(ChatFormatting.RED));
                return;
            }

            boolean hasOwnedPortals = DimensionStackPortalCommitter.hasOwnedPortals(player.server);
            DimStackInfo legacyReference = hasOwnedPortals
                ? null
                : DimensionStackLifecycle.getActiveStack();
            if (legacyReference == null && !hasOwnedPortals) {
                legacyReference = DimensionStackPreset.loadRaw();
            }
            DimensionStackLifecycle.UpdateResult result =
                DimensionStackLifecycle.removeStack(player.server, legacyReference);
            if (!result.isSuccess()) {
                sendFailure(player, result);
                return;
            }

            if (O_O.isDedicatedServer()) {
                DimensionStackPreset.save(null);
            }
            player.displayClientMessage(
                Component.translatable("imm_ptl.dim_stack_removed"), false
            );
        }

        private static void sendFailure(
            ServerPlayer player,
            DimensionStackLifecycle.UpdateResult result
        ) {
            String detail = result.errors().isEmpty()
                ? "Unknown dimension stack error"
                : result.errors().get(0).message();
            player.sendSystemMessage(Component.literal(
                "Dimension stack update rejected: " + detail
            ).withStyle(ChatFormatting.RED));
        }
    }
}
