package net.roguelogix.quartz.internal.gl;

import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.phosphophyllite.util.Util;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.internal.IrisDetection;
import net.roguelogix.quartz.internal.MagicNumbers;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.DrawInfo;

import javax.annotation.Nullable;

import static net.roguelogix.quartz.internal.gl.GLCore.SSBO_VERTEX_BLOCK_LIMIT;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.*;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glGenProgramPipelines;
import static org.lwjgl.opengl.GL32C.*;

@NonnullDefault
public class GLTransformFeedbackProgram {
    
    public static final ResourceLocation baseResourceLocation = new ResourceLocation(Quartz.modid, "shaders/gl/transform_feedback");
    private static final ResourceLocation vertexShaderLocation = new ResourceLocation(baseResourceLocation.getNamespace(), baseResourceLocation.getPath() + ".vert");
    public static final boolean SSBO = GLCore.SSBO && SSBO_VERTEX_BLOCK_LIMIT >= 2;
    
    public record VertexFormatOutput(VertexFormat format, int colorOffset, int textureOffset, int overlayOffset, int lightmapOffset, int normalOffset, int stride) {
        
        public static final VertexFormatOutput BLOCK = new VertexFormatOutput(DefaultVertexFormat.BLOCK);
        public static final VertexFormatOutput NEW_ENTITY = new VertexFormatOutput(DefaultVertexFormat.NEW_ENTITY);
        public static final VertexFormatOutput POSITION_COLOR_TEX_LIGHTMAP = new VertexFormatOutput(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        
        public VertexFormatOutput(VertexFormat format) {
            this(format,
                    offsetOf(format, DefaultVertexFormat.ELEMENT_COLOR),
                    offsetOf(format, DefaultVertexFormat.ELEMENT_UV0),
                    offsetOf(format, DefaultVertexFormat.ELEMENT_UV1),
                    offsetOf(format, DefaultVertexFormat.ELEMENT_UV2),
                    offsetOf(format, DefaultVertexFormat.ELEMENT_NORMAL),
                    format.getVertexSize()
            );
        }
        
        private static int offsetOf(VertexFormat format, VertexFormatElement element) {
            final var elementIndex = format.getElements().indexOf(element);
            if (elementIndex == -1) {
                return 0;
            }
            return format.getOffset(elementIndex);
        }
        
        boolean colorEnabled() {
            return colorOffset != 0;
        }
        
        boolean textureEnabled() {
            return textureOffset != 0;
        }
        
        boolean overlayEnabled() {
            return overlayOffset != 0;
        }
        
        boolean lightmapEnabled() {
            return lightmapOffset != 0;
        }
        
        boolean normalEnabled() {
            return normalOffset != 0;
        }
        
        String generateDefines() {
            var builder = new StringBuilder();
            generateDefines(builder);
            return builder.toString();
        }
        
        void generateDefines(StringBuilder builder) {
            if (colorEnabled()) {
                builder.append("#define COLOR_OUTPUT ").append(colorOffset).append("\n");
            }
            if (textureEnabled()) {
                builder.append("#define UV0_OUTPUT ").append(textureOffset).append("\n");
            }
            if (overlayEnabled()) {
                builder.append("#define UV1_OUTPUT ").append(overlayOffset).append("\n");
            }
            if (lightmapEnabled()) {
                builder.append("#define UV2_OUTPUT ").append(lightmapOffset).append("\n");
            }
            if (normalEnabled()) {
                builder.append("#define NORMAL_OUTPUT ").append(normalOffset).append("\n");
            }
            builder.append("#define FEEDBACK_BUFFER_STRIDE ").append(stride).append("\n");
        }
    }
    
