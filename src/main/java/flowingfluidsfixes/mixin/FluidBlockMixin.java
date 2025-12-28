package flowingfluidsfixes.mixin;

import flowingfluidsfixes.FluidOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for LiquidBlock to monitor fluid events and optimize fluid updates.
 * 
 * NOTE: This mixin integrates with FluidOptimizer for batched updates and uses FlowingFluidsIntegration for parity with original behavior.
 */
@Mixin(net.minecraft.world.level.block.LiquidBlock.class)
public class FluidBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) {
            FluidOptimizer.queueFluidUpdate(level, pos, fluidState, state);
            ci.cancel();
        }
    }
}
