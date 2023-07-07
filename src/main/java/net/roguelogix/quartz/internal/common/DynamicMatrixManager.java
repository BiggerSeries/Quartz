package net.roguelogix.quartz.internal.common;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.roguelogix.phosphophyllite.Phosphophyllite;
import net.roguelogix.phosphophyllite.threading.Queues;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3i;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DynamicMatrix;
import net.roguelogix.quartz.internal.Buffer;
import net.roguelogix.quartz.internal.MagicNumbers;
import net.roguelogix.quartz.internal.MultiBuffer;
import net.roguelogix.quartz.internal.QuartzCore;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

@NonnullDefault
public class DynamicMatrixManager implements DynamicMatrix.Manager {
    
    public static class Matrix implements DynamicMatrix {
        private final MultiBuffer<?>.Allocation allocation;
        private final Matrix4f localTransformMatrix = new Matrix4f();
        private final Matrix4f transformMatrix = new Matrix4f();
        private final Matrix4f normalMatrix = new Matrix4f();
        private final ObjectArrayList<WeakReference<Matrix>> childMatrices = new ObjectArrayList<>();
        @Nullable
        private final DynamicMatrix.UpdateFunc updateFunc;
        
        private boolean deleted = false;
        
        public Matrix(@Nullable Matrix4fc initialValue, MultiBuffer<?>.Allocation allocation, @Nullable UpdateFunc updateFunc, ObjectArrayList<WeakReference<Matrix>> matrixList) {
            if (initialValue != null) {
                this.localTransformMatrix.set(initialValue);
            }
            this.allocation = allocation;
            this.updateFunc = updateFunc;
            final var ref = new WeakReference<>(this);
            matrixList.add(ref);
            QuartzCore.mainThreadClean(this, () -> {
                matrixList.remove(ref);
                allocation.free();
            });
        }
        
        public void update(long nanos, float partialTicks, Vector3i playerBlock, Vector3f playerPartialBlock, Matrix4fc parentTransform) {
            if (deleted) {
                return;
            }
            if (updateFunc != null) {
                updateFunc.accept(localTransformMatrix, nanos, partialTicks, playerBlock, playerPartialBlock);
            }
            if (!transformMatrix.equals(localTransformMatrix)) {
                transformMatrix.set(localTransformMatrix);
                if ((parentTransform.properties() & Matrix4fc.PROPERTY_IDENTITY) == 0) {
                    transformMatrix.mulLocal(parentTransform);
                }
                transformMatrix.normal(normalMatrix);
            }
            final var buffer = allocation.activeAllocation().address();
            buffer.putMatrix4fIdx(0, transformMatrix);
            buffer.putMatrix4fIdx(1, normalMatrix);
            for (int i = 0; i < childMatrices.size(); i++) {
                var mat = childMatrices.get(i).get();
                if (mat == null || mat.deleted) {
                    var removed = childMatrices.pop();
                    if (i != childMatrices.size()) {
                        // removed non-end elements
                        childMatrices.set(i, removed);
                    }
                    i--;
                    continue;
                }
                mat.update(nanos, partialTicks, playerBlock, playerPartialBlock, transformMatrix);
            }
        }
        
        public int id(int frame) {
            return allocation.allocation(frame).offset() / MagicNumbers.MATRIX_4F_BYTE_SIZE_2;
        }
        
        @Override
        public void delete() {
            for (final var value : childMatrices) {
                var child = value.get();
                if (child == null) {
                    continue;
                }
                if (child.deleted) {
                    continue;
                }
                throw new IllegalStateException("Cannot delete a dynamic matrix with live children");
            }
            deleted = true;
        }
    }
    
    private final MultiBuffer<?> buffer;
    private final ObjectArrayList<WeakReference<Matrix>> rootMatrices = new ObjectArrayList<>();
    
    public DynamicMatrixManager(MultiBuffer<?> buffer) {
        this.buffer = buffer;
    }
    
    @Override
    public DynamicMatrix createMatrix(@Nullable Matrix4fc initialValue, @Nullable DynamicMatrix.UpdateFunc updateFunc, @Nullable DynamicMatrix parent) {
        Matrix parentMatrix = null;
        if (parent != null) {
            if (parent instanceof Matrix parentMat && owns(parentMat)) {
                parentMatrix = parentMat;
            } else {
                throw new IllegalArgumentException("Parent matrix must be from the same manager");
            }
        }
        final var list = parentMatrix == null ? rootMatrices : ((Matrix) parent).childMatrices;
        return new Matrix(initialValue, buffer.alloc(MagicNumbers.MATRIX_4F_BYTE_SIZE_2, MagicNumbers.MATRIX_4F_BYTE_SIZE_2), updateFunc, list);
    }
    
    @Override
    public boolean owns(@Nullable DynamicMatrix dynamicMatrix) {
        if (dynamicMatrix instanceof Matrix mat) {
            return mat.allocation.allocator() == buffer;
        }
        return false;
    }
    
    boolean updateRunning;
    long nanos;
    float partialTicks;
    Vector3i playerBlock;
    Vector3f playerPartialBlock;
    
    private final Runnable updateRunner = () -> {
        updateAll(nanos, partialTicks, playerBlock, playerPartialBlock);
        updateRunning = false;
    };
    
    public void updateAll(long nanos, float partialTicks, Vector3i playerBlock, Vector3f playerPartialBlock) {
        for (int i = 0; i < rootMatrices.size(); i++) {
            var mat = rootMatrices.get(i).get();
            if (mat != null) {
                mat.update(nanos, partialTicks, playerBlock, playerPartialBlock, MagicNumbers.IDENTITY_MATRIX);
            }
        }
        buffer.dirtyAll();
    }
    
    public void enqueueUpdate(long nanos, float partialTicks, Vector3i playerBlock, Vector3f playerPartialBlock) {
        while (updateRunning) {
            Thread.onSpinWait();
        }
        this.nanos = nanos;
        this.partialTicks = partialTicks;
        this.playerBlock = playerBlock;
        this.playerPartialBlock = playerPartialBlock;
        updateRunning = true;
//        updateRunner.run();
        Queues.offThread.enqueueUntracked(updateRunner);
    }
    
    public void waitUpdate() {
        while (updateRunning) {
            Thread.onSpinWait();
        }
    }
    
}
