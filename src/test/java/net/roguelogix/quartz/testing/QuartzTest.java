package net.roguelogix.quartz.testing;

import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.internal.QuartzInternalEvent;

import static net.roguelogix.quartz.testing.tests.Util.sendChatMessage;

@NonnullDefault
public abstract class QuartzTest {
    
    public final String id;
    
    protected QuartzTest(String id) {
        this.id = id.intern();
    }
    
    public abstract void setup();
    
    public abstract void cleanup();
    
    public abstract boolean running();
    
    public abstract void frameStart();
    
    public abstract void frameEnd();
    
    public abstract void feedbackCollected(QuartzInternalEvent.FeedbackCollected event);
    
    private boolean passed = true;
    
    public final boolean passed(){
        return passed;
    }
    
    public final void reset() {
        passed = true;
    }
    
    protected final void message(String message) {
        sendChatMessage(message);
    }
    
    protected final void fail(String message) {
        sendChatMessage(message);
        passed = false;
    }
}
