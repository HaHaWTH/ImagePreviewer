package io.wdsj.imagepreviewer.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageUtil {
    public static void sendMessage(CommandSender sender, String message) {
        if (message.isBlank()) return;
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    public static String translateColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
