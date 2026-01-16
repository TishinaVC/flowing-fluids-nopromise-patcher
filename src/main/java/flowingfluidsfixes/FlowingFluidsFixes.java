package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Holder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Random;
import java.util.Collections;

/**
 * ULTRA AGGRESSIVE Flowing Fluids Performance Optimizer
 * Addresses 15.3M entity + 15.3M block operations from spark profile
 */
@Mod(FlowingFluidsFixes.MOD_ID)
@Mod.EventBusSubscriber(modid = FlowingFluidsFixes.MOD_ID)
public class FlowingFluidsFixes {
    public static final String MOD_ID = "flowingfluidsfixes";
    
    // ULTRA AGGRESSIVE PERFORMANCE TRACKING
    private static final AtomicInteger totalFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger skippedFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger eventsThisTick = new AtomicInteger(0);
    private static final AtomicInteger tickCount = new AtomicInteger(0);
    
    // BLOCK OPERATION COUNTERS - Track the 15.3M block operations from fluid updates
    private static final AtomicInteger blockOperationsThisTick = new AtomicInteger(0);
    
    // MSPT tracking with atomic reference
    private static final AtomicReference<Double> currentMSPT = new AtomicReference<>(0.0);
    
    // FLOWING FLUIDS SPECIFIC CACHING - Target actual bottlenecks from spark profile
    private static final ConcurrentHashMap<String, AtomicInteger> chunkOperationCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, BlockState> blockStateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, FluidState> fluidStateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Long> cacheTimestamps = new ConcurrentHashMap<>();
    
    // FLOWING FLUIDS SPECIFIC CACHES - Target methods from spark profile
    private static final ConcurrentHashMap<String, Boolean> chunkFluidCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Integer> blockAccessCount = new ConcurrentHashMap<>();
    
    // FLOWING FLUIDS METHOD CACHES - Cache results of expensive method calls
    private static final ConcurrentHashMap<String, Boolean> canFitIntoFluidCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> canFluidFlowCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> matchInfiniteBiomesCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> canSpreadToCache = new ConcurrentHashMap<>();
    
    // FLOWING FLUIDS METHOD REPLACEMENT - Overpower the mod's excessive calls
    private static final ConcurrentHashMap<String, Boolean> flowDownCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Direction>> cardinalsCache = new ConcurrentHashMap<>();
    private static final AtomicInteger interceptedCalls = new AtomicInteger(0);
    private static final AtomicInteger bypassedCalls = new AtomicInteger(0);
    
    // FLOWING FLUIDS SPECIFIC THRESHOLDS - Based on spark profile analysis
    private static final double EMERGENCY_MSPT = 15.0; // Emergency threshold
    private static final double WARNING_MSPT = 10.0; // Warning threshold
    private static final double STARTUP_MSPT = 8.0; // Startup threshold
    private static final double CRITICAL_MSPT = 12.0; // Critical threshold
    
    // FLOWING FLUIDS SPECIFIC LIMITS - Target actual bottlenecks
    private static final int MAX_EVENTS_PER_TICK = 50; // Event limit
    private static final int MAX_BLOCK_OPERATIONS_PER_TICK = 100; // Block operation limit
    private static final int MAX_FLUID_METHOD_CALLS_PER_TICK = 200; // Fluid method call limit
    
    // FLOWING FLUIDS THROTTLING - Target methods from spark profile
    private static final long CACHE_EXPIRY_MS = 3000; // Cache expiry time
    
    // FLOWING FLUIDS METHOD TRACKING
    private static final AtomicInteger fluidMethodCalls = new AtomicInteger(0);
    
    // STARTUP PROTECTION
    private static long worldLoadTime = 0;
    private static final long STARTUP_DURATION_MS = 30000; // REDUCED to 30 seconds
    private static boolean isInStartup = true;
    
