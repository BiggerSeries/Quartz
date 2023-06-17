package net.roguelogix.quartz.internal.gl33;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.CrashReport;
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
import org.joml.Vector3d;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.KHRDebug;

import static org.lwjgl.opengl.GL33C.*;

@NonnullDefault
public class GL33Core extends QuartzCore {
    
    public static final GL33Core INSTANCE;
    
    static {
        if (!GL33Statics.AVAILABLE) {
            LOGGER.error("OpenGL 3.3 implementation requirements not available, this is the fallback for Quartz, something is very wrong");
            final var builder = new StringBuilder();
            builder.append("Unable to initialize Quartz GL33Core due to missing GL capability, report this error!\n");
            builder.append("ISSUE URL -> https://github.com/BiggerSeries/Quartz/issues\n");
            builder.append("GL_VENDOR : ").append(glGetString(GL_VENDOR)).append('\n');
            builder.append("GL_RENDERER : ").append(glGetString(GL_RENDERER)).append('\n');
            builder.append("GL_VERSION : ").append(glGetString(GL_VERSION)).append('\n');
            builder.append("GL_SHADING_LANGUAGE_VERSION : ").append(glGetString(GL_SHADING_LANGUAGE_VERSION)).append('\n');
            final var caps = GL.getCapabilities();
            builder.append("GL_ARB_separate_shader_objects : ").append(caps.GL_ARB_separate_shader_objects).append('\n');
            
            final var extensionCount = glGetInteger(GL_NUM_EXTENSIONS);
            builder.append("Supported OpenGL Extensions : ").append(extensionCount).append('\n');
            for (int i = 0; i < extensionCount; i++) {
                builder.append(glGetStringi(GL_EXTENSIONS, i)).append('\n');
            }
            
            // this is the backup impl, so this is ok to do
            Minecraft.crash(new CrashReport("Quartz startup failed", new IllegalStateException(builder.toString())));
        }
        final var hasKHRDebug = GL.getCapabilities().GL_KHR_debug;
        try {
            LOGGER.info("Quartz initializing GL33Core");
            if (hasKHRDebug) {
                KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_THIRD_PARTY, 0, "Quartz GL33 Renderer Setup");
            }
            INSTANCE = new GL33Core();
            LOGGER.info("Quartz GL33Core initialized");
        } finally {
            if (hasKHRDebug) {
                KHRDebug.glPopDebugGroup();
            }
        }
    }
    
    private long lastTimeNano = 0;
    public final DrawInfo drawInfo = new DrawInfo();
    
    @Override
    protected void startupInternal() {
        GL33ComputePrograms.startup();
        GL33FeedbackPrograms.startup();
        GL33LightEngine.startup();
        GL33FeedbackDrawing.startup();
    }
    
    @Override
    protected void shutdownInternal() {
        GL33FeedbackDrawing.shutdown();
        GL33LightEngine.shutdown();
        GL33FeedbackPrograms.shutdown();
        GL33ComputePrograms.shutdown();
    }
    
    @Override
    protected void resourcesReloadedInternal() {
        GL33FeedbackPrograms.reload();
    }
    
    @Override
    public DrawBatch createDrawBatch() {
        return GL33FeedbackDrawing.createDrawBatch();
    }
    
    @Override
    public Buffer allocBuffer(boolean GPUOnly) {
        return new GL33Buffer(GPUOnly);
    }
    
    @Override
    public void frameStart(PoseStack pMatrixStack, float pPartialTicks, long pFinishTimeNano, boolean pDrawBlockOutline, Camera pActiveRenderInfo, GameRenderer pGameRenderer, LightTexture pLightmap, Matrix4f pProjection) {
            deletionQueue.runAll();
        
        long timeNanos = System.nanoTime();
        long deltaNano = timeNanos - lastTimeNano;
        lastTimeNano = timeNanos;
        if (lastTimeNano == 0) {
            deltaNano = 0;
        }
        
        var playerPosition = pActiveRenderInfo.getPosition();
        Vector3d vec3d = new Vector3d(playerPosition.x, playerPosition.y, playerPosition.z);
        vec3d.floor();
        drawInfo.playerPosition.set((int) vec3d.x, (int) vec3d.y, (int) vec3d.z);
        drawInfo.playerPositionNegative.set(drawInfo.playerPosition).negate();
        drawInfo.playerSubBlock.set(playerPosition.x - drawInfo.playerPosition.x, playerPosition.y - drawInfo.playerPosition.y, playerPosition.z - drawInfo.playerPosition.z);
        drawInfo.playerSubBlockNegative.set(drawInfo.playerSubBlockNegative).negate();
        
        drawInfo.projectionMatrix.set(pProjection);
        drawInfo.projectionMatrix.mul(pMatrixStack.last().pose());
        drawInfo.projectionMatrix.get(drawInfo.projectionMatrixFloatBuffer);
        
        drawInfo.mojPose = pMatrixStack.last();
        
        drawInfo.deltaNano = deltaNano;
        drawInfo.partialTicks = pPartialTicks;
        
        GL33FeedbackDrawing.beginFrame();
        
        meshManager.vertexBuffer.as(GL33Buffer.class).flush();
    }
    
    @Override
    public void lightUpdated() {
        GL33LightEngine.update(Minecraft.getInstance().level);
    }
    
    @Override
    public void preTerrainSetup() {
        if(!GL33FeedbackDrawing.hasBatch()){
            return;
        }
        GL33FeedbackDrawing.collectAllFeedback(IrisDetection.areShadersActive());
    }
    
    @Override
    public void shadowPass(PoseStack modelView, Matrix4f projectionMatrix) {
        if(!GL33FeedbackDrawing.hasBatch()){
            return;
        }
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        GL33FeedbackDrawing.getActiveRenderTypes().forEach(GL33FeedbackDrawing::drawRenderType);
    }
    
    @Override
    public void preOpaque() {
        if(!GL33FeedbackDrawing.hasBatch()){
            return;
        }
        
        BufferUploader.invalidate();
        IrisDetection.bindIrisFramebuffer();
        
        for (final var renderType : GL33FeedbackDrawing.getActiveRenderTypes()) {
            if (!(renderType instanceof RenderType.CompositeRenderType compositeRenderType)) {
                continue;
            }
            if(compositeRenderType.state().transparencyState != RenderStateShard.NO_TRANSPARENCY) {
                continue;
            }
            GL33FeedbackDrawing.drawRenderType(renderType);
        }
    }
    
    @Override
    public void endOpaque() {
        if(!GL33FeedbackDrawing.hasBatch()){
            return;
        }
        BufferUploader.invalidate();
        
        for (final var renderType : GL33FeedbackDrawing.getActiveRenderTypes()) {
            if (!(renderType instanceof RenderType.CompositeRenderType compositeRenderType)) {
                continue;
            }
            if(compositeRenderType.state().transparencyState == RenderStateShard.NO_TRANSPARENCY) {
                continue;
            }
            RenderSystem.depthMask(false);
            GL33FeedbackDrawing.drawRenderType(renderType);
        }
        RenderSystem.depthMask(true);
    }
    
    @Override
    public void endTranslucent() {
    
    }
    
    @Override
    public void waitIdle() {
        // no need to wait for GPU idle
    }
    
    @Override
    public int frameInFlight() {
        return 0;
    }
    
    @Override
    public void sectionDirty(int x, int y, int z) {
        GL33LightEngine.sectionDirty(x, y, z);
    }
}
