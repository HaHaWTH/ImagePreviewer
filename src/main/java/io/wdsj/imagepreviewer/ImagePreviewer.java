package io.wdsj.imagepreviewer;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import io.wdsj.imagepreviewer.command.ConstructCommandExecutor;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.image.ImageLoader;
import io.wdsj.imagepreviewer.listener.ChatListener;
import io.wdsj.imagepreviewer.packet.MapManager;
import io.wdsj.imagepreviewer.permission.CachingPermTool;
import io.wdsj.imagepreviewer.task.MapDisplayDirectionTask;
import io.wdsj.imagepreviewer.util.Util;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bstats.bukkit.Metrics;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.logging.Logger;

public class ImagePreviewer extends JavaPlugin {
    private static ImagePreviewer instance;
    public static final String PLUGIN_VERSION = "1.0";
    public static Logger LOGGER;
    private static Config config;
    private MapManager mapManager;
    private File dataFolder;
    private CachingPermTool permTool;
    private static TaskScheduler scheduler;
    private MyScheduledTask playerRaytraceTask;
    private BukkitAudiences audiences;
    public static final boolean isPaper = Util.isClassLoaded("com.destroystokyo.paper.PaperConfig");
    public static ImagePreviewer getInstance() {
        return instance;
    }

    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void onEnable() {
        LOGGER = getLogger();
        instance = this;
        scheduler = UniversalScheduler.getScheduler(this);
        dataFolder = getDataFolder();
        audiences = BukkitAudiences.create(this);
        reloadConfiguration();
        mapManager = new MapManager(this);
        permTool = CachingPermTool.enable(this);
        ImageLoader.init();
        Objects.requireNonNull(getCommand("imagepreviewer")).setExecutor(new ConstructCommandExecutor());
        playerRaytraceTask = new MapDisplayDirectionTask(this).runTaskTimerAsynchronously(this, 40L, 1L);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        Metrics metrics = new Metrics(this, 23927);
        LOGGER.info("ImagePreviewer is enabled!");
    }

    @Override
    public void onDisable() {
        permTool.disable();
        Objects.requireNonNull(getCommand("imagepreviewer")).setExecutor(null);
        mapManager.close();
        HandlerList.unregisterAll(this);
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        if (!playerRaytraceTask.isCancelled()) {
            playerRaytraceTask.cancel();
        }
        LOGGER.info("ImagePreviewer is disabled.");
    }

    public MapManager getMapManager() {
        return mapManager;
    }
    public static Config config() {
        return config;
    }

    public void createDirectory(File dir) throws IOException {
        try {
            Files.createDirectories(dir.toPath());
        } catch (FileAlreadyExistsException e) { // Thrown if dir exists but is not a directory
            if (dir.delete()) createDirectory(dir);
        }
    }

    public BukkitAudiences getAudiences() {
        return audiences;
    }

    public void reloadConfiguration() {
        try {
            Config.isReloading = true;
            createDirectory(dataFolder);
            config = new Config(this, dataFolder);
            config.saveConfig();
        } catch (Throwable t) {
            LOGGER.severe("Failed while loading config!");
        } finally {
            Config.isReloading = false;
        }
    }
}