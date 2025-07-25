package com.github.arzormc.happyghastboost.logic;

import com.github.arzormc.happyghastboost.util.BoostLogger;
import com.github.arzormc.happyghastboost.config.BoostPresetManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;

import java.util.*;

public class PilotManager {

    private final BoostLogger logger;
    private final BoostPresetManager presetManager;
    private final ChargeStorage chargeStorage;
    private final Map<UUID, BoostState> boostStates;

    private final Map<UUID, UUID> ghastToPilot = new HashMap<>();
    private final Set<UUID> recentlyUnregistered = new HashSet<>();
    private final Map<UUID, Location> lastGhastLocation = new HashMap<>();
    private final Map<UUID, String> lastVerboseSnapshot = new HashMap<>();

    public PilotManager(BoostLogger logger,
                        BoostPresetManager presetManager,
                        ChargeStorage chargeStorage,
                        Map<UUID, BoostState> boostStates) {
        this.logger = logger;
        this.presetManager = presetManager;
        this.chargeStorage = chargeStorage;
        this.boostStates = boostStates;
    }

    // ======================
    // 🎮 Pilot Management
    // ======================
    public void registerPilot(Player player, HappyGhast ghast) {
        UUID playerId = player.getUniqueId();
        UUID ghastId = ghast.getUniqueId();

        ghastToPilot.put(ghastId, playerId);

        BoostState state = boostStates.get(playerId);
        if (state == null) {
            state = new BoostState();
            boostStates.put(playerId, state);
        } else {
            state.boosting = false;
            state.isHoldingForward = false;
            state.forwardStartTime = 0L;
            state.mustReleaseBeforeNextBoost = false;
        }

        BoostPresetManager.BoostPreset preset = presetManager.getPresetFor(player);
        state.refillRate = preset.refillRate;
        state.drainRate = preset.drainRate;
        state.particle = preset.particle;

        Double savedCharge = chargeStorage.getAndRemoveChargeLevel(ghastId);
        if (savedCharge != null) {
            state.charge = Math.max(0.0, Math.min(1.0, savedCharge));
            logger.logDebug("Charge", "Restored saved charge for ghast %s: %.2f", ghastId, state.charge);
        }

        if (player.getVehicle() == ghast && ghast.isValid()) {
            lastGhastLocation.put(ghast.getUniqueId(), ghast.getLocation().clone());
        }

        logger.logBasic("Pilot", "Assigned %s as pilot of Ghast %s", player.getName(), ghastId);
    }

    public void unregisterPilot(Player player) {
        UUID playerId = player.getUniqueId();
        BoostState state = boostStates.remove(playerId);

        UUID ghastId = null;
        for (Map.Entry<UUID, UUID> entry : ghastToPilot.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                ghastId = entry.getKey();
                break;
            }
        }

        if (ghastId != null) {
            ghastToPilot.remove(ghastId);
            if (state != null) {
                chargeStorage.saveChargeLevel(ghastId, state.charge);
                logger.logDebug( "Charge", "Saved charge for ghast %s: %.2f", ghastId, state.charge);
            }
        }

        player.sendActionBar(Component.empty());
        recentlyUnregistered.add(playerId);
        lastVerboseSnapshot.remove(playerId);

        logger.logBasic("Pilot", "Unregistered pilot %s", player.getName());
    }

    // ======================
    // 🌐 Accessors
    // ======================
    public Map<UUID, UUID> getGhastToPilotMap() {
        return ghastToPilot;
    }

    public Map<UUID, Location> getLastGhastLocations() {
        return lastGhastLocation;
    }

    public Set<UUID> getRecentlyUnregistered() {
        return recentlyUnregistered;
    }

    public Map<UUID, String> getLastVerboseSnapshot() {
        return lastVerboseSnapshot;
    }
}