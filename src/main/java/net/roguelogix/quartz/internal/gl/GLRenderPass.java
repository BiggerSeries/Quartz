package net.roguelogix.quartz.internal.gl;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix3f;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.internal.common.B3DStateHelper;
import net.roguelogix.quartz.internal.common.ShaderInfo;
import net.roguelogix.quartz.internal.util.VertexFormatOutput;

import javax.annotation.Nullable;

import java.util.function.Supplier;

import static net.roguelogix.quartz.internal.gl.GLCore.drawInfo;
import static org.lwjgl.opengl.GL32C.*;

@NonnullDefault
public class GLRenderPass {
    
    public final boolean QUAD;
    public final boolean TEXTURE;
    public final boolean LIGHTING;
    public final int VERTICES_PER_PRIMITIVE;
    public final int GL_MODE;
    
    private final ResourceLocation textureResourceLocation;
    private AbstractTexture texture;
    
    public final RenderType.CompositeRenderType sourceRenderType;
    public final VertexFormat vertexFormat;
    public final VertexFormatOutput vertexFormatOutput;
    public final Supplier<ShaderInstance> renderTypeShader;
    
    private final GLBuffer transformFeedbackBuffer = new GLBuffer(false);
    private GLBuffer.Allocation transformFeedbackBufferAlloc = transformFeedbackBuffer.alloc(16384);
    
    private final int drawVAO;
    
    private static final Object2ObjectOpenHashMap<RenderType, GLRenderPass> renderPasses = new Object2ObjectOpenHashMap<>();
    private static final ObjectArrayList<GLRenderPass> uniquePasses = new ObjectArrayList<>();
    
    
    public static void resourcesReloaded() {
        for (GLRenderPass value : renderPasses.values()) {
            value.resourceReload();
        }
    }
    
    public static GLRenderPass renderPassForRenderType(RenderType renderType) {
        return renderPasses.computeIfAbsent(renderType, (RenderType type) -> {
            final var renderPass = new GLRenderPass(renderType);
            for (final var potentialPass : renderPasses.values()) {
                if (potentialPass.compatible(renderPass)) {
                    return potentialPass;
                }
            }
            uniquePasses.add(renderPass);
            return renderPass;
        });
    }
    
    public static void shutdown() {
        uniquePasses.forEach(e -> glDeleteVertexArrays(e.drawVAO));
    }
    
    private GLRenderPass(RenderType rawRenderType) {
        if (!(rawRenderType instanceof RenderType.CompositeRenderType renderType)) {
            throw new IllegalArgumentException("RenderType must be composite type");
        }
        sourceRenderType = renderType;
        
        var compositeState = renderType.state();
        vertexFormat = renderType.format();
        vertexFormatOutput = VertexFormatOutput.of(vertexFormat);
        GLCore.INSTANCE.transformFeedbackProgram.setupVertexFormat(vertexFormatOutput);
        
        if (compositeState.shaderState.shader.isEmpty()) {
            throw new IllegalArgumentException("Attempt to use Shaderless RenderType" + rawRenderType);
        }
        renderTypeShader = compositeState.shaderState.shader.get();
        
        var shaderInfo = ShaderInfo.get(compositeState.shaderState);
        if (shaderInfo == null) {
            throw new IllegalArgumentException("Unsupported RenderType shader" + rawRenderType);
        }
        
        QUAD = renderType.mode() == VertexFormat.Mode.QUADS;
        TEXTURE = compositeState.textureState != RenderStateShard.NO_TEXTURE;
        LIGHTING = compositeState.lightmapState != RenderStateShard.NO_LIGHTMAP;
        
        if (TEXTURE && compositeState.textureState instanceof RenderStateShard.TextureStateShard texShard) {
            textureResourceLocation = texShard.cutoutTexture().orElse(null);
            if (textureResourceLocation == null) {
                throw new IllegalArgumentException("No Texture found for texture state shard");
            }
        } else {
            textureResourceLocation = null;
        }
        
        VERTICES_PER_PRIMITIVE = switch (renderType.mode()) {
            case LINES -> 2;
            case TRIANGLES -> 3;
            case QUADS -> 4;
            default -> throw new IllegalArgumentException("Unsupported primitive type");
        };
        GL_MODE = switch (renderType.mode()) {
            case LINES -> GL_LINE;
            case TRIANGLES, QUADS -> GL_TRIANGLES; // quads too, because element buffer
            default -> throw new IllegalArgumentException("Unsupported primitive type");
        };
        
        int drawVAO = glGenVertexArrays();
        this.drawVAO = drawVAO;
        
        B3DStateHelper.bindVertexArray(drawVAO);
        B3DStateHelper.bindArrayBuffer(transformFeedbackBuffer.handle());
        // Iris hooks into setupBufferState, so i call the private one to avoid that hook
        vertexFormat._setupBufferState();
        B3DStateHelper.bindVertexArray(0);
        
        resourceReload();
    }
    
