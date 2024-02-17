package net.roguelogix.quartz;

import net.neoforged.bus.api.Event;

public abstract class QuartzEvent extends Event {
    public QuartzEvent() {
    }
    
    public static class Startup extends QuartzEvent {
    }
    
    public static class Shutdown extends QuartzEvent {
    }
    
    public static class ResourcesLoaded extends QuartzEvent {
    }
    
    public static class ResourcesReloaded extends ResourcesLoaded {
    }
    
    public static class FrameStart extends QuartzEvent {
        public FrameStart() {
        }
    }
}
