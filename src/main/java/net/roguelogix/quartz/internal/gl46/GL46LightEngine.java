package net.roguelogix.quartz.internal.gl46;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector3i;
import net.roguelogix.phosphophyllite.repack.org.joml.Vector3ic;
import net.roguelogix.phosphophyllite.util.VectorUtil;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.util.PointerWrapper;
import org.lwjgl.system.MemoryStack;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import static org.lwjgl.opengl.ARBSparseTexture.*;
import static org.lwjgl.opengl.GL46C.*;

// TODO: 3d lookup texture
public class GL46LightEngine {
    private static final int CHUNK_UPDATES_PER_FRAME = 16;
    private static boolean dirty = false;
    private static final Long2ObjectOpenHashMap<SoftReference<Chunk>> allChunks = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<WeakReference<ChunkHandle>> chunkHandles = new Long2ObjectOpenHashMap<>();
    
    private static int freeCommitedIndices = 0;
    private static final ObjectArrayList<ShortArrayList> freeIndices = new ObjectArrayList<>(GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z());
    
    static {
        for (int i = 0; i < GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z(); i++) {
            freeIndices.add(new ShortArrayList(60));
        }
    }
    
    private static final BooleanArrayList residentLayers = new BooleanArrayList(GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z());
    
    private static final int[] intermediateTextures = new int[6];
    private static final Vector3i virtualPageSize = new Vector3i();
    
    private static final GL46Buffer rawDataBuffer = new GL46Buffer();
    
    private static PointerWrapper lookupData = PointerWrapper.alloc(64 * 64 * 24 * 2);
    private static Vector3i lookupOffset = new Vector3i();
    private static int lookupTexture;
    public static void startup() {
        int pageSizeIndex = -1;
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
            glTextureStorage3D(texture, 1, GL_R16UI, GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z());
        }
        for (int i = GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.z() - 1; i >= 0; i--) {
            residentLayers.add(false);
        }
        
        lookupTexture = glCreateTextures(GL_TEXTURE_3D);
        glTextureParameteri(lookupTexture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(lookupTexture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureStorage3D(lookupTexture, 1, GL_R16UI, 64, 24, 64);
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
        glBindTexture(GL_TEXTURE_3D, lookupTexture);
        for (int i = 0; i < 6; i++) {
            glActiveTexture(GL_TEXTURE2 + i);
            glBindTexture(GL_TEXTURE_2D_ARRAY, intermediateTextures[i]);
        }
        glActiveTexture(GL_TEXTURE0);
    }
    
    public static void unbind() {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_3D, 0);
        for (int i = 0; i < 6; i++) {
            glActiveTexture(GL_TEXTURE2 + i);
            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        }
        glActiveTexture(GL_TEXTURE0);
    }
    
    private static short allocLightChunk() {
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
                    glTexturePageCommitmentEXT(intermediateTextures[i], 0, 0, 0, layerIndex, GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), 1, true);
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
                glTexturePageCommitmentEXT(intermediateTextures[i], 0, 0, 0, layerIndex, GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.x(), GL46Statics.LIGHT_SPARE_TEXTURE_SIZE.y(), 1, false);
            }
        }
        freeCommitedIndices -= 60;
        residentLayers.set(layerIndex, false);
    }
    
    public static void update(BlockAndTintGetter blockAndTintGetter) {
        if (!dirty) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            glBindImageTexture(i + 1, intermediateTextures[i], 0, true, 0, GL_WRITE_ONLY, GL_R16UI);
        }
        int updatesThisFrame = 0;
        for (final var entry : allChunks.long2ObjectEntrySet()) {
            final var value = entry.getValue();
            if (value == null) {
                continue;
            }
            final var chunk = value.get();
            if (chunk == null) {
                continue;
            }
            if (chunk.update(blockAndTintGetter)) {
                updatesThisFrame++;
                if (updatesThisFrame == CHUNK_UPDATES_PER_FRAME) {
                    break;
                }
            }
        }
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        if (updatesThisFrame != CHUNK_UPDATES_PER_FRAME) {
            dirty = false;
        }
        
        final var tempVec = new Vector3i();
        lookupOffset.set(Integer.MAX_VALUE);
        for (final var chunk : chunkHandles.long2ObjectEntrySet()) {
            long chunkPos = chunk.getLongKey();
            lookupOffset.min(VectorUtil.fromSectionPos(chunkPos, tempVec));
        }
        lookupData.set((byte) -1);
        for (final var chunkEntry : chunkHandles.long2ObjectEntrySet()) {
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
        nglTextureSubImage3D(lookupTexture, 0, 0, 0, 0, 64, 24, 64, GL_RED_INTEGER, GL_UNSIGNED_SHORT, lookupData.pointer());
        
        for (int i = 0; i < 6; i++) {
            glBindImageTexture(i + 1, 0, 0, true, 0, GL_WRITE_ONLY, GL_R16UI);
        }
    }
    
    public static void sectionDirty(int x, int y, int z) {
        final long pos = SectionPos.asLong(x, y, z);
        final var softRef = allChunks.get(pos);
        if (softRef == null) {
            return;
        }
        final var chunk = softRef.get();
        if (chunk == null) {
            return;
        }
        chunk.dirty = true;
        dirty = true;
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
        chunkHandles.put(position, new WeakReference<>(handle));
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
            GL46LightEngine.dirty = true;
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
        
        private boolean update(BlockAndTintGetter blockAndTintGetter) {
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
            int lightChunkX = (lightChunkIndex >> 11) & 0x1F;
            int lightChunkY = (lightChunkIndex >> 10) & 0x1;
            int lightChunkZ = lightChunkIndex & 0x3FF;
            glProgramUniform3ui(GL46ComputePrograms.lightChunkProgram(), 0, lightChunkX * 17, lightChunkY * 320, lightChunkZ);
            glUseProgram(GL46ComputePrograms.lightChunkProgram());
            glDispatchCompute(17, 17, 17);
            
            lastSync[0] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            return true;
        }
    }
}
