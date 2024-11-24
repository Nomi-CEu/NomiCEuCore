package com.nomiceu.nomilabs.mixin.gregtech;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nomiceu.nomilabs.gregtech.mixinhelper.IRefreshBeforeConsumption;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.RecipeMap;

/**
 * Part of <a href="https://github.com/GregTechCEu/GregTech/pull/2646">GTCEu #2646</a> impl.
 */
@Mixin(value = RecipeMapMultiblockController.class, remap = false)
public abstract class RecipeMapMultiblockControllerMixin extends MultiblockWithDisplayBase
                                                         implements IRefreshBeforeConsumption {

    @Unique
    protected List<IRefreshBeforeConsumption> labs$refreshBeforeConsumptions;

    /**
     * Mandatory Ignored Constructor
     */
    public RecipeMapMultiblockControllerMixin(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Unique
    @Override
    public void labs$refreshBeforeConsumption() {
        if (labs$refreshBeforeConsumptions == null) return;
        for (var refresh : labs$refreshBeforeConsumptions) {
            refresh.labs$refreshBeforeConsumption();
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initRefresh(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, CallbackInfo ci) {
        labs$refreshBeforeConsumptions = new ArrayList<>();
    }

    @Inject(method = "initializeAbilities", at = @At("RETURN"))
    private void initRefresh(CallbackInfo ci) {
        for (IMultiblockPart part : getMultiblockParts()) {
            if (part instanceof IRefreshBeforeConsumption refresh) {
                labs$refreshBeforeConsumptions.add(refresh);
            }
        }
    }

    @Inject(method = "resetTileAbilities", at = @At("RETURN"))
    private void resetRefresh(CallbackInfo ci) {
        labs$refreshBeforeConsumptions.clear();
    }
}
