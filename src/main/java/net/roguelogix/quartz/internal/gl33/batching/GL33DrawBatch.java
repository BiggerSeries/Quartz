package net.roguelogix.quartz.internal.gl33.batching;

import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.SectionPos;
import net.roguelogix.phosphophyllite.util.FastArraySet;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.AABB;
import net.roguelogix.quartz.DynamicMatrix;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.internal.DrawBatchInternal;
import net.roguelogix.quartz.internal.IrisDetection;
import net.roguelogix.quartz.internal.MultiBuffer;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.B3DStateHelper;
import net.roguelogix.quartz.internal.common.DrawInfo;
import net.roguelogix.quartz.internal.common.DynamicMatrixManager;
import net.roguelogix.quartz.internal.common.InternalMesh;
import net.roguelogix.quartz.internal.gl33.GL33Buffer;
import net.roguelogix.quartz.internal.gl33.GL33FeedbackDrawing;
import net.roguelogix.quartz.internal.gl33.GL33LightEngine;
import org.joml.Matrix4fc;
import org.joml.Vector3ic;
import org.joml.Vector4f;

import javax.annotation.Nullable;

import static net.roguelogix.quartz.internal.MagicNumbers.IDENTITY_MATRIX;
import static org.lwjgl.opengl.GL33C.*;

@NonnullDefault
public class GL33DrawBatch implements DrawBatchInternal {
    
    private boolean enabled = true;
    private boolean culled = false;
    
    private boolean instanceAABBsDirty = false;
    @Nullable
    private AABB cullAABB = null;
    
    private Vector4f cullVector = new Vector4f();
    private Vector4f cullVectorMin = new Vector4f();
    private Vector4f cullVectorMax = new Vector4f();
    
    final GL33Buffer instanceDataBuffer = new GL33Buffer(false);
    final GL33Buffer intermediateInstanceDataBuffer = new GL33Buffer(true);
    
    final Reference2ReferenceMap<InternalMesh, GL33InstanceManager> instanceManagers = new Reference2ReferenceOpenHashMap<>();
    final ReferenceSet<GL33InstanceManager> instanceBatches = new ReferenceOpenHashSet<>();
    final FastArraySet<GL33InstanceManager> dirtyBatches = new FastArraySet<>();
    
    final MultiBuffer<GL33Buffer> dynamicMatrixBuffer = new MultiBuffer<>(1, false);
    
    final DynamicMatrixManager dynamicMatrixManager = new DynamicMatrixManager(dynamicMatrixBuffer);
    final int dynamicMatrixTexture;
    
    final DynamicMatrix IDENTITY_DYNAMIC_MATRIX = dynamicMatrixManager.createMatrix(null, null);
    
    
    private final Reference2ReferenceMap<RenderType, ReferenceArrayList<GL33DrawChunk>> renderChunkLists = new Reference2ReferenceArrayMap<>();
    private final ReferenceArrayList<RenderType> usedRenderTypes = new ReferenceArrayList<>();
    
    private boolean vertexCountDirty = false;
    private final Reference2IntMap<RenderType> verticesPerRenderType = new Reference2IntOpenHashMap<>();
    
