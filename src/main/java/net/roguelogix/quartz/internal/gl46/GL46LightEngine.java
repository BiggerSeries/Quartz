package net.roguelogix.quartz.internal.gl46;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.roguelogix.phosphophyllite.util.FastArraySet;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.VectorUtil;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.util.PointerWrapper;
import org.lwjgl.system.MemoryStack;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import static org.lwjgl.opengl.ARBSparseTexture.*;
import static org.lwjgl.opengl.GL45C.*;

// TODO: 3d lookup texture
public class GL46LightEngine {
    private static final int CHUNK_UPDATES_PER_FRAME = 16;
    private static boolean allocsDirty = false;
    private static final Long2ReferenceOpenHashMap<SoftReference<Chunk>> allChunks = new Long2ReferenceOpenHashMap<>();
    private static final Long2ReferenceOpenHashMap<WeakReference<ChunkHandle>> chunkHandles = new Long2ReferenceOpenHashMap<>();
    private static final FastArraySet<WeakReference<ChunkHandle>> dirtyChunks = new FastArraySet<>();
    
    private static int freeCommitedIndices = 0;
    private static final ObjectArrayList<ShortArrayList> freeIndices = new ObjectArrayList<>(GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z());
    
    static {
        for (int i = 0; i < GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z(); i++) {
            freeIndices.add(new ShortArrayList(60));
        }
    }
    
    private static final BooleanArrayList residentLayers = new BooleanArrayList(GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z());
    
    private static final int[] intermediateTextures = new int[6];
    private static int intermediateTextureDepth = GL46Statics.LIGHT_TEXTURE_BLOCK_DEPTH;
    private static final Vector3i virtualPageSize = new Vector3i();
    
    private static final GL46Buffer rawDataBuffer = new GL46Buffer(false);
    
    private static PointerWrapper lookupData = PointerWrapper.alloc(64 * 64 * 24 * 2);
    private static Vector3i lookupOffset = new Vector3i();
    private static GL46Buffer lookupBuffer = new GL46Buffer(64 * 64 * 24 * 2, true);
    private static int lookupTexture;
    private static GL46Buffer unpackBuffer = new GL46Buffer(CHUNK_UPDATES_PER_FRAME * 17 * 320 * 4 * 6, true);
    private static GL46Buffer.Allocation[] unpackBufferAllocs = new GL46Buffer.Allocation[CHUNK_UPDATES_PER_FRAME];
    private static long lastLightUpdateFence = 0;
    public static void startup() {
        int pageSizeIndex = -1;
        // TODO: on the fly realloc instead of sparse texture
        //       Terrascale doesnt support sparse texture, but does OpenGL 4.5
        //       Terrascale also has memory size limitations due to being so old
        //       potentially fine with system paging, but that would still require ~4GB of committed memory to these textures alone
        if (GL46Statics.SPARSE_TEXTURE_ENABLED) {
            try (var stack = MemoryStack.stackPush()) {
                final var format = GL_RGB32UI;
                final var pageSizeCount = glGetInternalformati(GL_TEXTURE_2D_ARRAY, format, GL_NUM_VIRTUAL_PAGE_SIZES_ARB);
                if (pageSizeCount == 0) {
                    throw new IllegalStateException("No sparse page sizes for GL_R16UI");
                }
                final var sizesX = stack.mallocInt(pageSizeCount);
                final var sizesY = stack.mallocInt(pageSizeCount);
                final var sizesZ = stack.mallocInt(pageSizeCount);
                glGetInternalformativ(GL_TEXTURE_2D_ARRAY, format, GL_VIRTUAL_PAGE_SIZE_X_ARB, sizesX);
                glGetInternalformativ(GL_TEXTURE_2D_ARRAY, format, GL_VIRTUAL_PAGE_SIZE_Y_ARB, sizesY);
                glGetInternalformativ(GL_TEXTURE_2D_ARRAY, format, GL_VIRTUAL_PAGE_SIZE_Z_ARB, sizesZ);
                
                for (int i = 0; i < pageSizeCount; i++) {
                    int xSize = sizesX.get(i);
                    int ySize = sizesY.get(i);
                    int zSize = sizesZ.get(i);
                    if (GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x() % xSize != 0) {
                        continue;
                    }
                    if (GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y() % ySize != 0) {
                        continue;
                    }
                    if (1 % zSize != 0) {
                        continue;
                    }
                    virtualPageSize.set(xSize, ySize, zSize);
                    pageSizeIndex = i;
                    break;
                }
                if (pageSizeIndex == -1) {
                    throw new IllegalStateException("No viable page size for GL_R16UI");
                }
                
            }
        }
        glCreateTextures(GL_TEXTURE_2D_ARRAY, intermediateTextures);
        for (int i = 0; i < intermediateTextures.length; i++) {
            final var texture = intermediateTextures[i];
            if (GL46Statics.SPARSE_TEXTURE_ENABLED) {
                glTextureParameteri(texture, GL_TEXTURE_SPARSE_ARB, GL_TRUE);
                glTextureParameteri(texture, GL_VIRTUAL_PAGE_SIZE_INDEX_ARB, pageSizeIndex);
            }
            glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            // large sparse texture, 60 renderchunks per 2d layer, size is to be a multiple of the page size, at least as it is on AMD hardware
            glTextureStorage3D(texture, 1, GL_R16UI, GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), GL46Statics.LIGHT_TEXTURE_BLOCK_DEPTH);
        }
        for (int i = GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z() - 1; i >= 0; i--) {
            residentLayers.add(false);
        }
        
