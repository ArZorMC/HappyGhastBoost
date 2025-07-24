package dev.arzor.happyghastboost;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * 📬 MessageUtil handles loading and displaying MiniMessage-formatted text
 * from a YAML config file (`messages.yml`) for both player and console output.
 */
public class MessageUtil {

    // 🌈 MiniMessage instance for parsing text with tags like <red>, <bold>, etc.
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // 📦 The loaded message data from messages.yml
    private static YamlConfiguration messages;

    /**
     * 🛠️ Loads the messages.yml file from the plugin's data folder.
     * Must be called during plugin initialization before using get/send.
     */
    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * 🎯 Retrieves a message string by key and returns it as a MiniMessage component.
     * Falls back to a placeholder error message if key is missing or not yet loaded.
     */
    public static Component get(String key) {
        if (messages == null) {
            // ⛔ Safety fallback in case load() was never called
            return MINI_MESSAGE.deserialize("<red>Message system not initialized");
        }

        String raw = messages.getString("messages." + key);
        if (raw == null) {
            // ⚠️ Warn when a message is missing from the config
            raw = "<red>Missing message: " + key;
        }

        return MINI_MESSAGE.deserialize(raw); // ✅ Convert MiniMessage string to Component
    }

    /**
     * 📤 Sends a MiniMessage-formatted message to a CommandSender.
     */
    public static void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    /**
     * 📤 Sends a MiniMessage-formatted message with placeholders to a CommandSender.
     */
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

    /**
     * ⚡ Constructs a visual boost bar using characters and colors from config.
     * Dynamically reflects the player's current boost charge and mode.

     * Example output: <green>██████<dark_gray>░░░░
     */
    public static String buildBoostBar(JavaPlugin plugin, double charge, boolean boosting) {
        // 🔢 Number of total segments in the bar (e.g. 10 blocks long)
        int length = plugin.getConfig().getInt("bar-style.length", 10);

        // 🔤 Bar characters pulled from config (can be Unicode, emoji, etc.)
        String filledChar = plugin.getConfig().getString("bar-style.character-filled", "█");
        String emptyChar = plugin.getConfig().getString("bar-style.character-empty", "░");

        // 🎨 Choose the fill color depending on whether the player is boosting
        String colorFilledKey = boosting ? "bar-style.color-filled-boosting" : "bar-style.color-filled";
        String colorFilled = plugin.getConfig().getString(colorFilledKey, "<green>");
        String colorEmpty = plugin.getConfig().getString("bar-style.color-empty", "<dark_gray>");

        // 📊 Determine how many segments to fill vs leave empty
        int filled = (int) Math.round(charge * length);
        int empty = length - filled;

        // 🧱 Build the final bar string with formatting applied
        return colorFilled + filledChar.repeat(filled) + colorEmpty + emptyChar.repeat(empty);
    }
}
