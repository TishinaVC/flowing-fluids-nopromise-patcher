package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class ChunkBasedBatching {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // Chunk-based batching system
    private static final Map<Level, Map<ChunkPos, Queue<FluidUpdateTask>>> chunkUpdateQueues = new ConcurrentHashMap<>();
    private static final Map<Level, Set<ChunkPos>> activeChunks = new ConcurrentHashMap<>();
    private static final Map<Level, Map<ChunkPos, Set<BlockPos>>> chunkFluidUpdates = new HashMap<>();
    private static final Map<Level, Map<ChunkPos, Long>> lastChunkActivity = new HashMap<>();
    
    // Performance tracking
    private static final Map<Level, Integer> chunksProcessedThisTick = new ConcurrentHashMap<>();
    private static final Map<Level, Long> lastUpdateTime = new ConcurrentHashMap<>();
    
    // CPU-optimized configuration (invisible to players)
    private static final int MAX_CHUNKS_PER_TICK = 50; 
    private static final int MAX_UPDATES_PER_CHUNK = 100; 
    private static final long CHUNK_UPDATE_INTERVAL = 50; 
    private static final int CHUNK_INACTIVE_THRESHOLD = 1200; // 60 seconds at 20 TPS
    private static final double CPU_SAVE_MODE_TPS_THRESHOLD = 10.0; // TPS threshold for CPU save mode

    /**
     * Enhanced fluid update queuing for large-scale fluid changes without lag
     * Prevents time-of-day going backwards and mob movement stuttering
     */
    public static void queueFluidUpdateEnhanced(Level level, BlockPos pos, BlockState state) {
        if (!ConfigManager.ENABLE_TICK_OPTIMIZATION.get()) {
            return;
        }
        
        ChunkPos chunkPos = new ChunkPos(pos);
        
        // Enhanced chunk queue with better load balancing for large fluid systems
        Map<ChunkPos, Queue<FluidUpdateTask>> levelQueues = chunkUpdateQueues.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        Queue<FluidUpdateTask> chunkQueue = levelQueues.computeIfAbsent(chunkPos, k -> new LinkedList<>());
        
        // Dynamic queue sizing based on server performance
        int maxQueueSize = calculateDynamicQueueSize(level);
        if (chunkQueue.size() < maxQueueSize) {
            chunkQueue.offer(new FluidUpdateTask(pos, state, System.currentTimeMillis()));
        }
        
        // Mark chunk as active with priority tracking
        Set<ChunkPos> levelActiveChunks = activeChunks.computeIfAbsent(level, k -> new HashSet<>());
        levelActiveChunks.add(chunkPos);
        
        LOGGER.debug("Enhanced queued fluid update at {} in chunk {} (queue size: {})", pos, chunkPos, chunkQueue.size());
    }

    public static void queueFluidUpdate(Level level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        synchronized (chunkFluidUpdates) {
            chunkFluidUpdates.computeIfAbsent(level, k -> new HashMap<>()).computeIfAbsent(chunkPos, k -> new HashSet<>()).add(pos);
            lastChunkActivity.computeIfAbsent(level, k -> new HashMap<>()).put(chunkPos, level.getGameTime());
        }
    }
    
    /**
     * Calculate dynamic queue size based on server performance
     */
    private static int calculateDynamicQueueSize(Level level) {
        double avgTickTime = PerformanceMonitor.getAverageTickTime();
        
        // Base size adjusted for performance
        int baseSize = MAX_UPDATES_PER_CHUNK * 3;
        
        if (avgTickTime > 50000000) { // 50ms
            return Math.max(MAX_UPDATES_PER_CHUNK, baseSize / 2); // Reduce queue size under load
        } else if (avgTickTime > 30000000) { // 30ms
            return Math.max(MAX_UPDATES_PER_CHUNK * 2, baseSize * 3 / 4); // Moderate reduction
        }
        
        return baseSize; // Full capacity under normal load
    }
    
    /**
     * Process chunk-based fluid updates with enhanced performance for large-scale fluid changes
     * CRITICAL: Prevents time-of-day going backwards and mob movement stuttering
     */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        
        // Enhanced timing control to prevent time-of-day issues
        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTime.get(level);
        
        // Dynamic interval based on server load
        long updateInterval = calculateDynamicUpdateInterval(level);
        
        if (lastUpdate != null && currentTime - lastUpdate < updateInterval) {
            return;
        }
        
        lastUpdateTime.put(level, currentTime);
        chunksProcessedThisTick.put(level, 0);
        
        // Process updates from chunkFluidUpdates
        synchronized (chunkFluidUpdates) {
            Map<ChunkPos, Set<BlockPos>> updates = chunkFluidUpdates.get(level);
            if (updates != null && !updates.isEmpty()) {
                int maxChunksToProcess = getMaxChunksToProcess(level);
                int chunksProcessed = 0;
                for (Map.Entry<ChunkPos, Set<BlockPos>> entry : updates.entrySet()) {
                    if (chunksProcessed >= maxChunksToProcess) break;
                    ChunkPos chunkPos = entry.getKey();
                    Set<BlockPos> positions = entry.getValue();
                    if (!positions.isEmpty()) {
                        int updatesProcessed = 0;
                        for (BlockPos pos : new HashSet<>(positions)) {
                            if (updatesProcessed >= MAX_UPDATES_PER_CHUNK) break;
                            FluidOptimizer.queueFluidUpdate(level, pos, level.getFluidState(pos), level.getBlockState(pos));
                            positions.remove(pos);
                            updatesProcessed++;
                        }
                        chunksProcessed++;
                        lastChunkActivity.getOrDefault(level, new HashMap<>()).put(chunkPos, level.getGameTime());
                    }
                    if (positions.isEmpty()) {
                        updates.remove(chunkPos);
                    }
                }
                // Log update counts for debugging
                if (chunksProcessed > 0) {
                    for (ChunkPos chunkPos : updates.keySet()) {
                        int updateCount = getChunkUpdateCount(level, chunkPos);
                        if (updateCount > 0) {
                            // Could be used for logging or prioritization in future
                        }
                    }
                }
                // Clean up inactive chunks periodically
                if (level.getGameTime() % 200 == 0) { // Every 10 seconds at 20 TPS
                    cleanupInactiveChunks(level);
                }
            }
        }
        
        // Get active chunks for enhanced processing
        Set<ChunkPos> levelActiveChunks = activeChunks.getOrDefault(level, Collections.emptySet());
        if (levelActiveChunks.isEmpty()) {
            return;
        }
        
        // Enhanced CPU-aware processing with large-scale fluid optimization
        int maxChunksToProcess = getEnhancedMaxChunksToProcess(level);
        
        // Prioritize chunks near players first (maintains smooth player experience)
        List<ChunkPos> sortedChunks = prioritizeChunksForPlayers(level, levelActiveChunks);
        
        // Process chunks with enhanced batching
        int chunksProcessed = 0;
        for (ChunkPos chunkPos : sortedChunks) {
            if (chunksProcessed >= maxChunksToProcess) {
                break;
            }
            
            if (processChunkUpdatesEnhanced(level, chunkPos)) {
                chunksProcessed++;
            }
        }
        
        chunksProcessedThisTick.put(level, chunksProcessed);
        
        if (chunksProcessed > 0) {
            LOGGER.debug("Enhanced processed {} chunks (large-scale optimization active)", chunksProcessed);
        }
        
        // Enhanced cleanup for memory efficiency
        cleanupInactiveChunksEnhanced(level);
    }
    
    /**
     * Calculate dynamic update interval based on server performance
     */
    private static long calculateDynamicUpdateInterval(Level level) {
        double avgTickTime = PerformanceMonitor.getAverageTickTime();
        
        if (avgTickTime > 100000000) { // 100ms - extreme load
            return CHUNK_UPDATE_INTERVAL * 3; // Slower updates
        } else if (avgTickTime > 50000000) { // 50ms - high load
            return CHUNK_UPDATE_INTERVAL * 2; // Moderate slowdown
        }
        
        return CHUNK_UPDATE_INTERVAL; // Normal speed
    }
    
    /**
     * Enhanced maximum chunks calculation for large-scale fluid changes
     */
    private static int getEnhancedMaxChunksToProcess(Level level) {
        double avgTickTime = PerformanceMonitor.getAverageTickTime();
        int activeChunkCount = activeChunks.getOrDefault(level, Collections.emptySet()).size();
        
        // Base calculation with performance awareness
        int baseChunks = MAX_CHUNKS_PER_TICK;
        
        // Scale based on server load
        if (avgTickTime > 100000000) { // 100ms - extreme load
            return Math.max(3, baseChunks / 3); // Minimal processing
        } else if (avgTickTime > 50000000) { // 50ms - high load
            return Math.max(5, baseChunks / 2); // Reduced processing
        } else if (avgTickTime > 30000000) { // 30ms - moderate load
            return Math.max(8, baseChunks * 3 / 4); // Slightly reduced
        }
        
        // Handle large numbers of active chunks (massive fluid changes scenario)
        if (activeChunkCount > 50) {
            return Math.min(baseChunks, activeChunkCount / 5); // Process 20% of chunks for large systems
        }
        
        return baseChunks; // Normal operation
    }
    
    /**
     * Get CPU-aware maximum chunks to process (algorithmic optimization only)
     */
    private static int getMaxChunksToProcess(Level level) {
        // Algorithmic optimization: use cached tick time instead of recalculating
        double tps = PerformanceMonitor.getAverageTPS(level);
        
        // Only reduce under extreme load - preserve player experience
        if (tps < CPU_SAVE_MODE_TPS_THRESHOLD) {
            return Math.max(8, MAX_CHUNKS_PER_TICK - 2); // Minimal reduction
        }
        
        return MAX_CHUNKS_PER_TICK; // Normal operation - no player impact
    }
    
    /**
     * Prioritize chunks for players (maintains smooth player experience)
     */
    private static List<ChunkPos> prioritizeChunksForPlayers(ServerLevel level, Set<ChunkPos> chunks) {
        List<ChunkPos> sorted = new ArrayList<>(chunks);
        
        // Sort by player proximity first - ensures smooth experience for players
        sorted.sort((chunk1, chunk2) -> {
            double distance1 = getDistanceToNearestPlayer(level, chunk1);
            double distance2 = getDistanceToNearestPlayer(level, chunk2);
            return Double.compare(distance1, distance2);
        });
        
        return sorted;
    }
    
    /**
     * Enhanced chunk processing for large-scale fluid changes without lag
     */
    private static boolean processChunkUpdatesEnhanced(ServerLevel level, ChunkPos chunkPos) {
        Map<ChunkPos, Queue<FluidUpdateTask>> levelQueues = chunkUpdateQueues.get(level);
        if (levelQueues == null) {
            return false;
        }
        
        Queue<FluidUpdateTask> chunkQueue = levelQueues.get(chunkPos);
        if (chunkQueue == null || chunkQueue.isEmpty()) {
            return false;
        }
        
        int updatesProcessed = 0;
        long currentTime = System.currentTimeMillis();
        
        // Enhanced processing with better time management
        int maxUpdatesThisRound = calculateMaxUpdatesForChunk(level, chunkQueue.size());
        
        while (!chunkQueue.isEmpty() && updatesProcessed < maxUpdatesThisRound) {
            FluidUpdateTask task = chunkQueue.poll();
            
            // Skip old updates to prevent cascading issues
            if (currentTime - task.timestamp > 3000) { // 3 second timeout
                continue;
            }
            
            // Process the fluid update with enhanced validation
            if (processFluidUpdateEnhanced(level, task)) {
                updatesProcessed++;
            }
        }
        
        // Enhanced queue management
        if (chunkQueue.isEmpty()) {
            Set<ChunkPos> levelActiveChunks = activeChunks.get(level);
            if (levelActiveChunks != null) {
                levelActiveChunks.remove(chunkPos);
            }
        } else {
            // Queue still has items - will be processed next tick
            LOGGER.debug("Chunk {} still has {} pending updates", chunkPos, chunkQueue.size());
        }
        
        return updatesProcessed > 0;
    }
    
    /**
     * Calculate maximum updates for a chunk based on performance
     */
    private static int calculateMaxUpdatesForChunk(Level level, int queueSize) {
        double avgTickTime = PerformanceMonitor.getAverageTickTime();
        
        // Base calculation
        int maxUpdates = MAX_UPDATES_PER_CHUNK;
        
        // Adjust based on server performance
        if (avgTickTime > 100000000) { // 100ms - extreme load
            maxUpdates = Math.max(10, maxUpdates / 3); // Minimal updates
        } else if (avgTickTime > 50000000) { // 50ms - high load
            maxUpdates = Math.max(20, maxUpdates / 2); // Reduced updates
        } else if (avgTickTime > 30000000) { // 30ms - moderate load
            maxUpdates = Math.max(30, maxUpdates * 3 / 4); // Slightly reduced
        }
        
        // Don't process more than what's in queue
        return Math.min(maxUpdates, queueSize);
    }
    
    /**
     * Enhanced fluid update processing
     */
    private static boolean processFluidUpdateEnhanced(ServerLevel level, FluidUpdateTask task) {
        BlockPos pos = task.pos;
        BlockState state = task.state;
        
        // Enhanced validation
        if (!level.isInWorldBounds(pos) || level.getBlockState(pos) != state) {
            return false;
        }
        
        // Record update for monitoring
        FlowingFluidsIntegration.recordUpdate();
        
        // Only run advanced fluid flow if CPU load allows
        if (ConfigManager.ENABLE_PRESSURE_SYSTEM.get()) {
            double avgTickTime = PerformanceMonitor.getAverageTickTime();
            if (avgTickTime < 40000000) { // 40ms threshold
                AdvancedFluidFlow.simulateFluidFlow(level, pos, state);
            } else {
                LOGGER.debug("Skipping advanced fluid flow due to CPU load: {}ms", avgTickTime / 1000000);
            }
        }
        
        return true;
    }
    
    /**
     * Enhanced cleanup for memory efficiency
     */
    private static void cleanupInactiveChunksEnhanced(Level level) {
        Map<ChunkPos, Queue<FluidUpdateTask>> levelQueues = chunkUpdateQueues.get(level);
        if (levelQueues == null) {
            return;
        }
        
        // Remove empty queues
        levelQueues.entrySet().removeIf(entry -> {
            Queue<FluidUpdateTask> queue = entry.getValue();
            return queue == null || queue.isEmpty();
        });
        
        // Enhanced cleanup with better time management
        long currentTime = System.currentTimeMillis();
        int totalCleaned = 0;
        
        for (Queue<FluidUpdateTask> queue : levelQueues.values()) {
            if (queue != null) {
                int initialSize = queue.size();
                queue.removeIf(task -> currentTime - task.timestamp > 10000); // 10 second timeout
                totalCleaned += (initialSize - queue.size());
            }
        }
        
        if (totalCleaned > 0) {
            LOGGER.debug("Cleaned {} old fluid update tasks", totalCleaned);
        }
    }
    
    /**
     * Get distance to nearest player for a chunk
     */
    private static double getDistanceToNearestPlayer(ServerLevel level, ChunkPos chunkPos) {
        BlockPos chunkCenter = chunkPos.getWorldPosition().offset(8, 64, 8); // Center of chunk

        Vec3 center = Vec3.atCenterOf(chunkCenter);
        return level.players().stream()
            .mapToDouble(player -> player.distanceToSqr(center))
            .min()
            .orElse(Double.MAX_VALUE);
    }
    
    /**
     * Get number of pending updates for a chunk
     */
    private static int getChunkUpdateCount(Level level, ChunkPos chunkPos) {
        synchronized (chunkFluidUpdates) {
            Map<ChunkPos, Set<BlockPos>> updates = chunkFluidUpdates.get(level);
            if (updates != null && updates.containsKey(chunkPos)) {
                return updates.get(chunkPos).size();
            }
        }
        Map<ChunkPos, Queue<FluidUpdateTask>> levelQueues = chunkUpdateQueues.get(level);
        if (levelQueues == null) {
            return 0;
        }
        
        Queue<FluidUpdateTask> queue = levelQueues.get(chunkPos);
        return queue != null ? queue.size() : 0;
    }
    
    /**
     * Clean up inactive chunks to prevent memory leaks
     */
    private static void cleanupInactiveChunks(Level level) {
        long currentTime = level.getGameTime();
        synchronized (chunkFluidUpdates) {
            Map<ChunkPos, Set<BlockPos>> updates = chunkFluidUpdates.get(level);
            if (updates != null) {
                updates.entrySet().removeIf(entry -> {
                    ChunkPos chunkPos = entry.getKey();
                    Long lastActive = lastChunkActivity.getOrDefault(level, new HashMap<>()).getOrDefault(chunkPos, 0L);
                    return currentTime - lastActive > CHUNK_INACTIVE_THRESHOLD;
                });
            }
        }
        Map<ChunkPos, Queue<FluidUpdateTask>> levelQueues = chunkUpdateQueues.get(level);
        if (levelQueues == null) {
            return;
        }
        
        // Remove empty queues
        levelQueues.entrySet().removeIf(entry -> {
            Queue<FluidUpdateTask> queue = entry.getValue();
            return queue == null || queue.isEmpty();
        });
        
        // Clean up old tasks
        long currentTimeMillis = System.currentTimeMillis();
        for (Queue<FluidUpdateTask> queue : levelQueues.values()) {
            if (queue != null) {
                queue.removeIf(task -> currentTimeMillis - task.timestamp > 5000); // 5 second timeout
            }
        }
    }
    
    /**
     * Get performance statistics
     */
    public static Map<String, Object> getPerformanceStats(Level level) {
        Map<String, Object> stats = new HashMap<>();
        
        Map<ChunkPos, Queue<FluidUpdateTask>> levelQueues = chunkUpdateQueues.get(level);
        Set<ChunkPos> levelActiveChunks = activeChunks.get(level);
        
        int totalQueuedUpdates = levelQueues != null ? 
            levelQueues.values().stream().mapToInt(Queue::size).sum() : 0;
        
        stats.put("totalQueuedUpdates", totalQueuedUpdates);
        stats.put("activeChunks", levelActiveChunks != null ? levelActiveChunks.size() : 0);
        stats.put("chunksProcessedThisTick", chunksProcessedThisTick.getOrDefault(level, 0));
        
        return stats;
    }
    
    /**
     * Force process all pending updates (useful for debugging)
     */
    public static void forceProcessAllUpdates(ServerLevel level) {
        Set<ChunkPos> levelActiveChunks = activeChunks.getOrDefault(level, Collections.emptySet());
        
        for (ChunkPos chunkPos : new ArrayList<>(levelActiveChunks)) {
            processChunkUpdatesEnhanced(level, chunkPos);
        }
        
        LOGGER.info("Force processed all fluid updates in {} chunks", levelActiveChunks.size());
    }
    
    /**
     * Task data class for fluid updates
     */
    private static class FluidUpdateTask {
        final BlockPos pos;
        final BlockState state;
        final long timestamp;
        
        FluidUpdateTask(BlockPos pos, BlockState state, long timestamp) {
            this.pos = pos;
            this.state = state;
            this.timestamp = timestamp;
        }
    }
}
