package io.wdsj.imagepreviewer.command;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.image.ImageLoader;
import io.wdsj.imagepreviewer.permission.PermissionsEnum;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConstructTabCompleter implements TabCompleter {
    private static String[] cachedLocalFiles = new String[0];
    static {
        rebuildLocalFilesCache();
    }
    public static void rebuildLocalFilesCache() {
        File localDir = new File(ImagePreviewer.getInstance().getDataFolder(), ImageLoader.LOCAL_DIR_NAME);
        if (localDir.exists() && localDir.isDirectory()) {
            String[] files = localDir.list();
            if (files != null) {
                cachedLocalFiles = files;
            }
        }
    }
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String @NonNull [] args) {
        if (args.length == 1) {
            List<String> tabComplete = new ArrayList<>();
            if (sender.hasPermission(PermissionsEnum.RELOAD.getPermission())) {
                tabComplete.add("reload");
            }
            if (sender.hasPermission(PermissionsEnum.HELP.getPermission())) {
                tabComplete.add("help");
            }
            if (sender.hasPermission(PermissionsEnum.CANCEL_PREVIEW.getPermission())) {
                tabComplete.add("cancel");
            }
            if (sender.hasPermission(PermissionsEnum.PREVIEW.getPermission())) {
                tabComplete.add("preview");
            }
            if (sender.hasPermission(PermissionsEnum.HISTORY.getPermission())) {
                tabComplete.add("history");
            }
            return StringUtil.copyPartialMatches(args[0], tabComplete, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            if (sender.hasPermission(PermissionsEnum.PREVIEW_LOCAL.getPermission())) {
                List<String> suggestions = new ArrayList<>();
                for (String f : cachedLocalFiles) {
                    suggestions.add("file:" + f);
                }
                return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
            }
        }
        return Collections.emptyList();
    }
}