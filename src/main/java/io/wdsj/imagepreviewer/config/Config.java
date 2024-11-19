package io.wdsj.imagepreviewer.config;

import io.github.thatsmusic99.configurationmaster.api.ConfigFile;
import io.github.thatsmusic99.configurationmaster.api.ConfigSection;
import io.wdsj.imagepreviewer.ImagePreviewer;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Config {

    private final ConfigFile config;
    private final ImagePreviewer plugin;
    public static boolean isReloading = false;
    public final boolean check_for_update;
    public final int preview_mode;
    public final double image_distance_to_player;
    public final long image_preview_lifetime;
    public final boolean process_multi_frame_gif;
    public final long gif_frame_delay;
    public final boolean gif_adaptive_frame_delay;
    public final boolean enable_image_cache;
    public final int cache_maximum_size;
    public final long cache_expire_time;
    public final boolean preload_images_in_chat;
    public final boolean listen_for_url_match;
    public final Pattern url_match_regex;
    public final boolean use_invisible_item_frame, use_glowing_item_frame;
    public final String message_reload_success, message_preview_loading,
    message_no_permission, message_invalid_url, message_command_player_only,
    message_unknown_command, message_already_on_previewing, message_url_matched, message_hover_event
            , message_preview_still_loading, message_help_info, message_args_error, message_cancel_success,
    message_nothing_to_cancel;

    public Config(ImagePreviewer plugin, File dataFolder) throws Exception {
        this.plugin = plugin;
        // Load config.yml with ConfigMaster
        this.config = ConfigFile.loadConfig(new File(dataFolder, "config.yml"));
        config.set("plugin-version", ImagePreviewer.PLUGIN_VERSION);

        // Pre-structure to force order
        structureConfig();

        this.check_for_update = getBoolean("plugin.check-update", true,
                "If set to true, will check for update on plugin startup.");
        this.message_reload_success = getString("message.reload-success", "&aImagePreviewer reloaded successfully!",
                "The message that will be sent to the player when the plugin is reloaded.");
        this.message_preview_loading = getString("message.preview-loading", "&aLoading image preview...",
                "The message that will be sent to the player when the image preview is loading.");
        this.message_no_permission = getString("message.no-permission", "&cYou do not have permission to do that.",
                "The message that will be sent to the player when they do not have permission to do something.");
        this.message_invalid_url = getString("message.invalid-url", "&cInvalid URL!",
                "The message that will be sent to the player when they enter an invalid URL.");
        this.message_command_player_only = getString("message.command-player-only", "&cThis command can only be used by players.",
                "The message that will be sent when try to use a command that can only be used by players.");
        this.message_unknown_command = getString("message.unknown-command", "&cUnknown command. Type /preview help for help.",
                "The message that will be sent to the player when they enter an unknown command.");
        this.message_args_error = getString("message.args-error", "&cInvalid arguments. Type /preview help for help.",
                "The message that will be sent to the player when they enter invalid arguments.");
        this.message_already_on_previewing = getString("message.already-on-previewing", "&cYou are already on previewing!",
                "The message that will be sent to the player when they are already on previewing.");
        this.message_preview_still_loading = getString("message.preview-still-loading", "&cPreview is still loading...",
                "The message that will be sent to the player when the image preview is still loading.");
        this.message_url_matched = getString("message.url-matched", "%player% just sent an image url!",
                "The message that will be sent to the player when the URL is matched.");
        this.message_hover_event = getString("message.hover-event", "Click to preview image",
                "The hover event that will be sent to the player when they hover over the image.");
        this.message_help_info = getString("message.help-info", """
                &b&l&nImage Previewer
                &a/imagepreviewer reload &7- Reload the plugin
                &a/imagepreviewer help &7- Show this message
                &a/imagepreviewer preview <url> [time-ticks] &7- Preview an image from given url
                &a/imagepreviewer cancel &7- Cancel running preview"""
        );
        this.message_cancel_success = getString("message.cancel-success", "&aCancelled image preview.",
                "The message that will be sent to the player when they cancel the image preview.");
        this.message_nothing_to_cancel = getString("message.nothing-to-cancel", "&cYou are not on previewing.",
                "The message that will be sent to the player when they are not on previewing.");


        this.preview_mode = getInt("plugin.preview-mode", 2,
                """
                        Preview modes:
                        1. Image will move with player, like ray tracing.
                        2. Image will stay in-place, but will rotate towards player.
                        3. Image will stay in-place.
                        4. Image will stay in-place, but will rotate towards player. (Only yaw)""");
        this.image_distance_to_player = getDouble("plugin.image-distance-to-player", 1.0,
                "The distance between the player and the image.");
        this.image_preview_lifetime = getInt("plugin.image-preview-lifetime", 180,
                "How long can one image preview survive.");
        this.enable_image_cache = getBoolean("plugin.image-cache.enabled", true,
                "Whether to cache converted image data.");
        this.cache_maximum_size = getInt("plugin.image-cache.cache-maximum-size", 100,
                "The maximum size of the cache.");
        this.preload_images_in_chat = getBoolean("plugin.image-cache.preload-images-in-chat", false,
                "If set to true, will preload images sent in chat.");
        this.cache_expire_time = getLong("plugin.image-cache.cache-expire-time", 5,
                "The time in minutes that the cache will expire.");
        this.process_multi_frame_gif = getBoolean("plugin.gif.process-multi-frame-gif", true,
                "If set to false, will process gifs as single image.");
        this.gif_frame_delay = getLong("plugin.gif.gif-frame-delay", 100,
                "The delay between each frame in milliseconds.");
        this.gif_adaptive_frame_delay = getBoolean("plugin.gif.gif-adaptive-frame-delay", true,
                "If set to true, will use adaptive frame delay.");
        this.url_match_regex = Pattern.compile(getString("plugin.url-match-regex", "https?://[^\\s]+?\\.(?:png|bmp|jpg|jpeg|gif|webp)\\b",
                "The regex that will be used to match the URL."));
        this.listen_for_url_match = getBoolean("plugin.listen-for-url-match", true,
                "If set to true, will listen for url match and send message to player.");
        this.use_invisible_item_frame = getBoolean("plugin.use-invisible-item-frame", false,
                "If set to true, will use invisible item frame to display image.");
        this.use_glowing_item_frame = getBoolean("plugin.use-glowing-item-frame", false,
                "If set to true, will use glowing item frame to display image.");
    }

    public void saveConfig() {
        try {
            config.save();
        } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to save config file: " + e.getMessage());
        }
    }

    private void structureConfig() {
        createTitledSection("Plugin general setting", "plugin");
        createTitledSection("Message", "message");
    }

    public void createTitledSection(String title, String path) {
        config.addSection(title);
        config.addDefault(path, null);
    }

    public boolean getBoolean(String path, boolean def, String comment) {
        config.addDefault(path, def, comment);
        return config.getBoolean(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path, def);
    }

    public String getString(String path, String def, String comment) {
        config.addDefault(path, def, comment);
        return config.getString(path, def);
    }

    public String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path, def);
    }

    public double getDouble(String path, double def, String comment) {
        config.addDefault(path, def, comment);
        return config.getDouble(path, def);
    }

    public double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path, def);
    }

    public int getInt(String path, int def, String comment) {
        config.addDefault(path, def, comment);
        return config.getInteger(path, def);
    }

    public int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInteger(path, def);
    }

    public long getLong(String path, long def, String comment) {
        config.addDefault(path, def, comment);
        return config.getLong(path, def);
    }

    public long getLong(String path, long def) {
        config.addDefault(path, def);
        return config.getLong(path, def);
    }

    public List<String> getList(String path, List<String> def, String comment) {
        config.addDefault(path, def, comment);
        return config.getStringList(path);
    }

    public List<String> getList(String path, List<String> def) {
        config.addDefault(path, def);
        return config.getStringList(path);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue) {
        config.addDefault(path, null);
        config.makeSectionLenient(path);
        defaultKeyValue.forEach((string, object) -> config.addExample(path+"."+string, object));
        return config.getConfigSection(path);
    }

    public ConfigSection getConfigSection(String path, Map<String, Object> defaultKeyValue, String comment) {
        config.addDefault(path, null, comment);
        config.makeSectionLenient(path);
        defaultKeyValue.forEach((string, object) -> config.addExample(path+"."+string, object));
        return config.getConfigSection(path);
    }

    public void addComment(String path, String comment) {
        config.addComment(path, comment);
    }
}