    private record ShaderInstance(
            VertexFormatOutput outputFormat, int vertexProgram, int pipeline,
            int PLAYER_BLOCK_UNIFORM_LOCATION,
            int PLAYER_SUB_BLOCK_UNIFORM_LOCATION,
            int PROJECTION_MATRIX_UNIFORM_LOCATION,
            int VERT_QUAD_UNIFORM_LOCATION,
            int VERT_LIGHTING_UNIFORM_LOCATION) {
        
        private static int createProgram(final VertexFormatOutput outputFormat, boolean shadersLoaded) {
            var prependBuilder = new StringBuilder();
            outputFormat.generateDefines(prependBuilder);
            
            // the string concat will happen once because these are static finals
            prependBuilder.append("#define POSITION_LOCATION " + MagicNumbers.GL.POSITION_LOCATION + "\n");
            prependBuilder.append("#define COLOR_LOCATION " + MagicNumbers.GL.COLOR_LOCATION + "\n");
            prependBuilder.append("#define TEX_COORD_LOCATION " + MagicNumbers.GL.TEX_COORD_LOCATION + "\n");
            prependBuilder.append("#define LIGHTINFO_LOCATION " + MagicNumbers.GL.LIGHTINFO_LOCATION + "\n");
            
            prependBuilder.append("#define WORLD_POSITION_LOCATION " + MagicNumbers.GL.WORLD_POSITION_LOCATION + "\n");
            prependBuilder.append("#define DYNAMIC_MATRIX_ID_LOCATION " + MagicNumbers.GL.DYNAMIC_MATRIX_ID_LOCATION + "\n");
            prependBuilder.append("#define DYNAMIC_LIGHT_ID_LOCATION " + MagicNumbers.GL.DYNAMIC_LIGHT_ID_LOCATION + "\n");
            prependBuilder.append("#define STATIC_MATRIX_LOCATION " + MagicNumbers.GL.STATIC_MATRIX_LOCATION + "\n");
            prependBuilder.append("#define STATIC_NORMAL_MATRIX_LOCATION " + MagicNumbers.GL.STATIC_NORMAL_MATRIX_LOCATION + "\n");
            
            if (SSBO) {
                prependBuilder.append("#define USE_SSBO\n");
            }
            
            if (shadersLoaded) {
                prependBuilder.append("#define SHADERS_LOADED\n");
            }
            
            var shaderCode = Util.readResourceLocation(vertexShaderLocation);
            if (shaderCode == null) {
                throw new IllegalStateException("Failed to load shader code for " + baseResourceLocation);
            }
            var codeBuilder = new StringBuilder();
            codeBuilder.append(shaderCode);
            codeBuilder.insert(shaderCode.indexOf('\n') + 1, prependBuilder);
            
            final int vertexProgram = glCreateShaderProgramv(GL_VERTEX_SHADER, codeBuilder.toString());
            
            if (glGetProgrami(vertexProgram, GL_LINK_STATUS) != GL_TRUE) {
                throw new IllegalStateException("Vertex shader compilation failed for " + vertexShaderLocation + '\n' + glGetProgramInfoLog(vertexProgram) + '\n');
            }
            
            return vertexProgram;
        }
        
        private static int createPipeline(final int shader) {
            final int pipeline = glGenProgramPipelines();
            glUseProgramStages(pipeline, GL_VERTEX_SHADER_BIT, shader);
            return pipeline;
        }
        
        private ShaderInstance(final VertexFormatOutput outputFormat, final int vertexProgram, final int pipeline) {
            this(outputFormat, vertexProgram, pipeline,
                    glGetUniformLocation(vertexProgram, "playerBlock"),
                    glGetUniformLocation(vertexProgram, "playerSubBlock"),
                    glGetUniformLocation(vertexProgram, "projectionMatrix"),
                    glGetUniformLocation(vertexProgram, "QUAD"),
                    glGetUniformLocation(vertexProgram, "LIGHTING"));
            if (!SSBO) {
                glProgramUniform1i(vertexProgram, glGetUniformLocation(vertexProgram, "dynamicMatrices"), MagicNumbers.GL.DYNAMIC_MATRIX_TEXTURE_UNIT);
                glProgramUniform1i(vertexProgram, glGetUniformLocation(vertexProgram, "dynamicLights"), MagicNumbers.GL.DYNAMIC_LIGHT_TEXTURE_UNIT);
            }
        }
        
        private ShaderInstance(final VertexFormatOutput outputFormat, final int vertexProgram) {
            this(outputFormat, vertexProgram, createPipeline(vertexProgram));
        }
        
        ShaderInstance(final VertexFormatOutput outputFormat, boolean shadersLoaded) {
            this(outputFormat, createProgram(outputFormat, shadersLoaded));
        }
        
        void bind() {
            GLStateTracker.bindPipeline(pipeline);
        }
        
        void delete() {
            glDeleteProgram(vertexProgram);
            glDeleteProgramPipelines(pipeline);
        }
    }
    
