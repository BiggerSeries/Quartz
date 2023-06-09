package net.roguelogix.quartz.internal;

import javax.annotation.Nullable;

@SuppressWarnings("unchecked")
public class MultiBuffer<T extends Buffer> {
    public class Allocation {
        private final T.Allocation[] backingAllocations = new T.Allocation[backingBuffers.length];
        
        public T.Allocation activeAllocation() {
            return backingAllocations[activeFrame];
        }
        
        public T.Allocation allocation(int frame) {
            return backingAllocations[frame % backingAllocations.length];
        }
        
        public void free() {
            MultiBuffer.this.free(this);
        }
        
        public MultiBuffer<T> allocator() {
            return MultiBuffer.this;
        }
    }
    
    private final Buffer[] backingBuffers;
    private int activeFrame;
    
    public MultiBuffer(int framesInFlight, boolean GPUOnly) {
        backingBuffers = new Buffer[framesInFlight];
        for (int i = 0; i < backingBuffers.length; i++) {
            backingBuffers[i] = QuartzCore.INSTANCE.allocBuffer(GPUOnly);
        }
    }
    
    public void setActiveFrame(int activeFrame) {
        this.activeFrame = activeFrame % backingBuffers.length;
    }
    
    public int activeFrame(){
        return activeFrame;
    }
    
    public T activeBuffer() {
        return (T) backingBuffers[activeFrame];
    }
    
    public T buffer(int frame) {
        return (T) backingBuffers[frame % backingBuffers.length];
    }
    
    /**
     * Allocates a block of the buffer of at least the size specified
     * Will not change OpenGL state
     */
    public Allocation alloc(int size) {
        return alloc(size, 1);
    }
    
    /**
     * Allocates a block of the buffer of at least the size specified with specified minimum alignment
     * Will not change OpenGL state
     * Alignment must be a power of 2
     */
    public Allocation alloc(int size, int alignment) {
        final var toRet = new Allocation();
        for (int i = 0; i < backingBuffers.length; i++) {
            toRet.backingAllocations[i] = backingBuffers[i].alloc(size, alignment);
        }
        return toRet;
    }
    
    /**
     * Resizes an allocation to a new size
     * May change OpenGL state, potentially writes to GL_ARRAY_BUFFER
     * May return a different allocation object than what was passed in
     * NOTE: may have different alignment than current allocation
     * If allocation is null, creates a new allocation
     */
    public Allocation realloc(@Nullable Allocation allocation, int newSize, boolean copyData) {
        return realloc(allocation, newSize, 1, copyData);
    }
    
    /**
     * Resizes an allocation to a new size with the specified alignment
     * May change OpenGL state, potentially writes to GL_ARRAY_BUFFER
     * May return a different allocation object than what was passed in
     * Alignment may differ from the current allocation alignment
     * If allocation is null, creates a new allocation
     */
    public Allocation realloc(@Nullable Allocation allocation, int newSize, int alignment, boolean copyData) {
        if (allocation == null) {
            return alloc(newSize, alignment);
        }
        for (int i = 0; i < backingBuffers.length; i++) {
            allocation.backingAllocations[i] = backingBuffers[i].realloc(allocation.backingAllocations[i], newSize, alignment, copyData);
        }
        return allocation;
    }
    
    public void free(Allocation allocation) {
        for (int i = 0; i < allocation.backingAllocations.length; i++) {
            allocation.backingAllocations[i].free();
        }
    }
    
    public void dirtyAll() {
        for (int i = 0; i < backingBuffers.length; i++) {
            backingBuffers[i].dirtyAll();
        }
    }
}
