package net.roguelogix.quartz.internal.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.roguelogix.quartz.internal.MagicNumbers;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCString;

import javax.annotation.Nonnull;
import java.lang.Math;

import static net.roguelogix.quartz.internal.QuartzDebug.DEBUG;

@SuppressWarnings("DuplicatedCode")
public record PointerWrapper(long pointer, long size) implements Comparable<PointerWrapper> {
    
    private static final boolean JOML_UNSAFE_AVAILABLE;
    
    static {
        boolean available = false;
        try {
            final var memUtilClass = PointerWrapper.class.getClassLoader().loadClass("org.joml.MemUtil");
            final var memUtilUnsafeClass = PointerWrapper.class.getClassLoader().loadClass("org.joml.MemUtil$MemUtilUnsafe");
            final var instanceField = memUtilClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            final var memUtilInstance = instanceField.get(null);
            available = memUtilUnsafeClass.isInstance(memUtilInstance);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        JOML_UNSAFE_AVAILABLE = available;
    }
    
    public static PointerWrapper NULLPTR = new PointerWrapper(0, 0);
    
    private static class MemoryLeak extends Exception {
        private final PointerWrapper allocated;
        
        private MemoryLeak(PointerWrapper allocated) {
            super("ptr: " + allocated.pointer + " size: " + allocated.size);
            this.allocated = allocated;
        }
    }
    
    private static final Long2ObjectMap<MemoryLeak> liveAllocations = new Long2ObjectOpenHashMap<>();
    
    private static PointerWrapper trackPointer(PointerWrapper wrapper) {
        if (!DEBUG) {
            return wrapper;
        }
        synchronized (liveAllocations) {
            liveAllocations.put(wrapper.pointer, new MemoryLeak(wrapper));
        }
        addAccessibleLocation(wrapper);
        return wrapper;
    }
    
    private static void untrackPointer(PointerWrapper wrapper) {
        if (!DEBUG) {
            return;
        }
        synchronized (liveAllocations) {
            if (liveAllocations.remove(wrapper.pointer) == null) {
                for (final var value : liveAllocations.values()) {
                    if (value.allocated.contains(wrapper)) {
                        throw new IllegalStateException("Attempt to free sub pointer, source pointer originally allocated at cause", value);
                    }
                }
                throw new IllegalStateException("Attempt to free pointer not currently live, potential double free?");
            }
        }
        removeAccessibleLocation(wrapper);
    }
    
    public static PointerWrapper alloc(long size) {
        final long ptr = MemoryUtil.nmemAlloc(size);
        return trackPointer(new PointerWrapper(ptr, size));
    }
    
    public PointerWrapper realloc(long newSize) {
        if (size == newSize) {
            return this;
        }
        final var newPtr = MemoryUtil.nmemRealloc(this.pointer, newSize);
        final var newWrapped = new PointerWrapper(newPtr, newSize);
        if (pointer != newPtr) {
            untrackPointer(this);
        }
        // retracks to realloc location
        trackPointer(newWrapped);
        return newWrapped;
    }
    
    public void free() {
        if (this == NULLPTR) {
            // technically, save to call free on a nullptr at the native level, but debug will complain if enabled
            return;
        }
        untrackPointer(this);
        MemoryUtil.nmemFree(pointer);
    }
    
    public static void logLeakedMemory() {
        if (DEBUG) {
            System.out.println("Logging memory leaks");
            for (final var value : liveAllocations.values()) {
                value.printStackTrace();
            }
            System.out.println("Memory leaks logged");
        }
    }
    
    private static final ObjectArraySet<PointerWrapper> validWriteLocations = new ObjectArraySet<>();
    
    public static void addAccessibleLocation(PointerWrapper wrapper) {
        if (!DEBUG) {
            return;
        }
        synchronized (validWriteLocations) {
            validWriteLocations.add(wrapper);
        }
    }
    
    public static void removeAccessibleLocation(PointerWrapper wrapper) {
        if (!DEBUG) {
            return;
        }
        synchronized (validWriteLocations) {
            validWriteLocations.remove(wrapper);
        }
    }
    
    public static void verifyCanAccessLocation(long ptr, long size) {
        if (!DEBUG) {
            return;
        }
        for (final var value : validWriteLocations) {
            // doesnt start in this range
            if (value.pointer > ptr || value.pointer + value.size <= ptr) {
                continue;
            }
            if (value.pointer + value.size < ptr + size) {
                throw new IllegalArgumentException("Attempt to write past end of native buffer");
            }
            // starts in a known range, and doesnt attempt to access past the end, its valid
            return;
        }
        throw new NullPointerException("Unable to find a valid write location for attempted write");
    }
    
    public void set(byte data) {
        set(0, size, data);
    }
    
    public void set(long offset, long size, byte data) {
        LibCString.nmemset(pointer + offset, data, size);
    }
    
    public static void copy(PointerWrapper src, long srcOffset, PointerWrapper dst, long dstOffset, long size) {
        if (src.pointer == 0 || dst.pointer == 0) {
            throw new IllegalStateException("Attempt to use NULLPTR");
        }
        final var srcPtr = src.pointer + srcOffset;
        final var dstPtr = dst.pointer + dstOffset;
        if (DEBUG) {
            if (size <= 0) {
                throw new IllegalArgumentException("Attempt to copy pointer with invalid size: " + size);
            }
            if (srcOffset <= 0) {
                throw new IllegalArgumentException("Attempt to copy pointer with invalid srcOffset: " + srcOffset);
            }
            if (dstOffset <= 0) {
                throw new IllegalArgumentException("Attempt to copy pointer with invalid dstOffset: " + dstOffset);
            }
            final long srcEndIndex = srcOffset + size;
            if (srcEndIndex > src.size) {
                throw new IllegalArgumentException("Attempt to copy pointer would read past end of source. source size: " + src.size + ", source offset: " + srcOffset + ", copy size: " + size);
            }
            final long dstEndIndex = dstOffset + size;
            if (dstEndIndex > dst.size) {
                throw new IllegalArgumentException("Attempt to copy pointer would read past end of destination. dest size: " + dst.size + ", dest offset: " + dstOffset + ", copy size: " + size);
            }
            verifyCanAccessLocation(srcPtr, size);
            verifyCanAccessLocation(dstPtr, size);
        }
        boolean overlaps = srcOffset == dstOffset;
        overlaps |= srcPtr < dstPtr && dstPtr < srcPtr + size;
        overlaps |= dstPtr < srcPtr && srcPtr < dstPtr + size;
        if (overlaps) {
            LibCString.nmemmove(dstPtr, srcPtr, size);
        } else {
            LibCString.nmemcpy(dstPtr, srcPtr, size);
        }
    }
    
    public void copyTo(long srcOffset, PointerWrapper dst, long dstOffset, long size) {
        copy(this, srcOffset, dst, dstOffset, size);
    }
    
    public void copyToSize(long srcOffset, PointerWrapper dst, long size) {
        copyTo(srcOffset, dst, 0, size);
    }
    
    public void copyTo(long srcOffset, PointerWrapper dst, long dstOffset) {
        copyTo(srcOffset, dst, 0, Math.min(size - srcOffset, dst.size));
    }
    
    public void copyTo(long srcOffset, PointerWrapper dst) {
        copyTo(srcOffset, dst, 0, Math.min(size - srcOffset, dst.size));
    }
    
    public void copyTo(PointerWrapper dst, long dstOffset) {
        copyTo(0, dst, 0, Math.min(size, dst.size - dstOffset));
    }
    
    public void copyToSize(PointerWrapper dst, long size) {
        copyTo(0, dst, 0, size);
    }
    
    public void copyTo(PointerWrapper dst) {
        copyTo(0, dst, 0, Math.min(size, dst.size));
    }
    
    private void checkRange(long offset, long writeSize) {
        checkRange(offset, writeSize, writeSize);
    }
    
    private void checkRange(long offset, long writeSize, long alignment) {
        if (this.pointer == 0) {
            throw new IllegalStateException("Attempt to use NULLPTR");
        }
        final var dstPtr = pointer + offset;
        if (DEBUG) {
            if (offset < 0) {
                throw new IllegalArgumentException("Attempt to access before beginning of pointer");
            }
            if (offset + writeSize > size) {
                throw new IllegalArgumentException("Attempt to access past end of pointer");
            }
            if ((dstPtr % alignment) != 0) {
                throw new IllegalArgumentException("Attempt to access unaligned address");
            }
            verifyCanAccessLocation(dstPtr, writeSize);
        }
    }
    
    public void putByte(long offset, byte val) {
        checkRange(offset, 1);
        MemoryUtil.memPutByte(pointer + offset, val);
    }
    
    public void putShort(long offset, short val) {
        checkRange(offset, 2);
        MemoryUtil.memPutShort(pointer + offset, val);
    }
    
    public void putInt(long offset, int val) {
        checkRange(offset, 4);
        MemoryUtil.memPutInt(pointer + offset, val);
    }
    
    public void putLong(long offset, long val) {
        checkRange(offset, 8);
        MemoryUtil.memPutLong(pointer + offset, val);
    }
    
    public void putFloat(long offset, float val) {
        checkRange(offset, 4);
        MemoryUtil.memPutFloat(pointer + offset, val);
    }
    
    public void putDouble(long offset, double val) {
        checkRange(offset, 8);
        MemoryUtil.memPutDouble(pointer + offset, val);
    }
    
    public void putVector3i(long offset, Vector3ic vector) {
        checkRange(offset, 12, 16);
        final var dstPtr = pointer + offset;
        if (JOML_UNSAFE_AVAILABLE) {
            vector.getToAddress(pointer + offset);
        } else {
            MemoryUtil.memPutInt(dstPtr, vector.x());
            MemoryUtil.memPutInt(dstPtr + 4, vector.y());
            MemoryUtil.memPutInt(dstPtr + 8, vector.z());
        }
    }
    
    public void putVector3f(long offset, Vector3fc vector) {
        checkRange(offset, 12, 16);
        final var dstPtr = pointer + offset;
        if(JOML_UNSAFE_AVAILABLE){
            vector.getToAddress(pointer + offset);
        } else {
            MemoryUtil.memPutFloat(dstPtr, vector.x());
            MemoryUtil.memPutFloat(dstPtr + 4, vector.y());
            MemoryUtil.memPutFloat(dstPtr + 8, vector.z());
        }
    }
    
    public void putVector4i(long offset, Vector4ic vector) {
        checkRange(offset, 16);
        final var dstPtr = pointer + offset;
        if (JOML_UNSAFE_AVAILABLE) {
            vector.getToAddress(pointer + offset);
        } else {
            MemoryUtil.memPutInt(dstPtr, vector.x());
            MemoryUtil.memPutInt(dstPtr + 4, vector.y());
            MemoryUtil.memPutInt(dstPtr + 8, vector.z());
            MemoryUtil.memPutInt(dstPtr + 12, vector.w());
        }
    }
    
    public void putVector4f(long offset, Vector4fc vector) {
        checkRange(offset, 16);
        final var dstPtr = pointer + offset;
        if (JOML_UNSAFE_AVAILABLE) {
            vector.getToAddress(pointer + offset);
        } else {
            MemoryUtil.memPutFloat(dstPtr, vector.x());
            MemoryUtil.memPutFloat(dstPtr + 4, vector.y());
            MemoryUtil.memPutFloat(dstPtr + 8, vector.z());
            MemoryUtil.memPutFloat(dstPtr + 12, vector.w());
        }
    }
    
    public void putMatrix4f(long offset, Matrix4fc matrix) {
        checkRange(offset, 64, 16);
        final var dstPtr = pointer + offset;
        if (JOML_UNSAFE_AVAILABLE) {
            matrix.getToAddress(dstPtr);
        } else {
            MemoryUtil.memPutFloat(dstPtr, matrix.m00());
            MemoryUtil.memPutFloat(dstPtr + 4, matrix.m01());
            MemoryUtil.memPutFloat(dstPtr + 8, matrix.m02());
            MemoryUtil.memPutFloat(dstPtr + 12, matrix.m03());
            MemoryUtil.memPutFloat(dstPtr + 16, matrix.m10());
            MemoryUtil.memPutFloat(dstPtr + 20, matrix.m11());
            MemoryUtil.memPutFloat(dstPtr + 24, matrix.m12());
            MemoryUtil.memPutFloat(dstPtr + 28, matrix.m13());
            MemoryUtil.memPutFloat(dstPtr + 32, matrix.m20());
            MemoryUtil.memPutFloat(dstPtr + 36, matrix.m21());
            MemoryUtil.memPutFloat(dstPtr + 40, matrix.m22());
            MemoryUtil.memPutFloat(dstPtr + 44, matrix.m23());
            MemoryUtil.memPutFloat(dstPtr + 48, matrix.m30());
            MemoryUtil.memPutFloat(dstPtr + 52, matrix.m31());
            MemoryUtil.memPutFloat(dstPtr + 56, matrix.m32());
            MemoryUtil.memPutFloat(dstPtr + 60, matrix.m33());
        }
    }
    
    public void putMatrix3x4f(long offset, Matrix4fc matrix) {
        checkRange(offset, 48, 16);
        // TODO: check new JOML for getToAddress3x4, or an open issue for it
        final var dstPtr = pointer + offset;
        MemoryUtil.memPutFloat(dstPtr, matrix.m00());
        MemoryUtil.memPutFloat(dstPtr + 4, matrix.m01());
        MemoryUtil.memPutFloat(dstPtr + 8, matrix.m02());
        MemoryUtil.memPutFloat(dstPtr + 12, matrix.m03());
        MemoryUtil.memPutFloat(dstPtr + 16, matrix.m10());
        MemoryUtil.memPutFloat(dstPtr + 20, matrix.m11());
        MemoryUtil.memPutFloat(dstPtr + 24, matrix.m12());
        MemoryUtil.memPutFloat(dstPtr + 28, matrix.m13());
        MemoryUtil.memPutFloat(dstPtr + 32, matrix.m20());
        MemoryUtil.memPutFloat(dstPtr + 36, matrix.m21());
        MemoryUtil.memPutFloat(dstPtr + 40, matrix.m22());
        MemoryUtil.memPutFloat(dstPtr + 44, matrix.m23());
    }
    
    public void putShortIdx(long index, short val) {
        putShort(index * MagicNumbers.SHORT_BYTE_SIZE, val);
    }
    
    public void putIntIdx(long index, int val) {
        putInt(index * MagicNumbers.INT_BYTE_SIZE, val);
    }
    
    public void putLongIdx(long index, long val) {
        putLong(index * MagicNumbers.LONG_BYTE_SIZE, val);
    }
    
    public void putFloatIdx(long index, float val) {
        putFloat(index * MagicNumbers.FLOAT_BYTE_SIZE, val);
    }
    
    public void putDoubleIdx(long index, double val) {
        putDouble(index * MagicNumbers.DOUBLE_BYTE_SIZE, val);
    }
    
    public void putVector3iIdx(long index, Vector3ic vector) {
        putVector3i(index * MagicNumbers.IVEC3_BYTE_SIZE, vector);
    }
    
    public void putVector4iIdx(long index, Vector4ic vector) {
        putVector4i(index * MagicNumbers.IVEC4_BYTE_SIZE, vector);
    }
    
    public void putVector4fIdx(long index, Vector4fc vector) {
        putVector4f(index * MagicNumbers.VEC4_BYTE_SIZE, vector);
    }
    
    public void putMatrix4fIdx(long index, Matrix4fc matrix4f) {
        putMatrix4f(index * MagicNumbers.MATRIX_4F_BYTE_SIZE, matrix4f);
    }
    
    public byte getByte(long offset) {
        checkRange(offset, 1);
        return MemoryUtil.memGetByte(pointer + offset);
    }
    
    public short getShort(long offset) {
        checkRange(offset, 2);
        return MemoryUtil.memGetShort(pointer + offset);
    }
    
    public int getInt(long offset) {
        checkRange(offset, 4);
        return MemoryUtil.memGetInt(pointer + offset);
    }
    
    public long getLong(long offset) {
        checkRange(offset, 8);
        return MemoryUtil.memGetLong(pointer + offset);
    }
    
    public float getFloat(long offset) {
        checkRange(offset, 4);
        return MemoryUtil.memGetFloat(pointer + offset);
    }
    
    public double getDouble(long offset) {
        checkRange(offset, 8);
        return MemoryUtil.memGetDouble(pointer + offset);
    }
    
    public void getMatrix4f(long offset, Matrix4f matrix) {
        checkRange(offset, 64, 16);
        matrix.setFromAddress(pointer + offset);
    }
    
    public void getMatrix3x4f(long offset, Matrix4f matrix) {
        checkRange(offset, 64, 16);
        matrix.setFromAddress(pointer + offset);
    }
    
    public void getVector3i(long offset, Vector3i vector) {
        checkRange(offset, 12, 16);
        vector.setFromAddress(pointer + offset);
    }
    
    public short getShortIdx(long index) {
        return getShort(index * MagicNumbers.SHORT_BYTE_SIZE);
    }
    
    public int getIntIdx(long index) {
        return getInt(index * MagicNumbers.INT_BYTE_SIZE);
    }
    
    public long getLongIdx(long index) {
        return getLong(index * MagicNumbers.LONG_BYTE_SIZE);
    }
    
    public float getFloatIdx(long index) {
        return getFloat(index * MagicNumbers.FLOAT_BYTE_SIZE);
    }
    
    public double getDoubleIdx(long index) {
        return getDouble(index * MagicNumbers.DOUBLE_BYTE_SIZE);
    }
    
    public void getMatrix4fIdx(long index, Matrix4f matrix4f) {
        getMatrix4f(index * MagicNumbers.MATRIX_4F_BYTE_SIZE, matrix4f);
    }
    
    public PointerWrapper slice(long offset, long size) {
        if (this.pointer == 0) {
            throw new IllegalStateException("Attempt to use NULLPTR");
        }
        if (DEBUG) {
            if (size <= 0) {
                throw new IllegalArgumentException("Attempt to slice pointer to invalid size: " + size);
            }
            if (offset < 0) {
                throw new IllegalArgumentException("Attempt to slice pointer to invalid offset: " + offset);
            }
            if (offset >= this.size) {
                throw new IllegalArgumentException("Attempt to slice pointer to offset past end. offset: " + offset + ", source size: " + this.size);
            }
            if ((offset + size) > this.size) {
                throw new IllegalArgumentException("Attempt to slice pointer to offset and size past end. offset: " + offset + ", size: " + size + ", source size: " + this.size);
            }
        }
        return new PointerWrapper(pointer + offset, size);
    }
    
    public boolean contains(PointerWrapper that) {
        return this.pointer <= that.pointer && that.pointer + that.size <= pointer + size;
    }
    
    @Override
    public int compareTo(@Nonnull PointerWrapper other) {
        var ptrCompare = Long.compare(this.pointer, other.pointer);
        if (ptrCompare != 0) {
            return ptrCompare;
        }
        // this is backwards so that child allocations are placed afterward
        return Long.compare(other.size, this.size);
    }
}
