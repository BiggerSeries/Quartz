package net.roguelogix.quartz.internal;

import net.minecraftforge.data.loading.DatagenModLoader;
import net.minecraftforge.fml.loading.FMLLoader;
import net.roguelogix.quartz.QuartzConfig;

public class QuartzDebug {
    public static final boolean DEBUG;
    
    static {
        if (!doesForgeExist() || runningDatagen()) {
            DEBUG = false;
        } else {
            DEBUG = QuartzConfig.INSTANCE.debug;
        }
    }
    
    public static boolean runningDatagen(){
        try{
            return DatagenModLoader.isRunningDataGen();
        } catch (Throwable e){
            return false;
        }
    }
    
    public static boolean doesForgeExist(){
        try{
            return FMLLoader.getLoadingModList() != null;
        } catch (Throwable e){
            return false;
        }
    }
}