    public boolean compatible(GLRenderPass otherPass) {
        return QUAD == otherPass.QUAD &&
                TEXTURE == otherPass.TEXTURE &&
                LIGHTING == otherPass.LIGHTING &&
                VERTICES_PER_PRIMITIVE == otherPass.VERTICES_PER_PRIMITIVE &&
                GL_MODE == otherPass.GL_MODE &&
                textureResourceLocation.equals(otherPass.textureResourceLocation) &&
                renderTypeShader.get().getName().equals(otherPass.renderTypeShader.get().getName()) &&
                vertexFormat.equals(otherPass.vertexFormat)
                ;
    }
    
    @Nullable
    public AbstractTexture texture() {
        return texture;
    }
    
    private void resourceReload() {
        texture = Minecraft.getInstance().getTextureManager().getTexture(textureResourceLocation);
    }
    
    private int drawnVertices = 0;
    private int verticesLastFeedback = 0;
    
    public void beginTransformFeedback(int vertices) {
        verticesLastFeedback = vertices;
        
        final var drawSize = vertices * vertexFormat.getVertexSize();
        final var currentOffset = drawnVertices * vertexFormat.getVertexSize();
        final var requiredBufferSize = drawSize + currentOffset;
        
        if (transformFeedbackBufferAlloc.size() < requiredBufferSize) {
            transformFeedbackBufferAlloc = transformFeedbackBuffer.realloc(transformFeedbackBufferAlloc, requiredBufferSize);
        }
        glBindBufferRange(GL_TRANSFORM_FEEDBACK_BUFFER, 0, transformFeedbackBuffer.handle(), currentOffset, drawSize);
        glBeginTransformFeedback(GL_MODE);
    }
    
    public void endTransformFeedback() {
        glEndTransformFeedback();
        
        drawnVertices += verticesLastFeedback;
    }
    
    public static void drawAll(boolean shadows) {
        for (final var value : uniquePasses) {
            value.draw(shadows);
        }
    }
    
    Matrix3f inverseMatrix = new Matrix3f();
    
    public void draw(boolean shadows) {
        if (drawnVertices == 0) {
            return;
        }
        
        glBindTexture(GL_TEXTURE_2D, texture.getId());
        B3DStateHelper.bindVertexArray(drawVAO);
        
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (TEXTURE) {
            RenderSystem.setShaderTexture(0, texture.getId());
        }
        
        var renderTypeShader = this.renderTypeShader.get();
        
        sourceRenderType.setupRenderState();
        
        for (int i = 0; i < 12; ++i) {
            int j = RenderSystem.getShaderTexture(i);
            renderTypeShader.setSampler("Sampler" + i, j);
        }
        
        if (renderTypeShader.MODEL_VIEW_MATRIX != null) {
            renderTypeShader.MODEL_VIEW_MATRIX.set(drawInfo.mojPose.pose());
        }
        
        if (renderTypeShader.PROJECTION_MATRIX != null) {
            renderTypeShader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        
        if (renderTypeShader.INVERSE_VIEW_ROTATION_MATRIX != null) {
            inverseMatrix.load(drawInfo.mojPose.normal());
            inverseMatrix.invert();
            renderTypeShader.INVERSE_VIEW_ROTATION_MATRIX.set(inverseMatrix);
        }
        
        if (renderTypeShader.COLOR_MODULATOR != null) {
            renderTypeShader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        
        if (renderTypeShader.FOG_START != null) {
            renderTypeShader.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        
        if (renderTypeShader.FOG_END != null) {
            renderTypeShader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        
        if (renderTypeShader.FOG_COLOR != null) {
            renderTypeShader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        
        if (renderTypeShader.FOG_SHAPE != null) {
            renderTypeShader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        
        if (renderTypeShader.TEXTURE_MATRIX != null) {
            renderTypeShader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        
        if (renderTypeShader.GAME_TIME != null) {
            renderTypeShader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        
        if (renderTypeShader.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            renderTypeShader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }
        
        RenderSystem.setupShaderLights(renderTypeShader);
        renderTypeShader.apply();
        // Iris is dumb, and doesnt want me to write anything
        glDepthMask(true);
        glColorMask(true, true, true, true);
        glDrawArrays(GL_MODE, 0, drawnVertices);
        renderTypeShader.clear();
        
        if (!shadows) {
            drawnVertices = 0;
        }
    }
}