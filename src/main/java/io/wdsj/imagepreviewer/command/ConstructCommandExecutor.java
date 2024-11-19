package io.wdsj.imagepreviewer.command;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.image.ImageLoader;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import io.wdsj.imagepreviewer.permission.CachingPermTool;
import io.wdsj.imagepreviewer.permission.PermissionsEnum;
import io.wdsj.imagepreviewer.util.MessageUtil;
import io.wdsj.imagepreviewer.util.Util;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ConstructCommandExecutor implements CommandExecutor {
    private final Config config = ImagePreviewer.config();
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (Config.isReloading) return true;
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission(PermissionsEnum.RELOAD.getPermission())) {
                ImagePreviewer.getInstance().reloadConfiguration();
                MessageUtil.sendMessage(sender, config.message_reload_success);
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                MessageUtil.sendMessage(sender, config.message_no_permission);
                return true;
            }
            if (args[0].equalsIgnoreCase("help") && sender.hasPermission(PermissionsEnum.HELP.getPermission())) {
                MessageUtil.sendMessage(sender, config.message_help_info);
                return true;
            }
            if (args[0].equalsIgnoreCase("help")) {
                MessageUtil.sendMessage(sender, config.message_no_permission);
                return true;
            }
            if (args[0].equalsIgnoreCase("cancel")) {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, config.message_command_player_only);
                    return true;
                }

                if (!CachingPermTool.hasPermission(PermissionsEnum.CANCEL_PREVIEW, player)) {
                    MessageUtil.sendMessage(sender, config.message_no_permission);
                    return true;
                }

                var tracker = ImagePreviewer.getInstance().getMapManager();
                if (tracker.hasRunningPreview(player)) {
                    tracker.getDisplay(player).despawn();
                    MessageUtil.sendMessage(sender, config.message_cancel_success);
                } else {
                    MessageUtil.sendMessage(sender, config.message_nothing_to_cancel);
                }
                return true;
            }
        }
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("preview")) {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, config.message_command_player_only);
                    return true;
                }
                if (CachingPermTool.hasPermission(PermissionsEnum.PREVIEW, player)) {
                    if (args.length < 2) {
                        MessageUtil.sendMessage(sender, config.message_args_error);
                        return true;
                    }
                    if (ImagePreviewer.getInstance().getMapManager().hasRunningPreview(player)) {
                        MessageUtil.sendMessage(sender, config.message_already_on_previewing);
                        return true;
                    }
                    if (ImagePreviewer.getInstance().getMapManager().queuedPlayers.contains(player.getUniqueId())) {
                        MessageUtil.sendMessage(sender, config.message_preview_still_loading);
                        return true;
                    }
                    MessageUtil.sendMessage(sender, config.message_preview_loading);
                    ImagePreviewer.getInstance().getMapManager().queuedPlayers.add(player.getUniqueId());
                    ImageLoader.imageAsData(args[1].trim())
                            .thenAccept(imageData -> {
                                if (args.length > 2) {
                                    ImagePreviewer.getInstance().getMapManager().queuedPlayers.remove(player.getUniqueId());
                                    if (!CachingPermTool.hasPermission(PermissionsEnum.PREVIEW_TIME, player)) {
                                        MessageUtil.sendMessage(sender, config.message_no_permission);
                                        return;
                                    }
                                    Long lifecycleTicks = Util.parseLong(args[2].trim());
                                    if (lifecycleTicks == null || lifecycleTicks < 1) {
                                        MessageUtil.sendMessage(sender, config.message_args_error);
                                        return;
                                    }
                                    new PacketMapDisplay(ImagePreviewer.getInstance(), player, imageData, lifecycleTicks).spawn();
                                } else {
                                    new PacketMapDisplay(ImagePreviewer.getInstance(), player, imageData).spawn();
                                }
                            })
                            .exceptionally(ex -> {
                                MessageUtil.sendMessage(sender, config.message_invalid_url);
                                ImagePreviewer.getInstance().getMapManager().queuedPlayers.remove(player.getUniqueId());
                                return null;
                            });
                } else {
                    MessageUtil.sendMessage(sender, config.message_no_permission);
                }
                return true;
            }
        }
        MessageUtil.sendMessage(sender, config.message_unknown_command);
        return true;
    }

}
