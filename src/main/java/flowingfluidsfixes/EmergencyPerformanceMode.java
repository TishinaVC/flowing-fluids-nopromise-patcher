package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class EmergencyPerformanceMode {
    private static final double EMERGENCY_TPS_THRESHOLD = 5.0;
    private static final int EMERGENCY_MODE_DURATION = 200; // 10 seconds at 20 TPS
    private static boolean isEmergencyMode = false;
    private static long emergencyModeStartTick = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Level level = event.getServer().overworld();
            double currentTPS = PerformanceMonitor.getAverageTPS(level);
            if (currentTPS < EMERGENCY_TPS_THRESHOLD && !isEmergencyMode) {
                isEmergencyMode = true;
                emergencyModeStartTick = level.getGameTime();
                // Trigger emergency performance measures if needed
            } else if (isEmergencyMode && level.getGameTime() - emergencyModeStartTick > EMERGENCY_MODE_DURATION) {
                isEmergencyMode = false;
                // Revert emergency measures if needed
            }
        }
    }

    public static boolean isEmergencyMode() {
        return isEmergencyMode;
    }

    public static int getMaxUpdatesPerTick() {
        return isEmergencyMode ? 500 : 10000; // Configurable update limits
    }
}
