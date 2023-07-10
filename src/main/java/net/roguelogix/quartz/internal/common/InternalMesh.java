package net.roguelogix.quartz.internal.common;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.roguelogix.quartz.SodiumBullshit;
import org.joml.Vector3f;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.internal.Buffer;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.util.PointerWrapper;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.roguelogix.quartz.internal.MagicNumbers.VERTEX_BYTE_SIZE;

@NonnullDefault
public class InternalMesh implements Mesh {
    
    public Consumer<Mesh.Builder> buildFunc;
    
    public InternalMesh(Consumer<Mesh.Builder> buildFunc) {
        this.buildFunc = buildFunc;
    }
    
    public Object2LongArrayMap<RenderType> build(Function<Integer, PointerWrapper> bufferCreator) {
        Builder builder = new Builder();
        buildFunc.accept(builder);
        var buffer = bufferCreator.apply(builder.bytesRequired());
        return builder.build(buffer);
    }
    
    @Override
    public void rebuild() {
        QuartzCore.INSTANCE.meshManager.buildMesh(this);
    }
    
    private static class Builder implements Mesh.Builder, MultiBufferSource {
        private static class Vertex {
            
            Vertex() {
            }
            
            Vertex(Vertex toCopy) {
                x = toCopy.x;
                y = toCopy.y;
                z = toCopy.z;
                normalX = toCopy.normalX;
                normalY = toCopy.normalY;
                normalZ = toCopy.normalZ;
                rgba = toCopy.rgba;
                texU = toCopy.texU;
                texV = toCopy.texV;
                lightmapU = toCopy.lightmapU;
                lightmapV = toCopy.lightmapV;
            }
            
            float x = 0, y = 0, z = 0;
            float normalX = 0, normalY = 0, normalZ = 0;
            int rgba = -1;
            float texU = 0, texV = 0;
            
            int lightmapU = 0, lightmapV = 0;
        }
        
        private static class BufferBuilder implements VertexConsumer {
            Vertex currentVertex = new Vertex();
            final LinkedList<Vertex> vertices = new LinkedList<>();
            
            private boolean defaultColorSet = false;
            private int drgba;
            
            @Override
            public VertexConsumer vertex(double x, double y, double z) {
                // its uploaded to GL as a float, so its cased here
                currentVertex.x = (float) x;
                currentVertex.y = (float) y;
                currentVertex.z = (float) z;
                return this;
            }
            
            @Override
            public VertexConsumer color(int r, int g, int b, int a) {
                // assumes each value is <= 255, if i need to put an "& 0xFF" on these, im going to find you
                // as a side note, that means you can pass in an RGBA value in r
                currentVertex.rgba = (a << 24) | (b << 16) | (g << 8) | r;
                return this;
            }
            
            @Override
            public VertexConsumer uv(float u, float v) {
                currentVertex.texU = u;
                currentVertex.texV = v;
                return this;
            }
            
            @Override
            public VertexConsumer overlayCoords(int oU, int oV) {
                // ignored, the fuck is this for?
                // figured it out, this is for entities when you hurt them, or creepers exploding
                return this;
            }
            
            @Override
            public VertexConsumer uv2(int u2, int v2) {
                currentVertex.lightmapU = u2;
                currentVertex.lightmapV = v2;
                return this;
            }
            
            @Override
            public VertexConsumer normal(float nx, float ny, float nz) {
                currentVertex.normalX = nx;
                currentVertex.normalY = ny;
                currentVertex.normalZ = nz;
                return this;
            }
            
            @Override
            public void endVertex() {
                vertices.add(currentVertex);
                
                currentVertex = new Vertex(currentVertex);
                if (defaultColorSet) {
                    currentVertex.rgba = drgba;
                }
            }
            
            @Override
            public void defaultColor(int r, int g, int b, int a) {
                // assumes each value is <= 255, if i need to put an "& 0xFF" on these, im going to find you
                // as a side note, that means you can pass in an RGBA value in a
                drgba = (a << 24) | (b << 16) | (g << 8) | r;
                defaultColorSet = true;
            }
            
            @Override
            public void unsetDefaultColor() {
                defaultColorSet = false;
            }
            
        }
        
