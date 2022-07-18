package net.roguelogix.quartz.internal.common;

import com.mojang.blaze3d.vertex.BufferUploader;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Blaze3d caches some state, sometimes i override this state, so, need to make sure it will set it back after i modify it
 */
@NonnullDefault
public class B3DStateHelper {
    
    public static void bindArrayBuffer(int buffer) {
        BufferUploader.invalidate();
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
    }
    
    public static void bindElementBuffer(int buffer) {
        BufferUploader.invalidate();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffer);
    }
    
    public static void bindVertexArray(int vertexArray) {
        BufferUploader.invalidate();
        glBindVertexArray(vertexArray);
    }
}
