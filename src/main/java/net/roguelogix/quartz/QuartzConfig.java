package net.roguelogix.quartz;

import net.minecraftforge.fml.loading.FMLLoader;
import net.roguelogix.phosphophyllite.Phosphophyllite;
import net.roguelogix.phosphophyllite.config.ConfigManager;
import net.roguelogix.phosphophyllite.config.ConfigType;
import net.roguelogix.phosphophyllite.config.ConfigValue;
import net.roguelogix.phosphophyllite.registry.IgnoreRegistration;
import net.roguelogix.phosphophyllite.registry.RegisterConfig;
import net.roguelogix.quartz.internal.QuartzDebug;
import net.roguelogix.quartz.internal.gl46.GL46Config;
import net.roguelogix.quartz.internal.vk.VKConfig;

public class QuartzConfig {
    
    @IgnoreRegistration
    @RegisterConfig(folder = Phosphophyllite.modid, name = "quartz", type = ConfigType.CLIENT)
    public static final QuartzConfig INSTANCE = new QuartzConfig();
    
    public static final boolean INIT_COMPLETED;
    
    private static boolean isValidPhosLoaading() {
        final var phosFileInfo = FMLLoader.getLoadingModList().getModFileById(Phosphophyllite.modid);
        if (phosFileInfo == null) {
            return false;
        }
        final var quartzFileInfo = FMLLoader.getLoadingModList().getModFileById(Quartz.modid);
        if (quartzFileInfo == null) {
            // this should always be false
            throw new IllegalStateException("Quartz not loading");
        }
        final var phosContainer = phosFileInfo.getMods().get(0);
        final var quartzContainer = quartzFileInfo.getMods().get(0);
        
        final var phosVersion = phosContainer.getVersion();
        final var quartzPhosDep = quartzContainer.getDependencies().stream().filter(dep -> dep.getModId().equals(Phosphophyllite.modid)).findAny().orElse(null);
        
        if (quartzPhosDep == null) {
            return false;
        }
        return quartzPhosDep.getVersionRange().containsVersion(phosVersion);
    }
    
    private static boolean setup() {
        if (!QuartzDebug.doesForgeExist()) {
            // loading without forge present
            return false;
        }
        if (!isValidPhosLoaading()) {
            // in the event we have an invalid phos loading, we dont really care anymore
            return false;
        }
        try {
            // this needs to be registered extra extra early, so it can be read at quartz init
            ConfigManager.registerConfig(INSTANCE, "quartz", QuartzConfig.class.getField("INSTANCE").getAnnotation(RegisterConfig.class));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
        return true;
    }
    
    static {
        INIT_COMPLETED = setup();
    }
    
    public enum Mode {
        Vulkan10,
        OpenGL46,
        OpenGL33,
        Automatic,
        ;
    }
    
    @ConfigValue(comment = "Enable debug features, may lower performance")
    public final boolean debug;
    
    @ConfigValue(comment = "Backend mode used by quartz\nAutomatic will try to use the best available, and fallback as necessary")
    public final Mode mode;
    
    {
        mode = Mode.Automatic;
        debug = false;
    }
    
    @ConfigValue(advanced = ConfigValue.BoolOption.True)
    public final VKConfig VK = VKConfig.INSTANCE;
    
    @ConfigValue(advanced = ConfigValue.BoolOption.True)
    public final GL46Config GL46 = GL46Config.INSTANCE;
}
