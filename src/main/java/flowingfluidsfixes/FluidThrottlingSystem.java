package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class FluidThrottlingSystem {
    private static final int BASE_MAX_UPDATES_PER_TICK = 3000; 
    private static final int PLAYER_PRIORITY_RADIUS = 64; 
    private static final int CHUNK_PRIORITY_RADIUS = 2; 
    private static final double THROTTLE_FACTOR = 0.8; 
    private static final int CPU_THRESHOLD_UPDATES = 2500; 
    private static final double CPU_LOAD_THRESHOLD = 0.8; 
    
    private static final int FLOW_PRIORITY_DISTANCE = 5; 
    private static final double SOURCE_BLOCK_PRIORITY = 2.0; 
    private static final double HIGH_FLUID_LEVEL_PRIORITY = 1.5; 
    
    private static final Map<Level, ThrottlingState> THROTTLING_STATES = new ConcurrentHashMap<>();
    private static final AtomicInteger GLOBAL_UPDATE_COUNT = new AtomicInteger(0);
    private static final Set<BlockPos> processedPositions = new HashSet<>();
    private static int currentUpdateLimit = 3000;
    private static final int MAX_UPDATES_PER_TICK = 10000;
    private static final int MIN_UPDATES_PER_TICK = 800;
    private static final double TARGET_TPS = 20.0;

    public static boolean shouldAllowUpdate(Level level, BlockPos pos) {
        ThrottlingState state = THROTTLING_STATES.computeIfAbsent(level, k -> new ThrottlingState());
        
        if (shouldThrottleForCPU(state)) {
            state.rejectedUpdates++;
            return false;
        }
        
        if (EmergencyPerformanceMode.isEmergencyMode()) {
            return true;
        }
        
        int globalCount = GLOBAL_UPDATE_COUNT.get();
        int maxGlobal = getCurrentUpdateLimit();
        
        if (globalCount >= maxGlobal) {
            state.rejectedUpdates++;
            return false;
        }
        
        if (state.updateCount >= state.maxUpdatesThisTick) {
            state.rejectedUpdates++;
            return false;
        }
        
        double flowPriority = calculateFlowPriority(level, pos, state);
        
        if (flowPriority >= 1.0) {
            GLOBAL_UPDATE_COUNT.incrementAndGet();
            state.updateCount++;
            state.priorityUpdates++;
            return true;
        }
        
        if (flowPriority >= 0.5 && shouldAllowStandardUpdate(level, pos, state)) {
            GLOBAL_UPDATE_COUNT.incrementAndGet();
            state.updateCount++;
            return true;
        }
        
        if (shouldAllowStandardUpdate(level, pos, state)) {
            GLOBAL_UPDATE_COUNT.incrementAndGet();
            state.updateCount++;
            return true;
        }
        
        state.rejectedUpdates++;
        return false;
    }
    
    private static double calculateFlowPriority(Level level, BlockPos pos, ThrottlingState throttlingState) {
        double priority = 0.0;
        
        try {
            BlockState blockState = level.getBlockState(pos);
            var fluidState = blockState.getFluidState();
            
            if (fluidState.isEmpty()) {
                return 0.0;
            }
            
            int fluidAmount = fluidState.getAmount();
            if (fluidAmount >= 8) {
                priority += SOURCE_BLOCK_PRIORITY; 
            } else if (fluidAmount > 4) {
                priority += HIGH_FLUID_LEVEL_PRIORITY; 
            } else if (fluidAmount >= 4) {
                priority += 1.0; 
            } else if (fluidAmount >= 2) {
                priority += 0.5; 
            }
            
            int flowPotential = countFlowPotential(level, pos);
            priority += flowPotential * 0.4; 
            
            int elevation = pos.getY();
            if (elevation < 60) {
                priority += 0.3; 
            } else if (elevation > 80) {
                priority += 0.2; 
            }
            
            if (BiomeOptimization.isInfiniteSource(level, pos)) {
                priority += 1.0;
            }
            
            if (isNearPlayer(level, pos)) {
                priority += 0.2;
            }
            
        } catch (Exception e) {
            priority = 0.5;
        }
        
        return Math.min(3.0, priority); 
    }
    
    private static int countFlowPotential(Level level, BlockPos pos) {
        int potential = 0;
        
        for (Direction direction : Direction.values()) {
            BlockPos checkPos = pos.relative(direction);
            if (!level.isInWorldBounds(checkPos)) {
                continue;
            }
            
            BlockState checkState = level.getBlockState(checkPos);
            if (checkState.isAir() || checkState.getFluidState().isEmpty()) {
                potential++;
            }
        }
        
        return potential;
    }
    
    private static boolean isNearPlayer(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            for (Player player : serverLevel.players()) {
                double dx = player.getX() - pos.getX();
                double dy = player.getY() - pos.getY();
                double dz = player.getZ() - pos.getZ();
                double distanceSq = dx * dx + dy * dy + dz * dz;
                
                if (distanceSq <= PLAYER_PRIORITY_RADIUS * PLAYER_PRIORITY_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static boolean shouldThrottleForCPU(ThrottlingState state) {
        double avgTickTime = PerformanceMonitor.getAverageTickTime();
        if (avgTickTime > 100000000) { 
            return state.updateCount > 1500; 
        }
        
        return false; 
    }
    
    private static boolean shouldAllowStandardUpdate(Level level, BlockPos pos, ThrottlingState state) {
        double utilization = (double) state.updateCount / state.maxUpdatesThisTick;
        
        if (utilization > THROTTLE_FACTOR) {
            return Math.random() < (1.0 - utilization);
        }
        
        return true;
    }
    
    @SubscribeEvent
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            GLOBAL_UPDATE_COUNT.set(0);
            
            for (ThrottlingState state : THROTTLING_STATES.values()) {
                state.reset();
                state.updatePlayerProximity(event.getServer().overworld());
            }
        }
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Level level = event.getServer().overworld();
            double currentTPS = PerformanceMonitor.getAverageTPS(level);
            adjustUpdateLimit(currentTPS);
            processedPositions.clear();
            // Additional logic for throttling fluid updates if needed
            if (EmergencyPerformanceMode.isEmergencyMode()) {
                // Adjust update limit based on emergency mode
                currentUpdateLimit = Math.min(currentUpdateLimit, EmergencyPerformanceMode.getMaxUpdatesPerTick());
            }
            // Apply CPU load threshold logic
            if (currentTPS < CPU_LOAD_THRESHOLD) {
                currentUpdateLimit = Math.max(MIN_UPDATES_PER_TICK, Math.min(currentUpdateLimit, CPU_THRESHOLD_UPDATES));
            }
        }
    }

    private static void adjustUpdateLimit(double currentTPS) {
        if (currentTPS < TARGET_TPS * 0.8) {
            currentUpdateLimit = Math.max(MIN_UPDATES_PER_TICK, currentUpdateLimit - 1000);
        } else if (currentTPS > TARGET_TPS * 1.2) {
            currentUpdateLimit = Math.min(MAX_UPDATES_PER_TICK, currentUpdateLimit + 1000);
        }
    }

    public static int getCurrentUpdateLimit() {
        return currentUpdateLimit;
    }

    /**
     * Calculate priority for fluid updates based on multiple factors:
     * - Source blocks get highest priority (they drive flow)
     * - High fluid levels get boosted priority (more flow potential)
     * - Proximity to players increases priority (visible to players)
     * - Flow potential (empty neighbors) increases priority
     * - Biome-specific factors from BiomeOptimization
     */
    public static int calculatePriority(Level level, BlockPos pos, FluidState state, BlockState blockState) {
        int priority = 0;
        
        // Source blocks are critical - they drive all fluid flow
        if (state.isSource()) {
            priority += (int) (SOURCE_BLOCK_PRIORITY * 10);
        }
        
        // High fluid levels have more flow potential
        int fluidAmount = state.getAmount();
        if (fluidAmount >= 8) {
            priority += (int) (HIGH_FLUID_LEVEL_PRIORITY * 10);
        } else if (fluidAmount > 4) {
            priority += (int) (HIGH_FLUID_LEVEL_PRIORITY * 5);
        } else if (fluidAmount > 0) {
            priority += fluidAmount;
        }
        
        // Count flow potential - empty neighbors mean fluid needs to spread
        int flowPotential = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            if (level.isInWorldBounds(neighborPos)) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.isAir() || neighborState.getFluidState().isEmpty()) {
                    flowPotential++;
                }
            }
        }
        priority += flowPotential * 3;
        
        // Proximity to players - visible fluid should update first
        if (level instanceof ServerLevel serverLevel) {
            double distanceToPlayer = serverLevel.players().stream()
                .mapToDouble(player -> player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()))
                .min()
                .orElse(Double.MAX_VALUE);
            
            if (distanceToPlayer < PLAYER_PRIORITY_RADIUS * PLAYER_PRIORITY_RADIUS) {
                // Inverse distance weighting - closer = higher priority
                double distanceFactor = 1.0 - (Math.sqrt(distanceToPlayer) / PLAYER_PRIORITY_RADIUS);
                priority += (int) (FLOW_PRIORITY_DISTANCE * distanceFactor * 5);
            }
        }
        
        // Biome-specific priority adjustment
        BiomeOptimization.BiomeProfile biomeProfile = BiomeOptimization.getProfile(level, pos);
        if (biomeProfile.infiniteSource) {
            priority += 5; // Infinite sources need regular updates
        }
        priority = (int) (priority * biomeProfile.flowMultiplier);
        
        // Elevation-based priority (water flows downhill)
        int y = pos.getY();
        if (y > 64) {
            priority += (y - 64) / 10; // Higher elevation = more flow potential
        }
        
        return Math.max(0, priority);
    }
    
    public static Map<String, Object> getStats(Level level) {
        Map<String, Object> stats = new HashMap<>();
        ThrottlingState state = THROTTLING_STATES.get(level);
        
        if (state != null) {
            stats.put("updateCount", state.updateCount);
            stats.put("maxUpdates", state.maxUpdatesThisTick);
            stats.put("rejectedUpdates", state.rejectedUpdates);
            stats.put("priorityUpdates", state.priorityUpdates);
            stats.put("nearPlayerCount", state.nearPlayers.size());
            stats.put("priorityChunkCount", state.priorityChunks.size());
        }
        
        stats.put("globalUpdateCount", GLOBAL_UPDATE_COUNT.get());
        
        return stats;
    }
    
    private static class ThrottlingState {
        int updateCount;
        int maxUpdatesThisTick;
        int rejectedUpdates;
        int priorityUpdates;
        final Set<BlockPos> nearPlayers = new HashSet<>();
        final Set<ChunkPos> priorityChunks = new HashSet<>();
        
        public ThrottlingState() {
            reset();
        }
        
        public void reset() {
            this.updateCount = 0;
            this.maxUpdatesThisTick = calculateMaxUpdates();
            this.rejectedUpdates = 0;
            this.priorityUpdates = 0;
            this.nearPlayers.clear();
            this.priorityChunks.clear();
        }
        
        private int calculateMaxUpdates() {
            int base = BASE_MAX_UPDATES_PER_TICK;
            
            double avgTickTime = PerformanceMonitor.getAverageTickTime();
            if (avgTickTime > 50000000) {
                base = (int) (base * 0.7);
            } else if (avgTickTime > 30000000) {
                base = (int) (base * 0.85);
            }
            
            return Math.max(100, base);
        }
        
        public void updatePlayerProximity(Level level) {
            if (level instanceof ServerLevel serverLevel) {
                nearPlayers.clear();
                priorityChunks.clear();
                
                for (Player player : serverLevel.players()) {
                    BlockPos playerPos = player.blockPosition();
                    nearPlayers.add(playerPos);
                    
                    ChunkPos playerChunk = new ChunkPos(playerPos);
                    for (int x = -CHUNK_PRIORITY_RADIUS; x <= CHUNK_PRIORITY_RADIUS; x++) {
                        for (int z = -CHUNK_PRIORITY_RADIUS; z <= CHUNK_PRIORITY_RADIUS; z++) {
                            priorityChunks.add(new ChunkPos(playerChunk.x + x, playerChunk.z + z));
                        }
                    }
                }
            }
        }
    }
}
