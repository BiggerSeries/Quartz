package net.roguelogix.quartz;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

public interface Mesh {
    interface Builder {
        MultiBufferSource bufferSource();
        
        PoseStack matrixStack();
    }
    
    void rebuild();
}
