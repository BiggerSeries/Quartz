package net.roguelogix.quartz;

import org.joml.Matrix4fc;
import org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

import javax.annotation.Nullable;

@NonnullDefault
public interface DrawBatch {
    
    interface Instance {
        void updateDynamicMatrix(@Nullable DynamicMatrix newDynamicMatrix);
        
        void updateStaticMatrix(@Nullable Matrix4fc newStaticMatrix);
        
        void updateDynamicLight(@Nullable DynamicLight newDynamicLight);
        
        void delete();
    }
    
    /**
     * DynamicMatrix and DynamicLight must be instances created by this draw batch
     */
    @Nullable
    Instance createInstance(Vector3ic position, Mesh mesh, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable DynamicLight light, @Nullable DynamicLight.Type lightType);
    
    interface InstanceBatch {
        void updateMesh(Mesh mesh);
        
        @Nullable
        Instance createInstance(Vector3ic position, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable DynamicLight light, @Nullable DynamicLight.Type lightType);
    }
    
    @Nullable
    InstanceBatch createInstanceBatch(Mesh mesh);
    
    DynamicMatrix createDynamicMatrix(@Nullable DynamicMatrix parentTransform, @Nullable DynamicMatrix.UpdateFunc updateFunc);
    
    default DynamicMatrix createDynamicMatrix(@Nullable DynamicMatrix.UpdateFunc updateFunc) {
        return createDynamicMatrix(null, updateFunc);
    }
    
    DynamicLight createLight(Vector3ic lightPosition, DynamicLight.Type lightType);
    
    void setCullAABB(AABB aabb);
    
    void setEnabled(boolean enabled);
    
    boolean isEmpty();
}
