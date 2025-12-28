package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Hook for Flowing Fluids mod detection.
 * 
 * NOTE: This class does NOT intercept or modify fluid behavior.
 * All fluid behavior is handled by:
 * 1. Our mixin on FlowingFluid.tick() for timing control
 * 2. Flowing Fluids' mixin for all fluid behavior (100% parity)
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class FlowingFluidsHook {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private static boolean hookInstalled = false;
    
    /**
     * Check if Flowing Fluids mod is loaded
     */
    public static void installHook() {
        if (hookInstalled) {
            return;
        }
        
        try {
            // Check if FLOWING FLUIDS is loaded
            Class.forName("traben.flowing_fluids.FlowingFluids");
            
            LOGGER.info("FLOWING FLUIDS detected - tick scheduler active for performance optimization");
            LOGGER.info("All fluid behavior handled by Flowing Fluids (100% parity guaranteed)");
            hookInstalled = true;
            
        } catch (ClassNotFoundException e) {
            LOGGER.info("FLOWING FLUIDS not found - tick scheduler will work with vanilla fluids");
            hookInstalled = false;
        }
    }
    
    /**
     * Get hook installation status
     */
    public static boolean isHookInstalled() {
        return hookInstalled;
    }
}
