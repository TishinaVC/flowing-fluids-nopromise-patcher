package flowingfluidsfixes;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static ForgeConfigSpec COMMON_CONFIG;
    public static ForgeConfigSpec CLIENT_CONFIG;
    
    // Performance settings
    public static ForgeConfigSpec.IntValue MAX_FLUID_UPDATES_PER_TICK;
    public static ForgeConfigSpec.IntValue SPREAD_CHECK_RADIUS;
    public static ForgeConfigSpec.IntValue TICK_DELAY;
    
    // Feature toggles
    public static ForgeConfigSpec.BooleanValue ENABLE_FLOATING_WATER_FIX;
    public static ForgeConfigSpec.BooleanValue ENABLE_TICK_OPTIMIZATION;
    public static ForgeConfigSpec.BooleanValue ENABLE_PRESSURE_SYSTEM;
    public static ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING;
    
    // Advanced settings
    public static ForgeConfigSpec.DoubleValue FLUID_FLOW_SPEED_MULTIPLIER;
    public static ForgeConfigSpec.IntValue BATCH_SIZE;
    public static ForgeConfigSpec.BooleanValue ADAPTIVE_PERFORMANCE;
    
    static {
        initCommonConfig();
        initClientConfig();
    }
    
    private static void initCommonConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        builder.comment("Performance Fix Configuration").push("performance");
        
        builder.comment("Performance Settings");
        MAX_FLUID_UPDATES_PER_TICK = builder
                .comment("Maximum fluid updates processed per tick to prevent lag")
                .defineInRange("maxFluidUpdatesPerTick", 1000, 100, 10000);
                
        SPREAD_CHECK_RADIUS = builder
                .comment("Radius to check for fluid spreading (lower = better performance)")
                .defineInRange("spreadCheckRadius", 8, 1, 16);
                
        TICK_DELAY = builder
                .comment("Delay between fluid ticks (higher = better performance, slower flow)")
                .defineInRange("tickDelay", 2, 1, 10);
        
        builder.comment("Feature Toggles");
        ENABLE_FLOATING_WATER_FIX = builder
                .comment("Enable fix for floating water layers")
                .define("enableFloatingWaterFix", true);
                
        ENABLE_TICK_OPTIMIZATION = builder
                .comment("Enable tick optimization for mass fluid changes")
                .define("enableTickOptimization", true);
                
        ENABLE_PRESSURE_SYSTEM = builder
                .comment("Enable optimized fluid pressure system")
                .define("enablePressureSystem", true);
                
        ENABLE_DEBUG_LOGGING = builder
                .comment("Enable debug logging for troubleshooting")
                .define("enableDebugLogging", false);
        
        builder.comment("Advanced Settings");
        FLUID_FLOW_SPEED_MULTIPLIER = builder
                .comment("Multiplier for fluid flow speed (1.0 = normal, lower = slower)")
                .defineInRange("fluidFlowSpeedMultiplier", 1.0, 0.1, 2.0);
                
        BATCH_SIZE = builder
                .comment("Batch size for processing fluid updates")
                .defineInRange("batchSize", 100, 10, 500);
                
        ADAPTIVE_PERFORMANCE = builder
                .comment("Enable adaptive performance scaling based on server load")
                .define("adaptivePerformance", true);
        
        builder.pop();
        
        COMMON_CONFIG = builder.build();
    }
    
    // Client-specific settings
    public static ForgeConfigSpec.BooleanValue SHOW_FLUID_DEBUG_INFO;
    public static ForgeConfigSpec.BooleanValue ENABLE_FLUID_PARTICLES;
    public static ForgeConfigSpec.IntValue FLUID_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue SMOOTH_FLUID_ANIMATION;
    public static ForgeConfigSpec.DoubleValue FLUID_TRANSPARENCY;
    
    private static void initClientConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        builder.comment("Client-side Performance Fix Configuration").push("client");
        
        builder.comment("Visual Settings");
        SHOW_FLUID_DEBUG_INFO = builder
                .comment("Show fluid debug information overlay (for troubleshooting)")
                .define("showFluidDebugInfo", false);
        
        ENABLE_FLUID_PARTICLES = builder
                .comment("Enable fluid particle effects (disable for better performance)")
                .define("enableFluidParticles", true);
        
        FLUID_RENDER_DISTANCE = builder
                .comment("Maximum distance to render fluid updates (lower = better performance)")
                .defineInRange("fluidRenderDistance", 64, 16, 256);
        
        SMOOTH_FLUID_ANIMATION = builder
                .comment("Enable smooth fluid animation transitions")
                .define("smoothFluidAnimation", true);
        
        FLUID_TRANSPARENCY = builder
                .comment("Fluid transparency level (0.0 = opaque, 1.0 = fully transparent)")
                .defineInRange("fluidTransparency", 0.3, 0.0, 1.0);
        
        builder.pop();
        
        CLIENT_CONFIG = builder.build();
    }
    
    @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_CONFIG);
        
        LOGGER.info("Performance Fix configuration registered");
    }
    
    public static void logConfig() {
        if (ENABLE_DEBUG_LOGGING.get()) {
            LOGGER.info("Performance Fix Configuration:");
            LOGGER.info("  Max Fluid Updates Per Tick: {}", MAX_FLUID_UPDATES_PER_TICK.get());
            LOGGER.info("  Spread Check Radius: {}", SPREAD_CHECK_RADIUS.get());
            LOGGER.info("  Tick Delay: {}", TICK_DELAY.get());
            LOGGER.info("  Floating Water Fix: {}", ENABLE_FLOATING_WATER_FIX.get());
            LOGGER.info("  Tick Optimization: {}", ENABLE_TICK_OPTIMIZATION.get());
            LOGGER.info("  Pressure System: {}", ENABLE_PRESSURE_SYSTEM.get());
            LOGGER.info("  Flow Speed Multiplier: {}", FLUID_FLOW_SPEED_MULTIPLIER.get());
            LOGGER.info("  Batch Size: {}", BATCH_SIZE.get());
            LOGGER.info("  Adaptive Performance: {}", ADAPTIVE_PERFORMANCE.get());
        }
    }
    
    public static boolean isAdaptiveMode() {
        return ADAPTIVE_PERFORMANCE.get();
    }
    
    public static int getMaxUpdatesForCurrentLoad() {
        if (!ADAPTIVE_PERFORMANCE.get()) {
            return MAX_FLUID_UPDATES_PER_TICK.get();
        }
        
        // Simple adaptive logic - could be enhanced with actual server metrics
        return Math.max(100, MAX_FLUID_UPDATES_PER_TICK.get() / 2);
    }
    
    public static boolean isLoaded() {
        return COMMON_CONFIG != null && CLIENT_CONFIG != null;
    }
}
