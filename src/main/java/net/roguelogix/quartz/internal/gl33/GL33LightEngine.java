package net.roguelogix.quartz.internal.gl33;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.roguelogix.phosphophyllite.util.FastArraySet;
import net.roguelogix.phosphophyllite.util.VectorUtil;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.common.B3DStateHelper;
import net.roguelogix.quartz.internal.util.PointerWrapper;
import org.joml.Vector2f;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import static org.lwjgl.opengl.GL33C.*;

// TODO: 3d lookup texture
public class GL33LightEngine {
    private static final int CHUNK_UPDATES_PER_FRAME = 16;
    private static boolean allocsDirty = false;
    private static final Long2ReferenceOpenHashMap<SoftReference<Chunk>> allChunks = new Long2ReferenceOpenHashMap<>();
    private static final Long2ReferenceOpenHashMap<WeakReference<ChunkHandle>> chunkHandles = new Long2ReferenceOpenHashMap<>();
    private static final FastArraySet<WeakReference<ChunkHandle>> dirtyChunks = new FastArraySet<>();
    
    private static int freeCommitedIndices = 0;
    private static final ObjectArrayList<ShortArrayList> freeIndices = new ObjectArrayList<>(GL33Statics.LIGHT_TEXTURE_ARRAY_FULL_SIZE.z());
    
    static {
        for (int i = 0; i < GL33Statics.LIGHT_TEXTURE_ARRAY_FULL_SIZE.z(); i++) {
            freeIndices.add(new ShortArrayList(56));
        }
    }
    
    private static final BooleanArrayList residentLayers = new BooleanArrayList(GL33Statics.LIGHT_TEXTURE_ARRAY_FULL_SIZE.z());
    
    private static final int[][] intermediateTextures = new int[GL33Statics.LIGHT_TEXTURE_ARRAY_BLOCK_COUNT.z()][6];
    private static final Vector3i virtualPageSize = new Vector3i();
    
    private static PointerWrapper lookupData = PointerWrapper.alloc(64 * 64 * 24 * 2);
    private static Vector3i lookupOffset = new Vector3i();
    private static GL33Buffer lookupBuffer = new GL33Buffer(64 * 64 * 24 * 2, true);
    private static int lookupTexture;
    
    private static GL33Buffer unpackBuffer = new GL33Buffer(CHUNK_UPDATES_PER_FRAME * 18 * 320 * 6 * 2, true);
    
    private static GL33Buffer.Allocation[] unpackBufferAllocs = new GL33Buffer.Allocation[CHUNK_UPDATES_PER_FRAME];
    
    public static void startup() {
        int pageSizeIndex = -1;
        
        for (int i = GL33Statics.LIGHT_TEXTURE_ARRAY_FULL_SIZE.z() - 1; i >= 0; i--) {
            residentLayers.add(false);
        }
        
        lookupTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_BUFFER, lookupTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_R16UI, lookupBuffer.handle());
        glBindTexture(GL_TEXTURE_BUFFER, 0);
        
