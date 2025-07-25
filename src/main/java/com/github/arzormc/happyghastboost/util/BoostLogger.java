package com.github.arzormc.happyghastboost.util;

import org.bukkit.plugin.java.JavaPlugin;

public class BoostLogger {

    // ======================
    // 🛠️ Logging Utilities
    // ======================
    public enum LoggingMode {OFF, BASIC, DEBUG, VERBOSE}

    private final JavaPlugin plugin;
    public LoggingMode loggingMode = LoggingMode.BASIC;

    private final java.util.Map<String, Long> logTimestamps = new java.util.HashMap<>();

    public BoostLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadLoggingMode() {
        String raw = plugin.getConfig().getString("logging-mode");
        if (raw == null) {
            plugin.getLogger().warning("⚠️ 'logging-mode' is missing from config.yml. Defaulting to BASIC.");
            loggingMode = LoggingMode.BASIC;
            return;
        }

        try {
            loggingMode = LoggingMode.valueOf(raw.trim().toUpperCase());
            plugin.getLogger().info("✅ Loaded logging-mode: " + loggingMode.name());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("❌ Invalid logging-mode in config.yml: '" + raw + "'. Defaulting to BASIC.");
            loggingMode = LoggingMode.BASIC;
        }
    }

    public void setLoggingMode(LoggingMode mode) {
        this.loggingMode = mode;
    }

    private boolean shouldLog(LoggingMode level) {
        return loggingMode.ordinal() >= level.ordinal();
    }

    public void log(LoggingMode level, String category, String message, Object... args) {
        if (shouldLog(level)) {
            plugin.getLogger().info(String.format(
                    "[HappyGhastBoost:%s:%s] %s",
                    level.name(), category, String.format(message, args)
            ));
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isThrottled(String key, long intervalMs) {
        long now = System.currentTimeMillis();
        long last = logTimestamps.getOrDefault(key, 0L);
        if (now - last >= intervalMs) {
            logTimestamps.put(key, now);
            return false;
        }
        return true;
    }

    public void logBasic(String category, String message, Object... args) {
        log(LoggingMode.BASIC, category, message, args);
    }

    public void logDebug(String category, String message, Object... args) {
        log(LoggingMode.DEBUG, category, message, args);
    }

    public void logVerbose(String category, String message, Object... args) {
        log(LoggingMode.VERBOSE, category, message, args);
    }
}