package net.roguelogix.quartz.internal.gl46.batching;

import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.client.renderer.RenderType;
import net.roguelogix.phosphophyllite.repack.org.joml.AABBi;
import net.roguelogix.phosphophyllite.repack.org.joml.Matrix4fc;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector4f;
import net.roguelogix.phosphophyllite.util.FastArraySet;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DynamicMatrix;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.internal.DrawBatchInternal;
import net.roguelogix.quartz.internal.IrisDetection;
import net.roguelogix.quartz.internal.MultiBuffer;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.DrawInfo;
import net.roguelogix.quartz.internal.common.DynamicMatrixManager;
import net.roguelogix.quartz.internal.common.InternalMesh;
import net.roguelogix.quartz.internal.gl46.GL46Buffer;
import net.roguelogix.quartz.internal.gl46.GL46Core;
import net.roguelogix.quartz.internal.gl46.GL46FeedbackDrawing;
import net.roguelogix.quartz.internal.gl46.GL46Statics;

import javax.annotation.Nullable;

import static net.roguelogix.quartz.internal.MagicNumbers.IDENTITY_MATRIX;
import static net.roguelogix.quartz.internal.MagicNumbers.INT_BYTE_SIZE;

import static org.lwjgl.opengl.GL46C.*;

// dont you dare touch my spaghet
@NonnullDefault
public class GL46DrawBatch implements DrawBatchInternal {
    
    private boolean enabled = true;
    private boolean culled = false;
    private AABBi cullAABB = null;
    
    private Vector4f cullVector = new Vector4f();
    private Vector4f cullVectorMin = new Vector4f();
    private Vector4f cullVectorMax = new Vector4f();
    
    final MultiBuffer<GL46Buffer> instanceDataBuffer = new MultiBuffer<>(GL46Statics.FRAMES_IN_FLIGHT);
    
    final Reference2ReferenceMap<InternalMesh, Gl46InstanceManager> instanceManagers = new Reference2ReferenceOpenHashMap<>();
    final ReferenceSet<Gl46InstanceManager> instanceBatches = new ReferenceOpenHashSet<>();
    final FastArraySet<Gl46InstanceManager> dirtyBatches = new FastArraySet<>();
    
    final MultiBuffer<GL46Buffer> dynamicMatrixBuffer = new MultiBuffer<>(GL46Statics.FRAMES_IN_FLIGHT);
    final DynamicMatrixManager dynamicMatrixManager = new DynamicMatrixManager(dynamicMatrixBuffer);
    final DynamicMatrix IDENTITY_DYNAMIC_MATRIX = dynamicMatrixManager.createMatrix(null, null);
    
    public GL46DrawBatch() {
        final var renderTypes = usedRenderTypes;
        final var indirectBuffers = indirectBufferAllocs;
        QuartzCore.mainThreadClean(this, () -> {
            for (int i = 0; i < indirectBuffers.length; i++) {
                indirectBuffers[i].free();
            }
            renderTypes.forEach(GL46FeedbackDrawing::removeRenderTypeUse);
        });
    }
    
    @Nullable
    @Override
    public Instance createInstance(Vector3ic position, Mesh mesh, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABBi aabb) {
        if (!(mesh instanceof InternalMesh castedMesh)) {
            return null;
        }
        if (dynamicMatrix == null) {
            dynamicMatrix = IDENTITY_DYNAMIC_MATRIX;
        }
        if (!(dynamicMatrix instanceof DynamicMatrixManager.Matrix castedMatrix) || !dynamicMatrixManager.owns(dynamicMatrix)) {
            return null;
        }
        if (staticMatrix == null) {
            staticMatrix = IDENTITY_MATRIX;
        }
        final var instanceManager = instanceManagers.computeIfAbsent(castedMesh, (InternalMesh internalMesh) -> new Gl46InstanceManager(this, internalMesh, true));
        return instanceManager.createInstance(position, castedMatrix, staticMatrix, aabb);
    }
    
    @Nullable
    @Override
    public InstanceBatch createInstanceBatch(Mesh mesh) {
        if (!(mesh instanceof InternalMesh castedMesh)) {
            return null;
        }
        return new GL46InstanceBatch(new Gl46InstanceManager(this, castedMesh, false));
    }
    
    @Override
    public DynamicMatrix createDynamicMatrix(@Nullable DynamicMatrix parentTransform, @Nullable DynamicMatrix.UpdateFunc updateFunc) {
        return dynamicMatrixManager.createMatrix(updateFunc, parentTransform);
    }
    
