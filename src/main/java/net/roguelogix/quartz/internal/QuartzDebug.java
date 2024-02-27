package net.roguelogix.quartz.internal;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.roguelogix.quartz.QuartzConfig;

public class QuartzDebug {
    public static final boolean DEBUG;
    
    static {
        if (!Util.doesForgeExist() || Util.runningDatagen()) {
            DEBUG = false;
        } else {
            DEBUG = QuartzCore.TESTING_ALLOWED || QuartzConfig.INSTANCE.debug;
        }
    }
    
    public static class Util {
        public static boolean runningDatagen() {
            try {
                return DatagenModLoader.isRunningDataGen();
            } catch (Throwable e) {
                return false;
            }
        }
        
        public static boolean doesForgeExist() {
            try {
                return FMLLoader.getLoadingModList() != null;
            } catch (Throwable e) {
                return false;
            }
        }
    }
}
