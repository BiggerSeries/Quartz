package net.roguelogix.quartz;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

import javax.annotation.Nullable;

@NonnullDefault
public interface DynamicMatrix {
    
    void delete();
    
    interface UpdateFunc {
        void accept(Matrix4f matrix, long nanoSinceLastFrame, float partialTicks, Vector3ic playerBlock, Vector3fc playerPartialBlock);
    }
    
    interface Manager {
        default DynamicMatrix createMatrix(UpdateFunc updateFunc) {
            return createMatrix(null, updateFunc, null);
        }
        default DynamicMatrix createMatrix(Matrix4fc initialValue, UpdateFunc updateFunc) {
            return createMatrix(initialValue, updateFunc, null);
        }
        
        DynamicMatrix createMatrix(@Nullable Matrix4fc initialValue, @Nullable UpdateFunc updateFunc, @Nullable DynamicMatrix parent);
        
        boolean owns(@Nullable DynamicMatrix dynamicMatrix);
    }
}
