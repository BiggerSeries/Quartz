package net.roguelogix.quartz.internal.gl46;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.resources.ResourceLocation;
import net.roguelogix.phosphophyllite.util.Util;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.internal.MagicNumbers;
import net.roguelogix.quartz.internal.common.DrawInfo;
import net.roguelogix.quartz.internal.util.VertexFormatOutput;

import static org.lwjgl.opengl.ARBSeparateShaderObjects.GL_PROGRAM_SEPARABLE;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glProgramParameteri;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL30C.GL_INTERLEAVED_ATTRIBS;
import static org.lwjgl.opengl.GL30C.glTransformFeedbackVaryings;

public class GL46FeedbackPrograms {
    
    public static final ResourceLocation shaderLocation = new ResourceLocation(Quartz.modid, "shaders/gl46/transform_feedback.vert");
    
    private static int vertexShader;
    private static final Reference2IntMap<VertexFormatOutput> programs = new Reference2IntArrayMap<>();
    
    
    public static void startup() {
        var extensionsBuilder = new StringBuilder();
        // gpuinfo says this is supported, so im using it
        extensionsBuilder.append("#version 460 core\n");
        extensionsBuilder.append("#line 1 1\n");
        // no GLSL extensions used outside of 460 core
        
        var prependBuilder = new StringBuilder();
        prependBuilder.append("#line 0 2\n");
        prependBuilder.append("#define QUARTZ_INSERT_DEFINES\n");
        
        
        // the string concat will happen once because these are static finals
        prependBuilder.append("#define POSITION_LOCATION " + GL46Statics.POSITION_LOCATION + "\n");
        prependBuilder.append("#define COLOR_LOCATION " + GL46Statics.COLOR_LOCATION + "\n");
        prependBuilder.append("#define TEX_COORD_LOCATION " + GL46Statics.TEX_COORD_LOCATION + "\n");
        prependBuilder.append("#define NORMAL_LOCATION " + GL46Statics.NORMAL_LOCATION + "\n");
        
        prependBuilder.append("#define WORLD_POSITION_LOCATION " + GL46Statics.WORLD_POSITION_LOCATION + "\n");
        prependBuilder.append("#define DYNAMIC_MATRIX_ID_LOCATION " + GL46Statics.DYNAMIC_MATRIX_ID_LOCATION + "\n");
        prependBuilder.append("#define STATIC_MATRIX_LOCATION " + GL46Statics.STATIC_MATRIX_LOCATION + "\n");
        prependBuilder.append("#define STATIC_NORMAL_MATRIX_LOCATION " + GL46Statics.STATIC_NORMAL_MATRIX_LOCATION + "\n");
        
        var shaderCode = Util.readResourceLocation(shaderLocation);
        if (shaderCode == null) {
            throw new IllegalStateException("Failed to load shader code for " + shaderLocation);
        }
        
        vertexShader = glCreateShader(GL_VERTEX_SHADER);
        
        glShaderSource(vertexShader, extensionsBuilder, prependBuilder, "#line 1 3\n", shaderCode);
        glCompileShader(vertexShader);
        
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) != GL_TRUE) {
            final var infoLog = glGetShaderInfoLog(vertexShader);
            glDeleteShader(vertexShader);
            throw new IllegalStateException("Feedback shader compilation failed for " + shaderLocation + '\n' + infoLog + '\n');
        }
        
        // create programs at startup instead of lazily
        // can still be created lazily if they aren't one of these
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.BLOCK));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.NEW_ENTITY));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.PARTICLE));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_NORMAL));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_LIGHTMAP));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_TEX));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX_COLOR));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR));
        getProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL));
    }
    
    public static void shutdown() {
        glDeleteShader(vertexShader);
    } 
    
    private static int createProgramForFormat(VertexFormatOutput outputFormat) {
        final int vertexProgram = glCreateProgram();
        glAttachShader(vertexProgram, vertexShader);
        glTransformFeedbackVaryings(vertexProgram, outputFormat.varyings(), GL_INTERLEAVED_ATTRIBS);
        glProgramParameteri(vertexProgram, GL_PROGRAM_SEPARABLE, GL_TRUE);
        glLinkProgram(vertexProgram);
        
        if (glGetProgrami(vertexProgram, GL_LINK_STATUS) != GL_TRUE) {
            final var infoLog = glGetProgramInfoLog(vertexProgram);
            glDeleteShader(vertexShader);
            glDeleteProgram(vertexProgram);
            throw new IllegalStateException("Feedback program link failed for " + outputFormat.format() + '\n' + infoLog + '\n');
        }
        
        glDetachShader(vertexProgram, vertexShader);
        return vertexProgram;
    }
    
    public static void setupDrawInfo(DrawInfo drawInfo) {
        
    }
    
    public static int getProgramForOutputFormat(VertexFormatOutput formatOutput) {
        return programs.computeIfAbsent(formatOutput, GL46FeedbackPrograms::createProgramForFormat);
    }
}
