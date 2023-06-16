package net.roguelogix.quartz.internal.gl33;

import net.minecraft.resources.ResourceLocation;
import net.roguelogix.phosphophyllite.util.Util;
import net.roguelogix.quartz.Quartz;

import static org.lwjgl.opengl.ARBSeparateShaderObjects.GL_PROGRAM_SEPARABLE;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glProgramParameteri;
import static org.lwjgl.opengl.GL33C.*;

// "Compute"
// really its transform feedback, but close enough
public class GL33ComputePrograms {
    
    private static int dynamicMatrixProgram;
    
    private static int createProgram(String path, String... outputs) {
        int shader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(shader, Util.readResourceLocation(new ResourceLocation(Quartz.modid, path)));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            final var infoLog = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Feedback shader compilation failed for " + path + '\n' + infoLog + '\n');
        }
        
        int program = glCreateProgram();
        
        glAttachShader(program, shader);
        glTransformFeedbackVaryings(program, outputs, GL_INTERLEAVED_ATTRIBS);
        glProgramParameteri(program, GL_PROGRAM_SEPARABLE, GL_TRUE);
        glLinkProgram(program);
        
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            final var infoLog = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Compute program link failed for " + path + '\n' + infoLog + '\n');
        }
        
        glDetachShader(program, shader);
        glDeleteShader(shader);
        return program;
    }
    
    
    public static void startup() {
        dynamicMatrixProgram = createProgram("shaders/gl33/dynamic_matrix.vert", "modelMatrixOut", "normalMatrixOut", "worldPositionOut", "dynamicMatrixIDOut");
        glUseProgram(dynamicMatrixProgram);
        {
            final var dynamicMatricesLocation = glGetUniformLocation(dynamicMatrixProgram, "dynamicMatrices");
            glUniform1i(dynamicMatricesLocation, 0);
        }
        glUseProgram(0);
    }
    
    public static void shutdown() {
        glDeleteProgram(dynamicMatrixProgram);
        dynamicMatrixProgram = 0;
    }
    
    public static int dynamicMatrixProgram() {
        return dynamicMatrixProgram;
    }
}
