package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * ULTRA-OPTIMIZED Flowing Fluids event hooking system.
 * CONSOLIDATED: All functionality moved to main FlowingFluidsFixes class
 * 
 * This file now contains only stub methods for compatibility.
 * All actual processing is done in FlowingFluidsFixes to avoid duplicate overhead.
 */
@EventBusSubscriber(modid = "flowingfluidsfixes")
public class FlowingFluidsEventHook {
    
    /**
     * STUB: Initialize Flowing Fluids hooks - moved to main class
     */
    public static void installHooks() {
        // All functionality moved to FlowingFluidsFixes for consolidation
        System.out.println("[FlowingFluidsEventHook] Hooks consolidated into main class");
    }
    
    /**
     * STUB: Check if hooks are active - moved to main class
     */
    public static boolean areHooksActive() {
        return FlowingFluidsFixes.isFlowingFluidsDetected();
    }
    
    /**
     * STUB: Get hook statistics - moved to main class
     */
    public static void logHookStatistics() {
        // Statistics now handled in main class
    }
    
    /**
     * STUB: Adjust Flowing Fluids configuration - moved to main class
     */
    public static void adjustFlowingFluidsConfig() {
        // Configuration now handled in main class
    }
    
    /**
     * STUB: Get intercepted events count - moved to main class
     */
    public static int getInterceptedEvents() {
        // Statistics now handled in main class
        return 0;
    }
    
    /**
     * STUB: Get skipped events count - moved to main class
     */
    public static int getSkippedEvents() {
        // Statistics now handled in main class
        return 0;
    }
    
    /**
     * STUB: Reset statistics - moved to main class
     */
    public static void resetStatistics() {
        // Statistics now handled in main class
        FlowingFluidsFixes.resetStats();
    }
    
    /**
     * STUB: Get hook status - moved to main class
     */
    public static String getHookStatus() {
        return "CONSOLIDATED - All functionality moved to main class";
    }
}
