package net.roguelogix.quartz;

import net.roguelogix.phosphophyllite.Phosphophyllite;
import net.roguelogix.phosphophyllite.config.ConfigManager;
import net.roguelogix.phosphophyllite.config.ConfigType;
import net.roguelogix.phosphophyllite.config.ConfigValue;
import net.roguelogix.quartz.internal.gl.GLConfig;
import net.roguelogix.quartz.internal.vk.VKConfig;
import net.roguelogix.phosphophyllite.registry.IgnoreRegistration;
import net.roguelogix.phosphophyllite.registry.RegisterConfig;

public class QuartzConfig {

    @IgnoreRegistration
    @RegisterConfig(folder = Phosphophyllite.modid, name = "quartz", type = ConfigType.CLIENT)
    public static final QuartzConfig INSTANCE = new QuartzConfig();
    
    static {
        try {
            // this needs to be registered extra extra early, so it can be read at quartz init
            ConfigManager.registerConfig(INSTANCE, "quartz", QuartzConfig.class.getField("INSTANCE").getAnnotation(RegisterConfig.class));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }
    
    public enum Mode {
        Vulkan10,
        OpenGL33,
        Automatic,
        ;
    }
    
    @ConfigValue(comment = "Backend mode used by quartz\nAutomatic will try to use the best available, and fallback as necessary")
    public final Mode mode;
    
    {
        mode = Mode.Automatic;
    }
    
    @ConfigValue(advanced = ConfigValue.BoolOption.True)
    public final GLConfig GL = GLConfig.INSTANCE;
    @ConfigValue(advanced = ConfigValue.BoolOption.True)
    public final VKConfig VK = VKConfig.INSTANCE;
}
