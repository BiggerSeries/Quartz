package net.roguelogix.quartz.internal.gl46.batching;

import net.roguelogix.quartz.AABB;
import org.joml.Matrix4fc;
import org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.DynamicMatrix;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.internal.QuartzCore;

import javax.annotation.Nullable;

@NonnullDefault
public class GL46InstanceBatch implements DrawBatch.InstanceBatch {
    
    private final GL46InstanceManager manager;
    
    GL46InstanceBatch(GL46InstanceManager manager){
        QuartzCore.mainThreadClean(this, manager::delete);
        this.manager = manager;
    }
    
    @Override
    public void updateMesh(Mesh mesh) {
        manager.updateMesh(mesh);
    }
    
    @Nullable
    @Override
    public DrawBatch.Instance createInstance(Vector3ic position, @Nullable DynamicMatrix dynamicMatrix, @Nullable Matrix4fc staticMatrix, @Nullable AABB aabb) {
        return manager.createInstance(position, dynamicMatrix, staticMatrix, aabb);
    }
}
