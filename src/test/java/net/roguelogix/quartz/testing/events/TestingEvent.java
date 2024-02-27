package net.roguelogix.quartz.testing.events;

public class TestingEvent {
    public static class Started extends TestingEvent {
    }
    
    public static class Stopped extends TestingEvent {
    }
    
    public static class FrameStart extends TestingEvent {
    }
    
    public static class FrameEnd extends TestingEvent {
    }
}
