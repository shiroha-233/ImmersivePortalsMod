// 本文件处理维度堆叠编辑器的客户端 RPC 入口与提交调用。
package qouteall.imm_ptl.peripheral.dim_stack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class DimensionStackClientRpc {
    private DimensionStackClientRpc() {}

    public static final class RemoteCallables {
        private RemoteCallables() {}

        public static void openScreen(DimensionStackEditorState editorState) {
            List<ResourceKey<Level>> dimensionList = editorState.dimensionIds().stream()
                .map(Helper::dimIdToKey)
                .toList();

            DimStackGuiController controller = new DimStackGuiController(
                null,
                () -> dimensionList,
                stack -> {
                    if (stack == null) {
                        McRemoteProcedureCall.tellServerToInvoke(
                            "qouteall.imm_ptl.peripheral.dim_stack.DimensionStackServerRpc.RemoteCallables.remove"
                        );
                    }
                    else {
                        McRemoteProcedureCall.tellServerToInvoke(
                            "qouteall.imm_ptl.peripheral.dim_stack.DimensionStackServerRpc.RemoteCallables.setup",
                            stack
                        );
                    }
                    Minecraft.getInstance().setScreen(null);
                }
            );
            controller.initialize(editorState.activeStack());
            Minecraft.getInstance().setScreen(controller.view);
        }
    }
}
