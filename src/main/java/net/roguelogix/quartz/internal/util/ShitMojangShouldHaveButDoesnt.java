package net.roguelogix.quartz.internal.util;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.QuartzEvent;

import static org.lwjgl.opengl.GL33C.*;

public class ShitMojangShouldHaveButDoesnt {
    private static int drawVAO = 0;
    
    @OnModLoad
    private static void onModLoad() {
        Quartz.EVENT_BUS.addListener(ShitMojangShouldHaveButDoesnt::quartzStartupEvent);
        Quartz.EVENT_BUS.addListener(ShitMojangShouldHaveButDoesnt::quartzShutdownEvent);
    }
    
    private static void quartzStartupEvent(QuartzEvent.Startup event) {
        drawVAO = glGenVertexArrays();
    }
    
    private static void quartzShutdownEvent(QuartzEvent.Shutdown event) {
        glDeleteVertexArrays(drawVAO);
    }
    
    public static void drawRenderTypePreboundVertexBuffer(RenderType renderType, int vertexCount) {
        drawRenderTypePreboundVertexBuffer(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), renderType, vertexCount);
    }
    
    public static void drawRenderTypePreboundVertexBuffer(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, RenderType renderType, int vertexCount) {
        renderType.setupRenderState();
        glBindVertexArray(drawVAO);
        // Iris hooks into the default one, so i have to use the private one
        renderType.format()._setupBufferState();
        
        drawWithShaderSequentialIndices(modelViewMatrix, projectionMatrix, RenderSystem.getShader(), renderType.mode(), vertexCount);
        
        renderType.format()._clearBufferState();
        glBindVertexArray(0);
        renderType.clearRenderState();
    }
    
    public static void drawWithShaderSequentialIndices(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, ShaderInstance shaderInstance, VertexFormat.Mode mode, int vertexCount) {
        final var indexCount = mode.indexCount(vertexCount);
        final var indexBuffer = RenderSystem.getSequentialBuffer(mode);
        // this does bind it
        // but it shouldn't be called just "bind"
        // resizeAndBind maybe?
        indexBuffer.bind(indexCount);
        drawElementsWithShader(modelViewMatrix, projectionMatrix, shaderInstance, mode, indexCount, indexBuffer.type());
        // technically unncesscary, but there are going to be so few draws, it doesnt matter
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    
    public static void drawElementsWithShader(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, ShaderInstance shaderInstance, VertexFormat.Mode mode, int indexCount, VertexFormat.IndexType indexType) {
        
        for (int i = 0; i < 12; ++i) {
            int j = RenderSystem.getShaderTexture(i);
            shaderInstance.setSampler("Sampler" + i, j);
        }
        
        if (shaderInstance.MODEL_VIEW_MATRIX != null) {
            shaderInstance.MODEL_VIEW_MATRIX.set(modelViewMatrix);
        }
        
        if (shaderInstance.PROJECTION_MATRIX != null) {
            shaderInstance.PROJECTION_MATRIX.set(projectionMatrix);
        }
        
        if (shaderInstance.INVERSE_VIEW_ROTATION_MATRIX != null) {
            shaderInstance.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        }
        
        if (shaderInstance.COLOR_MODULATOR != null) {
            shaderInstance.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        
        if (shaderInstance.FOG_START != null) {
            shaderInstance.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        
        if (shaderInstance.FOG_END != null) {
            shaderInstance.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        
        if (shaderInstance.FOG_COLOR != null) {
            shaderInstance.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        
        if (shaderInstance.FOG_SHAPE != null) {
            shaderInstance.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        
        if (shaderInstance.TEXTURE_MATRIX != null) {
            shaderInstance.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        
        if (shaderInstance.GAME_TIME != null) {
            shaderInstance.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        
        if (shaderInstance.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shaderInstance.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }
        
        if (shaderInstance.LINE_WIDTH != null && (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP)) {
            shaderInstance.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        }
        
        RenderSystem.setupShaderLights(shaderInstance);
        shaderInstance.apply();
        RenderSystem.drawElements(mode.asGLMode, indexCount, indexType.asGLType);
        shaderInstance.clear();
    }
}
