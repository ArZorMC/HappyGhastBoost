package dev.arzor.happyghastboost;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("NullableProblems")
public class HappyGhastBoost extends JavaPlugin implements CommandExecutor, TabCompleter {

    private HappyGhastBoostManager boostManager;

    // 🔧 Called when the plugin is enabled
    @Override
    public void onEnable() {
        // 🧠 Version check to ensure compatibility
        String serverVersion = getServer().getMinecraftVersion(); // e.g., "1.21.7"
        getLogger().info("Detected Minecraft version: " + serverVersion);

        if (!isCompatibleVersion(serverVersion)) {
            getLogger().severe("❌ HappyGhastBoost requires Minecraft 1.21.7 or later!");
            getServer().getPluginManager().disablePlugin(this); // 🔴 Disable plugin if incompatible
            return;
        }

        // 📂 Save default config.yml and messages.yml if they don’t exist
        saveDefaultConfig();
        reloadConfig();

        File msgFile = new File(getDataFolder(), "messages.yml");
        if (!msgFile.exists()) {
            saveResource("messages.yml", false);
        }

        // 💬 Load localized message strings
        MessageUtil.load(this);
        getLogger().info("✅ Message config loaded.");

        // 🚀 Initialize and start the core boost manager
        boostManager = new HappyGhastBoostManager(this);
        boostManager.loadChargeData(); // 🔋 Load persisted boost charge levels
        getServer().getPluginManager().registerEvents(boostManager, this); // 🪝 Hook into events
        boostManager.start(); // ⏱ Start the repeating tick task
        getLogger().info("✅ Boost manager started and event listeners registered.");

        // 💻 Register main command executor and tab completer
        registerCommand();

        getLogger().info("🎉 HappyGhastBoost enabled successfully.");
    }

    // 🔧 Called when the plugin is disabled (shutdown or reload)
    @Override
    public void onDisable() {
        // 💾 Save any remaining charge data to disk
        if (boostManager != null) {
            boostManager.saveChargeData();
        }
        getLogger().info("HappyGhastBoost disabled.");
    }

    // 🧾 Command handler for /happyghastboost
    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        // 🔐 Permission check
        if (!sender.hasPermission("happyghastboost.admin")) {
            MessageUtil.send(sender, "no-permission");
            return true;
        }

        // ⚙️ Handle admin subcommands
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            switch (arg) {
                case "logdisable" -> {
                    // 🔕 Set logging mode to OFF (no logs)
                    boostManager.setLoggingMode(HappyGhastBoostManager.LoggingMode.OFF);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<red>OFF"));
                    return true;
                }
                case "logbasic" -> {
                    // 📋 Set logging mode to BASIC (highlights only)
                    boostManager.setLoggingMode(HappyGhastBoostManager.LoggingMode.BASIC);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<gray>BASIC"));
                    return true;
                }
                case "logdebug" -> {
                    // 🐞 Set logging mode to DEBUG (developer info, filtered)
                    boostManager.setLoggingMode(HappyGhastBoostManager.LoggingMode.DEBUG);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<yellow>DEBUG"));
                    return true;
                }
                case "logverbose" -> {
                    // 🧪 Set logging mode to VERBOSE (maximum detail, spammy)
                    boostManager.setLoggingMode(HappyGhastBoostManager.LoggingMode.VERBOSE);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<aqua>VERBOSE"));
                    return true;
                }
                case "reload" -> {
                    // 🔄 Reload plugin config and message files
                    reloadConfig();
                    MessageUtil.load(this); // 💬 Re-load messages.yml
                    boostManager.reloadSettings(); // 🔧 Re-apply boost settings
                    MessageUtil.send(sender, "reload-complete");
                    return true;
                }
            }
        }

        // ❌ Show usage hint if no valid subcommand matched
        MessageUtil.send(sender, "invalid-usage");
        return true;
    }

    // ⌨️ Tab completion for /happyghastboost
    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        // 🔐 Prevent suggestions for users without admin permission
        if (!sender.hasPermission("happyghastboost.admin")) return Collections.emptyList();

        // 📄 Suggest top-level subcommands
        if (args.length == 1) {
            return Arrays.asList("logdisable", "logbasic", "logdebug", "logverbose", "reload");
        }

        return Collections.emptyList();
    }

    // 🧪 Registers the command if it exists in plugin.yml
    private void registerCommand() {
        PluginCommand command = getCommand("happyghastboost");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        } else {
            getLogger().severe("⚠️ Command 'happyghastboost' not defined in plugin.yml!");
        }
    }

    // 💾 Public API for saving charge data from other classes
    public void saveChargeData() {
        if (boostManager != null) {
            boostManager.saveChargeData();
        }
    }

    // ✅ Version compatibility check for 1.21.7+
    private boolean isCompatibleVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            return (major > 1 || (major == 1 && (minor > 21 || (minor == 21 && patch >= 7))));
        } catch (Exception e) {
            // ❗ If the version format is unrecognized, treat as incompatible
            getLogger().warning("Could not parse server version: " + version);
            return false;
        }
    }
}
