package net.roguelogix.quartz.testing.tests.common;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.roguelogix.phosphophyllite.debug.DebugInfo;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.QuartzInternalEvent;
import net.roguelogix.quartz.internal.gl46.GL46Core;
import net.roguelogix.quartz.testing.QuartzTest;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static net.roguelogix.quartz.testing.tests.Util.*;

@NonnullDefault
public abstract class ScreenshotCompareTest extends QuartzTest {
    
    
    
    
    protected final Vector3ic testPosition;
    protected final BlockPos testPositionBlockPos;
    
    private int frame;
    
    protected ScreenshotCompareTest(String id, Vector3ic testPosition) {
        super(id);
        this.testPosition = new Vector3i(testPosition);
        this.testPositionBlockPos = new BlockPos(testPosition.x(), testPosition.y(), testPosition.z());
        
        for (Vector3ic screenshotPosition : screenshotPositions) {
            screenshotLoop.add(() -> {setEyePos(screenshotPosition);setLookCenter(testPosition.x(), testPosition.y(), testPosition.z());});
            screenshotLoop.add(this::takeScreenshot);
        }
        
        frameActions.add(WAIT_FRAME);
        frameActions.add(this::setupQuartz);
        frameActions.add(() -> currentScreenshotList = quartzScreenshots);
        frameActions.add(WAIT_FRAME);
        frameActions.addAll(screenshotLoop);
        frameActions.add(this::cleanupQuartz);
        frameActions.add(this::setupReference);
        frameActions.add(() -> currentScreenshotList = referenceScreenshots);
        // wait a few frames for the chunk rebuild
//        frameActions.add(WAIT_FRAME);
        frameActions.addAll(screenshotLoop);
        frameActions.add(this::cleanupReference);
        frameActions.add(WAIT_FRAME);
    }
    
    @Override
    public void setup() {

        hideHUD();
        setFOV(40);
        setFlying();
        
        
        setWindowSize(256, 256);
        
        teleportToInOverworld(0, 0, 0);
        
        frame = 0;
    }
    
    @Override
    public void cleanup() {
        
        quartzScreenshots.forEach(NativeImage::close);
        referenceScreenshots.forEach(NativeImage::close);
        quartzScreenshots.clear();
        referenceScreenshots.clear();
    }
    
    @Override
    public boolean running() {
        return frame < frameActions.size();
    }
    
    @Override
    public void frameStart() {
    
    }
    
    private final List<NativeImage> quartzScreenshots = new ReferenceArrayList<>();
    private final List<NativeImage> referenceScreenshots = new ReferenceArrayList<>();
    @Nullable
    private List<NativeImage> currentScreenshotList;
    
    private static final Runnable WAIT_FRAME = () -> {
    };
    
    private static final List<Vector3ic> screenshotPositions = new ReferenceArrayList<>();
    private final List<Runnable> screenshotLoop = new ReferenceArrayList<>();
    private final List<Runnable> frameActions = new ReferenceArrayList<>();
    
    static {
//        screenshotPositions.add(new Vector3i(-5, -5, -5));
//        screenshotPositions.add(new Vector3i(-5, -5, +5));
//        screenshotPositions.add(new Vector3i(-5, +5, -5));
//        screenshotPositions.add(new Vector3i(-5, +5, +5));
//        screenshotPositions.add(new Vector3i(+5, -5, -5));
//        screenshotPositions.add(new Vector3i(+5, -5, +5));
//        screenshotPositions.add(new Vector3i(+5, +5, -5));
        screenshotPositions.add(new Vector3i(+5, +5, +5));
    }
    
    private void takeScreenshot() {
        if (currentScreenshotList != null) {
            currentScreenshotList.add(screenshot());
        }
    }
    
    @Override
    public void frameEnd() {
        frameActions.get(frame).run();
        frame++;
        if (frame == frameActions.size()) {
            compareFrames();
        }
    }
    
    public void compareFrames() {
        for (int i = 0; i < referenceScreenshots.size(); i++) {
            var quartzScreenshot = quartzScreenshots.get(i);
            var referenceScreenshot = referenceScreenshots.get(i);
            var width = referenceScreenshot.getWidth();
            var height = referenceScreenshot.getHeight();
            var totalPixels = width * height;
            var quartzPixels = quartzScreenshot.pixels;
            var referencePixels = referenceScreenshot.pixels;
            var totalBytes = totalPixels * 4;
            for (int j = 0; j < totalBytes; j++) {
                final var quartzByte = Byte.toUnsignedInt(MemoryUtil.memGetByte(quartzPixels + (j)));
                final var referenceByte = Byte.toUnsignedInt(MemoryUtil.memGetByte(referencePixels + (j)));
                final var difference = Math.abs(quartzByte - referenceByte);
                // ~1.5% error allowed, there can be tiny differences
                if (difference > 4) {
                    var pixelIndex = j >> 2;
                    var w = pixelIndex % width;
                    var h = pixelIndex / width;
                    fail("Pixel (" + w + ", " + h + ") different in screenshot " + i + " difference " + difference + " in byte " + (pixelIndex & 0b11));
                    return;
                }
            }
        }
    }
    
    @Override
    public void feedbackCollected(QuartzInternalEvent.FeedbackCollected event) {
    
    }
    
    protected abstract void setupReference();
    protected abstract void cleanupReference();
    
    protected abstract void setupQuartz();
    protected abstract void cleanupQuartz();
}
