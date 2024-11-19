package io.wdsj.imagepreviewer.listener;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.image.ImageLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.regex.Matcher;

public class ChatListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (Config.isReloading || !ImagePreviewer.config().listen_for_url_match) return;
        String message = event.getMessage();
        Matcher matcher = ImagePreviewer.config().url_match_regex.matcher(message);
        String url;
        if (matcher.find()) {
            url = matcher.group();
        } else {
            return;
        }
        if (ImagePreviewer.config().preload_images_in_chat && ImagePreviewer.config().enable_image_cache) {
            ImageLoader.imageAsBytes(url)
                    .exceptionally(throwable -> null);
        }
        String playerName = event.getPlayer().getName();
        Component component = Component.text(ImagePreviewer.config().message_url_matched.replace("%player%", playerName))
            .color(NamedTextColor.GREEN)
            .hoverEvent(HoverEvent.showText(Component.text(ImagePreviewer.config().message_hover_event)))
            .clickEvent(ClickEvent.runCommand("/imagepreviewer preview " + url));
        ImagePreviewer.getInstance().getAudiences().players().sendMessage(component);
    }
}
