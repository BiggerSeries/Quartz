package net.roguelogix.quartz;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

import javax.annotation.Nullable;

@NonnullDefault
public interface DynamicMatrix {
    void write(Matrix4fc matrixData);
    
    interface UpdateFunc {
        void accept(DynamicMatrix matrix, long nanoSinceLastFrame, float partialTicks, Vector3ic playerBlock, Vector3fc playerPartialBlock);
    }
    
    interface Manager {
        default DynamicMatrix createMatrix(UpdateFunc updateFunc) {
            return createMatrix(updateFunc, null);
        }
        
        DynamicMatrix createMatrix(@Nullable UpdateFunc updateFunc, @Nullable DynamicMatrix parent);
        
        boolean owns(@Nullable DynamicMatrix dynamicMatrix);
    }
}
