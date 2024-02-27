package net.roguelogix.quartz.testing;

import net.roguelogix.phosphophyllite.Phosphophyllite;
import net.roguelogix.phosphophyllite.config.ConfigManager;
import net.roguelogix.phosphophyllite.config.ConfigType;
import net.roguelogix.phosphophyllite.config.ConfigValue;
import net.roguelogix.phosphophyllite.registry.IgnoreRegistration;
import net.roguelogix.phosphophyllite.registry.RegisterConfig;
import net.roguelogix.quartz.QuartzConfig;

public class QuartzTestingConfig {
    @IgnoreRegistration
    @RegisterConfig(folder = Phosphophyllite.modid, name = "quartz-testing", type = ConfigType.CLIENT)
    public static final QuartzTestingConfig INSTANCE = new QuartzTestingConfig();
    
    static {
        if (QuartzConfig.INIT_COMPLETED) {
            try {
                // this needs to be registered extra extra early, so it can be read at quartz init
                ConfigManager.registerConfig(INSTANCE, "blockstates/quartz", QuartzTestingConfig.class.getField("INSTANCE").getAnnotation(RegisterConfig.class));
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
                throw new IllegalStateException();
            }
        }
    }

    @ConfigValue
    public final boolean Enabled;
    @ConfigValue
    public final boolean AutoRun;
    
    {
        Enabled = false;
        AutoRun = false;
    }
}
