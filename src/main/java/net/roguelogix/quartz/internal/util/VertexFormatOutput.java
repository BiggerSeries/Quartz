package net.roguelogix.quartz.internal.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.roguelogix.quartz.internal.gl.GLTransformFeedbackProgram;
import org.codehaus.plexus.util.dag.Vertex;

import java.util.Collections;
import java.util.Map;

public record VertexFormatOutput(VertexFormat format, String[] varyings, int vertexSize) {
    
    private static final Map<VertexFormatElement, String> elementVaryingNames;
    private static final Object2ObjectOpenHashMap<VertexFormat, VertexFormatOutput> outputs = new Object2ObjectOpenHashMap<>();
    
    static {
        final var map = new Object2ObjectArrayMap<VertexFormatElement, String>();
        map.put(DefaultVertexFormat.ELEMENT_POSITION, "positionOutput");
        map.put(DefaultVertexFormat.ELEMENT_NORMAL, "normalOutput");
        map.put(DefaultVertexFormat.ELEMENT_COLOR, "colorOutput");
        map.put(DefaultVertexFormat.ELEMENT_UV0, "textureOutput");
        map.put(DefaultVertexFormat.ELEMENT_UV1, "overlayOutput");
        map.put(DefaultVertexFormat.ELEMENT_UV2, "lightmapOutput");
        elementVaryingNames = Collections.unmodifiableMap(map);
    }
    
    public static VertexFormatOutput of(VertexFormat format) {
        return outputs.computeIfAbsent(format, (VertexFormat e) -> new VertexFormatOutput(e));
    }
    
    private VertexFormatOutput(VertexFormat format) {
        this(format, generateVaryings(format), format.getVertexSize());
    }
    
    private static int offsetOf(VertexFormat format, VertexFormatElement element) {
        final var elementIndex = format.getElements().indexOf(element);
        if (elementIndex == -1) {
            return 0;
        }
        return format.getOffset(elementIndex);
    }
    
    private static String[] generateVaryings(VertexFormat format) {
        final var elements = format.getElements();
        final var list = new ObjectArrayList<String>();
        for (final var element : elements) {
            if (element == DefaultVertexFormat.ELEMENT_PADDING) {
                continue;
            }
            var elementName = elementVaryingNames.get(element);
            if (elementName == null) {
                throw new IllegalStateException("Unknown vertex format element");
            }
            list.add(elementName);
        }
        return list.toArray(new String[]{});
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof VertexFormatOutput other && format == other.format;
    }
}