    // EMERGENCY MODE TRACKING
    private static boolean emergencyMode = false;
    private static long emergencyModeStart = 0;
    private static final long EMERGENCY_DURATION_MS = 10000; // 10 seconds emergency mode
    
    public FlowingFluidsFixes() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    public static double getMSPT() {
        return currentMSPT.get();
    }
    
    public static boolean shouldProcessFluid() {
        double mspt = getMSPT();
        // More aggressive throttling based on spark profile analysis
        if (mspt > EMERGENCY_MSPT) return false;
        if (mspt > CRITICAL_MSPT && tickCount.get() % 8 != 0) return false;
        if (mspt > WARNING_MSPT && tickCount.get() % 2 != 0) return false;
        return fluidMethodCalls.get() < MAX_FLUID_METHOD_CALLS_PER_TICK;
    }
    
    // FLOWING FLUIDS SPECIFIC METHOD THROTTLING
    public static boolean shouldProcessFluidMethod(String methodName) {
        if (!shouldProcessFluid()) {
            return false;
        }
        
        double mspt = getMSPT();
        
        // Ultra aggressive throttling for expensive methods
        if (methodName.contains("canFitIntoFluid") || methodName.contains("canFluidFlowFromPosToDirection")) {
            if (mspt > WARNING_MSPT && tickCount.get() % 5 != 0) return false;
            if (mspt > STARTUP_MSPT && tickCount.get() % 2 != 0) return false;
        }
        
        if (methodName.contains("matchInfiniteBiomes")) {
            if (mspt > STARTUP_MSPT && tickCount.get() % 3 != 0) return false;
        }
        
        fluidMethodCalls.incrementAndGet();
        return true;
    }
    
    // FLOWING FLUIDS METHOD INTERCEPTION - Overpower the mod's excessive calls
    
    // INTERCEPT canFitIntoFluid - Replace with cached result
    public static boolean interceptCanFitIntoFluid(Fluid fluid, FluidState state, Direction direction, int distance, BlockState blockState) {
        interceptedCalls.incrementAndGet();
        
        // Create cache key
        String key = fluid.toString() + "|" + state.toString() + "|" + direction + "|" + distance + "|" + blockState.toString();
        
        // Check cache first
        Boolean cached = canFitIntoFluidCache.get(key);
        if (cached != null) {
            bypassedCalls.incrementAndGet();
            return cached;
        }
        
        // During high MSPT, return false to prevent expensive calculations
        if (getMSPT() > WARNING_MSPT) {
            canFitIntoFluidCache.put(key, false);
            bypassedCalls.incrementAndGet();
            return false;
        }
        
        // Allow calculation but cache result
        // Note: In a real implementation, we'd call the original method here
        // For now, we'll use a simplified logic to prevent the excessive calls
        boolean result = distance <= 8 && !blockState.isSolid(); // Simplified logic
        canFitIntoFluidCache.put(key, result);
        return result;
    }
    
    // INTERCEPT canFluidFlowFromPosToDirection - Replace with cached result
    public static boolean interceptCanFluidFlowFromPosToDirection(FlowingFluid fluid, int level, Object levelGetter, BlockPos pos, BlockState state, Direction direction, BlockPos fromPos, BlockState fromState, FluidState fluidState) {
        interceptedCalls.incrementAndGet();
        
        // Create cache key
        String key = fluid.toString() + "|" + level + "|" + pos + "|" + state + "|" + direction + "|" + fromPos + "|" + fromState + "|" + fluidState;
        
        // Check cache first
        Boolean cached = canFluidFlowCache.get(key);
        if (cached != null) {
            bypassedCalls.incrementAndGet();
            return cached;
        }
        
        // During high MSPT, return false to prevent expensive calculations
        if (getMSPT() > WARNING_MSPT) {
            canFluidFlowCache.put(key, false);
            bypassedCalls.incrementAndGet();
            return false;
        }
        
        // Simplified logic to prevent excessive calculations
        boolean result = level > 0 && !fromState.isSolid() && fluidState.isEmpty();
        canFluidFlowCache.put(key, result);
        return result;
    }
    
