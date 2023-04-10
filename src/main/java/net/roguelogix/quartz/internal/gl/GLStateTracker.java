package net.roguelogix.quartz.internal.gl;

import static net.roguelogix.quartz.internal.MagicNumbers.GL.ATLAS_TEXTURE_UNIT;
import static net.roguelogix.quartz.internal.MagicNumbers.GL.ATLAS_TEXTURE_UNIT_GL;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glBindProgramPipeline;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL13C.glActiveTexture;

public class GLStateTracker {
    private static int boundAtlas = 0;
    private static int boundPipeline = 0;
    
    public static void reset() {
        boundAtlas = 0;
        boundPipeline = 0;
        glBindProgramPipeline(0);
    }
    
    public static void bindAtlas(int id) {
        if (id != boundAtlas) {
            glActiveTexture(ATLAS_TEXTURE_UNIT_GL);
            glBindTexture(GL_TEXTURE_2D, id);
            boundAtlas = id;
        }
    }
    
    public static void bindPipeline(int pipeline) {
        if(boundPipeline != pipeline) {
            boundPipeline = pipeline;
            glBindProgramPipeline(pipeline);
        }
    }
}
