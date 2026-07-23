// 本文件把维度堆叠编辑入口接入创建世界界面。
package qouteall.imm_ptl.peripheral.mixin.client.dim_stack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackGuiController;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackInfo;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackLifecycle;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackPreset;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackAPI;
import qouteall.imm_ptl.peripheral.ducks.IECreateWorldScreen;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen_CVB extends Screen implements IECreateWorldScreen {
    
    @Shadow
    @Final
    private static Logger LOGGER;
    
    @Shadow
    @Final
    private WorldCreationUiState uiState;
    
    @Nullable
    private DimStackGuiController ip_dimStackController;
    @Nullable
    private DimStackInfo ip_selectedDimStack;
    
    protected MixinCreateWorldScreen_CVB(Component title) {
        super(title);
        throw new RuntimeException();
    }
    
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void onInitEnd(
        Minecraft minecraft, Screen screen, WorldCreationContext worldCreationContext,
        Optional<ResourceKey<WorldPreset>> optional, OptionalLong optionalLong, CallbackInfo ci
    ) {
        DimStackInfo preset = DimensionStackPreset.load();
        ip_selectedDimStack = preset;
        if (preset != null) {
            LOGGER.info("[ImmPtl] Applying dimension stack preset");
        }
    }

    @WrapOperation(
        method = "createNewWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;createLevelFromExistingSettings(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/WorldData;)V"
        )
    )
    private void onCreateNewWorld(
        WorldOpenFlows worldOpenFlows,
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        ReloadableServerResources dataPackResources,
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        WorldData worldData,
        Operation<Void> original
    ) {
        DimensionStackLifecycle.setPendingStack(ip_selectedDimStack);
        try {
            original.call(
                worldOpenFlows,
                levelStorageAccess,
                dataPackResources,
                registryAccess,
                worldData
            );
        }
        catch (RuntimeException | Error exception) {
            DimensionStackLifecycle.setPendingStack(null);
            throw exception;
        }
    }
    
    @Override
    public void ip_openDimStackScreen() {
        if (ip_dimStackController == null) {
            CreateWorldScreen this_ = (CreateWorldScreen) (Object) this;
            ip_dimStackController = new DimStackGuiController(
                this_,
                () -> portal_getDimensionList(),
                info -> {
                    ip_selectedDimStack = info;
                    Minecraft.getInstance().setScreen(this_);
                }
            );
            ip_dimStackController.initializeAsDefault();
        }
        
        Minecraft.getInstance().setScreen(ip_dimStackController.view);
    }
    
    private List<ResourceKey<Level>> portal_getDimensionList() {
        Helper.log("Getting the dimension list");
        
        Set<ResourceKey<Level>> result = new LinkedHashSet<>();
        
        try {
            WorldCreationContext settings = uiState.getSettings();
            
            RegistryAccess.Frozen registryAccess = settings.worldgenLoadContext();
            
            WorldDimensions selectedDimensions = settings.selectedDimensions();
            
            // add vanilla dimensions
            for (var entry : selectedDimensions.dimensions().entrySet()) {
                result.add(Helper.dimIdToKey(entry.getKey().location()));
            }
            
            // add datapack dimensions
            for (var entry : settings.datapackDimensions().entrySet()) {
                result.add(Helper.dimIdToKey(entry.getKey().location()));
            }
            
            // add other dimensions via the event
            Collection<ResourceKey<Level>> other =
                DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT
                    .invoker().getExtraDimensionKeys(
                        registryAccess, settings.options()
                    );
            result.addAll(other);
        }
        catch (Exception e) {
            LOGGER.error("ImmPtl getting dimension list", e);
            if (result.isEmpty()) {
                result.add(Helper.dimIdToKey("error:error"));
            }
        }
        
        return new ArrayList<>(result);
    }
}