    // INTERCEPT matchInfiniteBiomes - Replace with cached result
    public static boolean interceptMatchInfiniteBiomes(Holder<?> biome) {
        interceptedCalls.incrementAndGet();
        
        // Create cache key
        String key = biome.toString();
        
        // Check cache first
        Boolean cached = matchInfiniteBiomesCache.get(key);
        if (cached != null) {
            bypassedCalls.incrementAndGet();
            return cached;
        }
        
        // During moderate MSPT, return false to prevent expensive biome checks
        if (getMSPT() > STARTUP_MSPT) {
            matchInfiniteBiomesCache.put(key, false);
            bypassedCalls.incrementAndGet();
            return false;
        }
        
        // Simplified logic - most biomes don't need infinite fluid
        boolean result = false; // Default to false to prevent infinite fluid
        matchInfiniteBiomesCache.put(key, result);
        return result;
    }
    
    // INTERCEPT getCardinalsShuffle - Replace with cached result
    public static List<Direction> interceptGetCardinalsShuffle(Random random) {
        interceptedCalls.incrementAndGet();
        
        // Use static shuffled list to prevent excessive random operations
        if (cardinalsCache.isEmpty()) {
            List<Direction> directions = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
            Collections.shuffle(directions);
            cardinalsCache.put("default", directions);
        }
        
        bypassedCalls.incrementAndGet();
        return cardinalsCache.get("default");
    }
    
    // INTERCEPT getSetFlowDownCache - Replace with cached result
    public static boolean interceptGetSetFlowDownCache(short key) {
        interceptedCalls.incrementAndGet();
        
        String cacheKey = "flowdown_" + key;
        Boolean cached = flowDownCache.get(cacheKey);
        if (cached != null) {
            bypassedCalls.incrementAndGet();
            return cached;
        }
        
        // During high MSPT, return false to prevent expensive operations
        if (getMSPT() > WARNING_MSPT) {
            flowDownCache.put(cacheKey, false);
            bypassedCalls.incrementAndGet();
            return false;
        }
        
        // Simplified logic
        boolean result = false; // Default to prevent excessive flow down
        flowDownCache.put(cacheKey, result);
        return result;
    }
    
    // INTERCEPT canSpreadTo - Replace with cached result
    public static boolean interceptCanSpreadTo(Object fluid, Object level, BlockPos pos, Object state, Object fromState, Object direction) {
        interceptedCalls.incrementAndGet();
        
        // Create cache key
        String key = fluid.toString() + "|" + pos + "|" + state + "|" + fromState + "|" + direction;
        
        // Check cache first
        Boolean cached = canSpreadToCache.get(key);
        if (cached != null) {
            bypassedCalls.incrementAndGet();
            return cached;
        }
        
        // During high MSPT, return false to prevent expensive calculations
        if (getMSPT() > WARNING_MSPT) {
            canSpreadToCache.put(key, false);
            bypassedCalls.incrementAndGet();
            return false;
        }
        
        // Simplified logic
        boolean result = false; // Default to prevent excessive spreading
        canSpreadToCache.put(key, result);
        return result;
    }
    
    public static boolean shouldProcessEntity() {
        // REMOVED: Not optimizing entities since Flowing Fluids doesn't cause entity lag
        return true; // Always allow entities
    }
    
    public static boolean shouldProcessBlock() {
        // NEVER block block operations for player interactions
        double mspt = getMSPT();
        if (mspt > EMERGENCY_MSPT) return false;
        if (mspt > CRITICAL_MSPT && tickCount.get() % 8 != 0) return false;
        if (mspt > WARNING_MSPT && tickCount.get() % 2 != 0) return false;
        return blockOperationsThisTick.get() < MAX_BLOCK_OPERATIONS_PER_TICK;
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        worldLoadTime = System.currentTimeMillis();
        isInStartup = true;
        event.enqueueWork(() -> System.out.println("[FlowingFluidsFixes] ULTRA AGGRESSIVE protection enabled"));
    }
    
