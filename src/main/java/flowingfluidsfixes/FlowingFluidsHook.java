package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartingEvent;

/**
 * Minimal Hook for Flowing Fluids mod detection
 * 
 * NOTE: This is a lightweight hook that delegates to FlowingFluidsFixes.
 * All optimization is handled by FlowingFluidsFixes via Forge events.
 */
@EventBusSubscriber(modid = "flowingfluidsfixes")
public class FlowingFluidsHook {
    
    /**
     * STUB: Server starting event - delegates to main class
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // All detection and optimization handled by FlowingFluidsFixes
        // This is just a stub for compatibility
    }
    
    /**
     * STUB: Check if Flowing Fluids is detected - delegates to main class
     */
    public static boolean isFlowingFluidsDetected() {
        return FlowingFluidsFixes.isFlowingFluidsDetected();
    }
}
