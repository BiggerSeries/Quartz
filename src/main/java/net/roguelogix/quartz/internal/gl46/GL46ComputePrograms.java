package net.roguelogix.quartz.internal.gl46;

import net.minecraft.resources.ResourceLocation;
import net.roguelogix.phosphophyllite.util.Util;
import net.roguelogix.quartz.Quartz;

import static org.lwjgl.opengl.GL45C.*;

public class GL46ComputePrograms {
    private static int dynamicMatrixProgram;
    private static int lightChunkProgram;
    
    private static int createProgram(String path) {
        int program = glCreateShaderProgramv(GL_COMPUTE_SHADER, Util.readResourceLocation(new ResourceLocation(Quartz.modid, path)));
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            final var infoLog = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Compute program link failed for " + program + '\n' + infoLog + '\n');
        }
        return program;
    }
    
    public static void startup() {
        dynamicMatrixProgram = createProgram("shaders/gl46/dynamic_matrix.comp");
        lightChunkProgram = createProgram("shaders/gl46/light_chunks.comp");
    }
    
    public static void shutdown() {
        glDeleteProgram(dynamicMatrixProgram);
        dynamicMatrixProgram = 0;
        glDeleteProgram(lightChunkProgram);
        lightChunkProgram = 0;
    }
    
    public static void reload() {
        shutdown();
        startup();
    }
    
    public static int dynamicMatrixProgram() {
        return dynamicMatrixProgram;
    }
    
    public static int lightChunkProgram() {
        return lightChunkProgram;
    }
}
