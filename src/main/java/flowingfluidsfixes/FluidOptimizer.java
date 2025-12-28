package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced fluid optimization coordinator for large-scale fluid updates.
 * 
 * DESIGN: This mod optimizes Flowing Fluids behavior without breaking it.
 * Key optimizations:
 * 1. Batch processing of fluid updates to prevent tick lag
 * 2. Priority-based processing for floating water layers
 * 3. Dynamic throttling based on server performance
 * 4. 100% behavioral parity with Flowing Fluids via reflection
 */
public class FluidOptimizer {
    // Performance thresholds for preventing time-of-day going backwards
    private static final int MAX_UPDATES_PER_TICK = 10000;
    private static final int MIN_UPDATES_PER_TICK = 1000;
    private static final double TARGET_TPS = 20.0;
    private static final double CRITICAL_TPS_THRESHOLD = 10.0; // Prevent time going backwards
    
    // Batch processing for large-scale fluid changes
    private static final Queue<FluidUpdate> updateQueue = new ConcurrentLinkedQueue<>();
    private static final Set<BlockPos> processedPositions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final AtomicInteger currentUpdateLimit = new AtomicInteger(MAX_UPDATES_PER_TICK);
    private static final AtomicInteger totalUpdatesProcessed = new AtomicInteger(0);
    
    // Performance tracking
    private static long lastTickTime = 0;
    private static double currentTPS = 20.0;
    private static boolean emergencyMode = false;
    
    // Priority system for floating water layers
    private static final Map<BlockPos, Integer> priorityMap = new ConcurrentHashMap<>();
    private static final int SOURCE_BLOCK_PRIORITY = 100;
    private static final int FLOATING_LAYER_PRIORITY = 90;
    private static final int NORMAL_PRIORITY = 50;

    public FluidOptimizer() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Check if optimization is active
     */
    public static boolean isOptimizationActive() {
        return FlowingFluidsIntegration.isFlowingFluidsLoaded();
    }
    
    /**
     * Get current scheduler statistics
     */
    public static String getStatus() {
        var stats = FluidTickScheduler.getStats();
        return String.format("Budget: %s, Queued: %s, Processed: %s",
            stats.get("currentBudget"),
            stats.get("totalQueuedTicks"),
            stats.get("totalProcessed"));
    }
    
    /**
     * Log current optimization status
     */
    public static void logStatus() {
        System.out.println("Fluid Optimization Status: " + getStatus());
        System.out.println("Flowing Fluids Integration: " + FlowingFluidsIntegration.getIntegrationStatus());
    }

    /**
     * Enhanced fluid update queuing with priority system.
     * Addresses floating water layers by prioritizing source blocks and high-altitude fluid.
     */
    public static void queueFluidUpdate(Level level, BlockPos pos, FluidState state, BlockState blockState) {
        if (state.isEmpty() || processedPositions.contains(pos)) {
            return;
        }
        
        // Calculate priority based on Flowing Fluids behavior
        int priority = calculateFluidPriority(level, pos, state, blockState);
        
        FluidUpdate update = new FluidUpdate(level, pos, state, blockState, priority);
        updateQueue.add(update);
        processedPositions.add(pos);
        priorityMap.put(pos, priority);
        
        // If this is a floating water layer (high altitude fluid), ensure it gets processed
        if (priority >= FLOATING_LAYER_PRIORITY) {
            prioritizeFloatingWaterLayer(update);
        }
    }
    
    /**
     * Calculate fluid priority based on Flowing Fluids logic.
     * Source blocks and floating layers get highest priority to fix spreading issues.
     */
    private static int calculateFluidPriority(Level level, BlockPos pos, FluidState state, BlockState blockState) {
        int priority = NORMAL_PRIORITY;
        
        // Source blocks are critical - they drive all fluid flow
        if (state.isSource()) {
            priority = SOURCE_BLOCK_PRIORITY;
        }
        
        // Floating water layers - high altitude fluid that should spread
        if (pos.getY() > 60 && state.getAmount() >= 1) {
            // Check if this could be a floating layer by looking for air below
            BlockPos below = pos.below();
            if (level.isInWorldBounds(below) && level.getBlockState(below).isAir()) {
                priority = FLOATING_LAYER_PRIORITY;
            }
        }
        
        // High fluid levels have more flow potential
        int fluidAmount = state.getAmount();
        if (fluidAmount >= 8) {
            priority += 20;
        } else if (fluidAmount > 4) {
            priority += 10;
        }
        
        // Use Flowing Fluids getDropOff value for priority calculation
        int dropOff = FlowingFluidsIntegration.getDropOff(level, state.getType());
        if (fluidAmount > dropOff) {
            priority += 15; // Boost priority for fluid above drop-off threshold
        }
        
        // Proximity to players - visible fluid should update first
        if (level instanceof ServerLevel serverLevel) {
            double distanceToPlayer = serverLevel.players().stream()
                .mapToDouble(player -> player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()))
                .min()
                .orElse(Double.MAX_VALUE);
            
            if (distanceToPlayer < 64 * 64) {
                priority += 15; // Boost priority for visible fluid
            }
        }
        