        private final PoseStack poseStack = new PoseStack();
        private final HashMap<RenderType, BufferBuilder> buffers = new HashMap<>();
        
        Builder() {
        }
        
        @Override
        public MultiBufferSource bufferSource() {
            return this;
        }
        
        @Override
        public PoseStack matrixStack() {
            return poseStack;
        }
        
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            if (!(renderType instanceof RenderType.CompositeRenderType)) {
                throw new IllegalArgumentException("RenderType must be composite type");
            }
            return SodiumBullshit.wrap(buffers.computeIfAbsent(renderType, e -> new BufferBuilder()));
        }
        
        int bytesRequired() {
            int totalVertices = 0;
            for (var entry : buffers.entrySet()) {
                var renderType = entry.getKey();
                var bufferBuilder = entry.getValue();
                int vertexCount = bufferBuilder.vertices.size() - bufferBuilder.vertices.size() % renderType.mode().primitiveLength;
                if (vertexCount == 0) {
                    continue;
                }
                totalVertices += vertexCount;
            }
            return totalVertices * VERTEX_BYTE_SIZE;
        }
        
        Object2LongArrayMap<RenderType> build(PointerWrapper masterBuffer) {
            Object2LongArrayMap<RenderType> drawInfoMap = new Object2LongArrayMap<>();
            
            int currentByteIndex = 0;
            for (var entry : buffers.entrySet()) {
                RenderType renderType = entry.getKey();
                BufferBuilder bufferBuilder = entry.getValue();
                int vertexCount = bufferBuilder.vertices.size() - bufferBuilder.vertices.size() % renderType.mode().primitiveLength;
                if (vertexCount == 0) {
                    continue;
                }
                final long offsetAndSize = (long) (currentByteIndex / VERTEX_BYTE_SIZE) << 32 | (long) vertexCount;
                final var typeBuffer = masterBuffer.slice(currentByteIndex, vertexCount * VERTEX_BYTE_SIZE);
                currentByteIndex += vertexCount * VERTEX_BYTE_SIZE;
                
                Vector3f tempNormalVec = new Vector3f();
                
                int vertexIndex = 0;
                for (Vertex vertex : bufferBuilder.vertices) {
                    if (vertexIndex > vertexCount) {
                        break;
                    }
                    final var vertexBuffer = typeBuffer.slice((long) vertexIndex * VERTEX_BYTE_SIZE, VERTEX_BYTE_SIZE);
                    
                    vertexBuffer.putFloatIdx(0, vertex.x); // 4
                    vertexBuffer.putFloatIdx(1, vertex.y); // 8
                    vertexBuffer.putFloatIdx(2, vertex.z); // 12
                    vertexBuffer.putIntIdx(3, vertex.rgba); // 16
                    vertexBuffer.putFloatIdx(4, vertex.texU); // 20
                    vertexBuffer.putFloatIdx(5, vertex.texV); // 24
                    
                    tempNormalVec.set(vertex.normalX, vertex.normalY, vertex.normalZ);
                    tempNormalVec.normalize(Short.MAX_VALUE);
                    
                    vertexBuffer.putShortIdx(12, (short) tempNormalVec.x);
                    vertexBuffer.putShortIdx(13, (short) tempNormalVec.y);
                    vertexBuffer.putShortIdx(14, (short) tempNormalVec.z);
                    vertexIndex++;
                }
                drawInfoMap.put(renderType, offsetAndSize);
            }
            return drawInfoMap;
        }
        
        private static int packInt(int value, int position, int width) {
            int signBitMask = 1 << (width - 1);
            int bitMask = signBitMask - 1;
            int returnVal = value & bitMask;
            returnVal |= (value >> (32 - width)) & signBitMask;
            return returnVal << position;
        }
        