    private final Object2ObjectArrayMap<VertexFormatOutput, ShaderInstance> shaderInstances = new Object2ObjectArrayMap<>();
    
    private boolean loaded = false;
    private boolean shadersLoaded = false;
    
    public GLTransformFeedbackProgram() {
        final var shaders = shaderInstances;
        QuartzCore.CLEANER.register(this, () -> QuartzCore.deletionQueue.enqueue(() -> shaders.values().forEach(ShaderInstance::delete)));
    }
    
    public boolean loaded() {
        return loaded;
    }
    
    public void initialLoad() {
        setupVertexFormat(VertexFormatOutput.BLOCK);
        setupVertexFormat(VertexFormatOutput.NEW_ENTITY);
        loaded = true;
    }
    
    public void setupVertexFormat(VertexFormatOutput format) {
        if (shaderInstances.get(format) == null) {
            shaderInstances.put(format, new ShaderInstance(format, shadersLoaded));
        }
    }
    
    public void reload() {
        if (shaderInstances.isEmpty()) {
            initialLoad();
        }
        for (final var value : shaderInstances.entrySet()) {
            final var newShader = new ShaderInstance(value.getKey(), shadersLoaded);
            value.getValue().delete();
            shaderInstances.put(value.getKey(), newShader);
        }
    }
    
    public void setupDrawInfo(DrawInfo drawInfo) {
        for (final var value : shaderInstances.values()) {
            glProgramUniformMatrix4fv(value.vertexProgram, value.PROJECTION_MATRIX_UNIFORM_LOCATION, false, drawInfo.projectionMatrixFloatBuffer);
            glProgramUniform3i(value.vertexProgram, value.PLAYER_BLOCK_UNIFORM_LOCATION, drawInfo.playerPosition.x, drawInfo.playerPosition.y, drawInfo.playerPosition.z);
            glProgramUniform3f(value.vertexProgram, value.PLAYER_SUB_BLOCK_UNIFORM_LOCATION, drawInfo.playerSubBlock.x, drawInfo.playerSubBlock.y, drawInfo.playerSubBlock.z);
        }
    }
    
    public void setupRenderPass(GLRenderPass renderPass) {
        if (IrisDetection.areShadersActive() != shadersLoaded) {
            shadersLoaded = IrisDetection.areShadersActive();
            reload();
        }
        
        var shaderToUse = shaderInstances.get(renderPass.vertexFormatOutput);
        if (shaderToUse == null) {
            shaderToUse = new ShaderInstance(renderPass.vertexFormatOutput, shadersLoaded);
            shaderInstances.put(renderPass.vertexFormatOutput, shaderToUse);
        }
        
        glProgramUniform1i(shaderToUse.vertexProgram, shaderToUse.VERT_QUAD_UNIFORM_LOCATION, renderPass.QUAD ? GL_TRUE : GL_FALSE);
        glProgramUniform1i(shaderToUse.vertexProgram, shaderToUse.VERT_LIGHTING_UNIFORM_LOCATION, renderPass.LIGHTING ? GL_TRUE : GL_FALSE);
        
        shaderToUse.bind();
    }
}
