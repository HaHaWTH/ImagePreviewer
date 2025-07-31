package io.wdsj.imagepreviewer.hook.floodgate.form;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.image.ImageLoader;
import io.wdsj.imagepreviewer.listener.ChatListener;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import io.wdsj.imagepreviewer.permission.CachingPermTool;
import io.wdsj.imagepreviewer.permission.PermissionsEnum;
import io.wdsj.imagepreviewer.util.MessageUtil;
import org.bukkit.Bukkit;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.ArrayList;
import java.util.List;

public class PreviewHistoryForm extends AbstractForm {
    private final SimpleForm.Builder formBuilder;
    private final List<ChatListener.MessageEntry> history;
    private void makeButton(ChatListener.MessageEntry entry) {
        formBuilder.button(MessageUtil.translateColors(ImagePreviewer.config().form_history_button
                        .replace("%time%", entry.time())
                        .replace("%sender%", entry.sender())
                        .replace("%url%", entry.message())),
                FormImage.Type.URL,
                entry.message());
    }
    public PreviewHistoryForm() {
        this.formBuilder = SimpleForm.builder();
        formBuilder.title(MessageUtil.translateColors(ImagePreviewer.config().form_title));
        formBuilder.content(MessageUtil.translateColors(ImagePreviewer.config().form_history_content));
        this.history = ChatListener.getUrlHistory();
        history.forEach(this::makeButton);
    }

    public PreviewHistoryForm(int limit) {
        this.formBuilder = SimpleForm.builder();
        formBuilder.title(MessageUtil.translateColors(ImagePreviewer.config().form_title));
        formBuilder.content(MessageUtil.translateColors(ImagePreviewer.config().form_history_content));
        var tempHistory = new ArrayList<ChatListener.MessageEntry>();
        int count = 0;
        for (var entry : ChatListener.getUrlHistory()) {
            if (count++ >= limit) {
                break;
            }
            tempHistory.add(entry);
        }
        this.history = tempHistory;
        history.forEach(this::makeButton);
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
                        if (!new PacketMapDisplay(plugin, javaPlayer, imageData).spawn()) {
                            MessageUtil.sendMessage(javaPlayer, config.message_not_empty_hand);
                        }
                    })
                    .exceptionally(ex -> {
                        MessageUtil.sendMessage(javaPlayer, config.message_failed_to_load.replace("%reason%", ex.getMessage()));
                        plugin.getMapManager().queuedPlayers.remove(uuid);
                        return null;
                    });
        });
        player.sendForm(formBuilder.build());
    }
}
