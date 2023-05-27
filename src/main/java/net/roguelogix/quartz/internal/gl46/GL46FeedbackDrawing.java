package net.roguelogix.quartz.internal.gl46;

import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.client.renderer.RenderType;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.internal.*;
import net.roguelogix.quartz.internal.common.B3DStateHelper;
import net.roguelogix.quartz.internal.gl46.batching.GL46DrawBatch;
import net.roguelogix.quartz.internal.util.VertexFormatOutput;

import java.lang.ref.WeakReference;
import java.util.List;

import static net.roguelogix.quartz.internal.util.ShitMojangShouldHaveButDoesnt.drawRenderTypePreboundVertexBuffer;
import static org.lwjgl.opengl.GL46C.*;

public class GL46FeedbackDrawing {
    
    private static final ReferenceArrayList<WeakReference<GL46DrawBatch>> drawBatches = new ReferenceArrayList<>();
    
    public static DrawBatch createDrawBatch() {
        final var batcher = new GL46DrawBatch();
        final var ref = new WeakReference<>(batcher);
        drawBatches.add(ref);
        QuartzCore.mainThreadClean(batcher, () -> drawBatches.remove(ref));
        return batcher;
    }
    
    private static final Reference2IntMap<RenderType> renderTypeUsages = new Reference2IntArrayMap<>();
    private static final Reference2IntMap<RenderType> renderTypeVAOs = new Reference2IntArrayMap<>();
    private static final Reference2IntMap<RenderType> renderTypeDrawnVertices = new Reference2IntArrayMap<>();
    private static final ReferenceArrayList<RenderType> inUseRenderTypes = new ReferenceArrayList<>();
    
    private static int feedbackVAO;
    
    private record FeedbackBuffer(int buffer, int size) {
        private FeedbackBuffer(int size) {
            this(glCreateBuffers(), roundUpPo2(size));
            // no flags, only used on the server side
            glNamedBufferStorage(buffer, this.size, 0);
        }
        
        void delete() {
            glDeleteBuffers(buffer);
        }
        
        private static int roundUpPo2(int minSize) {
            int size = Integer.highestOneBit(minSize);
            if (size < minSize) {
                size <<= 1;
            }
            return size;
        }
    }
    
    private static final Object2ObjectMap<RenderType, FeedbackBuffer> renderTypeFeedbackBuffers = new Object2ObjectArrayMap<>();
    
    private static Buffer.CallbackHandle rebuildCallbackHandle;
    
