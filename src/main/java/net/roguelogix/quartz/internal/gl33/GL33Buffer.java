package net.roguelogix.quartz.internal.gl33;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.QuartzConfig;
import net.roguelogix.quartz.internal.Buffer;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.util.CallbackDeleter;
import net.roguelogix.quartz.internal.util.PointerWrapper;
import org.lwjgl.system.MathUtil;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL33C.*;

// TODO: a lot of this is very similar between the GL33 and GL46 implementations
//       maybe see about moving a bunch of it to a common superclass?
//       common stuff includes the live allocation tracking
//       doing a realloc
//       realloc callbacks
@NonnullDefault
public class GL33Buffer implements Buffer {
    
    public class Allocation implements Buffer.Allocation {
        private record Info(int offset, int size) implements Comparable<Info> {
            public Info(Info a, Info b) {
                this(a.offset, a.size + b.size);
                if (a.offset + a.size != b.offset) {
                    throw new IllegalStateException("Cannot combine non-consecutive alloc infos");
                }
            }
            
            @Override
            public int compareTo(Info info) {
                return Integer.compare(offset, info.offset);
            }
            
            private Pair<Info, Info> split(int size) {
                if (size > this.size) {
                    throw new IllegalArgumentException("Cannot split allocation to larger size");
                }
                if (size == this.size) {
                    return new Pair<>(new Info(this.offset, size), null);
                }
                return new Pair<>(new Info(this.offset, size), new Info(this.offset + size, this.size - size));
            }
        }
        
        private final Info info;
        @Nullable
        private PointerWrapper cpuAddress;
        private final boolean[] freed;
        private final CallbackHandle bufferRealloc;
        private final ReferenceArrayList<Consumer<Buffer.Allocation>> reallocCallbacks;
        
        private Allocation(Info info) {
            this(new ReferenceArrayList<>(), info);
        }
        
        private Allocation(Allocation allocation, Info info, boolean copyData) {
            this(allocation.reallocCallbacks, info);
            allocation.bufferRealloc.delete();
            allocation.freed[0] = true;
            if (copyData && allocation.info.offset != info.offset) {
                allocation.copy(0, this, 0, Math.min(allocation.info.size, info.size));
            }
            for (int i = 0; i < reallocCallbacks.size(); i++) {
                reallocCallbacks.get(i).accept(this);
            }
        }
        
        private Allocation(ReferenceArrayList<Consumer<Buffer.Allocation>> reallocCallbacks, Info info) {
            final var allocator = GL33Buffer.this;
            final var weakRef = new WeakReference<>(this);
            final var bufferRealloc = GL33Buffer.this.addReallocCallback(false, e -> {
                final var ref = weakRef.get();
                if(ref == null){
                    return;
                }
                ref.cpuAddress = null;
                for (int i = 0; i < reallocCallbacks.size(); i++) {
                    final var callback = reallocCallbacks.get(i);
                    callback.accept(ref);
                }
            });
            
            final var freed = new boolean[]{false};
            final Exception allocationPoint;
            if (QuartzConfig.INSTANCE.debug) {
                allocationPoint = new Exception();
            } else {
                allocationPoint = null;
            }
            QuartzCore.mainThreadClean(this, () -> {
                bufferRealloc.delete();
                if (!freed[0]) {
                    freed[0] = true;
                    if (allocationPoint != null) {
                        allocationPoint.printStackTrace();
                    }
                    allocator.free(info);
                }
            });
            
            this.info = info;
            this.freed = freed;
            this.bufferRealloc = bufferRealloc;
            this.reallocCallbacks = reallocCallbacks;
        }
        
        @Override
        public PointerWrapper address() {
            if (cpuAddress == null) {
                if (GPUOnly) {
                    cpuAddress = PointerWrapper.NULLPTR;
                } else {
                    cpuAddress = cpuBuffer.slice(info.offset(), info.size());
                }
            }
            return cpuAddress;
        }
        
        @Override
        public int offset() {
            return info.offset();
        }
        
        @Override
        public int size() {
            return info.size();
        }
        
