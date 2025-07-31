package io.wdsj.imagepreviewer.command;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.hook.floodgate.FloodgateHook;
import io.wdsj.imagepreviewer.hook.floodgate.form.PreviewHistoryForm;
import io.wdsj.imagepreviewer.image.ImageLoader;
import io.wdsj.imagepreviewer.listener.ChatListener;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import io.wdsj.imagepreviewer.permission.CachingPermTool;
import io.wdsj.imagepreviewer.permission.PermissionsEnum;
import io.wdsj.imagepreviewer.util.MessageUtil;
import io.wdsj.imagepreviewer.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class ConstructCommandExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (Config.isReloading) return true;
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission(PermissionsEnum.RELOAD.getPermission())) {
                ImagePreviewer.getInstance().reloadConfiguration();
                MessageUtil.sendMessage(sender, ImagePreviewer.config().message_reload_success);
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                MessageUtil.sendMessage(sender, ImagePreviewer.config().message_no_permission);
                return true;
            }
            if (args[0].equalsIgnoreCase("help") && sender.hasPermission(PermissionsEnum.HELP.getPermission())) {
                MessageUtil.sendMessage(sender, ImagePreviewer.config().message_help_info);
                return true;
            }
            if (args[0].equalsIgnoreCase("help")) {
                MessageUtil.sendMessage(sender, ImagePreviewer.config().message_no_permission);
                return true;
            }
            if (args[0].equalsIgnoreCase("history")) {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_command_player_only);
                    return true;
                }
                if (!CachingPermTool.hasPermission(PermissionsEnum.HISTORY, player)) {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_no_permission);
                    return true;
                }
                if (ChatListener.getUrlHistory().isEmpty()) {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_no_history_to_show);
                    return true;
                }
                if (ImagePreviewer.config().hook_floodgate && FloodgateHook.isFloodgatePresent() && FloodgateHook.isFloodgatePlayer(player)) {
                    var fgPlayer = FloodgateHook.getFloodgatePlayer(player);
                    if (args.length == 2) {
                        int limit = Math.max(Util.toInt(args[1], ImagePreviewer.config().url_history_size), 1);
                        new PreviewHistoryForm(limit).sendForm(fgPlayer);
                    } else {
                        new PreviewHistoryForm().sendForm(fgPlayer);
                    }
                } else {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_history_command);
                    int index = 1;
                    if (args.length == 2) {
                        int limit = Math.max(Util.toInt(args[1], ImagePreviewer.config().url_history_size), 1);
                        var history = new ArrayList<ChatListener.MessageEntry>();
                        int size = 0;
                        for (var entry : ChatListener.getUrlHistory()) {
                            if (size++ >= limit) break;
                            history.add(entry);
                        }
                        for (var entry : history) {
                            ImagePreviewer.getInstance().getAudiences().player(player).sendMessage(buildHistoryComponent(entry, index++));
                        }
                    } else {
                        for (var entry : ChatListener.getUrlHistory()) {
                            ImagePreviewer.getInstance().getAudiences().player(player).sendMessage(buildHistoryComponent(entry, index++));
                        }
                    }
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("cancel")) {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_command_player_only);
                    return true;
                }

                if (!CachingPermTool.hasPermission(PermissionsEnum.CANCEL_PREVIEW, player)) {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_no_permission);
                    return true;
                }

                var tracker = ImagePreviewer.getInstance().getMapManager();
                if (tracker.hasRunningPreview(player)) {
                    tracker.getDisplay(player).despawn();
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_cancel_success);
                } else {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_nothing_to_cancel);
                }
                return true;
            }
        }
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("preview")) {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_command_player_only);
                    return true;
                }
                if (CachingPermTool.hasPermission(PermissionsEnum.PREVIEW, player)) {
                    if (args.length < 2) {
                        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_args_error);
                        return true;
                    }
                    if (ImagePreviewer.getInstance().getMapManager().hasRunningPreview(player)) {
                        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_already_on_previewing);
                        return true;
                    }
                    if (ImagePreviewer.getInstance().getMapManager().queuedPlayers.contains(player.getUniqueId())) {
                        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_preview_still_loading);
                        return true;
                    }
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_preview_loading);
                    ImagePreviewer.getInstance().getMapManager().queuedPlayers.add(player.getUniqueId());
                    ImageLoader.imageAsData(args[1].trim())
                            .thenAccept(imageData -> {
                                if (args.length > 2) {
                                    ImagePreviewer.getInstance().getMapManager().queuedPlayers.remove(player.getUniqueId());
                                    if (!CachingPermTool.hasPermission(PermissionsEnum.PREVIEW_TIME, player)) {
                                        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_no_permission);
                                        return;
                                    }
                                    Long lifecycleTicks = Util.parseLong(args[2].trim());
                                    if (lifecycleTicks == null || lifecycleTicks < 1) {
                                        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_args_error);
                                        return;
                                    }
                                    if (!new PacketMapDisplay(ImagePreviewer.getInstance(), player, imageData, lifecycleTicks).spawn()) {
                                        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_not_empty_hand);
                                    }
                                } else {
                                    if (!new PacketMapDisplay(ImagePreviewer.getInstance(), player, imageData).spawn()) {
                                        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_not_empty_hand);
                                    }
                                }
                            })
                            .exceptionally(ex -> {
                                MessageUtil.sendMessage(sender, ImagePreviewer.config().message_failed_to_load.replace("%reason%", ex.getMessage()));
                                ImagePreviewer.getInstance().getMapManager().queuedPlayers.remove(player.getUniqueId());
                                return null;
                            });
                } else {
                    MessageUtil.sendMessage(sender, ImagePreviewer.config().message_no_permission);
                }
                return true;
            }
        }
        MessageUtil.sendMessage(sender, ImagePreviewer.config().message_unknown_command);
        return true;
    }

    private Component buildHistoryComponent(ChatListener.MessageEntry entry, int index) {
        return Component.text(index + ". " + ImagePreviewer.config().message_history_entry
                .replace("%time%", entry.time())
                .replace("%sender%", entry.sender())
                .replace("%url%", entry.message()))
        .color(NamedTextColor.GRAY)
        .clickEvent(ClickEvent.runCommand("/imagepreviewer preview " + entry.message()));
    }

}