    public GL33DrawBatch() {
        final var renderTypes = usedRenderTypes;
        final var matrixTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_BUFFER, matrixTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, dynamicMatrixBuffer.activeBuffer().handle());
        glBindTexture(GL_TEXTURE_BUFFER, 0);
        QuartzCore.mainThreadClean(this, () -> {
            glDeleteTextures(matrixTexture);
            renderTypes.forEach(GL33FeedbackDrawing::removeRenderTypeUse);
        });
        this.dynamicMatrixTexture = matrixTexture;
    }
    
    @Nullable
    @Override
    public Instance createInstance(Vector3ic position, Mesh mesh, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABB aabb) {
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
        final var instanceManager = instanceManagers.computeIfAbsent(castedMesh, (InternalMesh internalMesh) -> new GL33InstanceManager(this, internalMesh, true));
        return instanceManager.createInstance(position, castedMatrix, staticMatrix, aabb);
    }
    
    @Nullable
    @Override
    public InstanceBatch createInstanceBatch(Mesh mesh) {
        if (!(mesh instanceof InternalMesh castedMesh)) {
            return null;
        }
        return new GL33InstanceBatch(new GL33InstanceManager(this, castedMesh, false));
    }
    
    @Override
    public DynamicMatrix createDynamicMatrix(@Nullable Matrix4fc initialValue, @Nullable DynamicMatrix parentTransform, @Nullable DynamicMatrix.UpdateFunc updateFunc) {
        return dynamicMatrixManager.createMatrix(initialValue, updateFunc, parentTransform);
    }
    
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // this is just holding handles so the light engine does its thing
    private final ReferenceArrayList<GL33LightEngine.ChunkHandle> cullLightChunks = new ReferenceArrayList<>();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // this is just holding handles so the light engine does its thing
    private final ReferenceArrayList<GL33LightEngine.ChunkHandle> instanceLightChunks = new ReferenceArrayList<>();
    
    
    @Override
    public void setCullAABB(@Nullable AABB aabb) {
        if (aabb == null) {
            cullAABB = null;
            return;
        }
        cullAABB = aabb;
        cullLightChunks.clear();
        for (int X = cullAABB.minX(); X < ((cullAABB.maxX() + 16) & 0xFFFFFFF0); X += 16) {
            for (int Y = cullAABB.minY(); Y < ((cullAABB.maxY() + 16) & 0xFFFFFFF0); Y += 16) {
                for (int Z = cullAABB.minZ(); Z < ((cullAABB.maxZ() + 16) & 0xFFFFFFF0); Z += 16) {
                    int chunkX = X >> 4;
                    int chunkY = Y >> 4;
                    int chunkZ = Z >> 4;
                    cullLightChunks.add(GL33LightEngine.getChunk(SectionPos.asLong(chunkX, chunkY, chunkZ)));
                }
            }
        }
    }
    
    public void instanceAABBsDirty() {
        instanceAABBsDirty = true;
    }
    
    private void updateInstanceAABBs() {
        if (!instanceAABBsDirty) {
            return;
        }
        instanceAABBsDirty = false;
        var fullAABB = new AABB();
        for (final var batch : instanceBatches) {
            for (final var instanceRef : batch.instances) {
                final var instance = instanceRef.get();
                if (instance == null || instance.aabb == null) {
                    continue;
                }
                fullAABB = fullAABB.union(instance.aabb);
            }
        }
        
        instanceLightChunks.clear();
        for (int X = fullAABB.minX(); X < ((fullAABB.maxX() + 16) & 0xFFFFFFF0); X += 16) {
            for (int Y = fullAABB.minY(); Y < ((fullAABB.maxY() + 16) & 0xFFFFFFF0); Y += 16) {
                for (int Z = fullAABB.minZ(); Z < ((fullAABB.maxZ() + 16) & 0xFFFFFFF0); Z += 16) {
                    int chunkX = X >> 4;
                    int chunkY = Y >> 4;
                    int chunkZ = Z >> 4;
                    instanceLightChunks.add(GL33LightEngine.getChunk(SectionPos.asLong(chunkX, chunkY, chunkZ)));
                }
            }
        }
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    @Override
    public boolean isEmpty() {
        return instanceManagers.isEmpty() && instanceBatches.isEmpty();
    }
    
    void setVertexCountDirty() {
        vertexCountDirty = true;
    }
    
    void addDrawChunk(GL33DrawChunk chunk) {
        final var components = renderChunkLists.computeIfAbsent(chunk.renderType, e -> new ReferenceArrayList<>());
        chunk.drawIndex = components.size();
        components.add(chunk);
    }
    
    void removeDrawChunk(GL33DrawChunk chunk) {
        if (chunk.drawIndex == -1) {
            return;
        }
        final var components = renderChunkLists.get(chunk.renderType);
        if (components == null) {
            return;
        }
        final var lastChunk = components.pop();
        if (lastChunk == chunk) {
            chunk.drawIndex = -1;
            return;
        }
        lastChunk.drawIndex = chunk.drawIndex;
        chunk.drawIndex = -1;
        components.set(lastChunk.drawIndex, lastChunk);
    }
    
    public int verticesForRenderType(RenderType renderType, boolean shadowsEnabled) {
        if (!enabled) {
            return 0;
        }
        if (culled && !shadowsEnabled) {
            return 0;
        }
        if (isEmpty()) {
            return 0;
        }
        int vertices = verticesPerRenderType.getOrDefault(renderType, -1);
        if (vertices == -1) {
            return 0;
        }
        return vertices;
    }
    
    @Override
    public void updateAndCull(DrawInfo drawInfo) {
        // these updates are global frame specific, so they must be checked and done every frame
        dynamicMatrixManager.updateAll(drawInfo.deltaNano, drawInfo.partialTicks, drawInfo.playerPosition, drawInfo.playerSubBlock);
        dynamicMatrixBuffer.activeBuffer().flush();
        if (!dirtyBatches.isEmpty()) {
            for (int i = 0; i < dirtyBatches.size(); i++) {
                final var batch = dirtyBatches.get(i);
                batch.writeUpdates();
            }
            dirtyBatches.clear();
            instanceDataBuffer.flush();
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
                cullVector.set(((i & 1) == 0 ? cullAABB.maxX() : cullAABB.minX()) - drawInfo.playerPosition.x, ((i & 2) == 0 ? cullAABB.maxY() : cullAABB.minY()) - drawInfo.playerPosition.y, ((i & 4) == 0 ? cullAABB.maxZ() : cullAABB.minZ()) - drawInfo.playerPosition.z, 1);
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
        
        if (vertexCountDirty) {
            vertexCountDirty = false;
            for (final var value : renderChunkLists.entrySet()) {
                int totalVertices = 0;
                for (final var chunk : value.getValue()) {
                    totalVertices += chunk.vertexCount * chunk.manager.instanceCount();
                }
                verticesPerRenderType.put(value.getKey(), totalVertices);
            }
            
            renderChunkLists.keySet().forEach(GL33FeedbackDrawing::addRenderTypeUse);
            usedRenderTypes.forEach(GL33FeedbackDrawing::removeRenderTypeUse);
            usedRenderTypes.clear();
            usedRenderTypes.addAll(renderChunkLists.keySet());
        }
        
        intermediateInstanceDataBuffer.expand(instanceDataBuffer.size());
        glBindTexture(GL_TEXTURE_BUFFER, dynamicMatrixTexture);
        for (final var batch : instanceManagers.values()) {
            glBindBufferRange(GL_TRANSFORM_FEEDBACK_BUFFER, 0, intermediateInstanceDataBuffer.handle(), batch.instanceDataAlloc.offset(), batch.instanceDataAlloc.size());
            glBeginTransformFeedback(GL_POINTS);
            B3DStateHelper.bindVertexArray(batch.matrixUpdateVAO);
            glDrawArrays(GL_POINTS, 0, batch.instanceCount());
            glEndTransformFeedback();
        }
        for (final var batch : instanceBatches) {
            glBindBufferRange(GL_TRANSFORM_FEEDBACK_BUFFER, 0, intermediateInstanceDataBuffer.handle(), batch.instanceDataAlloc.offset(), batch.instanceDataAlloc.size());
            glBeginTransformFeedback(GL_POINTS);
            B3DStateHelper.bindVertexArray(batch.matrixUpdateVAO);
            glDrawArrays(GL_POINTS, 0, batch.instanceCount());
            glEndTransformFeedback();
        }
    }
    
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
        final var chunks = renderChunkLists.get(renderType);
        if (chunks == null) {
            return;
        }
        for (final var chunk : chunks) {
            chunk.draw();
        }
    }
    
    public void rebuildVAOs() {
        for (final var batch : instanceManagers.values()) {
            for (final var chunk : batch.drawChunks) {
                chunk.rebuildVAO(batch.instanceDataAlloc.offset());
            }
        }
        for (final var batch : instanceBatches) {
            for (final var chunk : batch.drawChunks) {
                chunk.rebuildVAO(batch.instanceDataAlloc.offset());
            }
        }
    }
}
