package com.github.arzormc.happyghastboost.events;

import com.github.arzormc.happyghastboost.HappyGhastBoost;
import com.github.arzormc.happyghastboost.logic.BoostManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class BoostEventListener implements Listener {

    private final BoostManager manager;
    private final HappyGhastBoost plugin;

    public BoostEventListener(BoostManager manager) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
    }

    // ======================
    // 🧩 Event Listeners
    // ======================
    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getMount() instanceof HappyGhast ghast)) return;

        if (ghast.getPassengers().isEmpty() || ghast.getPassengers().getFirst().equals(player)) {
            manager.getPilotManager().registerPilot(player, ghast);
        } else {
            manager.getLogger().logDebug("Mount", "Mount skipped: %s is not the controlling passenger.", player.getName());
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDismounted() instanceof HappyGhast ghast)) return;

        UUID ghastId = ghast.getUniqueId();
        UUID playerId = player.getUniqueId();

        if (playerId.equals(manager.getPilotManager().getGhastToPilotMap().get(ghastId))) {

            manager.getChargeStorage().persistChargeToPDC(ghast, player);
            manager.getPilotManager().unregisterPilot(player);

            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Entity> passengers = ghast.getPassengers();
                if (!passengers.isEmpty() && passengers.getFirst() instanceof Player newPilot) {
                    manager.getPilotManager().registerPilot(newPilot, ghast);
                    manager.getLogger().logBasic("Pilot", "Pilot reassigned to %s 1 tick after dismount.", newPilot.getName());
                }
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof HappyGhast ghast) {

            manager.getChargeStorage().persistChargeToPDC(ghast, player);

            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Entity> passengers = ghast.getPassengers();
                if (!passengers.isEmpty() && passengers.getFirst() instanceof Player newPilot) {
                    manager.getPilotManager().registerPilot(newPilot, ghast);
                    manager.getLogger().logBasic("Pilot", "Pilot reassigned to %s after %s quit.", newPilot.getName(), player.getName());
                }
            });

            manager.getPilotManager().unregisterPilot(player);
        }
    }

    @EventHandler
    public void onGhastDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof HappyGhast ghast)) return;

        UUID pilotId = manager.getChargeStorage().clearGhastDataOnDeath(ghast);

        if (pilotId != null) {
            Player pilot = Bukkit.getPlayer(pilotId);
            if (pilot != null && pilot.isOnline()) {
                pilot.sendActionBar(Component.empty()); // clean up UI if needed
            }
            manager.getLogger().logBasic("Death", "Happy Ghast %s died. Unassigned pilot %s and cleared saved charge.", ghast.getUniqueId(), pilotId);
        } else {
            manager.getLogger().logBasic("Death", "Happy Ghast %s died. Cleared saved charge.", ghast.getUniqueId());
        }
    }

    // ======================
    // 🌍 World Change Handling
    // ======================

    @EventHandler
    public void onGhastWorldChange(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof HappyGhast ghast)) return;

        UUID ghastId = ghast.getUniqueId();

        Double charge = manager.getChargeStorage().getChargeLevel(ghastId);

        if (charge != null) {
            double clamped = Math.max(0.0, Math.min(1.0, charge));
            ghast.getPersistentDataContainer().set(manager.getChargeKey(), PersistentDataType.DOUBLE, clamped);
            manager.getLogger().logDebug("Charge", "Saved charge %.2f before world change for ghast %s", clamped, ghastId);
        }

        manager.getChargeStorage().removeChargeLevel(ghastId);
    }
}