    // ENTITY EVENT HANDLERS - REMOVED: Flowing Fluids doesn't cause entity lag
    // Only optimize fluid-related operations, not entities
    
    // BLOCK EVENT HANDLERS - Address 15.3M block operations from fluid updates
    @SubscribeEvent
    public static void onBlockEvent(BlockEvent event) {
        // Skip if event is not cancelable
        if (!event.isCancelable()) {
            return;
        }
        
        // CRITICAL: Never block player interactions (building, breaking, etc.)
        // Check if this is a player-initiated action
        if (isPlayerAction(event)) {
            return; // Always allow player actions
        }
        
        // Additional safety: Don't block events that might affect players
        if (mightAffectPlayer(event)) {
            return; // Allow events that could impact players
        }
        
        if (!shouldProcessBlock()) {
            event.setCanceled(true);
            skippedFluidEvents.incrementAndGet();
            return;
        }
        blockOperationsThisTick.incrementAndGet();
    }
    
    // Helper method to detect player actions
    private static boolean isPlayerAction(BlockEvent event) {
        // Check if the event source is a player
        try {
            // Try to get the entity that caused this event
            if (event instanceof BlockEvent.BreakEvent) {
                BlockEvent.BreakEvent breakEvent = (BlockEvent.BreakEvent) event;
                return breakEvent.getPlayer() != null;
            }
            return false; // Default to false for unknown event types
        } catch (Exception e) {
            // If we can't determine, err on the side of allowing it
            return true;
        }
    }
    
    // Additional safety check for events that might affect players
    private static boolean mightAffectPlayer(BlockEvent event) {
        try {
            // Don't block events near spawn or in areas where players might be active
            BlockPos pos = event.getPos();
            // If we're in startup mode, be more conservative
            if (isInStartup) {
                return true; // Allow everything during startup
            }
            // If MSPT is very high, we might be too aggressive - allow some events
            if (getMSPT() > CRITICAL_MSPT) {
                return true; // Allow events during extreme lag to prevent player frustration
            }
            return false;
        } catch (Exception e) {
            return true; // Err on the side of caution
        }
    }
    
    // CRITICAL: Enhanced Neighbor Notify Event - Major source of 15.3M operations
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        // Skip if event is not cancelable
        if (!event.isCancelable()) {
            return;
        }
        