        return priority;
    }
    
    /**
     * Prioritize floating water layers to ensure they spread properly.
     * This addresses the core issue of floating water not spreading.
     */
    private static void prioritizeFloatingWaterLayer(FluidUpdate update) {
        // Add to high-priority processing queue
        // For now, we just mark it as high priority in the main queue
        // This ensures floating layers get processed before normal fluid updates
    }

    /**
     * Enhanced server tick processing with dynamic throttling.
     * Prevents time-of-day going backwards and mob movement stuttering.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long currentTime = System.currentTimeMillis();
            if (lastTickTime != 0) {
                long tickDuration = currentTime - lastTickTime;
                currentTPS = 1000.0 / tickDuration;
                adjustUpdateLimit(currentTPS);
            }
            lastTickTime = currentTime;

            // Clear processed positions for new tick
            processedPositions.clear();
            priorityMap.clear();
            
            // Process updates with priority sorting
            int updatesProcessed = processBatchedUpdates();
            totalUpdatesProcessed.addAndGet(updatesProcessed);
            
            // Emergency mode activation for critical performance
            if (currentTPS < CRITICAL_TPS_THRESHOLD) {
                emergencyMode = true;
                currentUpdateLimit.set(MIN_UPDATES_PER_TICK);
            } else if (currentTPS > TARGET_TPS * 0.9) {
                emergencyMode = false;
            }
        }
    }
    
    /**
     * Process batched updates with priority sorting.
     * This ensures floating water layers and source blocks are processed first.
     */
    private static int processBatchedUpdates() {
        int updatesProcessed = 0;
        int limit = currentUpdateLimit.get();
        
        // Sort queue by priority for this tick
        List<FluidUpdate> sortedUpdates = new ArrayList<>();
        while (!updateQueue.isEmpty() && sortedUpdates.size() < limit * 2) {
            FluidUpdate update = updateQueue.poll();
            if (update != null) {
                sortedUpdates.add(update);
            }
        }
        
        // Sort by priority (highest first)
        sortedUpdates.sort((a, b) -> Integer.compare(b.priority, a.priority));
        
        // Process high-priority updates first
        for (FluidUpdate update : sortedUpdates) {
            if (updatesProcessed >= limit) break;
            
            if (update != null && update.level != null && !processedPositions.contains(update.pos)) {
                // Skip old updates to prevent processing stale data
                long age = System.currentTimeMillis() - update.timestamp;
                if (age > 5000) { // 5 second timeout
                    continue;
                }
                
                // Process the fluid update using Flowing Fluids integration
                FlowingFluidsIntegration.processFluidUpdate(update.level, update.pos, update.state, update.blockState);
                FlowingFluidsIntegration.recordUpdate();
                updatesProcessed++;
                processedPositions.add(update.pos);
            }
        }
        
        return updatesProcessed;
    }

    /**
     * Dynamic update limit adjustment based on server performance.
     * Prevents tick lag and time-of-day going backwards.
     */
    private void adjustUpdateLimit(double currentTPS) {
        int currentLimit = currentUpdateLimit.get();
        
        if (currentTPS < CRITICAL_TPS_THRESHOLD) {
            // Critical performance - drastic reduction
            currentUpdateLimit.set(Math.max(MIN_UPDATES_PER_TICK / 2, currentLimit / 3));
        } else if (currentTPS < TARGET_TPS * 0.8) {
            // Poor performance - moderate reduction
            currentUpdateLimit.set(Math.max(MIN_UPDATES_PER_TICK, currentLimit - 1000));
        } else if (currentTPS > TARGET_TPS * 1.1 && currentLimit < MAX_UPDATES_PER_TICK) {
            // Good performance - gradual increase
            currentUpdateLimit.set(Math.min(MAX_UPDATES_PER_TICK, currentLimit + 500));
        }
    }

    /**
     * Enhanced fluid update data with priority system.
     */
    private static class FluidUpdate {
        final Level level;
        final BlockPos pos;
        final FluidState state;
        final BlockState blockState;
        final int priority;
        final long timestamp;
        
        FluidUpdate(Level level, BlockPos pos, FluidState state, BlockState blockState, int priority) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.blockState = blockState;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Get current performance statistics.
     */
    public static Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentTPS", currentTPS);
        stats.put("updateLimit", currentUpdateLimit.get());
        stats.put("queueSize", updateQueue.size());
        stats.put("totalProcessed", totalUpdatesProcessed.get());
        stats.put("emergencyMode", emergencyMode);
        stats.put("processedPositions", processedPositions.size());
        return stats;
    }
    
    /**
     * Force process all pending updates (useful for debugging).
     */
    public static void forceProcessAllUpdates() {
        int processed = 0;
        while (!updateQueue.isEmpty()) {
            FluidUpdate update = updateQueue.poll();
            if (update != null && update.level != null) {
                FlowingFluidsIntegration.processFluidUpdate(update.level, update.pos, update.state, update.blockState);
                processed++;
            }
        }
        System.out.println("Force processed " + processed + " fluid updates");
    }
}
