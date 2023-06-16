package net.roguelogix.quartz.internal.gl46.batching;

import net.roguelogix.quartz.AABB;
import org.joml.*;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.DynamicMatrix;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.DynamicMatrixManager;
import net.roguelogix.quartz.internal.gl46.GL46Core;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Objects;

import static net.roguelogix.quartz.internal.gl46.GL46Statics.*;
import static org.lwjgl.opengl.GL11C.glFinish;

@Nullable
public class GL46Instance implements DrawBatch.Instance {
    
    static class Location {
        int location;
        
        private Location(int location) {
            this.location = location;
        }
    }
    
    final Location location;
    public final WeakReference<GL46Instance> selfWeakRef = new WeakReference<>(this);
    
    private final GL46InstanceManager manager;
    
    private final Vector3i position = new Vector3i();
    private DynamicMatrixManager.Matrix dynamicMatrix;
    private final Matrix4f staticMatrix = new Matrix4f();
    private final Matrix4f normalMatrix = new Matrix4f();
    
    @Nullable
    AABB aabb;
    
    private int dirtyForFrames;
    
    GL46Instance(GL46InstanceManager manager, int initialLocation) {
        var location = new Location(initialLocation);
        QuartzCore.mainThreadClean(this, () -> manager.removeInstance(location));
        this.location = location;
        this.manager = manager;
    }
    
    void setDirty() {
        if (location.location == -1) {
            throw new IllegalStateException("Attempt to dirty deleted instance");
        }
        if(dirtyForFrames == FRAMES_IN_FLIGHT){
            // already dirtied this frame
            return;
        }
        manager.dirtyInstances.add(selfWeakRef);
        manager.setDirty();
        dirtyForFrames = FRAMES_IN_FLIGHT;
    }
    
    @Override
    public void updatePosition(Vector3ic position) {
        if (this.position.equals(position)) {
            return;
        }
        this.position.set(position);
        setDirty();
    }
    
    @Override
    public void updateDynamicMatrix(@Nullable DynamicMatrix newDynamicMatrix) {
        if (newDynamicMatrix == null) {
            newDynamicMatrix = manager.drawBatch.IDENTITY_DYNAMIC_MATRIX;
        }
        if (Objects.equals(this.dynamicMatrix, newDynamicMatrix)) {
            return;
        }
        if (newDynamicMatrix instanceof DynamicMatrixManager.Matrix dynamicMatrix && manager.drawBatch.dynamicMatrixManager.owns(dynamicMatrix)) {
            this.dynamicMatrix = dynamicMatrix;
            setDirty();
        }
    }
    
    @Override
    public void updateStaticMatrix(@Nullable Matrix4fc newStaticMatrix) {
        if (Objects.equals(staticMatrix, newStaticMatrix)) {
            return;
        }
        if (newStaticMatrix == null) {
            staticMatrix.identity();
            return;
        }
        staticMatrix.set(newStaticMatrix);
        staticMatrix.normal(normalMatrix);
        setDirty();
    }
    
    @Override
    public void updateAABB(@Nullable AABB aabb) {
        if (this.aabb == null && aabb == null) {
            return;
        }
        if (this.aabb == null) {
            this.aabb = aabb;
            manager.drawBatch.instanceAABBsDirty();
        }
        if (this.aabb.minX() >> 4 != aabb.minX() >> 4
                || this.aabb.minY() >> 4 != aabb.minY() >> 4
                || this.aabb.minZ() >> 4 != aabb.minZ() >> 4
                || this.aabb.maxX() >> 4 != aabb.maxX() >> 4
                || this.aabb.maxY() >> 4 != aabb.maxY() >> 4
                || this.aabb.maxZ() >> 4 != aabb.maxZ() >> 4
        ) {
            // the light chunks this is inside have changed, mark for update
            manager.drawBatch.instanceAABBsDirty();
        }
        this.aabb = aabb;
    }
    
    @Override
    public void delete() {
        manager.removeInstance(location);
    }
    
    boolean write() {
        if (!dirty()) {
            return false;
        }
        final var instanceGPUMemory = manager.instanceDataAlloc.activeAllocation().address().slice((long) location.location * INSTANCE_DATA_BYTE_SIZE, INSTANCE_DATA_BYTE_SIZE);
        
        // TODO: dont need to write the entire instance data if only one things changed
        instanceGPUMemory.putMatrix4f(STATIC_MATRIX_OFFSET, staticMatrix);
        instanceGPUMemory.putMatrix3x4f(STATIC_NORMAL_MATRIX_OFFSET, normalMatrix);
        // these "overlap" because the normal matrix is only a mat3, so the last column is unused, and thus i can fit this info into it
        instanceGPUMemory.putVector3i(WORLD_POSITION_OFFSET, position);
        instanceGPUMemory.putInt(DYNAMIC_MATRIX_ID_OFFSET, dynamicMatrix.id(GL46Core.INSTANCE.frameInFlight()));
        
        dirtyForFrames--;
        return dirty();
    }
    
    boolean dirty() {
        return dirtyForFrames > 0;
    }
    
    void printAllInstanceData() {
        glFinish();
        for (int i = 0; i < FRAMES_IN_FLIGHT + 1; i++) {
            final var instanceGPUMemory = manager.instanceDataAlloc.allocation(i).address().slice((long) location.location * INSTANCE_DATA_BYTE_SIZE, INSTANCE_DATA_BYTE_SIZE);
            final var mat = new Matrix4f();
            instanceGPUMemory.getMatrix4f(STATIC_MATRIX_OFFSET, mat);
            System.out.println(mat);
            instanceGPUMemory.getMatrix3x4f(STATIC_NORMAL_MATRIX_OFFSET, mat);
            System.out.println(mat);
            // these "overlap" because the normal matrix is only a mat3, so the last column is unused, and thus i can fit this info into it
            final var vec = new Vector3i();
            instanceGPUMemory.getVector3i(WORLD_POSITION_OFFSET, vec);
            System.out.println(vec);
            System.out.println(instanceGPUMemory.getInt(DYNAMIC_MATRIX_ID_OFFSET));
        }
        System.out.println();
    }
}
