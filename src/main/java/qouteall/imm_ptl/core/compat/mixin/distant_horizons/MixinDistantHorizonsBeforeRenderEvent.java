// 允许 Distant Horizons 在门户目标维度的渲染上下文中提交 LOD。
package qouteall.imm_ptl.core.compat.mixin.distant_horizons;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor." +
        "AbstractImmersivePortalsAccessor$BeforeRenderEvent",
    remap = false
)
public class MixinDistantHorizonsBeforeRenderEvent {
    @ModifyExpressionValue(
        method = "beforeRender",
        at = @At(
            value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/" +
                "IImmersivePortalsAccessor;isRenderingPortal()Z",
            remap = false
        ),
        remap = false
    )
    private boolean allowDistantHorizonsInPortalRender(boolean isPortalRender) {
        // DH cancels its render event for portal passes after iPortal has already installed target-world state.
        return !PortalRendering.isRendering() && isPortalRender;
    }
}
