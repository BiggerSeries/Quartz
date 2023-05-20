package net.roguelogix.quartz.internal.gl;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.resources.ResourceLocation;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.phosphophyllite.util.Util;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.internal.IrisDetection;
import net.roguelogix.quartz.internal.MagicNumbers;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.DrawInfo;
import net.roguelogix.quartz.internal.util.VertexFormatOutput;

import static net.roguelogix.quartz.internal.gl.GLCore.SSBO_VERTEX_BLOCK_LIMIT;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.*;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glGenProgramPipelines;
import static org.lwjgl.opengl.GL32C.*;

@NonnullDefault
public class GLTransformFeedbackProgram {
    
    public static final ResourceLocation baseResourceLocation = new ResourceLocation(Quartz.modid, "shaders/gl/transform_feedback");
    private static final ResourceLocation vertexShaderLocation = new ResourceLocation(baseResourceLocation.getNamespace(), baseResourceLocation.getPath() + ".vert");
    public static final boolean SSBO = GLCore.SSBO && SSBO_VERTEX_BLOCK_LIMIT >= 2;
    
    private record ShaderInstance(
            VertexFormatOutput outputFormat, int vertexProgram, int pipeline,
            int PLAYER_BLOCK_UNIFORM_LOCATION,
            int PLAYER_SUB_BLOCK_UNIFORM_LOCATION,
            int PROJECTION_MATRIX_UNIFORM_LOCATION,
            int VERT_QUAD_UNIFORM_LOCATION,
            int VERT_LIGHTING_UNIFORM_LOCATION) {
        
        private static int createProgram(final VertexFormatOutput outputFormat, boolean shadersLoaded) {
            var extensionsBuilder = new StringBuilder();
            // gpuinfo says this is supported, so im using it
            extensionsBuilder.append("#version 150 core\n");
            extensionsBuilder.append("#line 1 1\n");
            extensionsBuilder.append("#extension GL_ARB_explicit_attrib_location : require\n");
            
            var prependBuilder = new StringBuilder();
            prependBuilder.append("#line 0 2\n");
            prependBuilder.append("#define QUARTZ_INSERT_DEFINES\n");
            
            
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
                extensionsBuilder.append("#extension GL_ARB_shader_storage_buffer_object : require\n");
            }
            
            if (shadersLoaded) {
                prependBuilder.append("#define SHADERS_LOADED\n");
            }
            
            var shaderCode = Util.readResourceLocation(vertexShaderLocation);
            if (shaderCode == null) {
                throw new IllegalStateException("Failed to load shader code for " + baseResourceLocation);
            }
            
            final int vertexShader = glCreateShader(GL_VERTEX_SHADER);
            
            glShaderSource(vertexShader, extensionsBuilder, prependBuilder, "#line 0 3\n", shaderCode);
            glCompileShader(vertexShader);
            
            if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) != GL_TRUE) {
                final var infoLog = glGetShaderInfoLog(vertexShader);
                glDeleteShader(vertexShader);
                throw new IllegalStateException("Vertex shader compilation failed for " + vertexShaderLocation + '\n' + infoLog + '\n');
            }
            
            final int vertexProgram = glCreateProgram();
            glAttachShader(vertexProgram, vertexShader);
            glTransformFeedbackVaryings(vertexProgram, outputFormat.varyings(), GL_INTERLEAVED_ATTRIBS);
            glProgramParameteri(vertexProgram, GL_PROGRAM_SEPARABLE, GL_TRUE);
            glLinkProgram(vertexProgram);
            
            if (glGetProgrami(vertexProgram, GL_LINK_STATUS) != GL_TRUE) {
                final var infoLog = glGetProgramInfoLog(vertexProgram);
                glDeleteShader(vertexShader);
                glDeleteProgram(vertexProgram);
                throw new IllegalStateException("Vertex program link failed for " + vertexShaderLocation + '\n' + infoLog + '\n');
            }
            
            glDetachShader(vertexProgram, vertexShader);
            glDeleteShader(vertexShader);
            
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
