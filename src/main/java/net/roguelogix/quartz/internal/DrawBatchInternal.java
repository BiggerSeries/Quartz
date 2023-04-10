package net.roguelogix.quartz.internal;

import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.internal.common.DrawInfo;

public interface DrawBatchInternal extends DrawBatch {
    
    void updateAndCull(@SuppressWarnings("SameParameterValue") DrawInfo drawInfo);
    
    void drawOpaque();
    
    void drawCutout();
}
