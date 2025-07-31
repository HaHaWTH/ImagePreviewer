package io.wdsj.imagepreviewer.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.util.MapImageUtil;
import io.wdsj.imagepreviewer.util.Util;
import io.wdsj.imagepreviewer.util.VirtualThreadUtil;
import org.bukkit.map.MapPalette;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.wdsj.imagepreviewer.ImagePreviewer.config;
import static io.wdsj.imagepreviewer.util.Util.formatSize;

public class ImageLoader {

    private static final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("ImagePreviewer ImageLoader-%d")
            .setThreadFactory(VirtualThreadUtil.newVirtualThreadFactoryOrDefault())
            .build()
    );

    private static Cache<String, ImageData> imageCache;

    private record AnimatedImage(List<BufferedImage> frames, Optional<Integer> delay) {}

    public static void init() {
        imageCache = CacheBuilder.newBuilder()
                .maximumSize(config().cache_maximum_size)
                .expireAfterWrite(config().cache_expire_time, TimeUnit.MINUTES)
                .build();
    }

    public static CompletableFuture<ImageData> imageAsData(String urlString) {
        if (config().enable_image_cache) {
            var cachedImage = imageCache.getIfPresent(urlString);
            if (cachedImage != null) {
                return CompletableFuture.completedFuture(cachedImage);
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URI(urlString).toURL();
                ImageData data = processImageFromUrl(url);

                if (config().enable_image_cache) {
                    imageCache.put(urlString, data);
                }
                return data;
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException("Failed to download or process the image from URL: " + e.getMessage());
            }
        }, executor);
    }

    private static ImageData processImageFromUrl(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        long limit = config().image_max_size_kb;
        if (limit != -1) {
            long contentLength = connection.getContentLengthLong();
            long limitBytes = limit * 1024;
            if (contentLength > limitBytes) {
                throw new IOException("Image size " + formatSize(contentLength) + " exceeds limit " + formatSize(limitBytes));
            }
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(connection.getInputStream())) {
            if (input == null) {
                throw new IOException("Unable to create ImageInputStream from URL.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No suitable ImageReader found for the image format at URL.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false);

                boolean isAnimated = reader.getFormatName().equalsIgnoreCase("gif")
                        && !Config.isReloading
                        && config().process_multi_frame_gif;

                if (isAnimated) {
                    AnimatedImage animatedImage = readAnimatedGif(reader); // currently only supports gif
                    if (animatedImage.frames().isEmpty()) {
                        throw new IllegalArgumentException("The provided URL is not a valid animated image");
                    }
                    List<byte[]> imageDataList = animatedImage.frames().stream()
                            .map(MapPalette::resizeImage)
                            .map(MapImageUtil::imageToBytes)
                            .collect(Collectors.toList());
                    return new ImageData(imageDataList, true, animatedImage.delay());
                } else {
                    BufferedImage originalImage = reader.read(0);
                    if (originalImage == null) {
                        throw new IllegalArgumentException("The provided URL is not a valid image");
                    }
                    BufferedImage resizedImage = MapPalette.resizeImage(originalImage);
                    List<byte[]> imageDataList = List.of(MapImageUtil.imageToBytes(resizedImage));
                    return new ImageData(imageDataList);
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static AnimatedImage readAnimatedGif(ImageReader reader) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        int frameCount = reader.getNumImages(true);
        BufferedImage previousFrame = null;

        for (int i = 0; i < frameCount; i++) {
            BufferedImage currentFrame = reader.read(i);
            IIOMetadata metadata = reader.getImageMetadata(i);
            String metaFormatName = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

            IIOMetadataNode gceNode = getNode(root, "GraphicControlExtension");
            String disposalMethod = "none";
            if (gceNode != null) {
                disposalMethod = gceNode.getAttribute("disposalMethod");
            }

            IIOMetadataNode imageDescNode = getNode(root, "ImageDescriptor");
            int imageLeft = 0, imageTop = 0;
            if (imageDescNode != null) {
                imageLeft = Integer.parseInt(imageDescNode.getAttribute("imageLeftPosition"));
                imageTop = Integer.parseInt(imageDescNode.getAttribute("imageTopPosition"));
            }

            if (previousFrame == null) {
                previousFrame = new BufferedImage(reader.getWidth(i), reader.getHeight(i), BufferedImage.TYPE_INT_ARGB);
            }

            BufferedImage combinedFrame = new BufferedImage(
                    previousFrame.getWidth(),
                    previousFrame.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g = combinedFrame.createGraphics();
            g.drawImage(previousFrame, 0, 0, null);
            g.drawImage(currentFrame, imageLeft, imageTop, null);
            frames.add(combinedFrame);

            switch (disposalMethod) {
                case "restoreToBackgroundColor":
                    previousFrame = new BufferedImage(previousFrame.getWidth(), previousFrame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    break;
                case "restoreToPrevious":
                    break;
                default:
                    previousFrame = combinedFrame;
                    break;
            }
        }

        Optional<Integer> delay = Optional.empty();
        IIOMetadata firstFrameMetadata = reader.getImageMetadata(0);
        String metaFormatName = firstFrameMetadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) firstFrameMetadata.getAsTree(metaFormatName);
        IIOMetadataNode gceNode = getNode(root, "GraphicControlExtension");
        if (gceNode != null) {
            delay = Optional.of(Util.toInt(gceNode.getAttribute("delayTime"), -1));
        }

        return new AnimatedImage(frames, delay);
    }

    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        for (int i = 0; i < rootNode.getLength(); i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        for (int i = 0; i < rootNode.getLength(); i++) {
            IIOMetadataNode foundNode = getNode((IIOMetadataNode) rootNode.item(i), nodeName);
            if (foundNode != null) {
                return foundNode;
            }
        }
        return null;
    }
}