        private static int extractInt(int packed, int pos, int width) {
            packed >>= pos;
            int signBitMask = 1 << (width - 1);
            int bitMask = signBitMask - 1;
            int val = ~bitMask * (((signBitMask & packed) != 0) ? 1 : 0);
            val |= packed & bitMask;
            return val;
        }
    }
    
    public static class Manager {
        public static class TrackedMesh {
            public record Component(int vertexOffset, int vertexCount) {
            }
            
            public final WeakReference<InternalMesh> meshRef;
            private final Buffer vertexBuffer;
            @Nullable
            private Buffer.Allocation vertexAllocation;
            private final Object2ObjectArrayMap<RenderType, Component> drawInfo = new Object2ObjectArrayMap<>();
            private final ObjectArrayList<Consumer<TrackedMesh>> buildCallbacks = new ObjectArrayList<>();
            
            public TrackedMesh(WeakReference<InternalMesh> meshRef, Buffer vertexBuffer) {
                this.meshRef = meshRef;
                this.vertexBuffer = vertexBuffer;
            }
            
            void rebuild() {
                QuartzCore.INSTANCE.waitIdle();
                var mesh = meshRef.get();
                if (mesh == null) {
                    return;
                }
                Object2LongArrayMap<RenderType> rawDrawInfo;
                drawInfo.clear();
                rawDrawInfo = mesh.build(this::allocBuffer);
                assert vertexAllocation != null;
                vertexAllocation.dirty();
                for (var renderTypeEntry : rawDrawInfo.object2LongEntrySet()) {
                    var renderType = renderTypeEntry.getKey();
                    var drawLong = renderTypeEntry.getLongValue();
                    var drawComponent = new Component((int) (drawLong >> 32) + vertexAllocation.offset() / VERTEX_BYTE_SIZE, (int) drawLong);
                    drawInfo.put(renderType, drawComponent);
                }
                for (int i = 0; i < buildCallbacks.size(); i++) {
                    buildCallbacks.get(i).accept(this);
                }
            }
            
            private PointerWrapper allocBuffer(int size) {
                if (vertexAllocation != null) {
                    vertexAllocation = vertexBuffer.realloc(vertexAllocation, size, VERTEX_BYTE_SIZE, false);
                } else {
                    vertexAllocation = vertexBuffer.alloc(size, VERTEX_BYTE_SIZE);
                }
                return vertexAllocation.address();
            }
            
            public Collection<RenderType> usedRenderTypes() {
                return drawInfo.keySet();
            }
            
            @Nullable
            public Component renderTypeComponent(RenderType renderType) {
                return drawInfo.get(renderType);
            }
            
            public void addBuildCallback(Consumer<TrackedMesh> consumer) {
                buildCallbacks.add(consumer);
            }
            
            public void removeBuildCallback(Consumer<TrackedMesh> consumer) {
                buildCallbacks.remove(consumer);
            }
        }
        
        private final ObjectArrayList<TrackedMesh> trackedMeshes = new ObjectArrayList<TrackedMesh>();
        public final Buffer vertexBuffer;
        
        public Manager(Buffer vertexBuffer) {
            this.vertexBuffer = vertexBuffer;
        }
        
        public InternalMesh createMesh(Consumer<Mesh.Builder> buildFunc) {
            final var staticMesh = new InternalMesh(buildFunc);
            final var trackedMesh = new TrackedMesh(new WeakReference<>(staticMesh), vertexBuffer);
            synchronized (trackedMeshes) {
                trackedMeshes.add(trackedMesh);
            }
            QuartzCore.CLEANER.register(staticMesh, () -> {
                synchronized (trackedMeshes) {
                    trackedMeshes.remove(trackedMesh);
                }
            });
            return staticMesh;
        }
        
        @Nullable
        public TrackedMesh getMeshInfo(Mesh mesh) {
            for (int i = 0; i < trackedMeshes.size(); i++) {
                var trackedMesh = trackedMeshes.get(i);
                if (trackedMesh.meshRef.get() == mesh) {
                    return trackedMesh;
                }
            }
            return null;
        }
        
        public void buildAllMeshes() {
            for (TrackedMesh value : trackedMeshes) {
                buildTrackedMesh(value);
            }
        }
        
        public void buildMesh(Mesh mesh) {
            var trackedMesh = getMeshInfo(mesh);
            if (trackedMesh != null) {
                buildTrackedMesh(trackedMesh);
            }
        }
        
        private void buildTrackedMesh(TrackedMesh trackedMesh) {
            QuartzCore.INSTANCE.waitIdle();
            trackedMesh.rebuild();
        }
        
    }
    
}

