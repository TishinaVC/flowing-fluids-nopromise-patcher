package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import java.util.HashMap;
import java.util.Map;

public class BiomeOptimization {
    // Biome optimization profiles
    private static final Map<ResourceKey<Biome>, BiomeProfile> BIOME_PROFILES = new HashMap<>();
    
    // Initialize biome profiles for FLOWING FLUIDS finite fluid system
    static {
        // Ocean/River biomes - FLOWING FLUIDS treats these as finite sources
        BIOME_PROFILES.put(Biomes.OCEAN, new BiomeProfile(
            "ocean", 0.4, 10, true, true, 0.6, false
        ));
        BIOME_PROFILES.put(Biomes.DEEP_OCEAN, new BiomeProfile(
            "deep_ocean", 0.3, 8, true, true, 0.5, false
        ));
        BIOME_PROFILES.put(Biomes.RIVER, new BiomeProfile(
            "river", 0.5, 8, true, true, 0.7, false
        ));
        BIOME_PROFILES.put(Biomes.BEACH, new BiomeProfile(
            "beach", 0.6, 6, false, false, 0.8, false
        ));
        
        // Cave biomes - FLOWING FLUIDS finite behavior (dripstone creates finite sources)
        BIOME_PROFILES.put(Biomes.DRIPSTONE_CAVES, new BiomeProfile(
            "dripstone_caves", 0.8, 4, false, false, 0.9, false
        ));
        BIOME_PROFILES.put(Biomes.LUSH_CAVES, new BiomeProfile(
            "lush_caves", 0.7, 5, false, false, 0.8, false
        ));
        BIOME_PROFILES.put(Biomes.DEEP_DARK, new BiomeProfile(
            "deep_dark", 0.6, 6, false, false, 0.8, false
        ));
        
        // Nether - FLOWING FLUIDS finite lava behavior
        BIOME_PROFILES.put(Biomes.NETHER_WASTES, new BiomeProfile(
            "nether_wastes", 0.4, 8, false, true, 0.6, false
        ));
        BIOME_PROFILES.put(Biomes.CRIMSON_FOREST, new BiomeProfile(
            "crimson_forest", 0.5, 7, false, true, 0.7, false
        ));
        BIOME_PROFILES.put(Biomes.WARPED_FOREST, new BiomeProfile(
            "warped_forest", 0.5, 7, false, true, 0.7, false
        ));
        BIOME_PROFILES.put(Biomes.SOUL_SAND_VALLEY, new BiomeProfile(
            "soul_sand_valley", 0.3, 10, false, true, 0.5, false
        ));
        BIOME_PROFILES.put(Biomes.BASALT_DELTAS, new BiomeProfile(
            "basalt_deltas", 0.2, 12, false, true, 0.4, false
        ));
        
        // Frozen biomes - FLOWING FLUIDS finite water with ice formation
        BIOME_PROFILES.put(Biomes.FROZEN_OCEAN, new BiomeProfile(
            "frozen_ocean", 0.2, 12, true, false, 0.3, false
        ));
        BIOME_PROFILES.put(Biomes.FROZEN_RIVER, new BiomeProfile(
            "frozen_river", 0.3, 10, true, false, 0.4, false
        ));
        BIOME_PROFILES.put(Biomes.SNOWY_PLAINS, new BiomeProfile(
            "snowy_plains", 0.4, 8, false, false, 0.6, false
        ));
        BIOME_PROFILES.put(Biomes.ICE_SPIKES, new BiomeProfile(
            "ice_spikes", 0.3, 10, false, false, 0.5, false
        ));
        
        // Swamp - FLOWING FLUIDS special finite water behavior
        BIOME_PROFILES.put(Biomes.SWAMP, new BiomeProfile(
            "swamp", 0.6, 6, true, false, 0.8, false
        ));
        BIOME_PROFILES.put(Biomes.MANGROVE_SWAMP, new BiomeProfile(
            "mangrove_swamp", 0.6, 6, true, false, 0.8, false
        ));
        
        // Mountain biomes - FLOWING FLUIDS finite water drainage
        BIOME_PROFILES.put(Biomes.WINDSWEPT_HILLS, new BiomeProfile(
            "windswept_hills", 0.6, 6, false, false, 0.8, false
        ));
        BIOME_PROFILES.put(Biomes.WINDSWEPT_GRAVELLY_HILLS, new BiomeProfile(
            "windswept_gravelly_hills", 0.6, 6, false, false, 0.8, false
        ));
        BIOME_PROFILES.put(Biomes.WINDSWEPT_SAVANNA, new BiomeProfile(
            "windswept_savanna", 0.7, 5, false, false, 0.8, false
        ));
        
        // Default profile for unregistered biomes - FLOWING FLUIDS finite behavior
        BIOME_PROFILES.put(null, new BiomeProfile(
            "default", 0.7, 6, false, false, 0.8, false
        ));
    }
    
    /**
     * Get biome optimization profile for a position
     */
    public static BiomeProfile getProfile(Level level, BlockPos pos) {
        ResourceKey<Biome> biome = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getResourceKey(level.getBiome(pos).value()).orElse(Biomes.PLAINS);
        return BIOME_PROFILES.getOrDefault(biome, BIOME_PROFILES.get(null));
    }
    
    public static boolean isInfiniteSource(Level level, BlockPos pos) {
        BiomeProfile profile = getProfile(level, pos);
        return profile.infiniteSource;
    }
    
    /**
     * Biome optimization profile
     */
    public static class BiomeProfile {
        public final String name;
        public final double flowMultiplier;        // Reduces/increases flow speed
        public final int maxSpreadRadius;          // Maximum spread distance
        public final boolean infiniteSource;       // Treat as infinite source
        public final boolean allowPressureSystem;   // Enable pressure calculations
        public final double updateFrequency;       // How often to update (1.0 = normal)
        public final boolean specialFluidBehavior;
        
        public BiomeProfile(String name, double flowMultiplier, int maxSpreadRadius, 
                           boolean infiniteSource, boolean allowPressureSystem, 
                           double updateFrequency, boolean specialFluidBehavior) {
            this.name = name;
            this.flowMultiplier = flowMultiplier;
            this.maxSpreadRadius = maxSpreadRadius;
            this.infiniteSource = infiniteSource;
            this.allowPressureSystem = allowPressureSystem;
            this.updateFrequency = updateFrequency;
            this.specialFluidBehavior = specialFluidBehavior;
        }
        
        @Override
        public String toString() {
            return String.format("BiomeProfile[%s: flow=%.2f, radius=%d, infinite=%s, pressure=%s, freq=%.2f]",
                name, flowMultiplier, maxSpreadRadius, infiniteSource, allowPressureSystem, updateFrequency);
        }
    }
}
