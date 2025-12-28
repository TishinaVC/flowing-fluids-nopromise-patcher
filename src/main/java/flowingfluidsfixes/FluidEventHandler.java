package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class FluidEventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Handle fluid updates if needed
        }
    }

    public static void handleFluidUpdate(Level level, BlockPos pos, FluidState state, BlockState blockState) {
        FluidOptimizer.queueFluidUpdate(level, pos, state, blockState);
    }
}
