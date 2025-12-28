package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parity-safe tick scheduler for fluid updates.
 * 
 * KEY DESIGN PRINCIPLES:
 * 1. NEVER drop fluid updates - only defer them to future ticks
 * 2. Process a fixed budget of updates per tick to prevent lag spikes
 * 3. Let Flowing Fluids' own logic handle ALL fluid behavior (we only control timing)
 * 4. Ensure all queued updates eventually drain (no stuck floating layers)
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class FluidTickScheduler {
    private static final Logger LOGGER = LogManager.getLogger();

    // Queued fluid tick positions per level
    private static final Map<Level, Queue<DeferredFluidTick>> DEFERRED_TICKS = new ConcurrentHashMap<>();
    
    // Budget configuration
    private static final int BASE_TICKS_PER_SERVER_TICK = 500;  // Base budget
    private static final int MIN_TICKS_PER_SERVER_TICK = 100;   // Minimum even under load
    private static final int MAX_TICKS_PER_SERVER_TICK = 2000;  // Maximum when server is idle
    
    // Tracking
    private static final AtomicInteger TICKS_THIS_CYCLE = new AtomicInteger(0);
    private static final AtomicInteger TOTAL_PROCESSED = new AtomicInteger(0);
    
    // State flag to prevent re-entrant scheduling during drain
    private static volatile boolean isDraining = false;

    /**
     * Queue a fluid tick to be processed later.
     * Called when we're over budget for this server tick.
     * 
     * @return true if queued (caller should skip immediate processing),
     *         false if should process immediately (under budget or draining)
     */
    public static boolean deferFluidTick(Level level, BlockPos pos, Fluid fluid) {
        // If we're currently draining the queue, don't re-queue (prevent loops)
        if (isDraining) {
            return false;
        }
        
        // Check if we're under budget - if so, process immediately
        int currentBudget = calculateCurrentBudget();
        if (TICKS_THIS_CYCLE.get() < currentBudget) {
            TICKS_THIS_CYCLE.incrementAndGet();
            return false; // Process immediately
        }
        
        // Over budget - queue for later
        Queue<DeferredFluidTick> queue = DEFERRED_TICKS.computeIfAbsent(level, k -> new ConcurrentLinkedQueue<>());
        queue.offer(new DeferredFluidTick(pos.immutable(), fluid));
        LOGGER.debug("Deferred fluid tick at {} (queue size: {}, budget: {}/{})", 
            pos, queue.size(), TICKS_THIS_CYCLE.get(), currentBudget);
        
        return true; // Skip immediate processing
    }
    
    /**
     * Check if an update should be deferred (over budget) without actually queueing.
     */
    public static boolean shouldDefer(Level level) {
        if (isDraining) {
            return false;
        }
        return TICKS_THIS_CYCLE.get() >= calculateCurrentBudget();
    }
    
    /**
     * Record that a tick was processed (for budget tracking).
     */
    public static void recordProcessedTick() {
        TICKS_THIS_CYCLE.incrementAndGet();
        TOTAL_PROCESSED.incrementAndGet();
    }
    
    /**
     * Calculate current tick budget based on server performance.
     */
    private static int calculateCurrentBudget() {
        double avgTickTime = PerformanceMonitor.getAverageTickTime();
        
        // Scale budget based on tick time (lower budget when server is struggling)
        if (avgTickTime > 45_000_000) { // 45ms - server struggling
            return MIN_TICKS_PER_SERVER_TICK;
        } else if (avgTickTime > 30_000_000) { // 30ms - moderate load
            return BASE_TICKS_PER_SERVER_TICK / 2;
        } else if (avgTickTime < 10_000_000) { // 10ms - server idle
            return MAX_TICKS_PER_SERVER_TICK;
        }
        
        return BASE_TICKS_PER_SERVER_TICK;
    }
    
    /**
     * Process deferred fluid ticks at the start of each server tick.
     * This ensures queued updates eventually run, preventing stuck floating layers.
     */
    @SubscribeEvent
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        
        // Reset per-tick counter
        TICKS_THIS_CYCLE.set(0);
        
        // Drain deferred queues
        drainDeferredTicks(event.getServer().getAllLevels());
    }
    
    /**
     * Drain deferred ticks across all levels within budget.
     */
    private static void drainDeferredTicks(Iterable<ServerLevel> levels) {
        isDraining = true;
        
        try {
            int budget = calculateCurrentBudget();
            int processed = 0;
            
            // Round-robin across levels to be fair
            List<ServerLevel> levelList = new ArrayList<>();
            levels.forEach(levelList::add);
            
            if (levelList.isEmpty()) {
                return;
            }
            
            // Process until budget exhausted or all queues empty
            boolean anyProcessed;
            do {
                anyProcessed = false;
                
                for (ServerLevel level : levelList) {
                    if (processed >= budget) {
                        break;
                    }
                    
                    Queue<DeferredFluidTick> queue = DEFERRED_TICKS.get(level);
                    if (queue == null || queue.isEmpty()) {
                        continue;
                    }
                    
                    DeferredFluidTick tick = queue.poll();
                    if (tick != null) {
                        // Re-schedule the fluid tick via Minecraft's normal system
                        // This lets Flowing Fluids' tick() method run with its full logic
                        rescheduleFluidTick(level, tick);
                        processed++;
                        anyProcessed = true;
                    }
                }
            } while (anyProcessed && processed < budget);
            
            if (processed > 0) {
                LOGGER.debug("Drained {} deferred fluid ticks (budget: {})", processed, budget);
            }
            
            // Log warning if queues are backing up
            int totalQueued = DEFERRED_TICKS.values().stream().mapToInt(Queue::size).sum();
            if (totalQueued > 5000) {
                LOGGER.warn("Fluid tick queue backing up: {} pending updates", totalQueued);
            }
            
        } finally {
            isDraining = false;
        }
    }
    
    /**
     * Re-schedule a deferred fluid tick to run via Minecraft's normal tick system.
     * This ensures Flowing Fluids' tick() method runs with full parity.
     */
    private static void rescheduleFluidTick(ServerLevel level, DeferredFluidTick tick) {
        BlockPos pos = tick.pos;
        
        // Verify position is still loaded and contains fluid
        if (!level.isLoaded(pos)) {
            return;
        }
        
        var fluidState = level.getFluidState(pos);
        if (fluidState.isEmpty()) {
            return; // Fluid was removed, nothing to do
        }
        
        // Schedule the tick with delay 0 so it runs next fluid tick cycle
        // This invokes Flowing Fluids' tick() method through normal MC mechanics
        level.scheduleTick(pos, tick.fluid, 1);
        
        LOGGER.debug("Rescheduled deferred tick at {}", pos);
    }
    
    /**
     * Get statistics for debugging/monitoring.
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalQueued = DEFERRED_TICKS.values().stream().mapToInt(Queue::size).sum();
        
        stats.put("totalQueuedTicks", totalQueued);
        stats.put("ticksThisCycle", TICKS_THIS_CYCLE.get());
        stats.put("currentBudget", calculateCurrentBudget());
        stats.put("totalProcessed", TOTAL_PROCESSED.get());
        stats.put("isDraining", isDraining);
        
        return stats;
    }
    
    /**
     * Clear all queues (for world unload, etc.)
     */
    public static void clearQueue(Level level) {
        Queue<DeferredFluidTick> queue = DEFERRED_TICKS.remove(level);
        if (queue != null) {
            LOGGER.info("Cleared {} deferred fluid ticks for level {}", 
                queue.size(), level.dimension().location());
        }
    }
    
    /**
     * Data class for deferred fluid tick.
     */
    private static class DeferredFluidTick {
        final BlockPos pos;
        final Fluid fluid;
        
        DeferredFluidTick(BlockPos pos, Fluid fluid) {
            this.pos = pos;
            this.fluid = fluid;
        }
    }
}
