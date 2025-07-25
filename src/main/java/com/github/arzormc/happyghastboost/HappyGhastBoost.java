package com.github.arzormc.happyghastboost;

import com.github.arzormc.happyghastboost.command.CommandHandler;
import com.github.arzormc.happyghastboost.config.BoostPresetManager;
import com.github.arzormc.happyghastboost.events.BoostEventListener;
import com.github.arzormc.happyghastboost.logic.BoostTickTasks;
import com.github.arzormc.happyghastboost.logic.BoostManager;
import com.github.arzormc.happyghastboost.util.MessageUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class HappyGhastBoost extends JavaPlugin {

    private BoostManager boostManager;

    @Override
    public void onEnable() {
        String serverVersion = getServer().getMinecraftVersion();
        getLogger().info("Detected Minecraft version: " + serverVersion);

        if (!isCompatibleVersion(serverVersion)) {
            getLogger().severe("❌ HappyGhastBoost requires Minecraft 1.21.7 or later!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        reloadConfig();

        File msgFile = new File(getDataFolder(), "messages.yml");
        if (!msgFile.exists()) {
            saveResource("messages.yml", false);
        }

        MessageUtil.load(this);
        getLogger().info("✅ Message config loaded.");

        BoostPresetManager presetManager = new BoostPresetManager(this);
        presetManager.loadSettings();

        boostManager = new BoostManager(this, presetManager);

        getServer().getPluginManager().registerEvents(boostManager, this);
        getServer().getPluginManager().registerEvents(new BoostEventListener(boostManager), this);

        boostManager.getChargeStorage().loadChargeDataFromPDC();
        getLogger().info("✅ Boost manager and listeners initialized.");

        new BoostTickTasks(boostManager).start();

        PluginCommand command = getCommand("happyghastboost");
        if (command != null) {
            CommandHandler handler = new CommandHandler(this, boostManager);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("⚠️ Command 'happyghastboost' not defined in plugin.yml!");
        }

        getLogger().info("🎉 HappyGhastBoost enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (boostManager != null) {
            boostManager.getChargeStorage().saveChargeDataToPDC();
        }
        getLogger().info("📦 HappyGhastBoost disabled.");
    }

    private boolean isCompatibleVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            return (major > 1 || (major == 1 && (minor > 21 || (minor == 21 && patch >= 7))));
        } catch (Exception e) {

            getLogger().warning("Could not parse server version: " + version);
            return false;
        }
    }
}