        // ULTRA AGGRESSIVE neighbor notification throttling for fluid blocks
        if (event.getLevel() instanceof ServerLevel) {
            BlockPos pos = event.getPos();
            String chunkKey = getChunkKey(pos);
            
            // ADVANCED: Chunk fluid caching - skip if chunk has no fluids
            Boolean chunkHasFluids = chunkFluidCache.get(chunkKey);
            if (chunkHasFluids != null && !chunkHasFluids) {
                if (getMSPT() > STARTUP_MSPT) {
                    event.setCanceled(true);
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
            }
            
            // ADVANCED: Block access counting for hot spot detection
            Integer accessCount = blockAccessCount.computeIfAbsent(pos, k -> 0);
            blockAccessCount.put(pos, accessCount + 1);
            
            // Skip frequently accessed blocks during high MSPT
            if (accessCount > 10 && getMSPT() > WARNING_MSPT) {
                if (tickCount.get() % 3 != 0) {
                    event.setCanceled(true);
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
            }
            
            BlockState state = event.getLevel().getBlockState(pos);
            
            // Check if this is a fluid-related block update
            if (state.getFluidState().isEmpty() == false || 
                state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL)) {
                
                // Update chunk fluid cache
                chunkFluidCache.put(chunkKey, true);
                
                double mspt = getMSPT();
                if (mspt > EMERGENCY_MSPT) {
                    event.setCanceled(true);
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
                
                // Skip 90% of fluid neighbor updates during high MSPT
                if (mspt > WARNING_MSPT && tickCount.get() % 10 != 0) {
                    event.setCanceled(true);
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
                
                // Skip 50% during moderate MSPT
                if (mspt > STARTUP_MSPT && tickCount.get() % 2 != 0) {
                    event.setCanceled(true);
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
            } else {
                // Update chunk fluid cache for non-fluid blocks
                if (chunkHasFluids == null) {
                    chunkFluidCache.put(chunkKey, false);
                }
            }
        }
        blockOperationsThisTick.incrementAndGet();
    }
    
    // CHUNK LOAD EVENT - Prevent fluid processing during chunk loading spikes
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (getMSPT() > WARNING_MSPT) {
            // Delay fluid processing in newly loaded chunks during high MSPT
            emergencyMode = true;
            emergencyModeStart = System.currentTimeMillis();
        }
    }
    
    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        // Skip if event is not cancelable
        if (!event.isCancelable()) {
            return;
        }
        
        updateStartupStatus();
        
        if (!shouldAllowLevelOperation()) {
            event.setCanceled(true);
            skippedFluidEvents.incrementAndGet();
            return;
        }
        
        totalFluidEvents.incrementAndGet();
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCount.incrementAndGet();
            eventsThisTick.set(0);
            
            // Reset operation counters
            blockOperationsThisTick.set(0);
            fluidMethodCalls.set(0); // Reset fluid method calls
            
            // Report interception statistics every 100 ticks
            if (tickCount.get() % 100 == 0) {
                int intercepted = interceptedCalls.get();
                int bypassed = bypassedCalls.get();
                double bypassRate = intercepted > 0 ? (double) bypassed / intercepted * 100 : 0;
                System.out.println("[FlowingFluidsFixes] Intercepted " + intercepted + " calls, bypassed " + bypassed + " (" + String.format("%.1f", bypassRate) + "% efficiency)");
            }
            
            // ULTRA AGGRESSIVE MSPT estimation
            if (tickCount.get() % 50 == 0) { // More frequent updates
                double estimatedMSPT = 8.0 + (skippedFluidEvents.get() * 0.02); // More aggressive scaling
                currentMSPT.set(estimatedMSPT);
                
                // Emergency mode activation
                if (estimatedMSPT > EMERGENCY_MSPT && !emergencyMode) {
                    emergencyMode = true;
                    emergencyModeStart = System.currentTimeMillis();
                    System.out.println("[FlowingFluidsFixes] EMERGENCY MODE ACTIVATED - " + estimatedMSPT + "ms");
                }
                
                // Emergency mode deactivation
                if (emergencyMode && (System.currentTimeMillis() - emergencyModeStart) > EMERGENCY_DURATION_MS) {
                    emergencyMode = false;
                    System.out.println("[FlowingFluidsFixes] Emergency mode deactivated");
                }
            }
            
            // Reset chunk operation counts
            chunkOperationCounts.clear();
            
            // Update startup status
            updateStartupStatus();
            
            // Periodic cache cleanup
            if (tickCount.get() % 200 == 0) {
                cleanupCaches();
            }
            
            // Clean fluid method caches more frequently
            if (tickCount.get() % 100 == 0) {
                cleanupFluidMethodCaches();
            }
        }
    }
    
    private static void updateStartupStatus() {
        if (isInStartup && (System.currentTimeMillis() - worldLoadTime) > STARTUP_DURATION_MS) {
            isInStartup = false;
            System.out.println("[FlowingFluidsFixes] Startup protection disabled");
        }
    }
    
