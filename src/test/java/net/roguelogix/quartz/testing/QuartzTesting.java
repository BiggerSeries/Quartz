package net.roguelogix.quartz.testing;

import com.mojang.blaze3d.platform.DebugMemoryUntracker;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlDebug;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.QuartzEvent;
import net.roguelogix.quartz.internal.QuartzInternalEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.GLDebugMessageCallbackI;
import org.lwjgl.opengl.KHRDebug;

import java.util.Collection;

import static net.roguelogix.quartz.testing.tests.Util.sendChatMessage;
import static org.lwjgl.opengl.GL46C.*;

public class QuartzTesting {
    
    public static boolean TESTING_RUNNING = false;
    public static final boolean AUTOMATED = QuartzTestingConfig.INSTANCE.AutoRun;
    
    @OnModLoad
    private static void onModLoad() {
        if (!QuartzTestingConfig.INSTANCE.Enabled) {
            return;
        }
        Quartz.EVENT_BUS.addListener(QuartzTesting::onTestingStatusEvent);
        Quartz.EVENT_BUS.addListener(QuartzTesting::onFrameStart);
        Quartz.EVENT_BUS.addListener(QuartzTesting::onFrameEnd);
        Quartz.EVENT_BUS.addListener(QuartzTesting::onFeedbackCollected);
        ModLoadingContext.get().getActiveContainer().getEventBus().addListener(QuartzTesting::clientSetupEvent);
    }
    
    private static void onTestingStatusEvent(QuartzInternalEvent.TestingStatus testingStatus) {
        TESTING_RUNNING = testingStatus.running;
        sendChatMessage("Quartz testing " + (TESTING_RUNNING ? "started" : "finished"));
    }
    
    private static int nextTestIndex = 0;
    private static final ReferenceArrayList<QuartzTest> runningTests = new ReferenceArrayList<>();
    @Nullable
    private static QuartzTest activeTest = null;
    
    @Nullable
    public static QuartzTest runningTest() {
        return activeTest;
    }
    
    private static void clientSetupEvent(FMLClientSetupEvent setupEvent) {
        setupEvent.enqueueWork(() -> {
            var glCaps = GL.getCapabilities();
            
            if (!glCaps.GL_KHR_debug) {
                Minecraft.getInstance().emergencySaveAndCrash(new CrashReport("Fatal test failure", new IllegalStateException("GL_KHR_debug required to run Quartz tests")));
            }
            
            // inject into the debug callback to fatal fail on an error
            var myCallback = new GLDebugMessageCallbackI() {
                @Override
                public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
                    GlDebug.printDebugLog(source, type, id, severity, length, message, userParam);
                    if (source == GL_DEBUG_SOURCE_APPLICATION) {
                        throw new RuntimeException("Application GL error caught");
                    }
                    switch (type) {
                        case GL_DEBUG_TYPE_ERROR:
                        case GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR:
                        case GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR: {
                            QuartzTestRegistry.fatalTestFailure(GLDebugMessageCallback.getMessage(length, message));
                        }
                        case GL_DEBUG_TYPE_PORTABILITY: {
                        
                        }
                    }
                }
            };
            
            glEnable(GL_DEBUG_OUTPUT);
            glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
            KHRDebug.glDebugMessageCallback(GLX.make(GLDebugMessageCallback.create(myCallback), DebugMemoryUntracker::untrack), 0L);
        });
    }
    
    public static void runTests(Collection<QuartzTest> testList) {
        if (TESTING_RUNNING) {
            throw new IllegalStateException("Testing already running");
        }
        runningTests.clear();
        runningTests.addAll(testList);
        nextTestIndex = 0;
        Quartz.EVENT_BUS.post(new QuartzInternalEvent.TestingStatus(true));
    }
    
    private static void onFrameStart(QuartzEvent.FrameStart frameStart) {
        if (!TESTING_RUNNING) {
            return;
        }
        if (activeTest == null) {
            if (nextTestIndex < runningTests.size()) {
                activeTest = runningTests.get(nextTestIndex);
                QuartzTestRegistry.testStarted(activeTest);
                activeTest.reset();
                activeTest.setup();
                nextTestIndex++;
            } else {
                Quartz.EVENT_BUS.post(new QuartzInternalEvent.TestingStatus(false));
            }
        }
        if (activeTest == null) {
            return;
        }
        activeTest.frameStart();
    }
    
    private static void onFeedbackCollected(QuartzInternalEvent.FeedbackCollected feedbackEvent) {
        if (activeTest == null) {
            return;
        }
        activeTest.feedbackCollected(feedbackEvent);
    }
    
    private static void onFrameEnd(QuartzEvent.FrameEnd frameEnd) {
        if (activeTest == null) {
            return;
        }
        activeTest.frameEnd();
        if (activeTest.running()) {
            return;
        }
        activeTest.cleanup();
        QuartzTestRegistry.testCompleted(activeTest);
        activeTest = null;
    }
}
