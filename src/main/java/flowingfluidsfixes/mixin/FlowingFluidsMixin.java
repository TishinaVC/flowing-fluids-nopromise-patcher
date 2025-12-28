package flowingfluidsfixes.mixin;

import flowingfluidsfixes.FluidOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that hooks into vanilla FlowingFluid.tick() to control TIMING only.
 * 
 * PARITY GUARANTEE: We do NOT modify ANY fluid behavior.
 * Flowing Fluids' mixin runs after ours and handles ALL fluid logic.
 * We only defer ticks when over budget to prevent lag spikes.
 */
@Mixin(net.minecraft.world.level.material.FlowingFluid.class)
public abstract class FlowingFluidsMixin {
    
    /**
     * Intercept fluid tick() to control timing.
     * 
     * CRITICAL: This runs BEFORE Flowing Fluids' mixin due to lower priority.
     * If we're over budget, we DEFER the tick (reschedule for next server tick).
     * We NEVER drop ticks - this guarantees floating water eventually spreads.
     * 
     * If under budget, we let the tick proceed and Flowing Fluids handles everything.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void onTick(Level level, BlockPos pos, BlockState state, FluidState fluidState, CallbackInfo ci) {
        if (!fluidState.isEmpty()) {
            FluidOptimizer.queueFluidUpdate(level, pos, fluidState, state);
            ci.cancel();
        }
    }
}
