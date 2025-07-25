package com.github.arzormc.happyghastboost.command;

import com.github.arzormc.happyghastboost.util.BoostLogger.LoggingMode;

import com.github.arzormc.happyghastboost.HappyGhastBoost;
import com.github.arzormc.happyghastboost.logic.BoostManager;
import com.github.arzormc.happyghastboost.util.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("NullableProblems")
public class CommandHandler implements CommandExecutor, TabCompleter {

    private final HappyGhastBoost plugin;
    private final BoostManager boostManager;

    public CommandHandler(HappyGhastBoost plugin, BoostManager boostManager) {
        this.plugin = plugin;
        this.boostManager = boostManager;
    }

    // ==========================
    // 📟 Command Handler
    // ==========================
    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("happyghastboost.admin")) {
            MessageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            switch (arg) {
                case "logdisable" -> {
                    boostManager.getLogger().setLoggingMode(LoggingMode.OFF);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<red>OFF"));
                    return true;
                }
                case "logbasic" -> {
                    boostManager.getLogger().setLoggingMode(LoggingMode.BASIC);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<gray>BASIC"));
                    return true;
                }
                case "logdebug" -> {
                    boostManager.getLogger().setLoggingMode(LoggingMode.DEBUG);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<yellow>DEBUG"));
                    return true;
                }
                case "logverbose" -> {
                    boostManager.getLogger().setLoggingMode(LoggingMode.VERBOSE);
                    MessageUtil.send(sender, "logging-mode-set", Placeholder.parsed("mode", "<aqua>VERBOSE"));
                    return true;
                }
                case "reload" -> {
                    plugin.reloadConfig();
                    MessageUtil.load(plugin);
                    boostManager.reloadSettings();
                    MessageUtil.send(sender, "reload-complete");
                    return true;
                }
            }
        }

        MessageUtil.send(sender, "invalid-usage");
        return true;
    }


    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("happyghastboost.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("logdisable", "logbasic", "logdebug", "logverbose", "reload");
        }

        return Collections.emptyList();
    }
}
