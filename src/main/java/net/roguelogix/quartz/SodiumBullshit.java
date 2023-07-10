package net.roguelogix.quartz;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter;
import me.jellysquid.mods.sodium.client.render.vertex.VertexFormatDescription;
import me.jellysquid.mods.sodium.client.render.vertex.transform.CommonVertexElement;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.quartz.internal.util.PointerWrapper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.NonnullDefault;

import static net.roguelogix.quartz.internal.MagicNumbers.FLOAT_BYTE_SIZE;
import static net.roguelogix.quartz.internal.MagicNumbers.SHORT_BYTE_SIZE;

@NonnullDefault
public class SodiumBullshit {
    private static boolean IS_SODIUM_LOADED = false;
    
    public static VertexConsumer wrap(VertexConsumer consumer) {
        if (!IS_SODIUM_LOADED) {
            return consumer;
        }
        
        return Detector.wrap(consumer);
    }
    
    public static final class Detector {
        @OnModLoad(required = false)
        public static void onModLoad() {
            VertexBufferWriter.of(null);
            IS_SODIUM_LOADED = true;
        }
        
        public static VertexConsumer wrap(VertexConsumer consumer) {
            return new Wrapper(consumer);
        }
        
        private record Wrapper(VertexConsumer wrapped) implements VertexBufferWriter, VertexConsumer {
            
            @Override
            public VertexConsumer vertex(double pX, double pY, double pZ) {
                return wrapped.vertex(pX, pY, pZ);
            }
            
            @Override
            public VertexConsumer color(int pRed, int pGreen, int pBlue, int pAlpha) {
                return wrapped.color(pRed, pGreen, pBlue, pAlpha);
            }
            
            @Override
            public VertexConsumer uv(float pU, float pV) {
                return wrapped.uv(pU, pV);
            }
            
            @Override
            public VertexConsumer overlayCoords(int pU, int pV) {
                return wrapped.overlayCoords(pU, pV);
            }
            
            @Override
            public VertexConsumer uv2(int pU, int pV) {
                return wrapped.uv2(pU, pV);
            }
            
            @Override
            public VertexConsumer normal(float pX, float pY, float pZ) {
                return wrapped.normal(pX, pY, pZ);
            }
            
            @Override
            public void endVertex() {
                wrapped.endVertex();
            }
            
            @Override
            public void defaultColor(int pDefaultR, int pDefaultG, int pDefaultB, int pDefaultA) {
                wrapped.defaultColor(pDefaultR, pDefaultG, pDefaultB, pDefaultA);
            }
            
            @Override
            public void unsetDefaultColor() {
                wrapped.unsetDefaultColor();
            }
            
            @Override
            public void push(MemoryStack memoryStack, long pointer, int count, VertexFormatDescription vertexFormatDescription) {
                final var basePointer = new PointerWrapper(pointer, count * vertexFormatDescription.stride);
                final var elements = vertexFormatDescription.getElements();
                final var commonElements = new ReferenceArrayList<CommonVertexElement>();
                final var offsets = vertexFormatDescription.getOffsets();
                for (int i = 0; i < elements.size(); i++) {
                    final var element = CommonVertexElement.getCommonType(elements.get(i));
                    if (element == null && elements.get(i) != DefaultVertexFormat.ELEMENT_PADDING) {
                        throw new IllegalArgumentException("Unknown vertex format element");
                    }
                    commonElements.add(element);
                }
                for (int i = 0; i < count; i++) {
                    final var vertexPointer = basePointer.slice(i * vertexFormatDescription.stride, vertexFormatDescription.stride);
                    for (int j = 0; j < commonElements.size(); j++) {
                        final var element = commonElements.get(j);
                        if (element == null) {
                            continue;
                        }
                        var offset = offsets.getInt(j);
                        switch (element) {
                            case POSITION -> {
                                final var x = vertexPointer.getFloat(offset);
                                offset += FLOAT_BYTE_SIZE;
                                final var y = vertexPointer.getFloat(offset);
                                offset += FLOAT_BYTE_SIZE;
                                final var z = vertexPointer.getFloat(offset);
                                wrapped.vertex(x, y, z);
                            }
                            case COLOR -> wrapped.color(vertexPointer.getInt(offset));
                            case TEXTURE -> {
                                final var u = vertexPointer.getFloat(offset);
                                offset += FLOAT_BYTE_SIZE;
                                final var v = vertexPointer.getFloat(offset);
                                wrapped.uv(u, v);
                            }
                            case OVERLAY -> {
                                final var u = vertexPointer.getShort(offset);
                                offset += SHORT_BYTE_SIZE;
                                final var v = vertexPointer.getShort(offset);
                                wrapped.overlayCoords(u, v);
                            }
                            case LIGHT -> {
                                final var u = vertexPointer.getShort(offset);
                                offset += SHORT_BYTE_SIZE;
                                final var v = vertexPointer.getShort(offset);
                                wrapped.uv2(u, v);
                            }
                            case NORMAL -> {
                                final var x = vertexPointer.getByte(offset++);
                                final var y = vertexPointer.getByte(offset++);
                                final var z = vertexPointer.getByte(offset);
                                wrapped.normal(x, y, z);
                            }
                        }
                    }
                    wrapped.endVertex();
                }
            }
        }
    }
}