        for (int i = 0; i < CHUNK_UPDATES_PER_FRAME; i++) {
            unpackBufferAllocs[i] = unpackBuffer.alloc(18 * 320 * 6 * 2, 2);
        }
    }
    
    public static void shutdown() {
        for (int i = 0; i < intermediateTextures.length; i++) {
            for (int j = 0; j < intermediateTextures[i].length; j++) {
                glDeleteTextures(intermediateTextures[i][j]);
            }
        }
        lookupData.free();
    }
    
    public static Vector3ic lookupOffset() {
        return lookupOffset;
    }
    
    public static void bindIndex() {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_BUFFER, lookupTexture);
        glActiveTexture(GL_TEXTURE0);
    }
    
    public static void unbindIndex() {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_3D, 0);
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
            for (short j = 5; j >= 0; j--) {
                indices.add(j);
            }
            
            final var layerTextures = intermediateTextures[layerIndex >> 4];
            
            for (int i = 0; i < layerTextures.length; i++) {
                layerTextures[i] = glGenTextures();
                glBindTexture(GL_TEXTURE_2D_ARRAY, layerTextures[i]);
                glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                // just allocated the texture,
                glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_R16UI, GL33Statics.LIGHT_TEXTURE_ARRAY_BLOCK_SIZE.x(), GL33Statics.LIGHT_TEXTURE_ARRAY_BLOCK_SIZE.y(), GL33Statics.LIGHT_TEXTURE_ARRAY_BLOCK_SIZE.z(), 0, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0);
            }
            RenderSystem.bindTexture(0);
            freeCommitedIndices += 56;
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
        if (freeIndices.size() != 56) {
            return;
        }
        // blocks are 32 layers, so, 1920 per block
        // TODO: find a way to avoid freeing it just to realloc if jumping between chunk counts
        //       that can happen either way too
        if (freeCommitedIndices <= 56 * 32) {
            return;
        }
        
        // check if any layer in the block is resident
        var blockBaseIndex = (layerIndex >> 4) << 4;
        for (int j = 0; j < 16; j++) {
            if (residentLayers.getBoolean(blockBaseIndex + j)) {
                // another is resident, dont free it
                return;
            }
        }
        
        final var layerTextures = intermediateTextures[layerIndex >> 4];
        glDeleteTextures(layerTextures);
        
        freeCommitedIndices -= 56;
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
        int updatesThisFrame = 0;
        for (int i = 0; i < dirtyChunks.size(); i++) {
            final var value = dirtyChunks.get(i);
            final var chunk = value.get();
            if (chunk == null) {
                dirtyChunks.remove(value);
                i--;
                continue;
            }
            
            chunk.chunk.update(blockAndTintGetter, unpackBufferAllocs[updatesThisFrame]);
            updatesThisFrame++;
            
            if (!chunk.chunk.dirty) {
                dirtyChunks.remove(value);
                i--;
            }
            if (updatesThisFrame >= CHUNK_UPDATES_PER_FRAME) {
                break;
            }
        }
        RenderSystem.bindTexture(0);
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
        glBindBuffer(GL_COPY_WRITE_BUFFER, lookupBuffer.handle());
        nglBufferSubData(GL_COPY_WRITE_BUFFER, 0, lookupData.size(), lookupData.pointer());
        glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
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
    
    public static void allDirty() {
        for (WeakReference<ChunkHandle> weakRef : chunkHandles.values()) {
            final var chunk = weakRef.get();
            if (chunk == null) {
                return;
            }
            chunk.chunk.dirty = true;
            dirtyChunks.add(weakRef);
        }
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
    
    public static int drawForEachLayer(int requiredVertices, int activeLayerLocation, int srcBuffer, int intermediateBuffer, int VAO1, int VAO2) {
        for (int i = 0; i < residentLayers.size(); i += 16) {
            checkLayers:{
                // check if any layer in the block is resident
                for (int j = 0; j < 16; j++) {
                    if (residentLayers.getBoolean(i + j)) {
                        break checkLayers;
                    }
                }
                continue;
            }
            final var textures = intermediateTextures[i >> 4];
            for (int j = 0; j < 6; j++) {
                RenderSystem.activeTexture(GL_TEXTURE2 + j);
                glBindTexture(GL_TEXTURE_2D_ARRAY, textures[j]);
            }
            glUniform1ui(activeLayerLocation, i);
            B3DStateHelper.bindVertexArray(VAO1);
            glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, intermediateBuffer);
            glBeginTransformFeedback(GL_POINTS);
            glDrawArrays(GL_POINTS, 0, requiredVertices);
            glEndTransformFeedback();
            srcBuffer ^= intermediateBuffer;
            intermediateBuffer ^= srcBuffer;
            srcBuffer ^= intermediateBuffer;
            VAO1 ^= VAO2;
            VAO2 ^= VAO1;
            VAO1 ^= VAO2;
        }
        for (int j = 0; j < 6; j++) {
            RenderSystem.activeTexture(GL_TEXTURE2 + j);
            RenderSystem.bindTexture(0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        }
        return srcBuffer;
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
        private boolean dirty = false;
        
        private Chunk(long pos) {
            this.sectionPos = pos;
            final var lightChunkIndex = allocLightChunk();
            dirty = true;
            final var lastSync = new long[1];
            QuartzCore.mainThreadClean(this, () -> {
                if (lastSync[0] != 0) {
                    glClientWaitSync(lastSync[0], GL_SYNC_FLUSH_COMMANDS_BIT, 0);
                    glDeleteSync(lastSync[0]);
                }
                freeLightChunk(lightChunkIndex);
            });
            this.lightChunkIndex = lightChunkIndex;
        }
        
        private boolean update(BlockAndTintGetter blockAndTintGetter, GL33Buffer.Allocation unpackAlloc) {
            if (!dirty) {
                return false;
            }
            dirty = false;
            
            final int sectionBaseX = SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos));
            final int sectionBaseY = SectionPos.sectionToBlockCoord(SectionPos.y(sectionPos));
            final int sectionBaseZ = SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos));
            
            final var mutableBlockPos = new BlockPos.MutableBlockPos();
            final var rawData = PointerWrapper.alloc(12288);
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
                        rawData.putByte(index++, (byte) skyLight);
                        rawData.putByte(index++, (byte) blockLight);
                    }
                }
            }
            
            final int lightChunkX = (lightChunkIndex >> 11) & 0x1F;
            final int lightChunkY = (lightChunkIndex >> 10) & 0x1;
            final int lightChunkZ = lightChunkIndex & 0x3FF;
            final int lightChunkTexelX = lightChunkX * 18;
            final int lightChunkTexelY = lightChunkY * 320;
            
            final var lightChunkTextures = intermediateTextures[lightChunkZ >> 4];
            final var lightChunkDirectionIndices = 18 * 320;
            final var lightChunkDirectionSize = lightChunkDirectionIndices * 2;
            final var directionalData = PointerWrapper.alloc(lightChunkDirectionSize * 6);
            
            // TODO: move this offthread
            //       its quite costly to do on the main thread
            final var neighborLevels = new Vector2f[2][2][2];
            final var outputValues = new Vector3i[6];
            for (int x = 0; x < 17; x++) {
                for (int y = 0; y < 17; y++) {
                    for (int z = 0; z < 17; z++) {
                        for (int i = 0; i < 2; i++) {
                            for (int j = 0; j < 2; j++) {
                                for (int k = 0; k < 2; k++) {
                                    final var shortIndex = (((x + i) * 18) + (y + j)) * 18 + (z + k);
                                    // TODO: less objects, these can be reused a bunch
                                    neighborLevels[i][j][k] = new Vector2f(rawData.getByte(shortIndex * 2L + 1), rawData.getByte(shortIndex * 2L));
                                }
                            }
                        }
                        outputValues[0] = averageValues(neighborLevels[1][0][0], neighborLevels[1][0][1], neighborLevels[1][1][0], neighborLevels[1][1][1]);
                        outputValues[3] = averageValues(neighborLevels[0][0][0], neighborLevels[0][0][1], neighborLevels[0][1][0], neighborLevels[0][1][1]);
                        outputValues[1] = averageValues(neighborLevels[0][1][0], neighborLevels[0][1][1], neighborLevels[1][1][0], neighborLevels[1][1][1]);
                        outputValues[4] = averageValues(neighborLevels[0][0][0], neighborLevels[0][0][1], neighborLevels[1][0][0], neighborLevels[1][0][1]);
                        outputValues[2] = averageValues(neighborLevels[0][0][1], neighborLevels[0][1][1], neighborLevels[1][0][1], neighborLevels[1][1][1]);
                        outputValues[5] = averageValues(neighborLevels[0][0][0], neighborLevels[0][1][0], neighborLevels[1][0][0], neighborLevels[1][1][0]);
                        
                        for (int i = 0; i < 6; i++) {
                            var val = outputValues[i];
                            if (val.z == 4) {
                                val = outputValues[(i + 3) % 6];
                            }
                            
                            final var bufferIndex = ((((z * 18) + y) * 18) + x) + (lightChunkDirectionIndices * i);
                            directionalData.putShortIdx(bufferIndex, packLightAOuint16(val));
//                            directionalData.putShortIdx(bufferIndex, packLightAOuint16(new Vector3i(60, 0, 0)));
                        }
                    }
                }
            }
            
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackAlloc.allocator().handle());
            nglBufferSubData(GL_PIXEL_UNPACK_BUFFER, unpackAlloc.offset(), Math.min(unpackAlloc.size(), directionalData.size()), directionalData.pointer());
            
            for (int i = 0; i < 6; i++) {
                final var offset = unpackAlloc.offset() + lightChunkDirectionSize * i;
                glBindTexture(GL_TEXTURE_2D_ARRAY, lightChunkTextures[i]);
                glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, lightChunkTexelX, lightChunkTexelY, lightChunkZ & 0xF, 18, 320, 1, GL_RED_INTEGER, GL_UNSIGNED_SHORT, offset);
            }
            
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            
            directionalData.free();
            
            return true;
        }
        
        private static Vector2f scratchVec = new Vector2f();
        
        private static Vector3i averageValues(Vector2f A, Vector2f B, Vector2f C, Vector2f D) {
            int validLevels = 0;
            final var lightLevel = scratchVec;
            lightLevel.set(0);
            
            if (A.x() != -1) {
                lightLevel.add(A);
                validLevels++;
            }
            if (B.x() != -1) {
                lightLevel.add(B);
                validLevels++;
            }
            if (C.x() != -1) {
                lightLevel.add(C);
                validLevels++;
            }
            if (D.x() != -1) {
                lightLevel.add(D);
                validLevels++;
            }
            if (validLevels != 0) {
                lightLevel.mul(4);
                lightLevel.div(validLevels);
            }
            return new Vector3i((int) lightLevel.x, (int) lightLevel.y, 4 - validLevels);
        }
        
        private static short packLightAOuint16(Vector3i lightAO) {
            short packedInt = 0;
            packedInt |= (lightAO.x() & 0x3F) << 6;
            packedInt |= (lightAO.y() & 0x3F);
            packedInt |= (lightAO.z() & 0x3) << 12;
            return packedInt;
        }
        
        private static short unpackAO(short packedVal) {
            return (short) ((packedVal >> 12) & 0x3);
        }
        
        private static short setAO(short val, short newAO) {
            return (short) ((val & 0x0FFF) | ((newAO & 0x3) << 12));
        }
    }
}
