package net.roguelogix.quartz.internal.gl33.batching;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.renderer.RenderType;
import net.roguelogix.phosphophyllite.util.FastArraySet;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.AABB;
import net.roguelogix.quartz.DynamicMatrix;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.internal.Buffer;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.B3DStateHelper;
import net.roguelogix.quartz.internal.common.DynamicMatrixManager;
import net.roguelogix.quartz.internal.common.InternalMesh;
import net.roguelogix.quartz.internal.gl33.GL33Buffer;
import net.roguelogix.quartz.internal.gl33.GL33Statics;
import net.roguelogix.quartz.internal.gl46.GL46Statics;
import org.joml.Matrix4fc;
import org.joml.Vector3ic;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import static net.roguelogix.quartz.internal.MagicNumbers.IDENTITY_MATRIX;

import static org.lwjgl.opengl.GL33C.*;

@NonnullDefault
public class GL33InstanceManager {
    final GL33DrawBatch drawBatch;
    private final boolean autoDelete;
    
    
    private int instancesAllocated = 8;
    GL33Buffer.Allocation instanceDataAlloc;
    
    private InternalMesh staticMesh;
    private InternalMesh.Manager.TrackedMesh trackedMesh;
    private final Consumer<InternalMesh.Manager.TrackedMesh> meshBuildCallback;
    
    final ReferenceArrayList<GL33DrawChunk> drawChunks = new ReferenceArrayList<>();
    
    final ReferenceArrayList<WeakReference<GL33Instance>> instances = new ReferenceArrayList<>();
    final FastArraySet<WeakReference<GL33Instance>> dirtyInstances = new FastArraySet<>();
    
    int matrixUpdateVAO;
    private final Buffer.CallbackHandle matrixVAOUpdateHandle;
    
    public GL33InstanceManager(GL33DrawBatch drawBatch, InternalMesh mesh, boolean autoDelete) {
        this.drawBatch = drawBatch;
        this.autoDelete = autoDelete;
        
        final var ref = new WeakReference<>(this);
        meshBuildCallback = ignored -> {
            final var manager = ref.get();
            if (manager != null) {
                manager.onRebuild();
            }
        };
        
        instanceDataAlloc = drawBatch.instanceDataBuffer.alloc(instancesAllocated * GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.INSTANCE_DATA_BYTE_SIZE);
        matrixVAOUpdateHandle = instanceDataAlloc.addReallocCallback(this::rebuildVAOs);
        this.rebuildVAOs(instanceDataAlloc);
        updateMesh(mesh);
    }
    
    void delete() {
        while (!instances.isEmpty()) {
            final var lastInstanceRef = instances.peek(0);
            final var lastInstance = lastInstanceRef.get();
            if (lastInstance == null) {
                instances.pop();
                continue;
            }
            removeInstance(lastInstance.location);
        }
        
        drawChunks.forEach(drawBatch::removeDrawChunk);
        
        drawChunks.clear();
        drawBatch.instanceManagers.remove(staticMesh, this);
        drawBatch.instanceBatches.remove(this);
        trackedMesh.removeBuildCallback(meshBuildCallback);
        instanceDataAlloc.free();
        
        glDeleteVertexArrays(matrixUpdateVAO);
        matrixVAOUpdateHandle.delete();
    }
    
