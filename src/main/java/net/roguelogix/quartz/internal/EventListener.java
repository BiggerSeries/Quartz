package net.roguelogix.quartz.internal;

import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.roguelogix.phosphophyllite.registry.ClientOnly;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

@ClientOnly
@NonnullDefault
public class EventListener {
    
    @OnModLoad
    private static void onModLoad() {
        ModLoadingContext.get().getActiveContainer().getEventBus().addListener(EventListener::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                QuartzCore.startup();
            } catch (Throwable e){
                Minecraft.getInstance().emergencySaveAndCrash(new CrashReport("Quartz startup exception", e));
            }
        });
    }
    
    public static void initQuartz() {
    }
    
    static {
        if (!DatagenModLoader.isRunningDataGen()) {
            try {
                QuartzCore.init();
            } catch (Throwable e) {
                Minecraft.getInstance().emergencySaveAndCrash(new CrashReport("Quartz failed to startup", e));
            }
        }
    }
}
