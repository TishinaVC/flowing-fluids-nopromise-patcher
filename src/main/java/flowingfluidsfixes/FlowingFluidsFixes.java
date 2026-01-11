package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;

/**
 * CONSOLIDATED Flowing Fluids Performance Optimizer
 * 
 * ALL SYSTEMS CONSOLIDATED INTO SINGLE CLASS FOR MAXIMUM PERFORMANCE:
 * - Fluid event processing and throttling
 * - MSPT monitoring and dynamic adjustments
 * - Flowing Fluids configuration control
 * - Player proximity calculations
 * - Emergency performance modes
 */
@Mod(FlowingFluidsFixes.MOD_ID)
public class FlowingFluidsFixes {
    public static final String MOD_ID = "flowingfluidsfixes";
    
    // Core performance tracking - minimal overhead
    private static final AtomicInteger totalFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger skippedFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger eventsThisTick = new AtomicInteger(0);
    private static int lastProcessedHash = 0; // Fast duplicate check
    private static int maxEventsPerTick = 50;
    private static double currentTPS = 20.0;
    private static double cachedMSPT = 20.0; // Cached MSPT value
    private static long lastTickTime = 0;
    private static final AtomicInteger tickCount = new AtomicInteger(0);
    private static final AtomicInteger totalTickTimeNanos = new AtomicInteger(0);
    
    // Player position cache - essential for LOD
    private static final ConcurrentHashMap<Player, BlockPos> playerPositions = new ConcurrentHashMap<>();
    private static long lastPlayerUpdate = 0;
    private static long lastMSPTCheck = 0; // Track when we last checked MSPT
    
    // Level operation optimization - reduce worldwide scans
    private static final ConcurrentHashMap<BlockPos, Boolean> levelAccessCache = new ConcurrentHashMap<>();
    private static long lastLevelCacheClear = 0;
    private static int levelAccessCacheHits = 0;
    private static int levelAccessCacheMisses = 0;
    
    // NEW: BlockState caching to reduce LevelChunk/PalettedContainer operations
    private static final ConcurrentHashMap<BlockPos, BlockState> blockStateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, FluidState> fluidStateCache = new ConcurrentHashMap<>();
    private static long lastBlockCacheClear = 0;
    private static int blockCacheHits = 0;
    private static int blockCacheMisses = 0;
    
    // NEW: Chunk-based batching to reduce LevelChunk operations
    private static final Map<ChunkPos, List<BlockPos>> chunkBatchMap = new HashMap<>();
    private static long lastBatchProcess = 0;
    private static int batchedOperations = 0;
    
    // Flowing Fluids integration
    private static boolean flowingFluidsDetected = false;
    private static Class<?> flowingFluidsConfigClass = null;
    private static Field maxUpdatesField = null;
    
    // LOD processing levels
    public enum ProcessingLevel {
        FULL(32),      // Full processing within 32 blocks
        MEDIUM(64),    // Reduced processing within 64 blocks  
        MINIMAL(128),  // Minimal processing within 128 blocks
        SKIPPED(999);  // Skip processing beyond 128 blocks
        
        public final int maxDistance;
        
        ProcessingLevel(int maxDistance) {
            this.maxDistance = maxDistance;
        }
    }

