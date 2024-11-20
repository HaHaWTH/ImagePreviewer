package io.wdsj.imagepreviewer.command;

import io.wdsj.imagepreviewer.permission.PermissionsEnum;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConstructTabCompleter implements TabCompleter {
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> tabComplete = new ArrayList<>();
            if (sender.hasPermission(PermissionsEnum.RELOAD.getPermission()) && args[0].startsWith("r")) {
                tabComplete.add("reload");
            } else if (sender.hasPermission(PermissionsEnum.HELP.getPermission()) && args[0].startsWith("h")) {
                tabComplete.add("help");
            } else if (sender.hasPermission(PermissionsEnum.CANCEL_PREVIEW.getPermission()) && args[0].startsWith("i")) {
                tabComplete.add("cancel");
            } else if (sender.hasPermission(PermissionsEnum.PREVIEW.getPermission()) && args[0].startsWith("p")) {
                tabComplete.add("preview");
            } else if (sender.hasPermission(PermissionsEnum.HISTORY.getPermission()) && args[0].startsWith("hi")) {
                tabComplete.add("history");
            } else if (sender.hasPermission(PermissionsEnum.RELOAD.getPermission()) ||
                    sender.hasPermission(PermissionsEnum.HELP.getPermission()) || sender.hasPermission(PermissionsEnum.PREVIEW.getPermission()) ||
                    sender.hasPermission(PermissionsEnum.HISTORY.getPermission())) {
                tabComplete.add("help");
                tabComplete.add("reload");
                tabComplete.add("preview");
                tabComplete.add("cancel");
                tabComplete.add("history");
            }
            return tabComplete;
        }
        return Collections.emptyList(); // Must return empty list, if null paper will supply player names
    }
}