        lookupTexture = glCreateTextures(GL_TEXTURE_BUFFER);
        glTextureBuffer(lookupTexture, GL_R16UI, lookupBuffer.handle());
        
        for (int i = 0; i < CHUNK_UPDATES_PER_FRAME; i++) {
            unpackBufferAllocs[i] = unpackBuffer.alloc(17 * 320 * 4 * 6 * 4,  2);
        }
    }
    
    public static void shutdown() {
        glDeleteTextures(intermediateTextures);
        rawDataBuffer.delete();
        lookupData.free();
    }
    
    public static Vector3ic lookupOffset() {
        return lookupOffset;
    }
    
    public static void bind() {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_BUFFER, lookupTexture);
        for (int i = 0; i < 6; i++) {
            glActiveTexture(GL_TEXTURE2 + i);
            glBindTexture(GL_TEXTURE_2D_ARRAY, intermediateTextures[i]);
        }
        glActiveTexture(GL_TEXTURE0);
    }
    
    public static void unbind() {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_BUFFER, 0);
        for (int i = 0; i < 6; i++) {
            glActiveTexture(GL_TEXTURE2 + i);
            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        }
        glActiveTexture(GL_TEXTURE0);
    }
    
    private static short allocLightChunk() {
        allocsDirty = true;
        for (int i = 0; i < residentLayers.size(); i++) {
            if (!residentLayers.getBoolean(i)) {
                continue;
            }
            final var indices = freeIndices.get(i);
            if (indices.isEmpty()) {
                continue;
            }
            short index = indices.popShort();
            freeCommitedIndices--;
            index <<= 10;
            index |= i;
            return index;
        }
        // no resident layer has a free index, need to add another layer
        for (int layerIndex = 0; layerIndex < residentLayers.size(); layerIndex++) {
            if (residentLayers.getBoolean(layerIndex)) {
                continue;
            }
            final var indices = freeIndices.get(layerIndex);
            indices.clear();
            for (short j = 59; j >= 0; j--) {
                indices.add(j);
            }
            
            if (GL46Statics.SPARSE_TEXTURE_ENABLED) {
                for (int i = 0; i < intermediateTextures.length; i++) {
                    SparseTextureHelper.glTexturePageCommitmentEXT(intermediateTextures[i], 0, 0, 0, layerIndex, GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), 1, true);
                }
            } else {
                if (layerIndex >= intermediateTextureDepth) {
                    final int newTextureDepth = intermediateTextureDepth + GL46Statics.LIGHT_TEXTURE_BLOCK_DEPTH;
                    
                    final var newTextures = new int[6];
                    glCreateTextures(GL_TEXTURE_2D_ARRAY, newTextures);
                    for (int i = 0; i < newTextures.length; i++) {
                        final var texture = newTextures[i];
                        glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                        glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                        glTextureStorage3D(texture, 1, GL_R16UI, GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), newTextureDepth);
                        glCopyImageSubData(intermediateTextures[i], GL_TEXTURE_2D_ARRAY, 0, 0, 0, 0, newTextures[i], GL_TEXTURE_2D_ARRAY, 0, 0, 0, 0,  GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), intermediateTextureDepth);
                    }
                    glDeleteTextures(intermediateTextures);
                    for (int i = 0; i < intermediateTextures.length; i++) {
                        intermediateTextures[i] = newTextures[i];
                    }
                    intermediateTextureDepth = newTextureDepth;
                }
            }
            freeCommitedIndices += 60;
            residentLayers.set(layerIndex, true);
            
            short index = indices.popShort();
            freeCommitedIndices--;
            index <<= 10;
            index |= layerIndex;
            return index;
        }
        throw new IllegalStateException("Unable to allocate lighting chunk index");
    }
    
    private static void freeLightChunk(final short index) {
        allocsDirty = true;
        final int layerIndex = index & 0x3FF;
        final short subIndex = (short) ((index >> 10) & 0x3F);
        if (!residentLayers.getBoolean(layerIndex)) {
            throw new IllegalArgumentException("Attempt to free lighting chunk index from non-resident layer");
        }
        final var indices = freeIndices.get(layerIndex);
        indices.add(subIndex);
        if (freeIndices.size() != 60) {
            return;
        }
        if (freeCommitedIndices <= 120) {
            return;
        }
        if (GL46Statics.SPARSE_TEXTURE_ENABLED) {
            for (int i = 0; i < intermediateTextures.length; i++) {
                SparseTextureHelper.glTexturePageCommitmentEXT(intermediateTextures[i], 0, 0, 0, layerIndex, GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), 1, false);
            }
        }
        freeCommitedIndices -= 60;
        residentLayers.set(layerIndex, false);
    }
    
    public static void update(BlockAndTintGetter blockAndTintGetter) {
        runLightingUpdates(blockAndTintGetter);
        runAllocUpdates();
    }
    
    public static void runLightingUpdates(BlockAndTintGetter blockAndTintGetter) {
        if (dirtyChunks.isEmpty()) {
            return;
        }
        if(lastLightUpdateFence != 0){
            glWaitSync(lastLightUpdateFence, 0, GL_TIMEOUT_IGNORED);
            glDeleteSync(lastLightUpdateFence);
        }
        //allocsDirty = true;
        for (int i = 0; i < 6; i++) {
            glBindImageTexture(i + 1, intermediateTextures[i], 0, true, 0, GL_WRITE_ONLY, GL_R16UI);
        }
        int updatesThisFrame = 0;
        for (int i = 0; i < dirtyChunks.size(); i++) {
            final var value = dirtyChunks.get(i);
            final var chunk = value.get();
            if (chunk == null) {
                dirtyChunks.remove(value);
                i--;
                continue;
            }
            if (chunk.chunk.update(blockAndTintGetter, unpackBufferAllocs[updatesThisFrame])) {
                updatesThisFrame++;
            }
            if (!chunk.chunk.dirty) {
                dirtyChunks.remove(value);
                i--;
            }
            if (updatesThisFrame >= CHUNK_UPDATES_PER_FRAME) {
                break;
            }
        }
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        for (int i = 0; i < 6; i++) {
            glBindImageTexture(i + 1, 0, 0, true, 0, GL_WRITE_ONLY, GL_R16UI);
        }
        lastLightUpdateFence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }
    
    public static void runAllocUpdates() {
        if (!allocsDirty) {
            return;
        }
        allocsDirty = false;
        final var tempVec = new Vector3i();
        lookupOffset.set(Integer.MAX_VALUE);
        for (final var chunk : chunkHandles.long2ReferenceEntrySet()) {
            long chunkPos = chunk.getLongKey();
            lookupOffset.min(VectorUtil.fromSectionPos(chunkPos, tempVec));
        }
        lookupData.set((byte) -1);
        for (final var chunkEntry : chunkHandles.long2ReferenceEntrySet()) {
            final long chunkPos = chunkEntry.getLongKey();
            final var chunkHandle = chunkEntry.getValue().get();
            if (chunkHandle == null) {
                continue;
            }
            final short lookupIndex = chunkHandle.chunk.lightChunkIndex;
            VectorUtil.fromSectionPos(chunkPos, tempVec);
            tempVec.sub(lookupOffset);
            int texelIndex = 0;
            texelIndex += tempVec.z;
            texelIndex *= 24;
            texelIndex += tempVec.y;
            texelIndex *= 64;
            texelIndex += tempVec.x;
            lookupData.putShortIdx(texelIndex, lookupIndex);
        }
        // this should be updated rarely enough that mapping and/or using two buffers doesnt really make sense
        nglNamedBufferSubData(lookupBuffer.handle(), 0, lookupData.size(), lookupData.pointer());
    }
    
    public static void sectionDirty(int x, int y, int z) {
        final long pos = SectionPos.asLong(x, y, z);
        final var weakRef = chunkHandles.get(pos);
        if (weakRef == null) {
            return;
        }
        final var chunk = weakRef.get();
        if (chunk == null) {
            return;
        }
        chunk.chunk.dirty = true;
        dirtyChunks.add(weakRef);
    }
    
    public static ChunkHandle getChunk(long position) {
        final var existingHandleRef = chunkHandles.get(position);
        if (existingHandleRef != null) {
            final var existingHandle = existingHandleRef.get();
            if (existingHandle != null) {
                return existingHandle;
            }
        }
        // no or null chunk handle
        final var chunk = getActualChunk(position);
        final var handle = new ChunkHandle(chunk);
        final var reference = new WeakReference<>(handle);
        allocsDirty = true;
        chunkHandles.put(position, reference);
        chunk.dirty = true;
        dirtyChunks.add(reference);
        return handle;
    }
    
    private static Chunk getActualChunk(long pos) {
        final var softRef = allChunks.get(pos);
        if (softRef != null) {
            final var chunk = softRef.get();
            if (chunk != null) {
                return chunk;
            }
        }
        
        final var newChunk = new Chunk(pos);
        allChunks.put(pos, new SoftReference<>(newChunk));
        return newChunk;
    }
    
    public static final class ChunkHandle {
        private final Chunk chunk;
        
        private ChunkHandle(Chunk chunk) {
            this.chunk = chunk;
            long pos = chunk.sectionPos;
            QuartzCore.mainThreadClean(this, () -> {
                allocsDirty = true;
                var removedRef = chunkHandles.remove(pos);
                // if GC ran in the middle of a frame and cleaned a handle that was gotten again later, this can happen
                if (removedRef.get() != null) {
                    chunkHandles.put(pos, removedRef);
                }
            });
        }
        
    }
    
    private static class Chunk {
        public final long sectionPos;
        
        public final short lightChunkIndex;
        private final GL46Buffer.Allocation alloc;
        private final long[] lastSync;
        
        private boolean dirty = false;
        
        private Chunk(long pos) {
            this.sectionPos = pos;
            final var lightChunkIndex = allocLightChunk();
            dirty = true;
            final var alloc = rawDataBuffer.alloc(12288, 12288);
            final var lastSync = new long[1];
            QuartzCore.mainThreadClean(this, () -> {
                if (lastSync[0] != 0) {
                    glClientWaitSync(lastSync[0], GL_SYNC_FLUSH_COMMANDS_BIT, 0);
                    glDeleteSync(lastSync[0]);
                }
                alloc.free();
                freeLightChunk(lightChunkIndex);
            });
            this.lightChunkIndex = lightChunkIndex;
            this.alloc = alloc;
            this.lastSync = lastSync;
        }
        
        private boolean update(BlockAndTintGetter blockAndTintGetter, GL46Buffer.Allocation unpackAllocation) {
            if (!dirty) {
                return false;
            }
            dirty = false;
            if (lastSync[0] != 0) {
                // need to wait for previous compute to finish before writing over the memory
                // given how slow minecraft is, this should basically never actually cause a return, but it might,
                final var waitResult = glClientWaitSync(lastSync[0], GL_SYNC_FLUSH_COMMANDS_BIT, 0);
                if (waitResult == GL_WAIT_FAILED) {
                    throw new IllegalStateException("OpenGL wait failed");
                }
                if (waitResult == GL_TIMEOUT_EXPIRED) {
                    // come back next frame, should be done by then
                    return false;
                }
                // already signaled or satisfied within the wait time, which is zero, so, already satisfied
                glDeleteSync(lastSync[0]);
                lastSync[0] = 0;
            }
            
            final int sectionBaseX = SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos));
            final int sectionBaseY = SectionPos.sectionToBlockCoord(SectionPos.y(sectionPos));
            final int sectionBaseZ = SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos));
            
            final var mutableBlockPos = new BlockPos.MutableBlockPos();
            final var pointer = alloc.address();
            int index = 0;
            for (int x = -1; x < 17; x++) {
                for (int y = -1; y < 17; y++) {
                    for (int z = -1; z < 17; z++) {
                        final int currentX = sectionBaseX + x;
                        final int currentY = sectionBaseY + y;
                        final int currentZ = sectionBaseZ + z;
                        mutableBlockPos.set(currentX, currentY, currentZ);
                        final int blockLight;
                        final int skyLight;
                        final var blockState = blockAndTintGetter.getBlockState(mutableBlockPos);
                        if (!blockState.propagatesSkylightDown(blockAndTintGetter, mutableBlockPos)) {
                            blockLight = -1;
                            skyLight = -1;
                        } else {
                            blockLight = blockAndTintGetter.getBrightness(LightLayer.BLOCK, mutableBlockPos);
                            skyLight = blockAndTintGetter.getBrightness(LightLayer.SKY, mutableBlockPos);
                        }
                        pointer.putByte(index++, (byte) skyLight);
                        pointer.putByte(index++, (byte) blockLight);
                    }
                }
            }
            
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, alloc.allocator().handle(), alloc.offset(), alloc.size());
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 1, unpackAllocation.allocator().handle(), unpackAllocation.offset(), unpackAllocation.size());
            int lightChunkX = (lightChunkIndex >> 11) & 0x1F;
            int lightChunkY = (lightChunkIndex >> 10) & 0x1;
            int lightChunkZ = lightChunkIndex & 0x3FF;
            glProgramUniform3ui(GL46ComputePrograms.lightChunkProgram(), 0, lightChunkX * 17, lightChunkY * 320, lightChunkZ);
            glUseProgram(GL46ComputePrograms.lightChunkProgram());
            glDispatchCompute(17, 17, 17);
            glMemoryBarrier(GL_PIXEL_BUFFER_BARRIER_BIT);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackAllocation.allocator().handle());
            for (int i = 0; i < intermediateTextures.length; i++) {
                final var lightChunkDirectionIndices = 17 * 320 * 4;
                final var offset = unpackAllocation.offset() + (lightChunkDirectionIndices * i);
                glTextureSubImage3D(intermediateTextures[i], 0, lightChunkX * 17, lightChunkY * 320, lightChunkZ, 17, 320, 1, GL_RED_INTEGER, GL_UNSIGNED_INT, offset);
            }
            
            lastSync[0] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            return true;
        }
    }
}
