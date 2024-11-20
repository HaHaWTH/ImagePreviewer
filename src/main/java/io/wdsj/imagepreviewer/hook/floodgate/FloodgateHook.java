package io.wdsj.imagepreviewer.hook.floodgate;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.image.ImageLoader;
import io.wdsj.imagepreviewer.listener.ChatListener;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import io.wdsj.imagepreviewer.permission.CachingPermTool;
import io.wdsj.imagepreviewer.permission.PermissionsEnum;
import io.wdsj.imagepreviewer.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.List;

public class FloodgateHook {

    public static boolean isFloodgatePresent() {
        return Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    public static boolean isFloodgatePlayer(Player player) {
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }

    public static FloodgatePlayer getFloodgatePlayer(Player player) {
        return FloodgateApi.getInstance().getPlayer(player.getUniqueId());
    }

    public static class PreviewHistoryForm extends AbstractForm {
        private final SimpleForm.Builder formBuilder;
        private final List<ChatListener.MessageEntry> history;
        public PreviewHistoryForm() {
            this.formBuilder = SimpleForm.builder();
            formBuilder.title(MessageUtil.translateColors(ImagePreviewer.config().form_title));
            formBuilder.content(MessageUtil.translateColors(ImagePreviewer.config().form_history_content));
            this.history = ChatListener.getUrlHistory();
            history.forEach(entry -> {
                formBuilder.button(entry.sender() + ": " + entry.message(), FormImage.Type.URL, entry.message());
            });
        }

        @Override
        public void sendForm(FloodgatePlayer player) {
            formBuilder.validResultHandler(result -> {
                int index = result.clickedButtonId();
                if (index >= ChatListener.getUrlHistory().size()) return;
                var entry = history.get(index);
                var plugin = ImagePreviewer.getInstance();
                var config = ImagePreviewer.config();
                var uuid = player.getCorrectUniqueId();
                var javaPlayer = Bukkit.getPlayer(uuid);
                if (javaPlayer == null) return;
                if (!CachingPermTool.hasPermission(PermissionsEnum.PREVIEW, javaPlayer)) {
                    MessageUtil.sendMessage(javaPlayer, config.message_no_permission);
                    return;
                }
                if (plugin.getMapManager().queuedPlayers.contains(uuid)) {
                    MessageUtil.sendMessage(javaPlayer, config.message_preview_still_loading);
                    return;
                }
                if (plugin.getMapManager().hasRunningPreview(javaPlayer)) {
                    MessageUtil.sendMessage(javaPlayer, config.message_already_on_previewing);
                    return;
                }
                plugin.getMapManager().queuedPlayers.add(uuid);
                MessageUtil.sendMessage(javaPlayer, config.message_preview_loading);
                ImageLoader.imageAsData(entry.message())
                        .thenAccept(imageData -> {
                            new PacketMapDisplay(plugin, javaPlayer, imageData).spawn();
                        })
                        .exceptionally(ex -> {
                            MessageUtil.sendMessage(javaPlayer, config.message_invalid_url);
                            plugin.getMapManager().queuedPlayers.remove(uuid);
                            return null;
                        });
            });
            player.sendForm(formBuilder.build());
        }
    }
}
