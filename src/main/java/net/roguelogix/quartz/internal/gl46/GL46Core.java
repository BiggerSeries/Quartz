package net.roguelogix.quartz.internal.gl46;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.internal.Buffer;
import net.roguelogix.quartz.internal.IrisDetection;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.DrawInfo;
import org.joml.Matrix4f;
import org.lwjgl.opengl.KHRDebug;

import java.util.List;

import static org.lwjgl.opengl.GL46C.glFinish;

@NonnullDefault
public class GL46Core extends QuartzCore {
    
    public static final GL46Core INSTANCE;
    
    static {
        if (GL46Statics.AVAILABLE) {
            try {
                LOGGER.info("Quartz initializing GL46Core");
                KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_THIRD_PARTY, 0, "Quartz GL46 Renderer Setup");
                INSTANCE = new GL46Core();
                LOGGER.info("Quartz GL46Core initialized");
            } finally {
                KHRDebug.glPopDebugGroup();
            }
        } else {
            // null signals not available
            //noinspection DataFlowIssue
            INSTANCE = null;
            LOGGER.info("GL46 not available");
        }
    }
    
    private int frameInFlight;
    private long lastTimeNano = 0;
    public final DrawInfo drawInfo = new DrawInfo();
    @Override
    protected void startupInternal() {
        GL46ComputePrograms.startup();
        GL46FeedbackPrograms.startup();
        GL46LightEngine.startup();
        GL46FeedbackDrawing.startup();
    }
    
    @Override
    protected void shutdownInternal() {
        GL46FeedbackDrawing.shutdown();
        GL46LightEngine.shutdown();
        GL46FeedbackPrograms.shutdown();
        GL46ComputePrograms.shutdown();
    }
    
    @Override
    protected void resourcesReloadedInternal() {
        GL46FeedbackPrograms.reload();
        GL46ComputePrograms.reload();
//        GL46LightEngine.dirtyAll();
    }
    
    @Override
    public DrawBatch createDrawBatch() {
        return GL46FeedbackDrawing.createDrawBatch();
    }
    
    @Override
    public Buffer allocBuffer(int options) {
        return new GL46Buffer(options);
    }
    
    @Override
    public void frameStart(PoseStack pMatrixStack, float pPartialTicks, long pFinishTimeNano, boolean pDrawBlockOutline, Camera pActiveRenderInfo, GameRenderer pGameRenderer, LightTexture pLightmap, Matrix4f pProjection) {
        deletionQueue.runAll();
        
        frameInFlight++;
        frameInFlight %= GL46Statics.FRAMES_IN_FLIGHT;
        
        GL46FeedbackDrawing.aboutToBeginFrame();
        
        long timeNanos = System.nanoTime();
        long deltaNano = timeNanos - lastTimeNano;
        lastTimeNano = timeNanos;
        if (lastTimeNano == 0) {
            deltaNano = 0;
        }
        
        var playerPosition = pActiveRenderInfo.getPosition();
        drawInfo.playerPosition.set((int) playerPosition.x, (int) playerPosition.y, (int) playerPosition.z);
        drawInfo.playerPositionNegative.set(drawInfo.playerPosition).negate();
        drawInfo.playerSubBlock.set(playerPosition.x - (int) playerPosition.x, playerPosition.y - (int) playerPosition.y, playerPosition.z - (int) playerPosition.z);
        drawInfo.playerSubBlockNegative.set(drawInfo.playerSubBlockNegative).negate();
        
        drawInfo.projectionMatrix.set(pProjection);
        drawInfo.projectionMatrix.mul(pMatrixStack.last().pose());
        drawInfo.projectionMatrix.get(drawInfo.projectionMatrixFloatBuffer);
        
        drawInfo.mojPose = pMatrixStack.last();
        
        drawInfo.deltaNano = deltaNano;
        drawInfo.partialTicks = pPartialTicks;
        
        GL46FeedbackDrawing.beginFrame();
    }
    
    @Override
    public void lightUpdated() {
        GL46LightEngine.update(Minecraft.getInstance().level);
        
        if(!GL46FeedbackDrawing.hasBatch()){
            return;
        }
        
        GL46FeedbackDrawing.collectAllFeedback(IrisDetection.areShadersActive());
    }
    
    @Override
    public void preTerrainSetup() {
    }
    
    @Override
    public void shadowPass(PoseStack modelView, Matrix4f projectionMatrix) {
        if(!GL46FeedbackDrawing.hasBatch()){
            return;
        }
        GL46FeedbackDrawing.setMatrices(projectionMatrix, modelView.last().pose());
        GL46FeedbackDrawing.getActiveRenderTypes().forEach(GL46FeedbackDrawing::drawRenderType);
    }
    
    @Override
    public void preOpaque() {
        if(!GL46FeedbackDrawing.hasBatch()){
            return;
        }
        
        BufferUploader.invalidate();
        IrisDetection.bindIrisFramebuffer();
        
        GL46FeedbackDrawing.setMatrices(RenderSystem.getProjectionMatrix(), drawInfo.mojPose.pose());
        
        for (final var renderType : GL46FeedbackDrawing.getActiveRenderTypes()) {
            if (!(renderType instanceof RenderType.CompositeRenderType compositeRenderType)) {
                continue;
            }
            if(compositeRenderType.state().transparencyState != RenderStateShard.NO_TRANSPARENCY) {
                continue;
            }
            GL46FeedbackDrawing.drawRenderType(renderType);
        }
    }
    
    @Override
    public void endOpaque() {
        if(!GL46FeedbackDrawing.hasBatch()){
            return;
        }
        
        BufferUploader.invalidate();
        
        for (final var renderType : GL46FeedbackDrawing.getActiveRenderTypes()) {
            if (!(renderType instanceof RenderType.CompositeRenderType compositeRenderType)) {
                continue;
            }
            if(compositeRenderType.state().transparencyState == RenderStateShard.NO_TRANSPARENCY) {
                continue;
            }
            RenderSystem.depthMask(false);
            GL46FeedbackDrawing.drawRenderType(renderType);
        }
        RenderSystem.depthMask(true);
    }
    
    @Override
    public void endTranslucent() {
    
    }
    
    @Override
    public void waitIdle() {
        glFinish();
    }
    
    @Override
    public int frameInFlight() {
        return frameInFlight;
    }
    
    @Override
    public void sectionDirty(int x, int y, int z) {
        GL46LightEngine.sectionDirty(x, y, z);
    }
    
    @Override
    public void allSectionsDirty() {
        GL46LightEngine.dirtyAll();
        GL46FeedbackDrawing.dirtyAll();
    }
    
    @Override
    public void addDebugText(List<String> list) {
        list.add("Quartz backend: OpenGL 4.6");
        list.add("Spare texture: " + (GL46Statics.SPARSE_TEXTURE_ENABLED ? "enabled" : "disabled"));
    }
}
