package net.roguelogix.quartz.internal;

import net.coderbot.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.roguelogix.phosphophyllite.registry.ClientOnly;
import net.roguelogix.phosphophyllite.registry.OnModLoad;

public final class IrisDetection {
    
    // split like this to handle as a soft dependeceny
    private static boolean IS_IRIS_LOADED = false;
    
    public static boolean areShadersActive() {
        if (IS_IRIS_LOADED) {
            return Detector.areShadersActive();
        }
        return false;
    }
    
    public static void bindIrisFramebuffer() {
        if (areShadersActive()) {
            Detector.bindIrisFramebuffer();
        }
    }
    
    public static boolean isRenderingShadows() {
        if (areShadersActive()) {
            return Detector.isRenderingShadows();
        }
        return false;
    }
    
    @ClientOnly
    private static final class Detector {
        
        private static final IrisApi irisApi = IrisApi.getInstance();
        
        @OnModLoad(required = false)
        public static void onModLoad() {
            IS_IRIS_LOADED = true;
        }
        
        public static boolean areShadersActive() {
            return irisApi.isShaderPackInUse();
        }
        
        public static void bindIrisFramebuffer() {
            Iris.getPipelineManager().getPipeline().ifPresent(pipe -> pipe.getSodiumTerrainPipeline().getTerrainSolidFramebuffer().bind());
        }
        
        public static boolean isRenderingShadows() {
            return irisApi.isRenderingShadowPass();
        }
    }
}
