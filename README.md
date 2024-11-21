# ImagePreviewer
Preview images in chat bar easily, efficiently.

## Features
- Pure vanilla experience, no resource pack needed.
- High customization.
- Support animated images like GIFs.
- Full packet-based, maps are client-side.
- Lightweight, zero load on server thread.

## Installation

1. **Download the plugin**: Download the latest version of ImagePreviewer.
2. **Install plugin**: Drop ImagePreviewer into the `./plugins` directory.
3. **Install [PacketEvents](https://github.com/retrooper/packetevents)**: Download the latest version of PacketEvents.
4. **Restart Server**: Restart your server to load ImagePreviewer.
5. Enjoy!

## Showcase

![image](./image/showcase_1.gif)

## Commands

- `/imagepreview preview <url>`: Preview an image from a URL.
- `/imagepreview preview <url> [time-ticks]`: Preview an image from a URL with a custom duration.
- `/imagepreview cancel`: Cancel the preview.
- `/imagepreview reload`: Reload the plugin config.
- `/imagepreview history`: Show preview history.
- `/imagepreview help`: Show help message.

## Permissions

- `imagepreviewer.use`: Allows the user to preview images.
- `imagepreviewer.use.time`: Allows the user to preview images for a specific duration.
- `imagepreviewer.command.reload`: Allows the user to reload the plugin.
- `imagepreviewer.command.help`: Allows the user to view help message.
- `imagepreviewer.command.history`: Allows the user to view preview history.
- `imagepreviewer.command.cancel`: Allows the user to cancel the preview.

## Compatibility

- Java 21
- Tested versions: Spigot/Paper 1.20 ~ 1.21.3(latest)

