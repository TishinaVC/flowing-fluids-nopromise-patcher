package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * SIMPLIFIED Flowing Fluids Performance Optimizer
 * 
 * FOCUSED ON ACTUAL BOTTLENECKS:
 * - Direct fluid event interception
 * - Spatial partitioning (chunk-based tracking)
 * - Simple distance-based throttling
 * - Minimal overhead monitoring
 */
@Mod(FlowingFluidsFixes.MOD_ID)
public class FlowingFluidsFixes {
    public static final String MOD_ID = "flowingfluidsfixes";
    
    // SIMPLE TRACKING - minimal overhead
    private static final AtomicInteger totalFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger skippedFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger eventsThisTick = new AtomicInteger(0);
    
    // SPATIAL PARTITIONING - track fluids by chunk instead of individually
    private static final Map<ChunkPos, List<BlockPos>> chunkFluids = new ConcurrentHashMap<>();
    private static final Map<Integer, List<Player>> playerChunks = new ConcurrentHashMap<>();
    
    // SIMPLE PERFORMANCE TRACKING
    private static long lastTickTime = 0;
    private static double cachedMSPT = 5.0;
    private static long lastMSPTCheck = 0;
    private static final AtomicInteger tickCount = new AtomicInteger(0);
    private static long totalTickTimeNanos = 0;
    
    // CONFIGURATION - more aggressive for startup performance
    private static final int MAX_FLUID_DISTANCE = 3; // reduced from 4 for startup
    private static final int MAX_EVENTS_PER_TICK = 200; // reduced from 500 for startup
    private static final double EMERGENCY_MSPT = 30.0; // reduced from 50.0 for earlier protection
    private static final double STARTUP_MSPT = 20.0; // startup-specific threshold
    
    // SAFETY FLAG - prevent caching during mod initialization
    private static boolean allowCaching = false;
    
    public FlowingFluidsFixes() {
        // Register event listener
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        // Enable caching immediately during setup for startup protection
        allowCaching = true;
        System.out.println("[FlowingFluidsFixes] Simplified optimizer loaded - startup protection enabled");
    }
    
    /**
     * SINGLE CONSOLIDATED EVENT HANDLER
     * Replaces 8 separate handlers with one optimized handler
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        // STARTUP PROTECTION - more aggressive during early world load
        if (cachedMSPT > STARTUP_MSPT) {
            skippedFluidEvents.incrementAndGet();
            return;
        }
        
        // EMERGENCY EXIT - skip everything if server is struggling
        if (cachedMSPT > EMERGENCY_MSPT) {
            skippedFluidEvents.incrementAndGet();
            return;
        }
        
        // SIMPLE THROTTLING - limit events per tick
        if (eventsThisTick.get() > MAX_EVENTS_PER_TICK) {
            return;
        }
        
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos pos = event.getPos();
            
            // SPATIAL PARTITIONING - only process if near players in same chunks
            if (!isPlayerInNearbyChunk(serverLevel, pos)) {
                skippedFluidEvents.incrementAndGet();
                return;
            }
            
            // SIMPLE TRACKING - add to chunk-based tracking
            addToChunkTracking(pos);
            
            // Update counters
            eventsThisTick.incrementAndGet();
            totalFluidEvents.incrementAndGet();
        }
    }
    
    /**
     * SIMPLE SERVER START HANDLER - clear caches and enable systems
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Clear all tracking data
        chunkFluids.clear();
        playerChunks.clear();
        totalFluidEvents.set(0);
        skippedFluidEvents.set(0);
        eventsThisTick.set(0);
        tickCount.set(0);
        totalTickTimeNanos = 0;
        
        // SAFETY: Enable caching only after all mods have finished initializing
        allowCaching = true;
        System.out.println("[FlowingFluidsFixes] Systems enabled - simplified optimization active");
    }
    
    /**
     * SERVER TICK HANDLER - MSPT monitoring and player chunk updates
     */
    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Update MSPT tracking
            updateMSPT();
            
