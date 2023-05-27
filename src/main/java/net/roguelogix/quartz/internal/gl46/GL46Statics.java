package net.roguelogix.quartz.internal.gl46;

import net.roguelogix.phosphophyllite.repack.org.joml.Vector3i;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector3ic;
import net.roguelogix.quartz.internal.QuartzCore;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import static net.roguelogix.quartz.internal.MagicNumbers.*;
import static org.lwjgl.opengl.ARBSparseTexture.*;
import static org.lwjgl.opengl.ARBSparseTexture.GL_VIRTUAL_PAGE_SIZE_Z_ARB;
import static org.lwjgl.opengl.GL45C.*;

public class GL46Statics {
    
    public static final boolean AVAILABLE;
    public static final boolean REQUIRE_SPARSE_TEXTURE = false;
    public static final boolean SPARSE_TEXTURE_ENABLED;
    
    public static final int FRAMES_IN_FLIGHT = 2;
    
    public static final Vector3ic LIGHT_SPARE_TEXTURE_SIZE = new Vector3i(512, 640, 1024);
    
    
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
    // light/matrix IDs could be packed together
    
    static {
        final var capabilities = GL.getCapabilities();
        AVAILABLE = capCheck();
        SPARSE_TEXTURE_ENABLED = false;
        if (!SPARSE_TEXTURE_ENABLED) {
            QuartzCore.LOGGER.warn("Sparse texture disabled, this will massively increase minimum vram requirements for lighting");
        }
    }
    
    private static boolean capCheck() {
        final var capabilities = GL.getCapabilities();
        
        if (!capabilities.OpenGL45) {
            return false;
        }
        if (!capabilities.GL_ARB_sparse_texture) {
            if (REQUIRE_SPARSE_TEXTURE) {
                return false;
            }
        } else {
            try (var stack = MemoryStack.stackPush()) {
                final var format = GL_R16UI;
                final var pageSizeCount = glGetInternalformati(GL_TEXTURE_2D_ARRAY, format, GL_NUM_VIRTUAL_PAGE_SIZES_ARB);
                if (pageSizeCount == 0) {
                    return false;
                }
                final var sizesX = stack.mallocInt(pageSizeCount);
                final var sizesY = stack.mallocInt(pageSizeCount);
                final var sizesZ = stack.mallocInt(pageSizeCount);
                glGetInternalformativ(GL_TEXTURE_2D_ARRAY, format, GL_VIRTUAL_PAGE_SIZE_X_ARB, sizesX);
                glGetInternalformativ(GL_TEXTURE_2D_ARRAY, format, GL_VIRTUAL_PAGE_SIZE_Y_ARB, sizesY);
                glGetInternalformativ(GL_TEXTURE_2D_ARRAY, format, GL_VIRTUAL_PAGE_SIZE_Z_ARB, sizesZ);
                
                boolean foundSize = false;
                for (int i = 0; i < pageSizeCount; i++) {
                    int xSize = sizesX.get(i);
                    int ySize = sizesY.get(i);
                    int zSize = sizesZ.get(i);
                    if (LIGHT_SPARE_TEXTURE_SIZE.x() % xSize != 0) {
                        continue;
                    }
                    if (LIGHT_SPARE_TEXTURE_SIZE.y() % ySize != 0) {
                        continue;
                    }
                    if (1 % zSize != 0) {
                        continue;
                    }
                    foundSize = true;
                    break;
                }
                if (!foundSize) {
                    return false;
                }
            }
        }
        
        return true;
    }
}
