package com.github.arzormc.happyghastboost.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static YamlConfiguration messages;

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public static Component get(String key) {
        if (messages == null) {
            return MINI_MESSAGE.deserialize("<red>Message system not initialized");
        }

        String raw = messages.getString("messages." + key);
        if (raw == null) {
            raw = "<red>Missing message: " + key;
        }

        return MINI_MESSAGE.deserialize(raw);
    }

    public static void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public static void send(CommandSender sender, String key, TagResolver... resolvers) {
        if (messages == null) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Message system not initialized"));
            return;
        }

        String raw = messages.getString("messages." + key);
        if (raw == null) {
            raw = "<red>Missing message: " + key;
        }

        Component msg = MINI_MESSAGE.deserialize(raw, resolvers);
        sender.sendMessage(msg);
    }

    public static String buildBoostBar(JavaPlugin plugin, double charge, boolean boosting) {
        int length = plugin.getConfig().getInt("bar-style.length", 10);

        String filledChar = plugin.getConfig().getString("bar-style.character-filled", "█");
        String emptyChar = plugin.getConfig().getString("bar-style.character-empty", "░");

        String colorFilledKey = boosting ? "bar-style.color-filled-boosting" : "bar-style.color-filled";
        String colorFilled = plugin.getConfig().getString(colorFilledKey, "<green>");
        String colorEmpty = plugin.getConfig().getString("bar-style.color-empty", "<dark_gray>");

        int filled = (int) Math.round(charge * length);
        int empty = length - filled;

        return colorFilled + filledChar.repeat(filled) + colorEmpty + emptyChar.repeat(empty);
    }
}
