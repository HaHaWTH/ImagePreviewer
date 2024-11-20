package io.wdsj.imagepreviewer.permission;

public enum PermissionsEnum {
    PREVIEW("imagepreviewer.use"),
    PREVIEW_TIME("imagepreviewer.use.time"),
    CANCEL_PREVIEW("imagepreviewer.command.cancel"),
    RELOAD("imagepreviewer.command.reload"),
    HISTORY("imagepreviewer.command.history"),
    HELP("imagepreviewer.command.help");

    private final String permission;

    PermissionsEnum(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
