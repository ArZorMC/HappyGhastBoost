package com.github.arzormc.happyghastboost.logic;

import com.github.arzormc.happyghastboost.util.BoostLogger;
import com.github.arzormc.happyghastboost.HappyGhastBoost;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChargeStorage {

    private final BoostLogger logger;

    private final Map<UUID, BoostState> boostStates;
    private final Map<UUID, UUID> ghastToPilot;
    private final Map<UUID, Double> ghastChargeLevels = new HashMap<>();

    private final org.bukkit.NamespacedKey CHARGE_KEY;


    public ChargeStorage(HappyGhastBoost plugin,
                         Map<UUID, BoostState> boostStates,
                         Map<UUID, UUID> ghastToPilot,
                         BoostLogger logger) {
        this.logger = logger;
        this.boostStates = boostStates;
        this.ghastToPilot = ghastToPilot;
        this.CHARGE_KEY = new org.bukkit.NamespacedKey(plugin, "boost_charge");
    }

    // ======================
    // 📁 Charge Persistence
    // ======================
    public void loadChargeDataFromPDC() {
        int loaded = 0;

        for (World world : Bukkit.getWorlds()) {
            for (HappyGhast ghast : world.getEntitiesByClass(HappyGhast.class)) {
                if (!ghast.isValid()) continue;

                PersistentDataContainer pdc = ghast.getPersistentDataContainer();
                if (pdc.has(CHARGE_KEY, PersistentDataType.DOUBLE)) {
                    Double value = pdc.get(CHARGE_KEY, PersistentDataType.DOUBLE);

                    // ✅ Safety check against corrupted data
                    if (value != null && Double.isFinite(value)) {
                        ghastChargeLevels.put(ghast.getUniqueId(), Math.max(0.0, Math.min(1.0, value)));
                        loaded++;
                    } else if (value != null) {
                        logger.logDebug("Charge", "Skipped loading invalid charge (non-finite) for ghast %s", ghast.getUniqueId());
                    }
                }
            }
        }

        logger.logDebug("Charge", "Loaded %d Happy Ghast charges from PDC.", loaded);
    }

    public void saveChargeDataToPDC() {
        int saved = 0;

        for (Map.Entry<UUID, Double> entry : ghastChargeLevels.entrySet()) {
            UUID ghastId = entry.getKey();
            Double charge = entry.getValue();

            if (charge == null) continue; // 🚫 Skip nulls

            Entity entity = Bukkit.getEntity(ghastId);
            if (entity instanceof HappyGhast ghast && ghast.isValid()) {
                double clamped = Math.max(0.0, Math.min(1.0, charge));
                ghast.getPersistentDataContainer().set(CHARGE_KEY, PersistentDataType.DOUBLE, clamped);
                saved++;
            }
        }

        logger.logDebug("Charge", "Saved %d Happy Ghast charges to PDC.", saved);
    }

    // ======================
    // 🔌 Cache Access
    // ======================

    public Double getChargeLevel(UUID ghastId) {
        return ghastChargeLevels.get(ghastId);
    }

    public void removeChargeLevel(UUID ghastId) {
        ghastChargeLevels.remove(ghastId);
    }

    public void persistChargeToPDC(HappyGhast ghast, Player player) {
        BoostState state = boostStates.get(player.getUniqueId());
        if (state != null && ghast.isValid()) {
            double clamped = Math.max(0.0, Math.min(1.0, state.charge));
            ghast.getPersistentDataContainer().set(CHARGE_KEY, PersistentDataType.DOUBLE, clamped);
            logger.logDebug("Charge", "Saved charge to PDC for ghast %s: %.2f", ghast.getUniqueId(), clamped);
        }
    }

    public UUID clearGhastDataOnDeath(HappyGhast ghast) {
        UUID ghastId = ghast.getUniqueId();
        ghastChargeLevels.remove(ghastId);
        UUID pilotId = ghastToPilot.remove(ghastId);
        ghast.getPersistentDataContainer().remove(CHARGE_KEY);
        return pilotId;
    }

    public Double getAndRemoveChargeLevel(UUID ghastId) {
        return ghastChargeLevels.remove(ghastId);
    }

    public void saveChargeLevel(UUID ghastId, double charge) {
        ghastChargeLevels.put(ghastId, charge);
    }
}