        @Override
        public void dirtyRange(int offset, int size) {
            
        }
        
        @Override
        public GL33Buffer allocator() {
            return GL33Buffer.this;
        }
        
        @Override
        public void copy(int srcOffset, int dstOffset, int size) {
            copy(srcOffset, this, dstOffset, size);
        }
        
        public void copy(Allocation dstAlloc) {
            copy(0, dstAlloc, 0, Math.min(this.size(), dstAlloc.size()));
        }
        
        public void copy(int srcOffset, Allocation dstAlloc, int dstOffset, int size) {
            final var dstAllocator = dstAlloc.allocator();
            if (GPUOnly && !dstAllocator.GPUOnly) {
                throw new IllegalStateException("Cannot copy from GPU buffer to CPU buffer");
            }
            if (dstAllocator.GPUOnly) {
                if (!GPUOnly) {
                    flush();
                }
                srcOffset += offset();
                dstOffset += dstAlloc.offset();
                glCopy(handle(), srcOffset, dstAllocator.handle(), dstOffset, size);
            } else {
                address().copyTo(srcOffset, dstAlloc.address(), dstOffset, size);
            }
        }
        
        public static void glCopy(int srcBuffer, int srcOffset, int dstBuffer, int dstOffset, int size) {
            boolean overlaps = srcOffset == dstOffset;
            overlaps |= srcOffset < dstOffset && dstOffset < srcOffset + size;
            overlaps |= dstOffset < srcOffset && srcOffset < dstOffset + size;
            overlaps &= srcBuffer == dstBuffer;
            glBindBuffer(GL_COPY_READ_BUFFER, srcBuffer);
            glBindBuffer(GL_COPY_WRITE_BUFFER, dstBuffer);
            if (!overlaps) {
                glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, srcOffset, dstOffset, size);
            } else {
                if (dstOffset == srcOffset) {
                    throw new IllegalArgumentException();
                }
                // yes this makes a ton of GL calls, GL spec doesnt allow a copy in an overlapping region, this makes the copies non-overlapping
                final var forward = dstOffset < srcOffset;
                if (forward) {
                    final var nonOverlappingRegionSize = srcOffset - dstOffset;
                    var currentCopyOffset = 0;
                    var leftToCopy = size;
                    while (leftToCopy > 0) {
                        final var toCopy = Math.min(nonOverlappingRegionSize, leftToCopy);
                        glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, srcOffset + currentCopyOffset, dstOffset + currentCopyOffset, toCopy);
                        currentCopyOffset += toCopy;
                        leftToCopy -= toCopy;
                    }
                } else {
                    final var nonOverlappingRegionSize = dstOffset - srcOffset;
                    var currentCopyOffset = 0;
                    var leftToCopy = size;
                    while (leftToCopy > 0) {
                        final var toCopy = Math.min(nonOverlappingRegionSize, leftToCopy);
                        currentCopyOffset -= toCopy;
                        glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, srcOffset + currentCopyOffset, dstOffset + currentCopyOffset, toCopy);
                        leftToCopy -= toCopy;
                    }
                }
            }
            glBindBuffer(GL_COPY_READ_BUFFER, 0);
            glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
        }
        
        @Override
        public CallbackHandle addReallocCallback(Consumer<Buffer.Allocation> consumer) {
            final var callbacks = reallocCallbacks;
            callbacks.add(consumer);
            return new CallbackDeleter(() -> callbacks.remove(consumer));
        }
    }
    
    private final boolean GPUOnly;
    
    // OpenGL 3.3 requires mutable buffers
    // so, its resized as needed
    private final int glBuffer;
    private int size;
    
    private PointerWrapper cpuBuffer = PointerWrapper.NULLPTR;
    private final PointerWrapper[] cpuBufferArray;
    
    
    private final ObjectArrayList<Allocation.Info> liveAllocations = new ObjectArrayList<>();
    private final ObjectArrayList<Allocation.Info> freeAllocations = new ObjectArrayList<>() {
        @Override
        public boolean add(@Nullable Allocation.Info allocation) {
            if (allocation == null) {
                return false;
            }
            int index = Collections.binarySearch(this, allocation);
            if (index < 0) {
                index = ~index;
                super.add(index, allocation);
            } else {
                super.set(index, allocation);
            }
            return true;
        }
    };
    
    private final ObjectArrayList<Consumer<Buffer>> reallocCallbacks = new ObjectArrayList<>();
    
    public GL33Buffer(boolean GPUOnly) {
        this(32768, GPUOnly);
    }
    
    public GL33Buffer(int initialSize, boolean GPUOnly) {
        this(initialSize, true, GPUOnly);
    }
    
    public GL33Buffer(int initialSize, boolean roundUpPo2, boolean GPUOnly) {
        this.GPUOnly = GPUOnly;
        if (roundUpPo2) {
            initialSize = MathUtil.mathRoundPoT(initialSize);
        }
        this.size = initialSize;
        final var buffer = glGenBuffers();
        final var cpuBufArray = new PointerWrapper[1];
        
        glBindBuffer(GL_COPY_WRITE_BUFFER, buffer);
        glBufferData(GL_COPY_WRITE_BUFFER, initialSize, GL_STATIC_DRAW);
        glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
        cpuBufArray[0] = cpuBuffer = PointerWrapper.alloc(initialSize);
        
        freeAllocations.add(new Allocation.Info(0, size));
        
        QuartzCore.mainThreadClean(this, () -> {
            if (cpuBufArray[0] != null) {
                cpuBufArray[0].free();
                glDeleteBuffers(buffer);
            }
        });
        
        this.glBuffer = buffer;
        this.cpuBufferArray = cpuBufArray;
    }
    
    @Override
    public void delete() {
        glDeleteBuffers(glBuffer);
        cpuBuffer.free();
        cpuBufferArray[0] = null;
    }
    
    
    public int handle() {
        return glBuffer;
    }
    
    @Override
    public int size() {
        return size;
    }
    
    @Override
    public Allocation alloc(int size, int alignment) {
        return new Allocation(allocSpace(size, alignment));
    }
    
    private Allocation.Info allocSpace(int size, int alignment) {
        for (int i = 0; i < freeAllocations.size(); i++) {
            final var attemptedAlloc = attemptAllocInSpace(freeAllocations.get(i), size, alignment);
            if (attemptedAlloc != null) {
                freeAllocations.remove(i);
                return attemptedAlloc;
            }
        }
        
        int endOffset = this.size;
        int minSize = this.size + size;
        if (!freeAllocations.isEmpty()) {
            var endAlloc = freeAllocations.get(freeAllocations.size() - 1);
            if (endAlloc.offset + endAlloc.size == this.size) {
                minSize -= endAlloc.size;
                endOffset = endAlloc.offset;
            }
        }
        final int nextValidAlignment = (endOffset + (alignment - 1)) & (-alignment);
        final int alignmentWaste = nextValidAlignment - endOffset;
        minSize += alignmentWaste;
        
        expand(minSize);
        
        final var attemptedAlloc = attemptAllocInSpace(freeAllocations.pop(), size, alignment);
        if (attemptedAlloc == null) {
            throw new IllegalStateException("Alloc failed even after expanding buffer");
        }
        return attemptedAlloc;
    }
    
    @Nullable
    private Allocation.Info attemptAllocInSpace(Allocation.Info freeAlloc, int size, int alignment) {
        // next value guaranteed to be at *most* one less than the next alignment, then bit magic because powers of two to round down without a divide
        final int nextValidAlignment = (freeAlloc.offset + (alignment - 1)) & (-alignment);
        final int alignmentWaste = nextValidAlignment - freeAlloc.offset;
        if (freeAlloc.size - alignmentWaste < size) {
            // wont fit, *neeeeeeeeeeeext*
            return null;
        }
        boolean collapse = false;
        if (alignmentWaste > 0) {
            final var newAllocs = freeAlloc.split(alignmentWaste);
            // not concurrent modification because this will always return
            freeAllocations.add(newAllocs.getFirst());
            freeAlloc = newAllocs.getSecond();
            
            int index = freeAllocations.indexOf(newAllocs.getFirst());
            collapseFreeAllocationWithNext(index - 1);
            collapseFreeAllocationWithNext(index);
        }
        if (freeAlloc.size > size) {
            final var newAllocs = freeAlloc.split(size);
            // not concurrent modification because this will always return
            freeAlloc = newAllocs.getFirst();
            freeAllocations.add(newAllocs.getSecond());
            int index = freeAllocations.indexOf(newAllocs.getSecond());
            collapseFreeAllocationWithNext(index - 1);
            collapseFreeAllocationWithNext(index);
        }
        
        liveAllocations.add(freeAlloc);
        return freeAlloc;
    }
    
    @Override
    public Allocation realloc(@Nullable Buffer.Allocation bufAlloc, int newSize, int alignment, boolean copyData) {
        if (!(bufAlloc instanceof Allocation alloc)) {
            throw new IllegalArgumentException("Cannot realloc allocation from another buffer");
        }
        return realloc(alloc, newSize, alignment, copyData);
    }
    
    public Allocation realloc(@Nullable Allocation allocation, int newSize, int alignment, boolean copyData) {
        if (allocation == null) {
            return alloc(newSize, alignment);
        }
        if (allocation.allocator() != this) {
            // not an allocation from this buffer
            throw new IllegalArgumentException("Cannot realloc allocation from another buffer");
        }
        
        var liveIndex = liveAllocations.indexOf(allocation.info);
        if (liveIndex == -1) {
            throw new IllegalArgumentException("Cannot realloc non-live allocation");
        }
        
        if (newSize <= allocation.info.size && (allocation.info.offset & (alignment - 1)) == 0) {
            // this allocation already meets size and alignment requirements
            if (newSize == allocation.info.size) {
                return allocation;
            }
            var removed = liveAllocations.pop();
            if (liveIndex != liveAllocations.size()) {
                liveAllocations.set(liveIndex, removed);
            }
            var newAllocInfos = allocation.info.split(newSize);
            var newAllocInfo = newAllocInfos.getFirst();
            var freeAllocInfo = newAllocInfos.getSecond();
            freeAllocations.add(freeAllocInfo);
            int index = freeAllocations.indexOf(freeAllocInfo);
            collapseFreeAllocationWithNext(index - 1);
            collapseFreeAllocationWithNext(index);
            liveAllocations.add(newAllocInfo);
            return new Allocation(allocation, newAllocInfo, copyData);
        }
        
        Allocation.Info precedingAlloc = null;
        Allocation.Info followingAlloc = null;
        for (int i = 0; i < freeAllocations.size(); i++) {
            var freeAllocation = freeAllocations.get(i);
            if (freeAllocation.offset + freeAllocation.size == allocation.info.offset) {
                precedingAlloc = freeAllocation;
                continue;
            }
            if (freeAllocation.offset == allocation.info.offset + allocation.info.size) {
                followingAlloc = freeAllocation;
                break;
            }
            if (freeAllocation.offset > allocation.info.offset) {
                break;
            }
        }
        int fullBlockOffset = precedingAlloc == null ? allocation.info.offset : precedingAlloc.offset;
        final int nextValidAlignment = (fullBlockOffset + (alignment - 1)) & (-alignment);
        final int alignmentWaste = nextValidAlignment - fullBlockOffset;
        int fullBlockSize = allocation.info.size;
        if (precedingAlloc != null) {
            fullBlockSize += precedingAlloc.size;
        }
        if (followingAlloc != null) {
            fullBlockSize += followingAlloc.size;
        } else if (allocation.info.offset + allocation.info.size == size) {
            // end allocation, so I can resize it to whatever is needed
            freeAllocations.remove(precedingAlloc);
            int minSize = fullBlockOffset + alignmentWaste + newSize;
            expand(minSize);
            followingAlloc = freeAllocations.get(freeAllocations.size() - 1);
            fullBlockSize += followingAlloc.size;
        }
        
        if (fullBlockSize - alignmentWaste >= newSize) {
            // ok, available memory exists around where the data currently is
            freeAllocations.remove(precedingAlloc);
            freeAllocations.remove(followingAlloc);
            var removed = liveAllocations.pop();
            if (liveIndex != liveAllocations.size()) {
                liveAllocations.set(liveIndex, removed);
            }
            
            var newAllocInfo = attemptAllocInSpace(new Allocation.Info(fullBlockOffset, fullBlockSize), newSize, alignment);
            if (newAllocInfo == null) {
                throw new IllegalStateException("Realloc failed in guaranteed space");
            }
            return new Allocation(allocation, newAllocInfo, copyData);
        }
        
        free(allocation);
        return new Allocation(allocation, allocSpace(newSize, alignment), copyData);
    }
    
    
    @Override
    public void free(Buffer.Allocation allocation) {
        if (allocation instanceof Allocation alloc) {
            free(alloc);
        }
    }
    
    public void free(Allocation allocation) {
        allocation.bufferRealloc.delete();
        if (!allocation.freed[0]) {
            allocation.freed[0] = true;
            free(allocation.info);
        }
    }
    
    private void free(Allocation.Info allocation) {
        var index = liveAllocations.indexOf(allocation);
        if (index == -1) {
            return;
        }
        var removed = liveAllocations.pop();
        if (index != liveAllocations.size()) {
            liveAllocations.set(index, removed);
        }
        freeAllocations.add(allocation);
        index = freeAllocations.indexOf(allocation);
        collapseFreeAllocationWithNext(index - 1);
        collapseFreeAllocationWithNext(index);
    }
    
    @Override
    public void dirtyAll() {
        
    }
    
    public void flush() {
        glBindBuffer(GL_COPY_WRITE_BUFFER, glBuffer);
        nglBufferSubData(GL_COPY_WRITE_BUFFER, 0, cpuBuffer.size(), cpuBuffer.pointer());
        glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
    }
    
    @Override
    public CallbackHandle addReallocCallback(boolean callImmediately, Consumer<Buffer> consumer) {
        if (callImmediately) {
            consumer.accept(this);
        }
        final var callbacks = reallocCallbacks;
        callbacks.add(consumer);
        return new CallbackDeleter(() -> callbacks.remove(consumer));
    }
    
    public void expand(int minSize) {
        if (size >= minSize) {
            return;
        }
        
        int oldSize = size;
        int newSize = Integer.highestOneBit(minSize);
        if (newSize < minSize) {
            newSize <<= 1;
        }
        
        cpuBufferArray[0] = cpuBuffer = cpuBuffer.realloc(newSize);
        
        // using GL_COPY_WRITE, because mojang doesnt touch it
        glBindBuffer(GL_COPY_WRITE_BUFFER, glBuffer);
        // usage hints are *probably* ignored by the driver, so static draw it is!
        nglBufferData(GL_COPY_WRITE_BUFFER, cpuBuffer.size(), cpuBuffer.pointer(), GL_STATIC_DRAW);
        glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
        
        size = newSize;
        
        freeAllocations.add(new Allocation.Info(oldSize, newSize - oldSize));
        
        collapseFreeAllocationWithNext(freeAllocations.size() - 2);
        
        reallocCallbacks.forEach(c -> c.accept(this));
    }
    
    private boolean collapseFreeAllocationWithNext(int freeAllocationIndex) {
        if (freeAllocationIndex < 0 || freeAllocationIndex >= freeAllocations.size() - 1) {
            return false;
        }
        var allocA = freeAllocations.get(freeAllocationIndex);
        var allocB = freeAllocations.get(freeAllocationIndex + 1);
        if (allocA.offset + allocA.size == allocB.offset) {
            // neighboring allocations, collapse them
            freeAllocations.remove(freeAllocationIndex + 1);
            freeAllocations.remove(freeAllocationIndex);
            freeAllocations.add(new Allocation.Info(allocA.offset, allocA.size + allocB.size));
            return true;
        }
        return false;
    }
}