    @Override
    public void setCullAABB(AABBi aabb) {
        cullAABB = aabb;
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public boolean isEmpty() {
        return instanceManagers.isEmpty() && instanceBatches.isEmpty();
    }
    
    private int currentFrame = -1;
    private long[] instanceDataFences = new long[GL46Statics.FRAMES_IN_FLIGHT];
    
    @Override
    public void updateAndCull(DrawInfo drawInfo) {
        currentFrame = GL46Core.INSTANCE.frameInFlight();
        indirectBuffers[currentIndirectBuffer].setActiveFrame(currentFrame);
        instanceDataBuffer.setActiveFrame(currentFrame);
        dynamicMatrixBuffer.setActiveFrame(currentFrame);
        
        // these updates are global frame specific, so they must be checked and done every frame
        if (!dirtyBatches.isEmpty()) {
            if (instanceDataFences[currentFrame] != 0 && glIsSync(instanceDataFences[currentFrame])) {
                glClientWaitSync(instanceDataFences[currentFrame], GL_SYNC_FLUSH_COMMANDS_BIT, -1);
            }
            for (int i = 0; i < dirtyBatches.size(); i++) {
                final var batch = dirtyBatches.get(i);
                if (batch.writeUpdates()) {
                    continue;
                }
                // null or no longer dirty
                dirtyBatches.remove(batch);
                if (dirtyBatches.isEmpty()) {
                    break;
                }
                i--;
            }
        }
        
        if (!enabled) {
            return;
        }
        
        if (isEmpty()) {
            return;
        }
        
        if (cullAABB != null && !IrisDetection.areShadersActive()) {
            cullVectorMin.set(2);
            cullVectorMax.set(-2);
            for (int i = 0; i < 8; i++) {
                cullVector.set(((i & 1) == 0 ? cullAABB.maxX : cullAABB.minX) - drawInfo.playerPosition.x, ((i & 2) == 0 ? cullAABB.maxY : cullAABB.minY) - drawInfo.playerPosition.y, ((i & 4) == 0 ? cullAABB.maxZ : cullAABB.minZ) - drawInfo.playerPosition.z, 1);
                cullVector.sub(drawInfo.playerSubBlock.x, drawInfo.playerSubBlock.y, drawInfo.playerSubBlock.z, 0);
                cullVector.mul(drawInfo.projectionMatrix);
                cullVector.div(cullVector.w);
                cullVectorMin.min(cullVector);
                cullVectorMax.max(cullVector);
            }
            culled = cullVectorMin.x > 1 || cullVectorMax.x < -1 || cullVectorMin.y > 1 || cullVectorMax.y < -1 || cullVectorMin.z > 1 || cullVectorMax.z < -1;
        } else {
            culled = false;
        }
        
        if (culled) {
            return;
        }
        
        if (indirectDirty) {
            
            int totalChunks = 0;
            for (final var value : instanceBatches) {
                totalChunks += value.drawChunks.size();
            }
            
            final var newBufferIndex = (currentIndirectBuffer + 1) % indirectBufferFences.length;
            if (indirectBufferFences[newBufferIndex] != 0) {
                if (glIsSync(indirectBufferFences[newBufferIndex])) {
                    glClientWaitSync(indirectBufferFences[newBufferIndex], GL_SYNC_FLUSH_COMMANDS_BIT, -1);
                }
                indirectBufferFences[newBufferIndex] = 0;
            }
            
            final var indirectBuffer = indirectBuffers[newBufferIndex];
            final var alloc = indirectBufferAllocs[newBufferIndex] = indirectBuffer.realloc(indirectBufferAllocs[newBufferIndex], totalChunks * 4 * INT_BYTE_SIZE, totalChunks * 5 * INT_BYTE_SIZE);
            
            for (int i = 0; i < GL46Statics.FRAMES_IN_FLIGHT; i++) {
                indirectBuffer.setActiveFrame(i);
                final var activeAlloc = alloc.activeAllocation();
                final var pointer = activeAlloc.address();
                int indexOffset = 0;
                for (final var value : renderChunkLists.entrySet()) {
                    final int indirectByteOffset = indexOffset * INT_BYTE_SIZE;
                    final int indirectDrawCount = value.getValue().size();
                    final long packedOffsets = ((long) indirectByteOffset << 32) | indirectDrawCount;
                    int totalVertices = 0;
                    for (final var chunk : value.getValue()) {
                        final var indirectInfo = chunk.indirectDrawInfo(i);
                        totalVertices += indirectInfo.elementCount() * indirectInfo.instanceCount();
                        // feedback is always done as draw arrays of points, MC handles the element buffer
                        pointer.putIntIdx(indexOffset++, indirectInfo.elementCount()); // (vertex) count
                        pointer.putIntIdx(indexOffset++, indirectInfo.instanceCount()); // instanceCount
                        pointer.putIntIdx(indexOffset++, indirectInfo.baseVertex()); // first (vertex)
                        pointer.putIntIdx(indexOffset++, indirectInfo.baseInstance()); // baseInstance
                    }
                    drawOffsets[i].put(value.getKey(), packedOffsets);
                    verticesPerRenderType.put(value.getKey(), totalVertices);
                }
            }
            
            renderChunkLists.keySet().forEach(GL46FeedbackDrawing::addRenderTypeUse);
            usedRenderTypes.forEach(GL46FeedbackDrawing::removeRenderTypeUse);
            usedRenderTypes.clear();
            usedRenderTypes.addAll(renderChunkLists.keySet());
            
            currentIndirectBuffer = newBufferIndex;
            indirectDirty = false;
        }
    }
    
    private final Reference2ReferenceMap<RenderType, ReferenceArrayList<GL46DrawChunk>> renderChunkLists = new Reference2ReferenceArrayMap<>();
    private final ReferenceArrayList<RenderType> usedRenderTypes = new ReferenceArrayList<>();
    
    void addDrawChunk(GL46DrawChunk chunk) {
        final var components = renderChunkLists.computeIfAbsent(chunk.renderType, e -> new ReferenceArrayList<>());
        chunk.drawIndex = components.size();
        components.add(chunk);
        setIndirectInfoDirty();
    }
    
    void removeDrawChunk(GL46DrawChunk chunk) {
        if (chunk.drawIndex == -1) {
            return;
        }
        final var components = renderChunkLists.get(chunk.renderType);
        if (components == null) {
            return;
        }
        final var lastChunk = components.pop();
        setIndirectInfoDirty();
        if (lastChunk == chunk) {
            chunk.drawIndex = -1;
            return;
        }
        lastChunk.drawIndex = chunk.drawIndex;
        chunk.drawIndex = -1;
        components.set(lastChunk.drawIndex, lastChunk);
    }
    
    private boolean indirectDirty = false;
    private int currentIndirectBuffer = 0;
    private MultiBuffer<GL46Buffer>[] indirectBuffers = new MultiBuffer[]{new MultiBuffer<>(GL46Statics.FRAMES_IN_FLIGHT), new MultiBuffer<>(GL46Statics.FRAMES_IN_FLIGHT)};
    private MultiBuffer<GL46Buffer>.Allocation[] indirectBufferAllocs = new MultiBuffer.Allocation[2];
    private long[] indirectBufferFences = new long[2];
    
    private final Reference2IntMap<RenderType> verticesPerRenderType = new Reference2IntOpenHashMap<>();
    private final Reference2LongMap<RenderType>[] drawOffsets = new Reference2LongMap[GL46Statics.FRAMES_IN_FLIGHT];
    {
        for (int i = 0; i < drawOffsets.length; i++) {
            drawOffsets[i] = new Reference2LongOpenHashMap<>();
        }
    }
    
    public void setIndirectInfoDirty() {
        indirectDirty = true;
    }
    
    public void setFrameSync(long sync) {
        indirectBufferFences[currentIndirectBuffer] = sync;
        instanceDataFences[currentFrame] = sync;
    }
    
    public int verticesForRenderType(RenderType renderType, boolean shadowsEnabled) {
        if (!enabled) {
            return 0;
        }
        if (culled && !shadowsEnabled) {
            return 0;
        }
        int vertices = verticesPerRenderType.getOrDefault(renderType, -1);
        if (vertices == -1) {
            return 0;
        }
        return (int) (vertices);
    }
    
    @Override
    public void drawFeedback(RenderType renderType, boolean shadowsEnabled) {
        if (!enabled) {
            return;
        }
        if (culled && !shadowsEnabled) {
            return;
        }
        if (isEmpty()) {
            return;
        }
        
        final long offsets = drawOffsets[currentFrame].getOrDefault(renderType, -1);
        if (offsets == -1) {
            return;
        }
        final int indirectOffset = (int) (offsets >> 32);
        final int draws = (int) (offsets);
        
        // updating vertex buffer bindings is pretty quick to do
        // changing the format, isn't
        // this does assume a VAO is already bound
        // the feedback program too, and all uniforms are set
        // this assumes a lot of stuff
        glBindVertexBuffer(1, instanceDataBuffer.activeBuffer().handle(), 0, GL46Statics.INSTANCE_DATA_BYTE_SIZE);
        // TODO: roll this into the "static" instance data
        //       currently this causes the GPU to do expensive matrix ops per vertex, instead of per instance
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, dynamicMatrixBuffer.activeBuffer().handle());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffers[currentIndirectBuffer].activeBuffer().handle());
        glMultiDrawArraysIndirect(GL_POINTS, indirectOffset, draws, 0);
    }
}
