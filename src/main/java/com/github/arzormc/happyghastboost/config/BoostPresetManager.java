package com.github.arzormc.happyghastboost.config;

import com.github.arzormc.happyghastboost.HappyGhastBoost;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BoostPresetManager {

    private final HappyGhastBoost plugin;

    private final Map<String, BoostPreset> presetMap = new HashMap<>();

    private BoostPreset defaultPreset;

    private double boostSpeed;
    private int updateInterval;
    private boolean trailEnabled;
    @SuppressWarnings("FieldCanBeLocal")
    private Particle trailType;

    public BoostPresetManager(HappyGhastBoost plugin) {
        this.plugin = plugin;
    }

    // ======================
    // ⚙️ Load from config.yml
    // ======================
    public void loadSettings() {
        plugin.saveDefaultConfig(); // Ensure defaults exist
        FileConfiguration config = plugin.getConfig();

        boostSpeed = config.getDouble("boost-speed", 1.0);
        updateInterval = config.getInt("update-interval", 2);
        trailEnabled = config.getBoolean("particle-trail.enabled", true);

        String trailName = config.getString("particle-trail.type", "FLAME").toUpperCase(Locale.ROOT);
        try {
            trailType = Particle.valueOf(trailName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid global particle-trail.type: " + trailName + ". Defaulting to FLAME.");
            trailType = Particle.FLAME;
        }

        double refillRate = config.getDouble("refill-rate", 0.02);
        double drainRate = config.getDouble("drain-rate", 0.00333);

        defaultPreset = new BoostPreset();
        defaultPreset.refillRate = refillRate;
        defaultPreset.drainRate = drainRate;
        defaultPreset.boostSpeed = boostSpeed;
        defaultPreset.particle = trailEnabled ? trailType : null;

        presetMap.clear();
        ConfigurationSection presets = config.getConfigurationSection("presets");
        if (presets != null) {
            for (String key : presets.getKeys(false)) {
                ConfigurationSection section = presets.getConfigurationSection(key);
                if (section == null) continue;

                BoostPreset preset = new BoostPreset();
                preset.refillRate = section.getDouble("refill-rate", refillRate);
                preset.drainRate = section.getDouble("drain-rate", drainRate);

                if (section.isDouble("boost-speed")) {
                    preset.boostSpeed = section.getDouble("boost-speed");

                    if (preset.boostSpeed > 1.5) {
                        plugin.getLogger().warning(
                                "[HappyGhastBoost] Preset '" + key + "' uses a high boost-speed value (" +
                                        preset.boostSpeed + ") — may cause rubberbanding or client desync.");
                    }
                } else {
                    preset.boostSpeed = boostSpeed;
                }

                String particleRaw = section.getString("particle", "").trim();
                if (!particleRaw.isEmpty()) {
                    try {
                        preset.particle = Particle.valueOf(particleRaw.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid particle in preset '" + key + "': " + particleRaw + ". Ignoring.");
                    }
                }

                if (preset.particle == null && trailEnabled) {
                    preset.particle = trailType;
                }

                presetMap.put(key, preset);
            }
        }

        plugin.getLogger().info("HappyGhastBoost settings loaded. " + presetMap.size() + " presets available.");
    }

    // ======================
    // 🔍 Get matching preset for player
    // ======================
    public BoostPreset getPresetFor(Player player) {
        for (String key : presetMap.keySet()) {
            if (player.hasPermission("happyghastboost.preset." + key)) {
                return presetMap.get(key);
            }
        }
        return defaultPreset;
    }

    // ======================
    // 🔓 Config value accessors
    // ======================
    public double getBoostSpeed() {
        return boostSpeed;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public boolean isTrailEnabled() {
        return trailEnabled;
    }

    // ======================
    // 🎛️ BoostPreset Structure
    // ======================
    public static class BoostPreset {
        public double refillRate;
        public double drainRate;
        public double boostSpeed;
        public Particle particle;
    }
}