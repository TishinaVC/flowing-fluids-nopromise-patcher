package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartingEvent;

/**
 * Minimal Hook for Flowing Fluids mod detection
 * 
 * NOTE: This is a lightweight hook that only detects Flowing Fluids presence.
 * All optimization is handled by FlowingFluidsFixes via Forge events.
 */
@EventBusSubscriber(modid = "flowingfluidsfixes")
public class FlowingFluidsHook {
    
    private static boolean flowingFluidsDetected = false;
    
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        try {
            Class.forName("traben.flowing_fluids.FlowingFluids");
            flowingFluidsDetected = true;
            System.out.println("[FlowingFluidsFixes] Flowing Fluids detected - optimization active");
        } catch (ClassNotFoundException e) {
            flowingFluidsDetected = false;
            System.out.println("[FlowingFluidsFixes] Flowing Fluids not found - vanilla fluid optimization");
        }
    }
    
    public static boolean isFlowingFluidsDetected() {
        return flowingFluidsDetected;
    }
}
