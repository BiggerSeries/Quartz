package net.roguelogix.quartz.internal;

import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraftforge.data.loading.DatagenModLoader;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.roguelogix.phosphophyllite.registry.ClientOnly;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

@ClientOnly
@NonnullDefault
public class EventListener {
    @OnModLoad
    private static void onModLoad() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventListener::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(QuartzCore::startup);
    }
    
    public static void initQuartz() {
    }
    
    static {
        if (!DatagenModLoader.isRunningDataGen()) {
            try {
                QuartzCore.init();
            } catch (Throwable e) {
                Minecraft.crash(new CrashReport("Quartz failed to startup", e));
            }
        }
    }
}
