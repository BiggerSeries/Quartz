package net.roguelogix.quartz.internal.gl46.batching;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.renderer.RenderType;
import net.roguelogix.quartz.AABB;
import org.joml.Matrix4fc;
import org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.FastArraySet;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DynamicMatrix;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.internal.MultiBuffer;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.DynamicMatrixManager;
import net.roguelogix.quartz.internal.common.InternalMesh;
import net.roguelogix.quartz.internal.gl46.GL46Buffer;
import net.roguelogix.quartz.internal.gl46.GL46Statics;

import javax.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import static net.roguelogix.quartz.internal.MagicNumbers.IDENTITY_MATRIX;

@NonnullDefault
public class Gl46InstanceManager {
    
    final GL46DrawBatch drawBatch;
    private final boolean autoDelete;
    
    private int instancesAllocated = 8;
    MultiBuffer<GL46Buffer>.Allocation instanceDataAlloc;
    
    private InternalMesh staticMesh;
    private InternalMesh.Manager.TrackedMesh trackedMesh;
    private final Consumer<InternalMesh.Manager.TrackedMesh> meshBuildCallback;
    
    final ReferenceArrayList<GL46DrawChunk> drawChunks = new ReferenceArrayList<>();
    
    final ReferenceArrayList<WeakReference<GL46Instance>> instances = new ReferenceArrayList<>();
    final FastArraySet<WeakReference<GL46Instance>> dirtyInstances = new FastArraySet<>();
    
    public Gl46InstanceManager(GL46DrawBatch drawBatch, InternalMesh mesh, boolean autoDelete) {
        this.drawBatch = drawBatch;
        this.autoDelete = autoDelete;
        drawBatch.instanceBatches.add(this);
        
        final var ref = new WeakReference<>(this);
        meshBuildCallback = ignored -> {
            final var manager = ref.get();
            if (manager != null) {
                manager.onRebuild();
            }
        };
        
        instanceDataAlloc = drawBatch.instanceDataBuffer.alloc(instancesAllocated * GL46Statics.INSTANCE_DATA_BYTE_SIZE, GL46Statics.INSTANCE_DATA_BYTE_SIZE);
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
        setDirty();
        trackedMesh.removeBuildCallback(meshBuildCallback);
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
            final var newChunk = new GL46DrawChunk(this, renderType, component);
            drawChunks.add(newChunk);
            drawBatch.addDrawChunk(newChunk);
        }
        setDirty();
    }
    
    @Nullable
    GL46Instance createInstance(Vector3ic position, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABB aabb) {
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
    
    GL46Instance createInstance(Vector3ic position, @Nullable DynamicMatrixManager.Matrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABB aabb) {
        final var instance = new GL46Instance(this, instances.size());
        instance.updatePosition(position);
        instance.updateDynamicMatrix(dynamicMatrix);
        instance.updateStaticMatrix(staticMatrix);
        instance.updateAABB(aabb);
        instances.add(instance.selfWeakRef);
        setDirty();
        return instance;
    }
    
    void removeInstance(GL46Instance.Location location) {
        if (location.location == -1) {
            return;
        }
        if (instances.isEmpty()) {
            return;
        }
        final var lastInstanceRef = instances.pop();
        setDirty();
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
    
    void setDirty() {
        drawBatch.dirtyBatches.add(this);
        drawBatch.setIndirectInfoDirty();
    }
    
    public int instanceCount() {
        return instances.size();
    }
    
    public int baseInstance(int frame) {
        return instanceDataAlloc.allocation(frame).offset() / GL46Statics.INSTANCE_DATA_BYTE_SIZE;
    }
    
    public boolean writeUpdates() {
        if (dirtyInstances.isEmpty()) {
            return false;
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
            if (instance != null && instance.write()) {
                continue;
            }
            // null or no longer dirty
            dirtyInstances.remove(dirtyInstanceRef);
            if (dirtyInstances.isEmpty()) {
                break;
            }
            i--;
        }
        return !dirtyInstances.isEmpty();
    }
}
