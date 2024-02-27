package net.roguelogix.quartz.internal;

import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.Event;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.phosphophyllite.util.Pair;
import net.roguelogix.quartz.internal.util.PointerWrapper;

import java.util.List;
import java.util.Map;

@NonnullDefault
public class QuartzInternalEvent extends Event {
    public static class CreateTests extends QuartzInternalEvent {
    }
    
    public static class TestingStatus extends QuartzInternalEvent {
        public final boolean running;
        
        public TestingStatus(boolean running) {
            this.running = running;
        }
    }
    
    public static class FeedbackCollected extends QuartzInternalEvent {
        
        public final List<RenderType> renderTypes;
        // note: readOnly pointers
        public final Map<RenderType, Pair<PointerWrapper, Integer>> feedbackBuffers;
        
        public FeedbackCollected(List<RenderType> renderTypes, Map<RenderType, Pair<PointerWrapper, Integer>> feedbackBuffers) {
            this.renderTypes = renderTypes;
            this.feedbackBuffers = feedbackBuffers;
        }
    }
}
