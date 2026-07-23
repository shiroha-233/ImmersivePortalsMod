// 在门户渲染期间让 Distant Horizons 从已切换的目标世界读取玩家状态。
package qouteall.imm_ptl.core.compat.mixin.distant_horizons;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IImmersivePortalsAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.common.wrappers.minecraft." +
        "MinecraftClientWrapper_fabric",
    remap = false
)
public class MixinDistantHorizonsClientWrapper {
    @Redirect(
        method = "getPlayerBlockPos",
        at = @At(
            value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/" +
                "IImmersivePortalsAccessor;getActualPlayerBlockPos()" +
                "Lcom/seibel/distanthorizons/core/pos/blockPos/DhBlockPos;",
            remap = false
        ),
        remap = false
    )
    private DhBlockPos getTargetPlayerBlockPos(IImmersivePortalsAccessor accessor) {
        return PortalRendering.isRendering() ? null : accessor.getActualPlayerBlockPos();
    }

    @Redirect(
        method = "getPlayerChunkPos",
        at = @At(
            value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/" +
                "IImmersivePortalsAccessor;getActualPlayerChunkPos()" +
                "Lcom/seibel/distanthorizons/core/pos/DhChunkPos;",
            remap = false
        ),
        remap = false
    )
    private DhChunkPos getTargetPlayerChunkPos(IImmersivePortalsAccessor accessor) {
        return PortalRendering.isRendering() ? null : accessor.getActualPlayerChunkPos();
    }

    @Redirect(
        method = "getWrappedClientLevel(Z)Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;",
        at = @At(
            value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/" +
                "IImmersivePortalsAccessor;getActualClientLevelWrapper()" +
                "Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;",
            remap = false
        ),
        remap = false
    )
    private IClientLevelWrapper getTargetClientLevel(IImmersivePortalsAccessor accessor) {
        return PortalRendering.isRendering() ? null : accessor.getActualClientLevelWrapper();
    }
}
