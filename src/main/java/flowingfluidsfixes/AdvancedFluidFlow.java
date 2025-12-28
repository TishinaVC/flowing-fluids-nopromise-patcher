package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancedFluidFlow {
    // Fluid flow simulation data structures
    private static final Map<Level, Map<BlockPos, FluidFlowData>> flowData = new ConcurrentHashMap<>();
    private static final Map<Level, Set<BlockPos>> activeFlowRegions = new ConcurrentHashMap<>();
    
    // Configuration values
    private static final double GRAVITY_STRENGTH = 0.98;
    private static final double FRICTION_COEFFICIENT = 0.85;
    private static final double PRESSURE_TRANSFER_RATE = 0.7;
    private static final int MAX_FLOW_DISTANCE = 16;
    
    /**
     * Enhanced fluid flow simulation based on FLOWING FLUIDS finite fluid dynamics
     * Implements FLOWING FLUIDS logic with realistic finite fluid behavior
     */
    public static void simulateFluidFlow(Level level, BlockPos pos, BlockState state) {
        if (!ConfigManager.ENABLE_PRESSURE_SYSTEM.get()) {
            return;
        }
        
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty() || fluidState.getType() == Fluids.EMPTY) {
            return;
        }
        
        // FLOWING FLUIDS: Initialize flow data for this position
        FluidFlowData data = getOrCreateFlowData(level, pos, fluidState);
        
        // Calculate FLOWING FLUIDS finite fluid physics
        calculateFiniteFluidPhysics(level, pos, data);
        
        // Apply flow updates respecting finite fluid amounts
        applyFiniteFlowUpdates(level, pos, data);
        
        // Mark region as active for optimization
        markActiveFlowRegion(level, pos);
    }
    
    /**
     * Calculate realistic finite fluid physics including pressure, gravity, and flow direction
     * Based on FLOWING FLUIDS finite fluid system
     */
    private static void calculateFiniteFluidPhysics(Level level, BlockPos pos, FluidFlowData data) {
        // FLOWING FLUIDS: Calculate pressure from above (finite fluid consideration)
        double pressureAbove = calculateFinitePressureFromAbove(level, pos);
        data.pressure = pressureAbove;
        
        // Calculate flow vectors based on pressure and gravity (finite fluid dynamics)
        Vec3 flowVector = calculateFiniteFlowVector(level, pos, data);
        data.flowDirection = determineFiniteFlowDirection(level, pos, flowVector, data);
        
        // Calculate flow rate based on finite fluid amount and pressure
        double flowRate = calculateFiniteFlowRate(data.fluidState.getAmount(), pressureAbove);
        data.flowRate = flowRate;
    }
    
    /**
     * Calculate pressure from finite fluid blocks above (FLOWING FLUIDS system)
     */
    private static double calculateFinitePressureFromAbove(Level level, BlockPos pos) {
        double pressure = 0.0;
        
        // FLOWING FLUIDS: Check up to MAX_FLOW_DISTANCE blocks above
        for (int y = 1; y <= MAX_FLOW_DISTANCE; y++) {
            BlockPos checkPos = pos.above(y);
            if (!level.isInWorldBounds(checkPos)) {
                break;
            }
            
            BlockState checkState = level.getBlockState(checkPos);
            FluidState checkFluid = checkState.getFluidState();
            
            if (!checkFluid.isEmpty()) {
                // FLOWING FLUIDS: Pressure decreases with distance (finite fluid consideration)
                pressure += (double) checkFluid.getAmount() / (y * y * 2); // More aggressive drop for finite fluids
            } else if (!checkState.isSolidRender(level, checkPos)) {
                // Solid block stops pressure transmission
                break;
            }
        }
        
        return pressure * PRESSURE_TRANSFER_RATE;
    }
    
    /**
     * Calculate flow vector based on finite fluid pressure gradients and gravity
     */
    private static Vec3 calculateFiniteFlowVector(Level level, BlockPos pos, FluidFlowData data) {
        Vec3 vector = new Vec3(0, 0, 0);
        
        // Apply gravity (stronger for finite fluids)
        vector = vector.add(0, -GRAVITY_STRENGTH * 1.2, 0); // Stronger gravity for finite fluids
        
        // Apply pressure gradients (horizontal flow) - finite fluid behavior
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            if (!level.isInWorldBounds(neighborPos)) {
                continue;
            }
            
            double neighborPressure = getNeighborFinitePressure(level, neighborPos);
            double pressureGradient = data.pressure - neighborPressure;
            
            // FLOWING FLUIDS: Flow from high pressure to low pressure (finite fluid consideration)
            if (pressureGradient > 0) {
                Vec3 directionVector = Vec3.atLowerCornerOf(direction.getNormal());
                vector = vector.add(directionVector.scale(pressureGradient * 0.15)); // Higher multiplier for finite fluids
            }
        }
        
        // Apply friction (higher for finite fluids)
        vector = vector.scale(FRICTION_COEFFICIENT * 0.8); // More friction for finite fluids
        
        return vector.normalize();
    }
    
    /**
     * Determine primary flow direction based on finite fluid flow vector and terrain
     */
    private static Direction determineFiniteFlowDirection(Level level, BlockPos pos, Vec3 flowVector, FluidFlowData data) {
        double maxComponent = Math.max(
            Math.max(Math.abs(flowVector.x), Math.abs(flowVector.y)),
            Math.abs(flowVector.z)
        );
        
        if (Math.abs(flowVector.y) > maxComponent * 0.7) {
            // Primarily vertical flow (finite fluid gravity-driven)
            return flowVector.y < 0 ? Direction.DOWN : Direction.UP;
        } else if (Math.abs(flowVector.x) > Math.abs(flowVector.z)) {
            // Primarily X-axis flow
            return flowVector.x < 0 ? Direction.WEST : Direction.EAST;
        } else {
            // Primarily Z-axis flow
            return flowVector.z < 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }
    
    /**
     * Calculate flow rate based on finite fluid amount and pressure
     */
    private static double calculateFiniteFlowRate(int fluidAmount, double pressure) {
        // FLOWING FLUIDS: Base flow rate from finite fluid amount (1-8 levels)
        double baseRate = fluidAmount / 8.0;
        
        // Pressure multiplier (reduced for finite fluids)
        double pressureMultiplier = 1.0 + (pressure * 0.3); // Lower multiplier for finite fluids
        
        // Apply configuration speed multiplier
        double speedMultiplier = ConfigManager.FLUID_FLOW_SPEED_MULTIPLIER.get();
        
        return baseRate * pressureMultiplier * speedMultiplier;
    }
    
    /**
     * Apply calculated flow updates respecting finite fluid amounts
     */
    private static void applyFiniteFlowUpdates(Level level, BlockPos pos, FluidFlowData data) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        
        // Apply flow in primary direction (finite fluid consideration)
        if (data.flowDirection != null && data.flowRate > 0.1) {
            BlockPos targetPos = pos.relative(data.flowDirection);
            
            if (shouldFiniteFlowToPosition(level, targetPos, data)) {
                transferFiniteFluid(serverLevel, pos, targetPos, data);
            }
        }
        
        // Apply spreading flow for realistic finite fluid behavior
        applyFiniteSpreadingFlow(serverLevel, pos, data);
    }
    
    /**
     * Apply spreading flow for realistic finite fluid behavior
     */
    private static void applyFiniteSpreadingFlow(ServerLevel level, BlockPos pos, FluidFlowData data) {
        // Check horizontal neighbors for spreading (finite fluid distribution)
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            
            if (shouldFiniteSpreadToPosition(level, neighborPos, data)) {
                double spreadAmount = data.flowRate * 0.25; // Spread 25% of flow rate
                transferFiniteFluidAmount(level, pos, neighborPos, spreadAmount);
            }
        }
    }
    
    /**
     * Check if finite fluid should flow to a position
     */
    private static boolean shouldFiniteFlowToPosition(Level level, BlockPos pos, FluidFlowData data) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        
        BlockState targetState = level.getBlockState(pos);
        FluidState targetFluid = targetState.getFluidState();
        
        // Can flow to air or lower fluid levels (finite fluid behavior)
        if (targetFluid.isEmpty()) {
            return true;
        }
        
        // Can flow to same fluid type with lower level (finite fluid consideration)
        if (targetFluid.getType() == data.fluidState.getType() && 
            targetFluid.getAmount() < data.fluidState.getAmount()) {
            return true;
        }
        
        // Check biome-specific infinite source behavior (FLOWING FLUIDS)
        if (BiomeOptimization.isInfiniteSource(level, pos)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if finite fluid should spread to a position
     */
    private static boolean shouldFiniteSpreadToPosition(Level level, BlockPos pos, FluidFlowData data) {
        if (!shouldFiniteFlowToPosition(level, pos, data)) {
            return false;
        }
        
        // Additional spreading logic based on pressure and flow direction (finite fluid)
        return data.pressure > 0.3 || data.flowRate > 0.2;
    }
    
    /**
     * Transfer finite fluid between positions
     */
    private static void transferFiniteFluid(ServerLevel level, BlockPos from, BlockPos to, FluidFlowData data) {
        int transferAmount = Math.min(
            (int)(data.flowRate * 8), // Convert flow rate to fluid levels
            data.fluidState.getAmount()
        );
        
        if (transferAmount > 0) {
            transferFiniteFluidAmount(level, from, to, transferAmount);
        }
    }
    
    /**
     * Transfer specific amount of finite fluid.
     * 
     * IMPORTANT: This method schedules fluid ticks rather than directly manipulating fluid states.
     * This ensures Flowing Fluids' mixin handles all actual fluid behavior, maintaining 100% parity.
     * We only trigger re-evaluation by scheduling ticks - FF's tick() method does the real work.
     */
    private static void transferFiniteFluidAmount(ServerLevel level, BlockPos from, BlockPos to, double amount) {
        BlockState fromState = level.getBlockState(from);
        FluidState fromFluid = fromState.getFluidState();
        
        if (fromFluid.isEmpty()) {
            return;
        }
        
        int transferLevels = (int) Math.min(amount, fromFluid.getAmount());
        
        if (transferLevels > 0) {
            // PARITY-SAFE: Instead of directly setting fluid levels, we schedule ticks
            // This lets Flowing Fluids' MixinFlowingFluid.tick() handle all fluid behavior
            // ensuring 100% parity with the original mod's spreading and leveling logic
            
            Fluid fluidType = fromFluid.getType();
            
            // Schedule tick at source position - FF will recalculate and spread
            if (!level.getFluidTicks().hasScheduledTick(from, fluidType)) {
                level.scheduleTick(from, fluidType, 1);
            }
            
            // Schedule tick at target position - FF will handle receiving fluid
            BlockState toState = level.getBlockState(to);
            FluidState toFluid = toState.getFluidState();
            
            if (toFluid.isEmpty() || toFluid.getType().isSame(fluidType)) {
                if (!level.getFluidTicks().hasScheduledTick(to, fluidType)) {
                    level.scheduleTick(to, fluidType, 1);
                }
            }
            
            // Also schedule neighbors to ensure proper flow propagation
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = from.relative(dir);
                FluidState neighborFluid = level.getFluidState(neighborPos);
                if (!neighborFluid.isEmpty() && neighborFluid.getType().isSame(fluidType)) {
                    if (!level.getFluidTicks().hasScheduledTick(neighborPos, fluidType)) {
                        level.scheduleTick(neighborPos, fluidType, 2);
                    }
                }
            }
        }
    }
    
    /**
     * Get finite pressure from neighbor position
     */
    private static double getNeighborFinitePressure(Level level, BlockPos pos) {
        Map<BlockPos, FluidFlowData> levelFlowData = flowData.get(level);
        if (levelFlowData == null) {
            return 0.0;
        }
        
        FluidFlowData data = levelFlowData.get(pos);
        return data != null ? data.pressure : 0.0;
    }
    
    /**
     * Get or create flow data for a position
     */
    private static FluidFlowData getOrCreateFlowData(Level level, BlockPos pos, FluidState fluidState) {
        Map<BlockPos, FluidFlowData> levelFlowData = flowData.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        return levelFlowData.computeIfAbsent(pos, k -> new FluidFlowData(fluidState));
    }
    
    /**
     * Mark region as active for optimization
     */
    private static void markActiveFlowRegion(Level level, BlockPos pos) {
        Set<BlockPos> activeRegions = activeFlowRegions.computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet());
        activeRegions.add(pos);
    }
    
    /**
     * Clean up old flow data to prevent memory leaks
     */
    public static void cleanupFlowData(Level level) {
        Map<BlockPos, FluidFlowData> levelFlowData = flowData.get(level);
        if (levelFlowData != null) {
            levelFlowData.entrySet().removeIf(entry -> {
                BlockPos pos = entry.getKey();
                BlockState currentState = level.getBlockState(pos);
                FluidState currentFluid = currentState.getFluidState();
                
                // Remove data for positions that no longer have fluids
                return currentFluid.isEmpty() || currentFluid.getType() == Fluids.EMPTY;
            });
        }
    }
    
    /**
     * Data class for fluid flow simulation
     */
    private static class FluidFlowData {
        public final FluidState fluidState;
        public double pressure;
        public Direction flowDirection;
        public double flowRate;
        
        public FluidFlowData(FluidState fluidState) {
            this.fluidState = fluidState;
            this.pressure = 0.0;
            this.flowDirection = null;
            this.flowRate = 0.0;
        }
    }
}
