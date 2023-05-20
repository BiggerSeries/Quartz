package net.roguelogix.quartz.internal.gl46.batching;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.internal.common.InternalMesh;
import net.roguelogix.quartz.internal.gl.GLCore;
import net.roguelogix.quartz.internal.util.IndirectDrawInfo;
import net.roguelogix.quartz.internal.util.VertexFormatOutput;

@NonnullDefault
public class GL46DrawChunk {
    
    private final Gl46InstanceManager manager;
    final RenderType renderType;
    private final VertexFormatOutput outputFormat;
    
    public final int baseVertex;
    public final int vertexCount;
    
    int drawIndex;
    
    GL46DrawChunk(Gl46InstanceManager manager, RenderType renderType, InternalMesh.Manager.TrackedMesh.Component component) {
        this.manager = manager;
        this.renderType = renderType;
        outputFormat = VertexFormatOutput.of(renderType.format());
        baseVertex = component.vertexOffset();
        vertexCount = component.vertexCount();
    }
    
    public IndirectDrawInfo indirectDrawInfo(int frame) {
        return new IndirectDrawInfo(vertexCount, manager.instanceCount(), baseVertex, manager.baseInstance(frame));
    }
    
    public int totalVertices() {
        return manager.instanceCount() * vertexCount;
    }
}
