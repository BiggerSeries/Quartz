package net.roguelogix.quartz.internal;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.phosphophyllite.threading.WorkQueue;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.QuartzConfig;
import net.roguelogix.quartz.QuartzEvent;
import net.roguelogix.quartz.internal.common.InternalMesh;
import net.roguelogix.quartz.internal.gl33.GL33Core;
import net.roguelogix.quartz.internal.gl46.GL46Core;
import net.roguelogix.quartz.internal.vk.VKCore;
import net.roguelogix.quartz.internal.world.WorldEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.Cleaner;
import java.util.List;

@NonnullDefault
public abstract class QuartzCore {
    
    public static final Logger LOGGER = LogManager.getLogger("Quartz");
    public static final boolean DEBUG;
    
    @Nonnull
    public static final QuartzCore INSTANCE;
    public static final Cleaner CLEANER = Cleaner.create();
    public static final WorkQueue deletionQueue = new WorkQueue();
    
    public static void mainThreadClean(Object referent, Runnable cleanFunc) {
        CLEANER.register(referent, () -> deletionQueue.enqueueUntracked(cleanFunc));
    }
    
    static {
        if (!Thread.currentThread().getStackTrace()[2].getClassName().equals(EventListener.class.getName())) {
            throw new IllegalStateException("Attempt to init quartz before it is ready");
        }
        LOGGER.info("Quartz Init");
        if (QuartzConfig.INSTANCE.debug) {
            LOGGER.warn("Debug mode enabled, performance may suffer");
        }
        DEBUG = QuartzConfig.INSTANCE.debug;
        QuartzCore instance = null;
        try {
            instance = createCore(QuartzConfig.INSTANCE.mode);
            if (instance == null && QuartzConfig.INSTANCE.mode != QuartzConfig.Mode.Automatic) {
                LOGGER.error("Failed to create QuartzCore of requested type, attempting automatic creation");
                instance = createCore(QuartzConfig.Mode.Automatic);
            }
            if (instance == null) {
                throw new IllegalStateException("QuartzCore failed to load, this shouldn't be possible");
            }
        } catch (NoClassDefFoundError e) {
            if (!e.getMessage().contains("phosphophyllite")) {
                throw e;
            }
            // Phosphophyllite isn't present, print but ignore
            e.printStackTrace();
        }
        // in the event this is null, phos isn't present
        //noinspection ConstantConditions
        INSTANCE = instance;
    }
    
    @Nullable
    private static QuartzCore createCore(QuartzConfig.Mode mode) {
        return switch (mode) {
            case Vulkan10 -> VKCore.INSTANCE;
            case OpenGL46 -> GL46Core.INSTANCE;
            case OpenGL33 -> GL33Core.INSTANCE;
            case Automatic -> {
                for (QuartzConfig.Mode value : QuartzConfig.Mode.values()) {
                    if (value == QuartzConfig.Mode.Automatic) {
                        yield null;
                    }
                    final var core = createCore(value);
                    if (core != null) {
                        yield core;
                    }
                }
                yield null;
            }
            default -> null;
        };
    }
    
    static void init() {
    }
    
    private static boolean wasInit = false;
    
    static void startup() {
        INSTANCE.startupInternal();
        Quartz.EVENT_BUS.post(new QuartzEvent.Startup());
        wasInit = true;
        MinecraftForge.EVENT_BUS.addListener(QuartzCore::addDebugTextEvent);
    }
    
    private static void addDebugTextEvent(CustomizeGuiOverlayEvent.DebugText debugTextEvent) {
        if (!Minecraft.getInstance().options.renderDebug) {
            return;
        }
        final var list = debugTextEvent.getRight();
        list.add("");
        INSTANCE.addDebugText(list);
        list.add("");
    }
    
    private static final ReferenceSet<ResourceLocation> modelsToRegister = new ReferenceArraySet<>();
    
    public static void registerModel(ResourceLocation modelLocation) {
        modelsToRegister.add(modelLocation);
    }
    
    public static void onModelRegisterEvent(ModelEvent.RegisterAdditional event) {
        modelsToRegister.forEach(event::register);
    }
    
    @OnModLoad
    private static void onModLoad() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(QuartzCore::onModelRegisterEvent);
    }
    
    protected abstract void startupInternal();
    
    public static void shutdown() {
        if (!wasInit) {
            return;
        }
        INSTANCE.entityBatch = null;
        Quartz.EVENT_BUS.post(new QuartzEvent.Shutdown());
        INSTANCE.shutdownInternal();
        // clean everything up, hopefully
        do {
            System.gc();
        } while (deletionQueue.runAll());
        System.gc();
    }
    
    protected abstract void shutdownInternal();
    
    public static void resourcesReloaded() {
        if (!wasInit) {
            return;
        }
        INSTANCE.meshManager.buildAllMeshes();
        INSTANCE.resourcesReloadedInternal();
    }
    
    protected abstract void resourcesReloadedInternal();
    
    @Nullable
    public DrawBatch entityBatch = null;
    public final WorldEngine worldEngine = new WorldEngine();
    public final InternalMesh.Manager meshManager = new InternalMesh.Manager(allocBuffer(false));
    
    public WorldEngine getWorldEngine() {
        return worldEngine;
    }
    
    public abstract DrawBatch createDrawBatch();
    
    public DrawBatch getEntityBatcher() {
        if (entityBatch == null){
            entityBatch = createDrawBatch();
        }
        return entityBatch;
    }
    
    public abstract Buffer allocBuffer(boolean GPUOnly);
    
    public abstract void frameStart(PoseStack pMatrixStack, float pPartialTicks, long pFinishTimeNano, boolean pDrawBlockOutline, Camera pActiveRenderInfo, GameRenderer pGameRenderer, LightTexture pLightmap, Matrix4f pProjection);
    
    /**
     * This is abstract because VK can handle the writes being done on a separate thread
     */
    public abstract void lightUpdated();
    
    public abstract void preTerrainSetup();
    
    public abstract void shadowPass(PoseStack modelView, Matrix4f projectionMatrix);
    
    public abstract void preOpaque();
    
    public abstract void endOpaque();
    
    public abstract void endTranslucent();
    
    public abstract void waitIdle();
    
    public abstract int frameInFlight();
    
    public abstract void sectionDirty(int x, int y, int z);
    
    public abstract void addDebugText(List<String> list);
}