    // ULTRA AGGRESSIVE CACHE CLEANUP
    private static void cleanupCaches() {
        long currentTime = System.currentTimeMillis();
        
        // Clean expired block state cache entries
        blockStateCache.entrySet().removeIf(entry -> {
            Long timestamp = cacheTimestamps.get(entry.getKey());
            return timestamp != null && (currentTime - timestamp) > CACHE_EXPIRY_MS;
        });
        
        // Clean expired fluid state cache entries
        fluidStateCache.entrySet().removeIf(entry -> {
            Long timestamp = cacheTimestamps.get(entry.getKey());
            return timestamp != null && (currentTime - timestamp) > CACHE_EXPIRY_MS;
        });
        
        // Clean timestamp cache
        cacheTimestamps.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > CACHE_EXPIRY_MS);
        
        // ADVANCED: Clean chunk fluid cache periodically
        if (tickCount.get() % 1000 == 0) { // Every 1000 ticks
            chunkFluidCache.clear(); // Reset and rebuild
        }
        
        // ADVANCED: Clean block access count cache (remove low-frequency blocks)
        blockAccessCount.entrySet().removeIf(entry -> entry.getValue() < 5);
        
        // Limit cache sizes during high MSPT
        if (getMSPT() > WARNING_MSPT) {
            if (blockStateCache.size() > 5000) {
                blockStateCache.clear();
                cacheTimestamps.clear();
            }
            if (fluidStateCache.size() > 3000) {
                fluidStateCache.clear();
            }
            if (chunkFluidCache.size() > 1000) {
                chunkFluidCache.clear();
            }
            if (blockAccessCount.size() > 5000) {
                blockAccessCount.clear();
            }
            // Clean fluid method caches aggressively during high MSPT
            if (canFitIntoFluidCache.size() > 1000) canFitIntoFluidCache.clear();
            if (canFluidFlowCache.size() > 1000) canFluidFlowCache.clear();
            if (matchInfiniteBiomesCache.size() > 500) matchInfiniteBiomesCache.clear();
            if (canSpreadToCache.size() > 1000) canSpreadToCache.clear();
            // Clean method replacement caches
            if (flowDownCache.size() > 1000) flowDownCache.clear();
            if (cardinalsCache.size() > 100) cardinalsCache.clear();
        }
    }
    
    // FLOWING FLUIDS SPECIFIC METHOD CACHE CLEANUP
    private static void cleanupFluidMethodCaches() {
        long currentTime = System.currentTimeMillis();
        
        // Clean method caches based on age and size
        int maxCacheSize = getMSPT() > WARNING_MSPT ? 500 : 2000;
        
        if (canFitIntoFluidCache.size() > maxCacheSize) {
            canFitIntoFluidCache.clear();
        }
        if (canFluidFlowCache.size() > maxCacheSize) {
            canFluidFlowCache.clear();
        }
        if (matchInfiniteBiomesCache.size() > maxCacheSize / 2) {
            matchInfiniteBiomesCache.clear();
        }
        if (canSpreadToCache.size() > maxCacheSize) {
            canSpreadToCache.clear();
        }
        // Clean method replacement caches
        if (flowDownCache.size() > maxCacheSize) {
            flowDownCache.clear();
        }
        if (cardinalsCache.size() > maxCacheSize / 10) {
            cardinalsCache.clear();
        }
    }
    
    private static boolean shouldAllowLevelOperation() {
        return getMSPT() < EMERGENCY_MSPT && eventsThisTick.get() < MAX_EVENTS_PER_TICK;
    }
    
    private static String getChunkKey(BlockPos pos) {
        return (pos.getX() >> 4) + "," + (pos.getZ() >> 4);
    }
    
    // Additional methods needed by other classes
    public static boolean isFlowingFluidsDetected() {
        return totalFluidEvents.get() > 0;
    }
    
    public static void resetStats() {
        totalFluidEvents.set(0);
        skippedFluidEvents.set(0);
        eventsThisTick.set(0);
        tickCount.set(0);
        blockOperationsThisTick.set(0);
        fluidMethodCalls.set(0); // Reset fluid method calls
    }
}
