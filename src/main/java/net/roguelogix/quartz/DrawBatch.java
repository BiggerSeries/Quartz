package net.roguelogix.quartz;

import net.roguelogix.phosphophyllite.repack.org.joml.AABBi;
import net.roguelogix.phosphophyllite.repack.org.joml.Matrix4fc;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

import javax.annotation.Nullable;

@NonnullDefault
public interface DrawBatch {
    
    interface Instance {
        
        void updatePosition(Vector3ic position);
        
        void updateDynamicMatrix(@Nullable DynamicMatrix newDynamicMatrix);
        
        void updateStaticMatrix(@Nullable Matrix4fc newStaticMatrix);
        
        void updateAABB(@Nullable AABBi aabb);
        
        void delete();
    }
    
    /**
     * DynamicMatrix and DynamicLight must be instances created by this draw batch
     */
    @Nullable
    Instance createInstance(Vector3ic position, Mesh mesh, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABBi aabb);
    
    interface InstanceBatch {
        void updateMesh(Mesh mesh);
        
        @Nullable
        Instance createInstance(Vector3ic position, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABBi aabb);
    }
    
    @Nullable
    InstanceBatch createInstanceBatch(Mesh mesh);
    
    DynamicMatrix createDynamicMatrix(@Nullable Matrix4fc initialValue, @Nullable DynamicMatrix parentTransform, @Nullable DynamicMatrix.UpdateFunc updateFunc);
    
    default DynamicMatrix createDynamicMatrix(@Nullable Matrix4fc initialValue, @Nullable DynamicMatrix.UpdateFunc updateFunc) {
        return createDynamicMatrix(initialValue, null, updateFunc);
    }
    
    default DynamicMatrix createDynamicMatrix(@Nullable DynamicMatrix.UpdateFunc updateFunc) {
        return createDynamicMatrix(null, updateFunc);
    }
    
    void setCullAABB(@Nullable AABBi aabb);
    
    void setEnabled(boolean enabled);
    
    boolean isEmpty();
}
