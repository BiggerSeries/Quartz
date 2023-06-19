package net.roguelogix.quartz.internal.gl33;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.resources.ResourceLocation;
import net.roguelogix.phosphophyllite.util.Util;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.internal.util.VertexFormatOutput;

import java.util.function.BiConsumer;

import static net.roguelogix.quartz.internal.gl33.BrokenMacDriverWorkaroundFragmentShader.bullshitFragShaderBecauseApple;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.GL_PROGRAM_SEPARABLE;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glProgramParameteri;
import static org.lwjgl.opengl.GL33C.*;

// TODO: GL33 and GL46 variants basically identical, should merge
public class GL33FeedbackPrograms {
    
    public static final ResourceLocation shaderLocation = new ResourceLocation(Quartz.modid, "shaders/gl33/transform_feedback.vert");
    public static final ResourceLocation postShaderLocation = new ResourceLocation(Quartz.modid, "shaders/gl33/light_post_pass.vert");
    
    private static int vertexShader;
    private static final Reference2IntMap<VertexFormatOutput> programs = new Reference2IntArrayMap<>();
    private static int postShader;
    private static final Reference2ReferenceMap<VertexFormatOutput, IntIntPair> postPrograms = new Reference2ReferenceArrayMap<>();
    
    
    public static void startup() {
        var extensionsBuilder = new StringBuilder();
        // gpuinfo says this is supported, so im using it
        extensionsBuilder.append("#version 330 core\n");
        extensionsBuilder.append("#line 1 1\n");
        // no GLSL extensions used outside of 330 core
        
        var prependBuilder = new StringBuilder();
        prependBuilder.append("#line 0 2\n");
        prependBuilder.append("#define QUARTZ_INSERT_DEFINES\n");
        
        
        // the string concat will happen once because these are static finals
        prependBuilder.append("#define POSITION_LOCATION " + GL33Statics.POSITION_LOCATION + "\n");
        prependBuilder.append("#define COLOR_LOCATION " + GL33Statics.COLOR_LOCATION + "\n");
        prependBuilder.append("#define TEX_COORD_LOCATION " + GL33Statics.TEX_COORD_LOCATION + "\n");
        prependBuilder.append("#define NORMAL_LOCATION " + GL33Statics.NORMAL_LOCATION + "\n");
        
        prependBuilder.append("#define WORLD_POSITION_LOCATION " + GL33Statics.WORLD_POSITION_LOCATION + "\n");
        prependBuilder.append("#define DYNAMIC_MATRIX_ID_LOCATION " + GL33Statics.DYNAMIC_MATRIX_ID_LOCATION + "\n");
        prependBuilder.append("#define STATIC_MATRIX_LOCATION " + GL33Statics.STATIC_MATRIX_LOCATION + "\n");
        prependBuilder.append("#define STATIC_NORMAL_MATRIX_LOCATION " + GL33Statics.STATIC_NORMAL_MATRIX_LOCATION + "\n");
        
        var shaderCode = Util.readResourceLocation(shaderLocation);
        if (shaderCode == null) {
            throw new IllegalStateException("Failed to load shader code for " + shaderLocation);
        }
        
        vertexShader = glCreateShader(GL_VERTEX_SHADER);
        
        glShaderSource(vertexShader, extensionsBuilder, prependBuilder, shaderCode);
        glCompileShader(vertexShader);
        
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) != GL_TRUE) {
            final var infoLog = glGetShaderInfoLog(vertexShader);
            glDeleteShader(vertexShader);
            throw new IllegalStateException("Feedback shader compilation failed for " + shaderLocation + '\n' + infoLog + '\n');
        }
        
        {
            var postShaderCode = Util.readResourceLocation(postShaderLocation);
            if (postShaderCode == null) {
                throw new IllegalStateException("Failed to load shader code for " + postShaderLocation);
            }
            postShader = glCreateShader(GL_VERTEX_SHADER);
            
            glShaderSource(postShader, postShaderCode);
            glCompileShader(postShader);
            
            if (glGetShaderi(postShader, GL_COMPILE_STATUS) != GL_TRUE) {
                final var infoLog = glGetShaderInfoLog(postShader);
                glDeleteShader(postShader);
                glDeleteShader(vertexShader);
                throw new IllegalStateException("Feedback shader compilation failed for " + postShaderLocation + '\n' + infoLog + '\n');
            }
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
        
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.BLOCK));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.NEW_ENTITY));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.PARTICLE));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_NORMAL));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_LIGHTMAP));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_TEX));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX_COLOR));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR));
        getPostProgramForOutputFormat(VertexFormatOutput.of(DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL));
    }
    
    public static void shutdown() {
        glDeleteShader(vertexShader);
        vertexShader = 0;
        for (final var value : programs.reference2IntEntrySet()) {
            glDeleteProgram(value.getIntValue());
        }
        programs.clear();
        
        glDeleteShader(postShader);
        postShader = 0;
        for (final var value : postPrograms.reference2ReferenceEntrySet()) {
            glDeleteProgram(value.getValue().firstInt());
        }
        postPrograms.clear();
    }
    
    public static void reload() {
        shutdown();
        startup();
    }
    
    private static int createProgramForFormat(VertexFormatOutput outputFormat) {
        final int vertexProgram = glCreateProgram();
        glAttachShader(vertexProgram, vertexShader);
        glAttachShader(vertexProgram, bullshitFragShaderBecauseApple());
        
        glTransformFeedbackVaryings(vertexProgram, outputFormat.varyings(), GL_INTERLEAVED_ATTRIBS);
        glProgramParameteri(vertexProgram, GL_PROGRAM_SEPARABLE, GL_TRUE);
        glLinkProgram(vertexProgram);
        
        if (glGetProgrami(vertexProgram, GL_LINK_STATUS) != GL_TRUE) {
            final var infoLog = glGetProgramInfoLog(vertexProgram);
            glDeleteProgram(vertexProgram);
            throw new IllegalStateException("Feedback program link failed for " + outputFormat.format() + '\n' + infoLog + '\n');
        }
        
        glDetachShader(vertexProgram, bullshitFragShaderBecauseApple());;
        glDetachShader(vertexProgram, vertexShader);
        
        final var UBOLocation = glGetUniformBlockIndex(vertexProgram, "MainUBO");
        glUniformBlockBinding(vertexProgram, UBOLocation, 0);
        
        glUseProgram(vertexProgram);
        final var chunkIndexTextureLocation = glGetUniformLocation(vertexProgram, "intermediateLightChunkIndexLookup");
        glUniform1i(chunkIndexTextureLocation, 1);
        glUseProgram(0);
        
        return vertexProgram;
    }
    
    private static IntIntPair createPostProgramForFormat(VertexFormatOutput outputFormat) {
        final int vertexProgram = glCreateProgram();
        glAttachShader(vertexProgram, postShader);
        glAttachShader(vertexProgram, bullshitFragShaderBecauseApple());
        
        glTransformFeedbackVaryings(vertexProgram, outputFormat.varyings(), GL_INTERLEAVED_ATTRIBS);
        glProgramParameteri(vertexProgram, GL_PROGRAM_SEPARABLE, GL_TRUE);
        glLinkProgram(vertexProgram);
        
        if (glGetProgrami(vertexProgram, GL_LINK_STATUS) != GL_TRUE) {
            final var infoLog = glGetProgramInfoLog(vertexProgram);
            glDeleteProgram(vertexProgram);
            throw new IllegalStateException("Feedback program link failed for " + outputFormat.format() + '\n' + infoLog + '\n');
        }
        
        glDetachShader(vertexProgram, bullshitFragShaderBecauseApple());
        glDetachShader(vertexProgram, postShader);
        
        final var UBOLocation = glGetUniformBlockIndex(vertexProgram, "MainUBO");
        glUniformBlockBinding(vertexProgram, UBOLocation, 0);
        
        glUseProgram(vertexProgram);
        for (int i = 0; i < 6; i++) {
            final var chunkIndexTextureLocation = glGetUniformLocation(vertexProgram, "intermediateLightDataTexture[" + i + "]");
            glUniform1i(chunkIndexTextureLocation, 2 + i);
        }
        glUseProgram(0);
        
        return new IntIntImmutablePair(vertexProgram, glGetUniformLocation(vertexProgram, "activeArrayLayer"));
    }
    
    public static int getProgramForOutputFormat(VertexFormatOutput formatOutput) {
        return programs.computeIfAbsent(formatOutput, GL33FeedbackPrograms::createProgramForFormat);
    }
    
    public static IntIntPair getPostProgramForOutputFormat(VertexFormatOutput formatOutput) {
        return postPrograms.computeIfAbsent(formatOutput, GL33FeedbackPrograms::createPostProgramForFormat);
    }
    
    private static final Object2ObjectMap<String, BiConsumer<Integer, Integer>> varyingSetupFunctions = new Object2ObjectArrayMap<>();
    
    static {
        varyingSetupFunctions.put("positionOutput", (stride, offset) -> {
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, offset);
        });
        varyingSetupFunctions.put("normalOutput", (stride, offset) -> {
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 3, GL_BYTE, true, stride, offset);
        });
        varyingSetupFunctions.put("colorOutput", (stride, offset) -> {
            glEnableVertexAttribArray(2);
            glVertexAttribIPointer(2, 1, GL_UNSIGNED_INT, stride, offset);
        });
        varyingSetupFunctions.put("textureOutput", (stride, offset) -> {
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 2, GL_FLOAT, false, stride, offset);
        });
        varyingSetupFunctions.put("overlayOutput", (stride, offset) -> {
            glEnableVertexAttribArray(4);
            glVertexAttribIPointer(4, 1, GL_UNSIGNED_INT, stride, offset);
        });
        varyingSetupFunctions.put("lightmapOutput", (stride, offset) -> {
            glEnableVertexAttribArray(5);
            glVertexAttribIPointer(5, 1, GL_UNSIGNED_INT, stride, offset);
        });
    }
    
    public static void setupVAOForPostProgramOutputFormat(VertexFormatOutput formatOutput) {
        final var elements = formatOutput.format().getElements();
        final int stride = formatOutput.vertexSize();
        int offset = 0;
        for (int i = 0; i < elements.size(); i++) {
            final var element = elements.get(i);
            final var outputName = VertexFormatOutput.outputName(element);
            if (element.getUsage() == VertexFormatElement.Usage.PADDING) {
                offset++;
                continue;
            }
            final var setupFunc = varyingSetupFunctions.get(outputName);
            setupFunc.accept(stride, offset);
            offset += element.getByteSize();
        }
    }
}
