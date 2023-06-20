package net.roguelogix.quartz.internal.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.AABB;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.internal.QuartzCore;

import java.lang.ref.WeakReference;

@NonnullDefault
public class WorldEngine {
    
    private final Long2ObjectOpenHashMap<WeakReference<DrawBatch>> sectionDrawBatchers = new Long2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<AABB, WeakReference<DrawBatch>> customDrawBatchers = new Object2ObjectOpenHashMap<>();
    
    public synchronized DrawBatch getBatcherForAABB(final AABB aabb) {
        return getBatcherForAABB(aabb, false);
    }
    
    public synchronized DrawBatch getBatcherForAABB(final AABB aabb, boolean allowReuse) {
        if ((aabb.minX() >> 4) == (aabb.maxX() >> 4) &&
                    (aabb.minY() >> 4) == (aabb.maxY() >> 4) &&
                    (aabb.minZ() >> 4) == (aabb.maxZ() >> 4)) {
            // AABB is conained entirely  in a single section, just return the section's batcher
            return getBatcherForSection(SectionPos.asLong(aabb.minX() >> 4, aabb.minY() >> 4, aabb.minZ() >> 4));
        }
        final var weakRef = customDrawBatchers.get(aabb);
        DrawBatch drawBatch = null;
        if (weakRef != null) {
            drawBatch = weakRef.get();
        }
        if (drawBatch == null) {
            if (allowReuse) {
                var iter = customDrawBatchers.object2ObjectEntrySet().fastIterator();
                while (iter.hasNext()) {
                    var entry = iter.next();
                    var AABB = entry.getKey();
                    if (AABB.contains(aabb)) {
                        drawBatch = entry.getValue().get();
                        if (drawBatch != null) {
                            return drawBatch;
                        }
                    }
                }
            }
            final var newDrawBatch = QuartzCore.INSTANCE.createDrawBatch();
            newDrawBatch.setCullAABB(aabb);
            QuartzCore.CLEANER.register(newDrawBatch, () -> {
                synchronized (this) {
                    customDrawBatchers.remove(aabb);
                }
            });
            customDrawBatchers.put(aabb, new WeakReference<>(newDrawBatch));
            return newDrawBatch;
        }
        return drawBatch;
    }
    
    public synchronized DrawBatch getBatcherForSection(final long sectionPos) {
        final var weakRef = sectionDrawBatchers.get(sectionPos);
        DrawBatch drawBatch = null;
        if (weakRef != null) {
            drawBatch = weakRef.get();
        }
        if (drawBatch == null) {
            final var newDrawBatch = QuartzCore.INSTANCE.createDrawBatch();
            newDrawBatch.setCullAABB(new AABB(0, 0, 0, 15, 15, 15).translate(SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos)), SectionPos.sectionToBlockCoord(SectionPos.y(sectionPos)), SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos))));
            QuartzCore.CLEANER.register(newDrawBatch, () -> {
                synchronized (this){
                    sectionDrawBatchers.remove(sectionPos);
                }
            });
            sectionDrawBatchers.put(sectionPos, new WeakReference<>(newDrawBatch));
            return newDrawBatch;
        }
        return drawBatch;
    }
}