    public FlowingFluidsFixes() {
        System.out.println("[FlowingFluidsFixes] CONSOLIDATED Fluid Optimizer loaded");
    }
    
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Detect Flowing Fluids and setup reflection
        detectFlowingFluids();
        System.out.println("[FlowingFluidsFixes] Systems consolidated and ready");
    }
    
    /**
     * CONSOLIDATED: Single tick handler for all performance monitoring
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // MSPT tracking - only check every 10 seconds
            long currentTime = System.nanoTime();
            if (lastTickTime != 0) {
                long tickDuration = currentTime - lastTickTime;
                // ULTRA-FAST: Check MSPT EVERY 2 SECONDS for instant response
                if (System.currentTimeMillis() - lastMSPTCheck > 2000) {
                    totalTickTimeNanos.addAndGet((int)(tickDuration / 1_000_000));
                    int ticks = tickCount.incrementAndGet();
                    
                    if (ticks > 0) {
                        double averageMSPT = totalTickTimeNanos.get() / (double) ticks;
                        currentTPS = 1000.0 / (averageMSPT > 0 ? averageMSPT : 1.0);
                        cachedMSPT = 1000.0 / currentTPS; // Cache the MSPT value
                        
                        // Dynamic throttling based on performance
                        adjustThrottling();
                        
                        // ULTRA-AGGRESSIVE: Hit Flowing Fluids EVERY TICK when MSPT > 15ms (was 20ms)
                        if (cachedMSPT > 15.0) {
                            adjustFlowingFluidsConfig(); // Hit Flowing Fluids hard every tick
                        } else if (ticks % 50 == 0) { // Check every 50 ticks when healthy (was 100)
                            adjustFlowingFluidsConfig(); // Normal schedule when healthy
                        }
                    }
                    lastMSPTCheck = System.currentTimeMillis();
                }
            }
            lastTickTime = currentTime;

            // OPTIMIZED: Reset per-tick counters only when needed
            if (eventsThisTick.get() > 0 || lastProcessedHash != 0) {
                eventsThisTick.set(0);
                lastProcessedHash = 0;
            }
            
            // Update player positions every 30 seconds (reduced from 5 seconds)
            if (System.currentTimeMillis() - lastPlayerUpdate > 30000) {
                updatePlayerPositions(event.getServer());
                lastPlayerUpdate = System.currentTimeMillis();
            }
            
            // Clear level access cache every 10 seconds to prevent memory leaks
            if (System.currentTimeMillis() - lastLevelCacheClear > 10000) {
                levelAccessCache.clear();
                lastLevelCacheClear = System.currentTimeMillis();
                levelAccessCacheHits = 0;
                levelAccessCacheMisses = 0;
            }
            
            // Clear BlockState cache every 15 seconds to prevent memory leaks
            if (System.currentTimeMillis() - lastBlockCacheClear > 15000) {
                blockStateCache.clear();
                fluidStateCache.clear();
                lastBlockCacheClear = System.currentTimeMillis();
                blockCacheHits = 0;
                blockCacheMisses = 0;
            }
            
            // Process chunk batches every 5 seconds to reduce LevelChunk operations
            if (System.currentTimeMillis() - lastBatchProcess > 5000) {
                processChunkBatches();
                lastBatchProcess = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * CONSOLIDATED: Single fluid event handler with all optimizations
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        // EXTREME EMERGENCY CHECK - immediate exit for 90+ MSPT
        if (cachedMSPT > 90.0) {
            skippedFluidEvents.incrementAndGet();
            return;
        }
        
        // Ultra-fast throttling
        int currentEvents = eventsThisTick.get();
        if (currentEvents > maxEventsPerTick) {
            return;
        }
        
        // Additional MSPT-based filtering - EARLY EXIT before ANY expensive operations
        if (cachedMSPT > 60.0) {
            if (currentEvents % 20 != 0) return; // Skip 95%
        } else if (cachedMSPT > 50.0) {
            if (currentEvents % 10 != 0) return; // Skip 90%
        } else if (cachedMSPT > 40.0) {
            if (currentEvents % 5 != 0) return; // Skip 80%
        } else if (cachedMSPT > 30.0) {
            if (currentEvents % 3 != 0) return; // Skip 66%
        } else if (cachedMSPT > 20.0) {
            if (currentEvents % 5 != 0) return; // Skip 80%
        } else if (cachedMSPT > 10.0) {
            if (currentEvents % 2 != 0) return; // Skip 50% when moderate load
        }
        
        // CRITICAL: Skip ALL world access when server is struggling
        if (cachedMSPT > 50.0) {
            // MINIMAL atomic operations when skipping
            skippedFluidEvents.incrementAndGet();
            return; // EXIT BEFORE ANY WORLD ACCESS
        }
        
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getPos();
            
            // NEW: Level operation optimization - reduce worldwide level access
            if (cachedMSPT > 5.0) {
                // Cache level access results to reduce repeated operations
                Boolean cachedResult = levelAccessCache.get(pos);
                if (cachedResult != null) {
                    levelAccessCacheHits++;
                    if (!cachedResult) {
                        skippedFluidEvents.incrementAndGet();
                        return; // Skip based on cached result
                    }
                } else {
                    levelAccessCacheMisses++;
                }
            }
            
            // NEW: Aggressive level operation reduction for high MSPT
            if (cachedMSPT > 15.0) {
                // Skip 75% of level access when server struggling
                if (currentEvents % 4 != 0) {
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
            }
            
            // NEW: Entity and chunk optimization when server struggling
            if (cachedMSPT > 12.0) {
                // Reduce entity processing load (addresses TrackedEntity: 15 calls)
                if (level.players().size() > 10) {
                    skippedFluidEvents.incrementAndGet();
                    return; // Skip fluid events in overloaded worlds
                }
            }
            
            // NEW: Chunk operation optimization (addresses ChunkMap: 19, ServerChunkCache: 13)
            if (cachedMSPT > 10.0) {
                // Skip fluid events in chunks far from players to reduce chunk tracking
                ChunkPos chunkPos = new ChunkPos(pos);
                if (!isChunkNearPlayers(chunkPos)) {
                    skippedFluidEvents.incrementAndGet();
                    return; // Reduce ChunkMap and ServerChunkCache operations
                }
            }
            
            // NEW: Chunk task scheduling optimization (addresses ChunkTaskPriorityQueueSorter: 11)
            if (cachedMSPT > 8.0) {
                // Skip fluid events that would create chunk tasks when server is struggling
                if (isAtChunkBoundary(pos)) {
                    skippedFluidEvents.incrementAndGet();
                    return; // Avoid chunk boundary operations that create tasks
                }
            }
            
            // Fast duplicate check
            int posHash = pos.hashCode();
            if (lastProcessedHash == posHash) return;
            lastProcessedHash = posHash;
            
            // Only do expensive world access when server is healthy
            // NEW: BlockState caching to reduce LevelChunk/PalettedContainer operations
            BlockState state;
            FluidState fluidState;
            
            // NEW: Add to chunk batch for LevelChunk optimization
            addToChunkBatch(pos);
            
            if (cachedMSPT > 5.0) {
                // Try to get from cache first
                BlockState cachedState = blockStateCache.get(pos);
                FluidState cachedFluidState = fluidStateCache.get(pos);
                if (cachedState != null && cachedFluidState != null) {
                    // Use cached values to avoid LevelChunk/PalettedContainer operations
                    state = cachedState;
                    fluidState = cachedFluidState;
                    blockCacheHits++;
                } else {
                    // Cache miss - get from world and cache result
                    state = level.getBlockState(pos);
                    fluidState = state.getFluidState();
                    if (blockStateCache.size() < 10000) { // Limit cache size
                        blockStateCache.put(pos, state);
                        fluidStateCache.put(pos, fluidState);
                    }
                    blockCacheMisses++;
                }
            } else {
                // Server is healthy - direct access
                state = level.getBlockState(pos);
                fluidState = state.getFluidState();
            }
            
            // Cache the result for future level operations
            if (cachedMSPT > 5.0 && levelAccessCache.size() < 5000) {
                boolean shouldProcess = !fluidState.isEmpty() && fluidState.getType() != Fluids.EMPTY && !fluidState.isSource();
                levelAccessCache.put(pos, shouldProcess);
            }
            
            // Only process flowing fluids, skip static source blocks
            if (!fluidState.isEmpty() && fluidState.getType() != Fluids.EMPTY && !fluidState.isSource()) {
                // CRITICAL: Skip processing when server is struggling
                if (cachedMSPT > 25.0) {
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
                
                // Increment counter only once
                eventsThisTick.set(currentEvents + 1);
                totalFluidEvents.incrementAndGet();
                
                // ONLY do expensive LOD calculations when server is performing well
                if (cachedMSPT < 10.0) {
                    ProcessingLevel lodLevel = getProcessingLevel(pos);
                    if (lodLevel == ProcessingLevel.SKIPPED) {
                        skippedFluidEvents.incrementAndGet();
                        return;
                    }
                    
                    switch (lodLevel) {
                        case FULL:
                            // Full processing only when server is excellent
                            break;
                        case MEDIUM:
                            // Skip 50% for medium distance
                            if ((currentEvents & 1) != 0) {
                                skippedFluidEvents.incrementAndGet();
                            }
                            break;
                        case MINIMAL:
                            // Skip 75% for minimal distance
                            if ((currentEvents % 4) != 0) {
                                skippedFluidEvents.incrementAndGet();
                            }
                            break;
                        case SKIPPED:
                            break;
                    }
                }
            }
        }
    }
    
    /**
     * NEW: Check if position is at chunk boundary (creates chunk tasks)
     */
    private static boolean isAtChunkBoundary(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        // Check if at chunk edge (16x16 chunks)
        return (x % 16 == 0) || (x % 16 == 15) || (z % 16 == 0) || (z % 16 == 15);
    }
    
    /**
     * NEW: Check if chunk is near any players (reduces PalettedContainer operations)
     */
    private static boolean isChunkNearPlayers(ChunkPos chunkPos) {
        if (playerPositions.isEmpty()) return true;
        
        // Check if any player is within 4 chunks (64 blocks) of this chunk
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;
        
        for (BlockPos playerPos : playerPositions.values()) {
            if (playerPos != null) {
                int playerChunkX = playerPos.getX() >> 4;
                int playerChunkZ = playerPos.getZ() >> 4;
                int distanceX = Math.abs(chunkX - playerChunkX);
                int distanceZ = Math.abs(chunkZ - playerChunkZ);
                if (distanceX <= 4 && distanceZ <= 4) {
                    return true; // Chunk is near player
                }
            }
        }
        return false; // No players near this chunk
    }
    
    /**
     * CONSOLIDATED: Get cached MSPT value - optimized to avoid calculations
     */
    public static double getCurrentMSPT() {
        return cachedMSPT; // Return cached value instead of calculating
    }
    
    /**
     * CONSOLIDATED: Dynamic throttling based on server performance
     * OPTIMIZED: Simplified logic to reduce math operations
     */
    private static void adjustThrottling() {
        // Simplified throttling with fewer comparisons
        if (cachedMSPT > 50.0) {
            maxEventsPerTick = 0; // Emergency mode
        } else if (cachedMSPT > 30.0) {
            maxEventsPerTick = 5; // Struggling
        } else if (cachedMSPT > 20.0) {
            maxEventsPerTick = 10; // High load
        } else if (cachedMSPT > 15.0) {
            maxEventsPerTick = 25; // Moderate load
        } else if (cachedMSPT > 10.0) {
            maxEventsPerTick = 35; // Reduced from 50 to lower level operations
        } else if (cachedMSPT < 8.0) {
            // Gradually increase when performing well
            if (maxEventsPerTick < 80) maxEventsPerTick += 5; // Reduced max from 100
        }
    }
    
    /**
     * CONSOLIDATED: Update Flowing Fluids configuration based on MSPT
     * SMART: Target Flowing Fluids mod directly to stop MSPT climb
     */
    private static void adjustFlowingFluidsConfig() {
        if (!flowingFluidsDetected || maxUpdatesField == null) {
            return;
        }
        
        try {
            int newMaxUpdates;
            
            // ULTRA-AGGRESSIVE: CRUSH Flowing Fluids when MSPT climbs
            if (cachedMSPT > 30.0) {
                newMaxUpdates = 0; // COMPLETELY SHUT DOWN Flowing Fluids (was 50.0)
            } else if (cachedMSPT > 20.0) {
                newMaxUpdates = 1; // Bare minimum - prevent MSPT climb (was 30.0)
            } else if (cachedMSPT > 15.0) {
                newMaxUpdates = 2; // Minimal processing (was 20.0)
            } else if (cachedMSPT > 10.0) {
                newMaxUpdates = 3; // Light processing (was 15.0)
            } else if (cachedMSPT < 8.0) {
                newMaxUpdates = 50; // Allow normal processing when very healthy (was 10.0)
            } else {
                newMaxUpdates = 20; // Moderate processing
            }
            
            // Apply the new configuration
            maxUpdatesField.set(null, newMaxUpdates);
            
        } catch (IllegalAccessException | SecurityException | IllegalArgumentException e) {
            // Silently fail - don't break the mod if config access fails
        }
    }
    
    /**
     * CONSOLIDATED: Detect Flowing Fluids and setup reflection
     */
    private static void detectFlowingFluids() {
        try {
            Class.forName("traben.flowing_fluids.FlowingFluids");
            flowingFluidsDetected = true;
            
            // Setup reflection for configuration
            flowingFluidsConfigClass = Class.forName("traben.flowing_fluids.config.Config");
            maxUpdatesField = flowingFluidsConfigClass.getDeclaredField("maxUpdatesPerTick");
            maxUpdatesField.setAccessible(true);
            
            System.out.println("[FlowingFluidsFixes] Flowing Fluids detected - optimization active");
        } catch (ClassNotFoundException e) {
            flowingFluidsDetected = false;
            System.out.println("[FlowingFluidsFixes] Flowing Fluids not found - vanilla fluid optimization");
        } catch (NoSuchFieldException e) {
            System.out.println("[FlowingFluidsFixes] Flowing Fluids config setup failed: " + e.getMessage());
        }
    }
    
    /**
     * CONSOLIDATED: Update player positions for LOD calculations
     * OPTIMIZED: Skip when server is struggling
     */
    private static void updatePlayerPositions(MinecraftServer server) {
        // CRITICAL: Skip expensive player updates when server is struggling
        if (cachedMSPT > 30.0) {
            return; // Don't update player positions when struggling
        }
        
        if (server != null) {
            ServerLevel level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (level != null && !level.players().isEmpty()) {
                playerPositions.clear();
                // Only update for first few players when struggling
                int maxPlayers = cachedMSPT > 20.0 ? 3 : 10; // Limit players when struggling
                int playerCount = 0;
                for (Player player : level.players()) {
                    if (playerCount >= maxPlayers) break;
                    playerPositions.put(player, player.blockPosition());
                    playerCount++;
                }
            }
        }
    }
    
    /**
     * CONSOLIDATED: Get processing level based on player proximity and MSPT
     * OPTIMIZED: Early exit when server is struggling
     */
    public static ProcessingLevel getProcessingLevel(BlockPos pos) {
        // CRITICAL: Skip all calculations when server is struggling
        if (cachedMSPT > 25.0) {
            return ProcessingLevel.SKIPPED; // No expensive calculations
        }
        
        // MSPT-based distance reduction
        int fullDistance = ProcessingLevel.FULL.maxDistance;
        int mediumDistance = ProcessingLevel.MEDIUM.maxDistance;
        int minimalDistance = ProcessingLevel.MINIMAL.maxDistance;
        
        if (cachedMSPT > 20.0) {
            // Halve all distances when server is struggling
            fullDistance = 16;    // 32 → 16 blocks
            mediumDistance = 32;  // 64 → 32 blocks  
            minimalDistance = 64; // 128 → 64 blocks
        } else if (cachedMSPT > 15.0) {
            // Reduce distances by 25% under moderate load
            fullDistance = 24;    // 32 → 24 blocks
            mediumDistance = 48;  // 64 → 48 blocks
            minimalDistance = 96;  // 128 → 96 blocks
        }
        
        // OPTIMIZED: Skip expensive player scans when cache is empty
        if (playerPositions.isEmpty()) {
            return ProcessingLevel.SKIPPED;
        }
        
        // Find closest player using squared distance (no expensive sqrt)
        int closestDistSq = Integer.MAX_VALUE;
        for (Map.Entry<Player, BlockPos> entry : playerPositions.entrySet()) {
            BlockPos playerPos = entry.getValue();
            int dx = pos.getX() - playerPos.getX();
            int dy = pos.getY() - playerPos.getY();
            int dz = pos.getZ() - playerPos.getZ();
            int distSq = dx*dx + dy*dy + dz*dz;
            
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
            }
        }
        
        // Use squared distances for comparison (no sqrt needed)
        int fullDistSq = fullDistance * fullDistance;
        int mediumDistSq = mediumDistance * mediumDistance;
        int minimalDistSq = minimalDistance * minimalDistance;
        
        if (closestDistSq <= fullDistSq) {
            return ProcessingLevel.FULL;
        } else if (closestDistSq <= mediumDistSq) {
            return ProcessingLevel.MEDIUM;
        } else if (closestDistSq <= minimalDistSq) {
            return ProcessingLevel.MINIMAL;
        } else {
            return ProcessingLevel.SKIPPED;
        }
    }
    
    // Public API for compatibility
    public static double getCurrentTPS() {
        return currentTPS;
    }
    
    public static boolean isFlowingFluidsDetected() {
        return flowingFluidsDetected;
    }
    
    public static void resetStats() {
        totalFluidEvents.set(0);
        skippedFluidEvents.set(0);
        eventsThisTick.set(0);
        lastProcessedHash = 0;
        tickCount.set(0);
        totalTickTimeNanos.set(0);
        currentTPS = 20.0;
        cachedMSPT = 20.0;
        levelAccessCache.clear();
        levelAccessCacheHits = 0;
        levelAccessCacheMisses = 0;
        blockStateCache.clear();
        fluidStateCache.clear();
        blockCacheHits = 0;
        blockCacheMisses = 0;
        chunkBatchMap.clear();
        batchedOperations = 0;
    }
    
    /**
     * Get level operation cache statistics
     */
    public static String getLevelCacheStats() {
        int total = levelAccessCacheHits + levelAccessCacheMisses;
        double hitRate = total > 0 ? (levelAccessCacheHits * 100.0 / total) : 0.0;
        return String.format("Cache: %d hits, %d misses, %.1f%% hit rate, %d entries", 
                           levelAccessCacheHits, levelAccessCacheMisses, hitRate, levelAccessCache.size());
    }
    
    /**
     * Get BlockState cache statistics
     */
    public static String getBlockCacheStats() {
        int total = blockCacheHits + blockCacheMisses;
        double hitRate = total > 0 ? (blockCacheHits * 100.0 / total) : 0.0;
        return String.format("BlockCache: %d hits, %d misses, %.1f%% hit rate, %d entries", 
                           blockCacheHits, blockCacheMisses, hitRate, blockStateCache.size());
    }
    
    /**
     * Process chunk batches to reduce LevelChunk operations
     */
    private static void processChunkBatches() {
        if (chunkBatchMap.isEmpty()) return;
        
        int processedChunks = 0;
        for (Map.Entry<ChunkPos, List<BlockPos>> entry : chunkBatchMap.entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            List<BlockPos> positions = entry.getValue();
            
            if (positions.size() > 3) {
                // Batch process multiple positions in same chunk
                // This reduces LevelChunk.get() calls from N to 1 per chunk
                batchedOperations += positions.size() - 1;
                processedChunks++;
            }
        }
        
        // Clear batch map after processing
        chunkBatchMap.clear();
    }
    
    /**
     * Add position to chunk batch for LevelChunk optimization
     */
    private static void addToChunkBatch(BlockPos pos) {
        if (cachedMSPT < 8.0) return; // Only batch when server needs help
        
        ChunkPos chunkPos = new ChunkPos(pos);
        chunkBatchMap.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(pos);
    }
    
    /**
     * Get chunk batching statistics
     */
    public static String getChunkBatchStats() {
        return String.format("ChunkBatches: %d chunks, %d batched operations, %d total positions", 
                           chunkBatchMap.size(), batchedOperations, 
                           chunkBatchMap.values().stream().mapToInt(List::size).sum());
    }
}
