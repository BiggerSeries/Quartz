package net.roguelogix.quartz.internal.gl;

import static org.lwjgl.opengl.ARBSeparateShaderObjects.glBindProgramPipeline;
import static org.lwjgl.opengl.GL20C.glUseProgram;

public class GLStateTracker {
    private static int boundPipeline = 0;
    private static int boundProgram = 0;
    
    public static void reset() {
        bindPipeline(0);
    }
    
    public static void bindPipeline(int pipeline) {
        if (boundPipeline != pipeline) {
            boundPipeline = pipeline;
            glBindProgramPipeline(pipeline);
        }
    }
    
    public static void bindProgram(int program) {
        bindPipeline(0);
        if (boundProgram != program) {
            boundProgram = program;
            glUseProgram(program);
        }
    }
}
