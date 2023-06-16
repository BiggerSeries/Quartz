package net.roguelogix.quartz.internal.gl33;

import net.roguelogix.quartz.internal.QuartzCore;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.lwjgl.opengl.GL;

import static net.roguelogix.quartz.internal.MagicNumbers.*;

public class GL33Statics {
    public static final boolean AVAILABLE;
    
    public static final Vector3ic LIGHT_TEXTURE_ARRAY_FULL_SIZE = new Vector3i(512, 640, 1024);
    
    public static final int INSTANCE_DATA_BYTE_SIZE = 128;
    public static final int STATIC_MATRIX_OFFSET = 0;
    public static final int STATIC_NORMAL_MATRIX_OFFSET = STATIC_MATRIX_OFFSET + MATRIX_4F_BYTE_SIZE;
    public static final int WORLD_POSITION_OFFSET = STATIC_NORMAL_MATRIX_OFFSET + GLSL_MATRIX_3F_BYTE_SIZE;
    public static final int DYNAMIC_MATRIX_ID_OFFSET = WORLD_POSITION_OFFSET + IVEC3_BYTE_SIZE;
    
    
    public static final int POSITION_LOCATION = 0;
    public static final int COLOR_LOCATION = 1;
    public static final int TEX_COORD_LOCATION = 2;
    public static final int NORMAL_LOCATION = 3;
    public static final int WORLD_POSITION_LOCATION = 4;
    public static final int DYNAMIC_MATRIX_ID_LOCATION = 5;
    // location 6 open
    // location 7 open
    public static final int STATIC_MATRIX_LOCATION = 8;
    public static final int STATIC_NORMAL_MATRIX_LOCATION = 12;
    // location 15 open
    
    // 16 locations available, so, none left, if more are needed, will need to pack values
    // lightInfo and colorIn could be packed together
    
    static {
        AVAILABLE = checkRequirements();
    }
    
    private static boolean checkRequirements() {
        final var capabilities = GL.getCapabilities();
        
        if (!capabilities.OpenGL33) {
            QuartzCore.LOGGER.debug("Failure, OpenGL 3.3 not found");
            return false;
        }
        
        if (!capabilities.GL_ARB_separate_shader_objects) {
            QuartzCore.LOGGER.debug("Failure, GL_ARB_separate_shader_objects not found");
            return false;
        }
        
        return true;
    }
}
