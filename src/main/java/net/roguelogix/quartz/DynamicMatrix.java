package net.roguelogix.quartz;

import net.roguelogix.phosphophyllite.repack.org.joml.Matrix4f;
import net.roguelogix.phosphophyllite.repack.org.joml.Matrix4fc;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector3fc;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.NonnullDefault;

import javax.annotation.Nullable;

@NonnullDefault
public interface DynamicMatrix {
    void write(Matrix4fc matrixData);
    
    void delete();
    
    interface UpdateFunc {
        void accept(Matrix4f matrix, long nanoSinceLastFrame, float partialTicks, Vector3ic playerBlock, Vector3fc playerPartialBlock);
    }
    
    interface Manager {
        default DynamicMatrix createMatrix(UpdateFunc updateFunc) {
            return createMatrix(updateFunc, null);
        }
        
        DynamicMatrix createMatrix(@Nullable UpdateFunc updateFunc, @Nullable DynamicMatrix parent);
        
        boolean owns(@Nullable DynamicMatrix dynamicMatrix);
    }
}
