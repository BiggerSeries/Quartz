package net.roguelogix.quartz.internal.gl33;

import static org.lwjgl.opengl.GL33C.*;

/*
 * Apple's driver is broken, and doesn't allow programs with only a fragment shader to be used for transform feedback
 * solution is to add this bullshit fragment shader to it, so then it doesnt complain about something it shouldn't be complaining about
 *
 * This is true for at least the GL -> Metal layer used on M1
 */
public class BrokenMacDriverWorkaroundFragmentShader {
    private static int bullshitFragShaderBecauseApple;
    
    public static void startup() {
        bullshitFragShaderBecauseApple = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(bullshitFragShaderBecauseApple, "#version 150\nout vec4 bullshitColor; void main(){ bullshitColor = vec4(1, 1, 1, 1); }");
        glCompileShader(bullshitFragShaderBecauseApple);
        
        if (glGetShaderi(bullshitFragShaderBecauseApple, GL_COMPILE_STATUS) != GL_TRUE) {
            final var infoLog = glGetShaderInfoLog(bullshitFragShaderBecauseApple);
            glDeleteShader(bullshitFragShaderBecauseApple);
            throw new IllegalStateException("Failed to compile bullshit fragment shader code: " + infoLog);
        }
    }
    
    public static void shutdown() {
        glDeleteShader(bullshitFragShaderBecauseApple);
        bullshitFragShaderBecauseApple = 0;
    }
    
    public static int bullshitFragShaderBecauseApple() {
        return bullshitFragShaderBecauseApple;
    }
}
