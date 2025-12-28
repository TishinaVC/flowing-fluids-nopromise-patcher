package flowingfluidsfixes;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class PerformanceMonitor {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // Performance tracking data
    private static final Map<Level, PerformanceData> performanceData = new ConcurrentHashMap<>();
    private static final Map<Level, List<Long>> tickTimes = new ConcurrentHashMap<>();
    private static final Map<Level, List<Long>> tickDurations = new ConcurrentHashMap<>();
    private static final Map<Level, Integer> fluidUpdateCounts = new ConcurrentHashMap<>();
    
    // Performance thresholds with CPU optimization focus
    private static final long TICK_TIME_WARNING_THRESHOLD = 40000000; // 40ms (reduced from 50ms)
    private static final long TICK_TIME_CRITICAL_THRESHOLD = 80000000; // 80ms (reduced from 100ms)
    private static final int FLUID_UPDATE_WARNING_THRESHOLD = 3000; // Reduced from 5000
    private static final int FLUID_UPDATE_CRITICAL_THRESHOLD = 6000; // Reduced from 10000
    
    // CPU monitoring state
    private static boolean cpuOptimizationMode = false;
    private static long lastCPUCheck = 0;
    private static final long CPU_CHECK_INTERVAL = 5000; // 5 seconds
    private static double cpuUsageEstimate = 0.0;
    
    // Monitoring state
    private static boolean monitoringEnabled = true;
    private static long lastReportTime = 0;
    private static final long REPORT_INTERVAL = 60000; // 1 minute
    
    private static final int TICK_HISTORY_SIZE = 100;
    private static final Collection<Long> tickTimesGlobal = new ArrayList<>(TICK_HISTORY_SIZE);
    private static long lastTickTime = 0;

    /**
     * Track server tick performance
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!monitoringEnabled || event.phase != TickEvent.Phase.START) {
            return;
        }
        
        // Record tick start time for all levels
        for (Level level : tickTimes.keySet()) {
            List<Long> levelTickTimes = tickTimes.get(level);
            levelTickTimes.add(System.nanoTime());
        }
    }
    
    @SubscribeEvent
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (!monitoringEnabled || event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Calculate tick duration and update statistics
        for (Level level : tickTimes.keySet()) {
            List<Long> levelTickTimes = tickTimes.get(level);
            if (!levelTickTimes.isEmpty()) {
                long startTime = levelTickTimes.remove(0);
                long duration = System.nanoTime() - startTime;
                
                List<Long> levelDurations = tickDurations.computeIfAbsent(level, k -> new ArrayList<>());
                levelDurations.add(duration);
                
                // Keep only recent measurements (last 100 ticks)
                if (levelDurations.size() > 100) {
                    Iterator<Long> iterator = levelDurations.iterator();
                    if (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();
                    }
                }
            }
        }
        
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        
        // Update performance data
        for (ServerLevel level : server.getAllLevels()) {
            List<Long> levelDurations = tickDurations.get(level);
            if (levelDurations != null && !levelDurations.isEmpty()) {
                double avgDuration = levelDurations.stream().mapToLong(Long::longValue).average().orElse(0.0);

                updatePerformanceData(level, (long) avgDuration);
            }
        }
        
        // Periodic performance reporting
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastReportTime > REPORT_INTERVAL) {
            lastReportTime = currentTime;
            generatePerformanceReport();
        }
        
        // Update global tick times
        if (lastTickTime != 0) {
            long tickDuration = currentTime - lastTickTime;
            synchronized (tickTimesGlobal) {
                tickTimesGlobal.add(tickDuration);
                if (tickTimesGlobal.size() > TICK_HISTORY_SIZE) {
                    Iterator<Long> iterator = tickTimesGlobal.iterator();
                    if (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();
                    }
                }
            }
        }
        lastTickTime = currentTime;
    }
    
    /**
     * Update performance data for a level with CPU monitoring
     */
    private static void updatePerformanceData(Level level, long tickDuration) {
        PerformanceData data = performanceData.computeIfAbsent(level, k -> new PerformanceData());
        
        data.totalTicks++;
        data.totalTickTime += tickDuration;
        data.maxTickTime = Math.max(data.maxTickTime, tickDuration);
        
        // Calculate rolling average (last 50 ticks instead of 100 for CPU efficiency)
        data.recentTickTimes.add(tickDuration);
        if (data.recentTickTimes.size() > 50) {
            Iterator<Long> iterator = data.recentTickTimes.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        
        // CPU monitoring and adaptive optimization
        updateCPUMonitoring(tickDuration);
        
        // Check for performance warnings with lower thresholds
        if (tickDuration > TICK_TIME_CRITICAL_THRESHOLD) {
            data.criticalTicks++;
            LOGGER.warn("Critical tick time detected in level {}: {}ms - CPU optimization active", 
                level.dimension().location(), tickDuration / 1000000.0);
            activateCPUOptimization();
        } else if (tickDuration > TICK_TIME_WARNING_THRESHOLD) {
            data.warningTicks++;
            LOGGER.debug("High tick time detected in level {}: {}ms", 
                level.dimension().location(), tickDuration / 1000000.0);
        }
        
        // Update fluid update count with lower threshold
        int fluidUpdates = fluidUpdateCounts.getOrDefault(level, 0);
        if (fluidUpdates > FLUID_UPDATE_CRITICAL_THRESHOLD) {
            LOGGER.warn("Critical fluid update count in level {}: {} - CPU throttling active", 
                level.dimension().location(), fluidUpdates);
            activateCPUOptimization();
        } else if (fluidUpdates > FLUID_UPDATE_WARNING_THRESHOLD) {
            LOGGER.debug("High fluid update count in level {}: {}", 
                level.dimension().location(), fluidUpdates);
        }
        
        // Reset fluid update count for next tick
        fluidUpdateCounts.put(level, 0);
    }
    
    /**
     * Update CPU monitoring and adaptive optimization
     */
    private static void updateCPUMonitoring(long tickDuration) {
        long currentTime = System.currentTimeMillis();
        
        // Update CPU usage estimate based on tick time
        if (tickDuration > 50000000) { // 50ms indicates high CPU usage
            cpuUsageEstimate = Math.min(1.0, cpuUsageEstimate + 0.1);
        } else if (tickDuration < 20000000) { // 20ms indicates low CPU usage
            cpuUsageEstimate = Math.max(0.0, cpuUsageEstimate - 0.05);
        }
        
        // Periodic CPU check
        if (currentTime - lastCPUCheck > CPU_CHECK_INTERVAL) {
            lastCPUCheck = currentTime;
            
            if (cpuUsageEstimate > 0.7) {
                activateCPUOptimization();
            } else if (cpuUsageEstimate < 0.3) {
                deactivateCPUOptimization();
            }
            
            LOGGER.debug("CPU usage estimate: {}, optimization mode: {}", 
                cpuUsageEstimate, cpuOptimizationMode ? "ACTIVE" : "NORMAL");
        }
    }
    
    /**
     * Activate CPU optimization mode
     */
    private static void activateCPUOptimization() {
        if (!cpuOptimizationMode) {
            cpuOptimizationMode = true;
            LOGGER.info("CPU optimization mode activated - reducing fluid processing overhead");
        }
    }
    
    /**
     * Deactivate CPU optimization mode
     */
    private static void deactivateCPUOptimization() {
        if (cpuOptimizationMode) {
            cpuOptimizationMode = false;
            LOGGER.info("CPU optimization mode deactivated - normal fluid processing resumed");
        }
    }
    
    /**
     * Check if CPU optimization mode is active
     */
    public static boolean isCPUOptimizationMode() {
        return cpuOptimizationMode;
    }
    
    /**
     * Get current CPU usage estimate
     */
    public static double getCPUUsageEstimate() {
        return cpuUsageEstimate;
    }
    
    /**
     * Record a fluid update for performance tracking
     */
    public static void recordFluidUpdate(Level level) {
        if (!monitoringEnabled) {
            return;
        }
        
        fluidUpdateCounts.merge(level, 1, Integer::sum);
        
        PerformanceData data = performanceData.computeIfAbsent(level, k -> new PerformanceData());
        data.totalFluidUpdates++;
    }
    
    /**
     * Generate and log performance report
     */
    private static void generatePerformanceReport() {
        LOGGER.info("=== Performance Fix Report ===");
        
        for (Map.Entry<Level, PerformanceData> entry : performanceData.entrySet()) {
            Level level = entry.getKey();
            PerformanceData data = entry.getValue();
            
            double avgTickTime = data.totalTicks > 0 ? 
                (double) data.totalTickTime / data.totalTicks / 1000000.0 : 0.0;
            
            double recentAvgTickTime = !data.recentTickTimes.isEmpty() ?
                data.recentTickTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000000.0 : 0.0;
            
            LOGGER.info("Level: {}", level.dimension().location());
            LOGGER.info("  Total ticks: {}", data.totalTicks);
            LOGGER.info("  Average tick time: {}ms", String.format("%.2f", avgTickTime));
            LOGGER.info("  Recent average tick time: {}ms", String.format("%.2f", recentAvgTickTime));
            LOGGER.info("  Max tick time: {}ms", String.format("%.2f", data.maxTickTime / 1000000.0));
            LOGGER.info("  Warning ticks: {} ({}%)", 
                data.warningTicks, 
                String.format("%.1f", data.totalTicks > 0 ? (data.warningTicks * 100.0 / data.totalTicks) : 0.0));
            LOGGER.info("  Critical ticks: {} ({}%)", 
                data.criticalTicks, 
                String.format("%.1f", data.totalTicks > 0 ? (data.criticalTicks * 100.0 / data.totalTicks) : 0.0));
            LOGGER.info("  Total fluid updates: {}", data.totalFluidUpdates);
            
            // Get chunk batching stats
            Map<String, Object> chunkStats = ChunkBasedBatching.getPerformanceStats(level);
            LOGGER.info("  Queued chunk updates: {}", chunkStats.get("totalQueuedUpdates"));
            LOGGER.info("  Active chunks: {}", chunkStats.get("activeChunks"));
            LOGGER.info("  Chunks processed this tick: {}", chunkStats.get("chunksProcessedThisTick"));
        }
        
        LOGGER.info("=== End Report ===");
    }
    
    /**
     * Get current performance statistics for a level
     */
    public static Map<String, Object> getCurrentStats(Level level) {
        Map<String, Object> stats = new HashMap<>();
        
        PerformanceData data = performanceData.get(level);
        if (data != null) {
            stats.put("totalTicks", data.totalTicks);
            stats.put("averageTickTime", data.totalTicks > 0 ? 
                data.totalTickTime / data.totalTicks / 1000000.0 : 0.0);
            stats.put("maxTickTime", data.maxTickTime / 1000000.0);
            stats.put("warningTicks", data.warningTicks);
            stats.put("criticalTicks", data.criticalTicks);
            stats.put("totalFluidUpdates", data.totalFluidUpdates);
        }
        
        // Add chunk batching stats
        stats.putAll(ChunkBasedBatching.getPerformanceStats(level));
        
        return stats;
    }
    
    /**
     * Check if performance optimization should be triggered
     */
    public static boolean shouldOptimize(Level level) {
        PerformanceData data = performanceData.get(level);
        if (data == null) {
            return false;
        }
        
        // Optimize if recent average tick time is high
        if (!data.recentTickTimes.isEmpty()) {
            double recentAvg = data.recentTickTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0) / 1000000.0;
            
            return recentAvg > 40.0; // 40ms threshold
        }
        
        return false;
    }
    
    /**
     * Enable or disable monitoring
     */
    public static void setMonitoringEnabled(boolean enabled) {
        monitoringEnabled = enabled;
        LOGGER.info("Performance monitoring {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Reset performance data
     */
    public static void resetData() {
        performanceData.clear();
        tickTimes.clear();
        fluidUpdateCounts.clear();
        lastReportTime = 0;
        LOGGER.info("Performance monitoring data reset");
    }
    
    /**
     * Get optimization recommendations based on current performance
     */
    public static List<String> getOptimizationRecommendations(Level level) {
        List<String> recommendations = new ArrayList<>();
        
        PerformanceData data = performanceData.get(level);
        if (data == null) {
            return recommendations;
        }
        
        double avgTickTime = data.totalTicks > 0 ? 
            (double) data.totalTickTime / data.totalTicks / 1000000.0 : 0.0;
        
        if (avgTickTime > 50.0) {
            recommendations.add("Consider reducing maxFluidUpdatesPerTick in config");
        }
        
        if (data.totalFluidUpdates > 50000) {
            recommendations.add("Consider increasing tickDelay in config");
        }
        
        if (data.criticalTicks > data.totalTicks * 0.1) {
            recommendations.add("Server performance is critical - consider reducing flow distance");
        }
        
        Map<String, Object> chunkStats = ChunkBasedBatching.getPerformanceStats(level);
        int queuedUpdates = (Integer) chunkStats.get("totalQueuedUpdates");
        
        if (queuedUpdates > 10000) {
            recommendations.add("Consider reducing batchSize or increasing chunk processing limits");
        }
        
        return recommendations;
    }
    
    /**
     * Get average tick time across all levels
     */
    public static double getAverageTickTime() {
        if (performanceData.isEmpty()) {
            return 0.0;
        }
        
        double totalTime = 0.0;
        int count = 0;
        
        for (PerformanceData data : performanceData.values()) {
            if (data.totalTicks > 0) {
                totalTime += (double) data.totalTickTime / data.totalTicks;
                count++;
            }
        }
        
        return count > 0 ? totalTime / count : 0.0;
    }
    
    /**
     * Get average TPS across all levels
     */
    public static double getAverageTPS(Level level) {
        synchronized (tickTimesGlobal) {
            if (tickTimesGlobal.isEmpty()) return 20.0;
            long totalDuration = 0;
            for (Long duration : tickTimesGlobal) {
                totalDuration += duration;
            }
            double averageDuration = totalDuration / (double) tickTimesGlobal.size();
            return Math.min(20.0, 1000.0 / averageDuration);
        }
    }
    
    /**
     * Performance data container
     */
    private static class PerformanceData {
        long totalTicks = 0;
        long totalTickTime = 0;
        long maxTickTime = 0;
        int warningTicks = 0;
        int criticalTicks = 0;
        long totalFluidUpdates = 0;
        Queue<Long> recentTickTimes = new LinkedList<>();
    }
}
