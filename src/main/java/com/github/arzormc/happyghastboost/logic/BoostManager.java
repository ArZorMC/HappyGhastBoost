package com.github.arzormc.happyghastboost.logic;

import com.github.arzormc.happyghastboost.HappyGhastBoost;
import com.github.arzormc.happyghastboost.config.BoostPresetManager;
import com.github.arzormc.happyghastboost.util.BoostLogger;
import com.github.arzormc.happyghastboost.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.util.Vector;

import java.util.*;

public class BoostManager implements Listener {

    private final HappyGhastBoost plugin;

    private final NamespacedKey CHARGE_KEY;

    private final Map<UUID, BoostState> boostStates = new HashMap<>();

    private final BoostLogger logger;
    private final ChargeStorage chargeStorage;
    private final PilotManager pilotManager;

    private final Map<UUID, String> lastBarSnapshot = new HashMap<>();
    private final Map<UUID, String> lastDotSpeedSnapshot = new HashMap<>();

    private final double boostSpeed;
    private final int updateInterval;
    private final boolean trailEnabled;

    public BoostManager(HappyGhastBoost plugin, BoostPresetManager presetManager) {
        this.plugin = plugin;
        this.CHARGE_KEY = new NamespacedKey(plugin, "boost_charge");

        this.boostSpeed = presetManager.getBoostSpeed();
        this.updateInterval = presetManager.getUpdateInterval();
        this.trailEnabled = presetManager.isTrailEnabled();

        this.logger = new BoostLogger(plugin);
        this.logger.loadLoggingMode();

        this.chargeStorage = new ChargeStorage(plugin, boostStates, new HashMap<>(), logger);
        this.pilotManager = new PilotManager(logger, presetManager, chargeStorage, boostStates);
    }

    public void reloadSettings() {
        plugin.reloadConfig();
        logger.logDebug("Config", "Settings reloaded from config.yml and messages.yml.");
    }

    // ======================
    // 🧠 Boost Mechanics
    // ======================
    public void applyBoost(Player player, BoostState state) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof HappyGhast ghast) {
            if (!ghast.isValid()) return;

            Vector direction = player.getLocation().getDirection().normalize();

            long rampUpDuration = plugin.getConfig().getLong("boost-rampup-speed-ms", 1500L);
            long elapsed = System.currentTimeMillis() - state.boostStartTime;

            double progress = Math.min(1.0, (double) elapsed / rampUpDuration);
            double adjustedSpeed = boostSpeed * progress;

            Vector velocity = direction.multiply(adjustedSpeed);
            ghast.setVelocity(velocity);

            if (trailEnabled) {
                Location loc = ghast.getLocation().add(0, 0.5, 0);
                player.getWorld().spawnParticle(state.particle, loc, 3, 0.1, 0.1, 0.1, 0);
                logger.logVerbose("Particles", "Spawned particle %s at %s", state.particle, loc.toVector());
            }

            logger.logVerbose("Velocity", "Applied velocity to ghast: %s", velocity);
        }
    }

    public void updateActionBar(Player player, BoostState state) {
        if (plugin.getConfig().getBoolean("require-permission", false)
                && !player.hasPermission("happyghastboost.use")) {
            return;
        }

        if (state == null) return;

        double percent = state.charge;
        boolean boosting = state.boosting;

        String labelKey = boosting
                ? "boosting"
                : (percent >= 1.0 ? "boost-ready" : "charging");

        Component label = MessageUtil.get(labelKey);
        String bar = MessageUtil.buildBoostBar(plugin, percent, boosting);
        String percentStr = String.valueOf((int) (percent * 100));

        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("label", label))
                .resolver(Placeholder.parsed("bar", bar))
                .resolver(Placeholder.unparsed("percent", percentStr))
                .build();

        String format = plugin.getConfig().getString("actionbar-format", "<label> <bar> <percent>%");

        player.sendActionBar(MiniMessage.miniMessage().deserialize(format, resolver));

        if (plugin.getConfig().getBoolean("verbose-debug")) {
            UUID uuid = player.getUniqueId();
            int roundedPercent = (int) (percent * 100 / 5) * 5;
            String logSnapshot = "charge~" + roundedPercent + "% boosting=" + boosting;

            String last = lastBarSnapshot.get(uuid);
            if (!logSnapshot.equals(last)) {
                lastBarSnapshot.put(uuid, logSnapshot);
                if (!logger.isThrottled("actionbar-" + uuid, 250)) {
                    logger.logVerbose("HUD", "ActionBar update → %s: %s", player.getName(), logSnapshot);
                }
            }
        }
    }

    // ======================
    // 📤 Public Helpers for EventListener
    // ======================

    public HappyGhastBoost getPlugin() {
        return plugin;
    }

    public NamespacedKey getChargeKey() {
        return CHARGE_KEY;
    }

    public ChargeStorage getChargeStorage() {
        return chargeStorage;
    }

    public PilotManager getPilotManager() {
        return pilotManager;
    }

    public Map<UUID, BoostState> getBoostStates() {
        return boostStates;
    }

    public Map<UUID, String> getLastDotSpeedSnapshot() {
        return lastDotSpeedSnapshot;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public BoostLogger getLogger() {
        return logger;
    }
}