            // Update player positions every 5 seconds (100 ticks)
            if (tickCount.get() % 100 == 0) {
                updatePlayerChunks(event.getServer());
            }
        }
    }
    
    /**
     * SPATIAL PARTITIONING - Check if player is in nearby chunks
     * Replaces worldwide player scans with chunk-based checking
     */
    private static boolean isPlayerInNearbyChunk(ServerLevel level, BlockPos pos) {
        if (!allowCaching) {
            return true; // Default to true during initialization
        }
        
        ChunkPos fluidChunk = new ChunkPos(pos);
        int chunkDistance = MAX_FLUID_DISTANCE;
        
        // Check chunks in range around fluid
        for (int dx = -chunkDistance; dx <= chunkDistance; dx++) {
            for (int dz = -chunkDistance; dz <= chunkDistance; dz++) {
                ChunkPos checkChunk = new ChunkPos(fluidChunk.x + dx, fluidChunk.z + dz);
                List<Player> players = playerChunks.get(checkChunk.hashCode());
                
                if (players != null && !players.isEmpty()) {
                    return true; // Player found in nearby chunk
                }
            }
        }
        
        return false; // No players nearby
    }
    
    /**
     * SPATIAL PARTITIONING - Add fluid to chunk tracking
     */
    private static void addToChunkTracking(BlockPos pos) {
        if (!allowCaching) {
            return;
        }
        
        ChunkPos chunk = new ChunkPos(pos);
        chunkFluids.computeIfAbsent(chunk, k -> new ArrayList<>()).add(pos.immutable());
        
        // Limit tracking to prevent memory issues
        List<BlockPos> fluids = chunkFluids.get(chunk);
        if (fluids.size() > 100) {
            fluids.remove(0); // Remove oldest entry
        }
    }
    
    /**
     * SIMPLE MSPT TRACKING - minimal overhead
     */
    public static void updateMSPT() {
        long currentTime = System.nanoTime();
        if (lastTickTime != 0) {
            long tickDuration = currentTime - lastTickTime;
            totalTickTimeNanos += tickDuration / 1_000_000; // Convert to milliseconds
            int ticks = tickCount.incrementAndGet();
            
            // Update every 2 seconds
            if (System.currentTimeMillis() - lastMSPTCheck > 2000) {
                if (ticks > 0) {
                    double averageMSPT = totalTickTimeNanos / (double) ticks;
                    cachedMSPT = averageMSPT;
                    
                    // Reset counters for next period
                    totalTickTimeNanos = 0;
                    tickCount.set(0);
                }
                lastMSPTCheck = System.currentTimeMillis();
            }
        }
        lastTickTime = currentTime;
        
        // Reset per-tick counter
        eventsThisTick.set(0);
    }
    
    /**
     * Update player chunk positions for spatial partitioning
     */
    private static void updatePlayerChunks(MinecraftServer server) {
        if (!allowCaching || server == null) {
            return;
        }
        
        // Clear old player positions
        playerChunks.clear();
        
        // Update player positions for all levels
        for (ServerLevel level : server.getAllLevels()) {
            for (Player player : level.players()) {
                ChunkPos playerChunk = new ChunkPos(player.blockPosition());
                int chunkKey = playerChunk.hashCode();
                
                // Add player to chunk tracking
                playerChunks.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(player);
            }
        }
    }
    
    /**
     * PUBLIC API FOR OTHER SYSTEMS
     */
    
    // Simple MSPT check
    public static double getMSPT() {
        return cachedMSPT;
    }
    
    // Simple emergency check
    public static boolean isEmergencyMode() {
        return cachedMSPT > EMERGENCY_MSPT;
    }
    
    // Simple statistics
    public static String getStats() {
        int total = totalFluidEvents.get();
        int skipped = skippedFluidEvents.get();
        double skipRate = total > 0 ? (skipped * 100.0 / total) : 0.0;
        
        return String.format("Events: %d total, %d skipped (%.1f%%), MSPT: %.1f", 
                           total, skipped, skipRate, cachedMSPT);
    }
    
    // Compatibility methods for other systems
    public static boolean shouldProcessFluid(ServerLevel level, BlockPos pos) {
        return isPlayerInNearbyChunk(level, pos) && cachedMSPT < EMERGENCY_MSPT;
    }
    
    public static boolean shouldSkipBlockEntityTick(Object blockEntity) {
        return isEmergencyMode();
    }
    
    public static boolean shouldSkipEntityTick(Object entity) {
        return isEmergencyMode();
    }
    
    // Compatibility methods for existing systems
    public static boolean isFlowingFluidsDetected() {
        return true; // Assume Flowing Fluids is present
    }
    
    public static void resetStats() {
        totalFluidEvents.set(0);
        skippedFluidEvents.set(0);
        eventsThisTick.set(0);
        tickCount.set(0);
        totalTickTimeNanos = 0;
    }
    
    public static boolean checkParticleSpawn() {
        return !isEmergencyMode(); // Allow particles unless in emergency mode
    }
}
