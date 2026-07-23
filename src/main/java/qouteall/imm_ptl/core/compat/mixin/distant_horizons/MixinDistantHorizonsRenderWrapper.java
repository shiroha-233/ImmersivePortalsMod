// 在门户渲染期间让 Distant Horizons 从已切换的目标相机读取精确位置。
package qouteall.imm_ptl.core.compat.mixin.distant_horizons;

import com.seibel.distanthorizons.core.util.math.DhVec3d;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IImmersivePortalsAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.common.wrappers.minecraft." +
        "MinecraftRenderWrapper_fabric",
    remap = false
)
public class MixinDistantHorizonsRenderWrapper {
    @Redirect(
        method = "getCameraExactPosition",
        at = @At(
            value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/" +
                "IImmersivePortalsAccessor;getActualCameraPos()" +
                "Lcom/seibel/distanthorizons/core/util/math/DhVec3d;",
            remap = false
        ),
        remap = false
    )
    private DhVec3d getTargetCameraPos(IImmersivePortalsAccessor accessor) {
        return PortalRendering.isRendering() ? null : accessor.getActualCameraPos();
    }
}
