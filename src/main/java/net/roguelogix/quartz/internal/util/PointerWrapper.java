package net.roguelogix.quartz.internal.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.joml.*;
import net.roguelogix.quartz.internal.MagicNumbers;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCString;

import java.lang.Math;

import static net.roguelogix.quartz.internal.QuartzCore.DEBUG;

public record PointerWrapper(long pointer, long size) {
    
    public static PointerWrapper NULLPTR = new PointerWrapper(0, 0);
    
    private static class MemoryLeak extends Exception {
        private final PointerWrapper allocated;
        
        private MemoryLeak(PointerWrapper allocated) {
            super("ptr: " + allocated.pointer + " size: " + allocated.size);
            this.allocated = allocated;
        }
    }
    
    private static final Long2ObjectMap<MemoryLeak> liveAllocations = new Long2ObjectOpenHashMap<>();
    
    public static PointerWrapper alloc(long size) {
        final long ptr = MemoryUtil.nmemAlloc(size);
        final var toReturn = new PointerWrapper(ptr, size);
        if (DEBUG) {
            synchronized (liveAllocations) {
                liveAllocations.put(ptr, new MemoryLeak(toReturn));
            }
        }
        return toReturn;
    }
    
    public void free() {
        if (DEBUG) {
            synchronized (liveAllocations) {
                if (liveAllocations.remove(pointer) == null) {
                    for (final var value : liveAllocations.values()) {
                        if (value.allocated.contains(this)) {
                            throw new IllegalStateException("Attempt to free sub pointer, source pointer originally allocated at cause", value);
                        }
                    }
                    throw new IllegalStateException("Attempt to free pointer not currently live, potential double free?");
                }
            }
        }
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
        }
        final var srcPtr = src.pointer + srcOffset;
        final var dstPtr = dst.pointer + dstOffset;
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
        vector.getToAddress(pointer + offset);
    }
    
    public void putVector3f(long offset, Vector3fc vector) {
        checkRange(offset, 12, 16);
        vector.getToAddress(pointer + offset);
    }
    
    public void putVector4i(long offset, Vector4ic vector) {
        checkRange(offset, 16);
        vector.getToAddress(pointer + offset);
    }
    
    public void putVector4f(long offset, Vector4fc vector) {
        checkRange(offset, 16);
        vector.getToAddress(pointer + offset);
    }
    
    public void putMatrix4f(long offset, Matrix4fc matrix) {
        checkRange(offset, 64, 16);
        matrix.getToAddress(pointer + offset);
    }
    
    public void putMatrix3x4f(long offset, Matrix4fc matrix) {
        checkRange(offset, 48, 16);
        matrix.getToAddress(pointer + offset);
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
}