    public static void startup() {
        if(rebuildCallbackHandle != null){
            rebuildCallbackHandle.delete();
            rebuildCallbackHandle = null;
        }
        
        feedbackVAO = glCreateVertexArrays();
        rebuildCallbackHandle = QuartzCore.INSTANCE.meshManager.vertexBuffer.addReallocCallback(true, buffer -> {
            glVertexArrayVertexBuffer(feedbackVAO, 0, buffer.as(GL46Buffer.class).handle(), 0, MagicNumbers.VERTEX_BYTE_SIZE);
        });
        // instance data is bound per drawbatch feedback draw
        glVertexArrayBindingDivisor(feedbackVAO, 1, 1);

        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.POSITION_LOCATION);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.COLOR_LOCATION);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.TEX_COORD_LOCATION);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.NORMAL_LOCATION);

        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.WORLD_POSITION_LOCATION);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.DYNAMIC_MATRIX_ID_LOCATION);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 1);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 2);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 3);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION + 1);
        glEnableVertexArrayAttrib(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION + 2);

        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.POSITION_LOCATION, 0);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.COLOR_LOCATION, 0);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.TEX_COORD_LOCATION, 0);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.NORMAL_LOCATION, 0);

        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.POSITION_LOCATION, 3, GL_FLOAT, false, 0);
        glVertexArrayAttribIFormat(feedbackVAO, GL46Statics.COLOR_LOCATION, 1, GL_INT, 12);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.TEX_COORD_LOCATION, 3, GL_FLOAT, false, 16);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.NORMAL_LOCATION, 3, GL_SHORT, true, 24);

        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.WORLD_POSITION_LOCATION, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.DYNAMIC_MATRIX_ID_LOCATION, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 1, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 2, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 3, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION + 1, 1);
        glVertexArrayAttribBinding(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION + 2, 1);

        glVertexArrayAttribIFormat(feedbackVAO, GL46Statics.WORLD_POSITION_LOCATION, 3, GL_INT, GL46Statics.WORLD_POSITION_OFFSET);
        glVertexArrayAttribIFormat(feedbackVAO, GL46Statics.DYNAMIC_MATRIX_ID_LOCATION, 1, GL_INT, GL46Statics.DYNAMIC_MATRIX_ID_OFFSET);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION, 4, GL_FLOAT, false, GL46Statics.STATIC_MATRIX_OFFSET);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 1, 4, GL_FLOAT, false, GL46Statics.STATIC_MATRIX_OFFSET + 16);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 2, 4, GL_FLOAT, false, GL46Statics.STATIC_MATRIX_OFFSET + 32);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.STATIC_MATRIX_LOCATION + 3, 4, GL_FLOAT, false, GL46Statics.STATIC_MATRIX_OFFSET + 48);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION, 4, GL_FLOAT, false, GL46Statics.STATIC_NORMAL_MATRIX_OFFSET);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION + 1, 4, GL_FLOAT, false, GL46Statics.STATIC_NORMAL_MATRIX_OFFSET + 16);
        glVertexArrayAttribFormat(feedbackVAO, GL46Statics.STATIC_NORMAL_MATRIX_LOCATION + 2, 4, GL_FLOAT, false, GL46Statics.STATIC_NORMAL_MATRIX_OFFSET + 32);
    }
    
    public static void shutdown() {
        glDeleteVertexArrays(feedbackVAO);
        renderTypeFeedbackBuffers.values().forEach(FeedbackBuffer::delete);
        rebuildCallbackHandle.delete();
        rebuildCallbackHandle = null;
    }
    
    public static void addRenderTypeUse(RenderType renderType) {
        if (renderTypeUsages.put(renderType, renderTypeUsages.getOrDefault(renderType, 0) + 1) == 0) {
            inUseRenderTypes.add(renderType);
        }
    }
    
    public static void removeRenderTypeUse(RenderType renderType) {
        final var usages = renderTypeUsages.getInt(renderType);
        if (usages == 1) {
            renderTypeUsages.removeInt(renderType);
            inUseRenderTypes.remove(renderType);
            final var feedbackBuffer = renderTypeFeedbackBuffers.remove(renderType);
            if (feedbackBuffer != null) {
                feedbackBuffer.delete();
            }
            return;
        }
        renderTypeUsages.put(renderType, usages - 1);
    }
    
    public static List<RenderType> getActiveRenderTypes() {
        return inUseRenderTypes;
    }
    
    private static int requiredVertices = 0;
    private static long[] prevousFrameSyncs = new long[GL46Statics.FRAMES_IN_FLIGHT];
    private static MultiBuffer<GL46Buffer> UBOBuffers = new MultiBuffer<>(GL46Statics.FRAMES_IN_FLIGHT);
    private static MultiBuffer<GL46Buffer>.Allocation UBOAllocation = UBOBuffers.alloc(64);
    
    public static boolean hasBatch() {
        return !drawBatches.isEmpty();
    }
    
    public static void aboutToBeginFrame() {
        final int frameInFlight = GL46Core.INSTANCE.frameInFlight();
        
        if (prevousFrameSyncs[frameInFlight] != 0) {
            glClientWaitSync(prevousFrameSyncs[frameInFlight], GL_SYNC_FLUSH_COMMANDS_BIT, -1);
            glDeleteSync(prevousFrameSyncs[frameInFlight]);
            prevousFrameSyncs[frameInFlight] = 0;
        }
    }
    
    public static void beginFrame() {
        glUseProgram(GL46ComputePrograms.dynamicMatrixProgram());
        for (final var batchRef : drawBatches) {
            final var batch = batchRef.get();
            if (batch == null) {
                return;
            }
            batch.updateAndCull(GL46Core.INSTANCE.drawInfo);
        }
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, 0);
        glUseProgram(0);
        glMemoryBarrier(GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
    }
    
    public static void collectAllFeedback(boolean shadowsEnabled) {
        final int frameInFlight = GL46Core.INSTANCE.frameInFlight();
        
        UBOBuffers.setActiveFrame(frameInFlight);
        final var UBOPointer = UBOAllocation.activeAllocation().address();
        
        UBOPointer.putVector3i(0, GL46Core.INSTANCE.drawInfo.playerPosition);
        UBOPointer.putVector3f(16, GL46Core.INSTANCE.drawInfo.playerSubBlock);
        UBOPointer.putVector3i(32, GL46LightEngine.lookupOffset());
        UBOPointer.putInt(44, IrisDetection.areShadersActive() ? 1 : 0);
        
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, UBOBuffers.activeBuffer().handle());

        GL46LightEngine.bind();
        
        B3DStateHelper.bindVertexArray(feedbackVAO);
        for (final var renderType : inUseRenderTypes) {
            final var outputFormat = VertexFormatOutput.of(renderType.format());
            
            requiredVertices = 0;
            for (final var batchRef : drawBatches) {
                final var drawBatch = batchRef.get();
                if (drawBatch == null) {
                    return;
                }
                requiredVertices += drawBatch.verticesForRenderType(renderType, shadowsEnabled);
            }
            
            renderTypeDrawnVertices.put(renderType, requiredVertices);
            
            requiredVertices *= outputFormat.vertexSize();
            
            var buffer = renderTypeFeedbackBuffers.computeIfAbsent(renderType, e -> new FeedbackBuffer(requiredVertices));
            if (buffer.size < requiredVertices) {
                buffer.delete();
                buffer = new FeedbackBuffer(requiredVertices);
                renderTypeFeedbackBuffers.put(renderType, buffer);
            }
            
            glUseProgram(GL46FeedbackPrograms.getProgramForOutputFormat(outputFormat));
            
            glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffer.buffer);
            glBeginTransformFeedback(GL_POINTS);
            for (final var batchRef : drawBatches) {
                final var drawBatch = batchRef.get();
                if (drawBatch == null) {
                    return;
                }
                drawBatch.drawFeedback(renderType, shadowsEnabled);
            }
            glEndTransformFeedback();
        }
        B3DStateHelper.bindVertexArray(0);

        GL46LightEngine.unbind();
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
        
        long frameSync = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        for (final var batchRef : drawBatches) {
            final var drawBatch = batchRef.get();
            if (drawBatch == null) {
                return;
            }
            drawBatch.setFrameSync(frameSync);
        }
        prevousFrameSyncs[frameInFlight] = frameSync;
    }
    
    public static void drawRenderType(RenderType renderType) {
        final var feedbackBuffer = renderTypeFeedbackBuffers.get(renderType);
        if (feedbackBuffer == null) {
            return;
        }
        final var drawnVertices = renderTypeDrawnVertices.getInt(renderType);
        if (drawnVertices == 0) {
            return;
        }
        
        B3DStateHelper.bindArrayBuffer(feedbackBuffer.buffer);
        drawRenderTypePreboundVertexBuffer(renderType, drawnVertices);
    }
}
