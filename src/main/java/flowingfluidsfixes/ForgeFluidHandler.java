package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Forge event handler for monitoring fluid-related events.
 * 
 * NOTE: This handler does NOT process fluid behavior - that's handled by:
 * 1. Our mixin on FlowingFluid.tick() for timing control
 * 2. Flowing Fluids' mixin for all fluid behavior
 * 
 * This handler only monitors fluid events for statistics.
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class ForgeFluidHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    
    /**
     * Monitor block placement events (when players place fluid blocks)
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof Level level) || level.isClientSide()) {
            return;
        }
        BlockState state = event.getState();
        
        // Just monitor - don't process
        if (state.getBlock() instanceof LiquidBlock) {
            LOGGER.debug("Forge: Fluid block placed at {}", event.getPos());
            FlowingFluidsIntegration.recordUpdate();
        }
    }
    
    /**
     * Monitor block break events (when fluid blocks are broken)
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof Level level) || level.isClientSide()) {
            return;
        }
        BlockState state = event.getState();
        
        // Just monitor - don't process
        if (state.getBlock() instanceof LiquidBlock) {
            LOGGER.debug("Forge: Fluid block broken at {}", event.getPos());
            FlowingFluidsIntegration.recordUpdate();
        }
    }
}
