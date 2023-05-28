package net.roguelogix.quartz.internal.gl46;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.JNI;
import org.lwjgl.system.NativeType;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_TRUE;

public class SparseTextureHelper {
    
    private static final long glTexturePageCommitmentEXTAddress;
    
    static {
        glTexturePageCommitmentEXTAddress = GL.getFunctionProvider().getFunctionAddress("glTexturePageCommitmentEXT");
    }
    
    public static boolean load() {
        return glTexturePageCommitmentEXTAddress != 0;
    }
    
    public static void glTexturePageCommitmentEXT(@NativeType("GLuint") int texture, @NativeType("GLint") int level, @NativeType("GLint") int xoffset, @NativeType("GLint") int yoffset, @NativeType("GLint") int zoffset, @NativeType("GLsizei") int width, @NativeType("GLsizei") int height, @NativeType("GLsizei") int depth, @NativeType("GLboolean") boolean commit) {
        if(glTexturePageCommitmentEXTAddress == 0){
            throw new NullPointerException();
        }
        JNI.callV(texture, level, xoffset, yoffset, zoffset, width, height, depth, commit ? GL_TRUE : GL_FALSE, glTexturePageCommitmentEXTAddress);
    }
}
