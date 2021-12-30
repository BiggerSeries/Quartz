package net.roguelogix.quartz.internal.mixin;

import net.minecraft.client.ResourceLoadStateTracker;
import net.roguelogix.quartz.internal.QuartzCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourceLoadStateTracker.class)
public class ResourceReloadListener {
    @Inject(method = "finishReload", at = @At("HEAD"))
    public void finishReload(CallbackInfo ci) {
        QuartzCore.resourcesReloaded();
    }
}
