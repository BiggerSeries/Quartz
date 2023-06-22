package net.roguelogix.quartz.internal.vk;

import org.lwjgl.vulkan.VK;

import static net.roguelogix.quartz.internal.QuartzCore.LOGGER;
import static org.lwjgl.vulkan.VK13.*;

public class VKStatics {
    public static final boolean AVAILABLE = checkVkAvailable();
    
    
    private static boolean checkVkAvailable() {
        if (!VKConfig.INSTANCE.enable) {
            LOGGER.info("Vulkan core disabled");
            return false;
        }
        
        try {
            if (VK.getInstanceVersionSupported() < VK_API_VERSION_1_3) {
                return false;
            }
        } catch (Throwable e) {
            LOGGER.info("Failed to load vulkan classes");
            return false;
        }
        
        LOGGER.info("Vulkan core available");
        return true;
    }
}
