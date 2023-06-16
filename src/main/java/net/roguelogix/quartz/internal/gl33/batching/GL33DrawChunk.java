package net.roguelogix.quartz.internal.gl33.batching;

import net.minecraft.client.renderer.RenderType;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.B3DStateHelper;
import net.roguelogix.quartz.internal.common.InternalMesh;
import net.roguelogix.quartz.internal.gl33.GL33FeedbackDrawing;
import net.roguelogix.quartz.internal.util.IndirectDrawInfo;
import net.roguelogix.quartz.internal.util.VertexFormatOutput;

import static org.lwjgl.opengl.GL33C.*;

public class GL33DrawChunk {
    final GL33InstanceManager manager;
    final RenderType renderType;
    private final VertexFormatOutput outputFormat;
    
    public final int baseVertex;
    public final int vertexCount;
    
    int drawIndex;
    
    int[] VAO = new int[1];
    
    GL33DrawChunk(GL33InstanceManager manager, RenderType renderType, InternalMesh.Manager.TrackedMesh.Component component) {
        this.manager = manager;
        this.renderType = renderType;
        outputFormat = VertexFormatOutput.of(renderType.format());
        baseVertex = component.vertexOffset();
        vertexCount = component.vertexCount();
        rebuildVAO(manager.instanceDataAlloc.offset());
        final var VAOAry = VAO;
        QuartzCore.mainThreadClean(this, () -> glDeleteVertexArrays(VAOAry[0]));
    }
    
    void rebuildVAO(int offset) {
        glDeleteVertexArrays(VAO[0]);
        VAO[0] = glGenVertexArrays();
        B3DStateHelper.bindVertexArray(VAO[0]);
        GL33FeedbackDrawing.setupVAO(manager.drawBatch.intermediateInstanceDataBuffer.handle(), offset);
        B3DStateHelper.bindVertexArray(0);
    }
    
    void draw() {
        B3DStateHelper.bindVertexArray(VAO[0]);
        glDrawArraysInstanced(GL_POINTS, baseVertex, vertexCount, manager.instanceCount());
    }
}
