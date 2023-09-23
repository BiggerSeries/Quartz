package net.roguelogix.quartz.internal.vk;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.internal.Buffer;
import net.roguelogix.quartz.internal.QuartzCore;
import org.joml.Matrix4f;

import java.util.List;

public class VKCore extends QuartzCore {
    
    public static final VKCore INSTANCE;
    
    static {
        if (VKStatics.AVAILABLE) {
            LOGGER.info("Quartz initializing VKCore");
            INSTANCE = new VKCore();
            LOGGER.info("Quartz VKCore initialized");
        }else {
            INSTANCE = null;
            LOGGER.info("Vulkan not available");
        }
    }
    
    @Override
    protected void startupInternal() {
    
    }
    
    @Override
    protected void shutdownInternal() {
    
    }
    
    @Override
    protected void resourcesReloadedInternal() {
    
    }
    
    @Override
    public DrawBatch createDrawBatch() {
        return null;
    }
    
    @Override
    public Buffer allocBuffer(int options) {
        return null;
    }
    
    @Override
    public void frameStart(PoseStack pMatrixStack, float pPartialTicks, long pFinishTimeNano, boolean pDrawBlockOutline, Camera pActiveRenderInfo, GameRenderer pGameRenderer, LightTexture pLightmap, Matrix4f pProjection) {
    
    }
    
    @Override
    public void lightUpdated() {
    
    }
    
    @Override
    public void preTerrainSetup() {
    
    }
    
    @Override
    public void shadowPass(PoseStack modelView, Matrix4f projectionMatrix) {
    
    }
    
    @Override
    public void preOpaque() {
    
    }
    
    @Override
    public void endOpaque() {
    
    }
    
    @Override
    public void endTranslucent() {
    
    }
    
    @Override
    public void waitIdle() {
    
    }
    
    @Override
    public int frameInFlight() {
        return 0;
    }
    
    @Override
    public void sectionDirty(int x, int y, int z) {
    
    }
    
    @Override
    public void allSectionsDirty() {
    
    }
    
    @Override
    public void addDebugText(List<String> list) {
        list.add("Quartz backend: Vulkan");
    }
}