    void rebuildVAOs(Buffer.Allocation newBuffer) {
        glDeleteVertexArrays(matrixUpdateVAO);
        matrixUpdateVAO = glGenVertexArrays();
        
        B3DStateHelper.bindVertexArray(matrixUpdateVAO);
        B3DStateHelper.bindArrayBuffer(newBuffer.allocator().as(GL33Buffer.class).handle());
        for (int i = 0; i < 9; i++) {
            glEnableVertexAttribArray(i);
        }
        
        final var instanceDataOffset = newBuffer.offset();
        
        glVertexAttribPointer(0, 4, GL_FLOAT, false, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.STATIC_MATRIX_OFFSET + instanceDataOffset);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.STATIC_MATRIX_OFFSET + 16 + instanceDataOffset);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.STATIC_MATRIX_OFFSET + 32 + instanceDataOffset);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.STATIC_MATRIX_OFFSET + 48 + instanceDataOffset);
        
        glVertexAttribPointer(4, 4, GL_FLOAT, false, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.STATIC_NORMAL_MATRIX_OFFSET + instanceDataOffset);
        glVertexAttribPointer(5, 4, GL_FLOAT, false, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.STATIC_NORMAL_MATRIX_OFFSET + 16 + instanceDataOffset);
        glVertexAttribPointer(6, 4, GL_FLOAT, false, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.STATIC_NORMAL_MATRIX_OFFSET + 32 + instanceDataOffset);
        
        glVertexAttribIPointer(7, 3, GL_INT, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.WORLD_POSITION_OFFSET + instanceDataOffset);
        glVertexAttribIPointer(8, 1, GL_INT, GL33Statics.INSTANCE_DATA_BYTE_SIZE, GL33Statics.DYNAMIC_MATRIX_ID_OFFSET + instanceDataOffset);
        
        for (final var chunk : drawChunks) {
            chunk.rebuildVAO(newBuffer.offset());
        }
    }
    
    void setDirty() {
        drawBatch.dirtyBatches.add(this);
    }
    
    void updateMesh(Mesh quartzMesh) {
        if (!(quartzMesh instanceof InternalMesh mesh)) {
            return;
        }
        if (trackedMesh != null) {
            trackedMesh.removeBuildCallback(meshBuildCallback);
        }
        
        staticMesh = mesh;
        trackedMesh = QuartzCore.INSTANCE.meshManager.getMeshInfo(mesh);
        if (trackedMesh == null) {
            throw new IllegalArgumentException("Unable to find mesh in mesh registry");
        }
        
        onRebuild();
        trackedMesh.addBuildCallback(meshBuildCallback);
    }
    
    private void onRebuild() {
        drawChunks.forEach(drawBatch::removeDrawChunk);
        drawChunks.clear();
        for (RenderType renderType : trackedMesh.usedRenderTypes()) {
            var component = trackedMesh.renderTypeComponent(renderType);
            if (component == null) {
                continue;
            }
            final var newChunk = new GL33DrawChunk(this, renderType, component);
            drawChunks.add(newChunk);
            drawBatch.addDrawChunk(newChunk);
        }
        setDirty();
        drawBatch.setVertexCountDirty();
    }
    
    @Nullable
    GL33Instance createInstance(Vector3ic position, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABB aabb) {
        if (dynamicMatrix == null) {
            dynamicMatrix = drawBatch.IDENTITY_DYNAMIC_MATRIX;
        }
        if (!(dynamicMatrix instanceof DynamicMatrixManager.Matrix castedMatrix) || !drawBatch.dynamicMatrixManager.owns(dynamicMatrix)) {
            return null;
        }
        if (staticMatrix == null) {
            staticMatrix = IDENTITY_MATRIX;
        }
        return createInstance(position, castedMatrix, staticMatrix, aabb);
    }
    
    GL33Instance createInstance(Vector3ic position, @Nullable DynamicMatrixManager.Matrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABB aabb) {
        final var instance = new GL33Instance(this, instances.size());
        instance.updatePosition(position);
        instance.updateDynamicMatrix(dynamicMatrix);
        instance.updateStaticMatrix(staticMatrix);
        instance.updateAABB(aabb);
        instances.add(instance.selfWeakRef);
        setDirty();
        drawBatch.setVertexCountDirty();
        return instance;
    }
    
    public void removeInstance(GL33Instance.Location location) {
        if (location.location == -1) {
            return;
        }
        if (instances.isEmpty()) {
            return;
        }
        final var lastInstanceRef = instances.pop();
        setDirty();
        drawBatch.setVertexCountDirty();
        if (instances.size() == location.location) {
            // removed last instance, nothing to do
            location.location = -1;
            dirtyInstances.remove(lastInstanceRef);
            if (instances.isEmpty() && autoDelete) {
                delete();
            }
            return;
        }
        final var lastInstance = lastInstanceRef.get();
        if (lastInstance == null) {
            // about to be removed
            location.location = -1;
            dirtyInstances.remove(lastInstanceRef);
            return;
        }
        // move to opened slot
        lastInstance.location.location = location.location;
        final var removed = instances.set(location.location, lastInstanceRef);
        location.location = -1;
        dirtyInstances.remove(removed);
        lastInstance.setDirty();
    }
    
    
    public int instanceCount() {
        return instances.size();
    }
    
    public void writeUpdates() {
        if (dirtyInstances.isEmpty()) {
            return;
        }
        if (instanceCount() > instancesAllocated || (instanceCount() > 16 && instanceCount() <= (instancesAllocated / 4))) {
            int newAllocCount = instancesAllocated;
            do {
                newAllocCount = instanceCount() > newAllocCount ? newAllocCount * 2 : newAllocCount / 2;
            } while (instanceCount() > newAllocCount || (instanceCount() > 16 && instanceCount() <= (newAllocCount / 4)));
            instanceDataAlloc = drawBatch.instanceDataBuffer.realloc(instanceDataAlloc, newAllocCount * GL46Statics.INSTANCE_DATA_BYTE_SIZE, GL46Statics.INSTANCE_DATA_BYTE_SIZE, false);
            instancesAllocated = newAllocCount;
            for (final var instanceRef : instances) {
                final var instance = instanceRef.get();
                if (instance == null) {
                    continue;
                }
                instance.setDirty();
            }
        }
        for (int i = 0; i < dirtyInstances.size(); i++) {
            final var dirtyInstanceRef = dirtyInstances.get(i);
            final var instance = dirtyInstanceRef.get();
            if (instance == null) {
                continue;
            }
            instance.write();
        }
        dirtyInstances.clear();
    }
}
