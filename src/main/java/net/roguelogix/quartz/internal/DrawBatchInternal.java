package net.roguelogix.quartz.internal;

import net.minecraft.client.renderer.RenderType;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.internal.common.DrawInfo;

@NonnullDefault
public interface DrawBatchInternal extends DrawBatch {
    
    void updateAndCull(@SuppressWarnings("SameParameterValue") DrawInfo drawInfo);
    
    void drawFeedback(RenderType renderType, boolean shadowsEnabled);
}
