package net.roguelogix.quartz.internal.vk;

import net.roguelogix.phosphophyllite.config.ConfigValue;

public class VKConfig {
    public static final VKConfig INSTANCE = new VKConfig();
    
    public static class DebugOptions {
        
        @ConfigValue(comment = "Enables Vulkan validation layers")
        public final boolean enableValidationLayers;
        @ConfigValue(comment = "Enabled RobustBufferAccess VkPhysicalDeviceFeature")
        public final boolean robustBufferAccess;
        @ConfigValue(comment = "Allows wireframe rendering to be enabled")
        public final boolean allowWireFrame;
        
        {
            enableValidationLayers = false;
            robustBufferAccess = false;
            allowWireFrame = false;
        }
    }
    
    @ConfigValue
    public final boolean enable;
    
    {
        enable = false;
    }
    
    @ConfigValue(comment = """
            Debug and development options
            May hurt performance
            Should not be used unless necessary or otherwise directed
            Enabling some of these may cause vulkan initialization to fail, potentially not being logged
            """)
    public final DebugOptions debug = new DebugOptions();
    
    @ConfigValue(comment = """
            Number of framebuffers to allocate
            Allows for the CPU/GPU to be working on multiple frames at the same time
            Increasing this number increases host and device memory requirements for values that are updated every frame
            """,
            range = "[1,9]"
    )
    public final int inFlightFrames;
    @ConfigValue(comment = """
            Allows the use of a secondary thread for submission to the main graphics queue between frames
            """)
    public final boolean secondaryGraphicsQueueThread;
    
    {
        inFlightFrames = 3;
        secondaryGraphicsQueueThread = true;
    }
    
    @ConfigValue
    public final boolean useHostFrameCopy;
    
    {
        useHostFrameCopy = false;
    }
    
    @ConfigValue
    public final MemoryOptions memory = new MemoryOptions();
    
    public static class MemoryOptions {
        public enum AllocationStyle {
            BUDDY,
            PACKED
        }
        
        @ConfigValue
        public final AllocationStyle deviceMemorySubAllocationStyle;
        @ConfigValue
        public final AllocationStyle bufferSubAllocationStyle;
        
        {
            deviceMemorySubAllocationStyle = AllocationStyle.BUDDY;
            bufferSubAllocationStyle = AllocationStyle.PACKED;
        }
        
        @ConfigValue(comment = "Size of VK memory allocations for both the host and device\ndefault is 128MiB\nlarger sizes may help with overall memory usage")
        public final int memoryBlockSize;
        @ConfigValue(comment = "Minimum size allocated to a buffer, also serves serves as the minimum guaranteed alignment, will be rounded up to next power of two", range = "[16384,)")
        public final int minimumBufferSize;
        
        {
            memoryBlockSize = 1 << 28; // 128MiB
            minimumBufferSize = 1 << 16; // 64KiB
        }
        
    }
}
