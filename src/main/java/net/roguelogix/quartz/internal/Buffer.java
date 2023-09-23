package net.roguelogix.quartz.internal;

import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.internal.util.PointerWrapper;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Generic GPU side buffer
 * use GL or VK specific implementations for more details
 * GL implementation is NOT thread safe, and may alter GL state
 * VK is unimplemented
 */
@NonnullDefault
public interface Buffer {
    
    class Options {
        private Options() {
        }
        
        public static int GPU_ONLY = 1;
        
        public static boolean isGPUOnly(int options) {
            return (options & GPU_ONLY) != 0;
        }
        
        public static int CPU_MEMORY = 2;
        
        public static boolean isCPUMemory(int options) {
            return (options & CPU_MEMORY) != 0;
        }
    }
    
    @FunctionalInterface
    interface CallbackHandle {
        void delete();
    }
    
    interface Allocation {
        
        /**
         * @return memory address for this allocation
         * @apiNote buffer reflects CPU side of allocation, may be a coherent mapped buffer
         * reading is not allowed
         */
        PointerWrapper address();
        
        /**
         * @return offset (in bytes) into OpenGL buffer
         */
        int offset();
        
        /**
         * @return allocation size (in bytes)
         */
        int size();
        
        /**
         * marks entire allocation as dirty
         */
        default void dirty() {
            dirtyRange(0, size());
        }
        
        /**
         * marks range in allocation as dirty
         * writes are not visible until API specific flush is called
         */
        void dirtyRange(int offset, int size);
        
        /**
         * @return allocator used to allocate this allocation
         */
        Buffer allocator();
        
        /**
         * Copies buffer data internally
         * CPU side only, marks range dirty for next flush
         */
        void copy(int srcOffset, int dstOffset, int size);
        
        /**
         * Called when this allocation is reallocated, allocation fed to consumer is the new allocation
         * Callback is fed this allocation immediately at add when added
         * <p>
         * WARNING: these callbacks must only weakly refer to this allocation object, else you will cause a memory leak
         *
         * @param consumer: callback
         */
        CallbackHandle addReallocCallback(Consumer<Allocation> consumer);
        
        default void free() {
            allocator().free(this);
        }
    }
    
    void delete();
    
    /**
     * Size of the entire buffer, allocations are not considered
     *
     * @return size of OpenGL buffer
     */
    int size();
    
    /**
     * Allocates a block of the buffer of at least the size specified
     * Will not change OpenGL state
     */
    default Allocation alloc(int size) {
        return alloc(size, 1);
    }
    
    /**
     * Allocates a block of the buffer of at least the size specified with specified minimum alignment
     * Will not change OpenGL state
     * Alignment must be a power of 2
     */
    Allocation alloc(int size, int alignment);
    
    /**
     * Resizes an allocation to a new size
     * May change OpenGL state, potentially writes to GL_ARRAY_BUFFER
     * May return a different allocation object than what was passed in
     * NOTE: may have different alignment than current allocation
     * If allocation is null, creates a new allocation
     */
    default Allocation realloc(@Nullable Allocation allocation, int newSize, boolean copyData) {
        return realloc(allocation, newSize, 1, copyData);
    }
    
    /**
     * Resizes an allocation to a new size with the specified alignment
     * May change OpenGL state, potentially writes to GL_ARRAY_BUFFER
     * May return a different allocation object than what was passed in
     * Alignment may differ from the current allocation alignment
     * If allocation is null, creates a new allocation
     */
    Allocation realloc(@Nullable Allocation allocation, int newSize, int alignment, boolean copyData);
    
    void free(Allocation allocation);
    
    void dirtyAll();
    
    /**
     * Called when the CPU side buffer is changed
     * <p>
     * WARNING: these callbacks must only weakly refer to this buffer object, else you will cause a memory leak
     *
     * @param consumer: callback
     */
    CallbackHandle addReallocCallback(boolean callImmediately, Consumer<Buffer> consumer);
    
    default <T extends Buffer> T as(Class<T> ignored) {
        //noinspection unchecked
        return (T) this;
    }
}

