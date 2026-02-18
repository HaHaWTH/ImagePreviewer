package io.wdsj.imagepreviewer.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.wdsj.imagepreviewer.ImagePreviewer;
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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
    private static final Map<String, CompletableFuture<ImageData>> pendingTasks = new ConcurrentHashMap<>();

    public static final String LOCAL_DIR_NAME = "local_storage";
    private static final String FILE_PREFIX = "file:";

    private record AnimatedImage(List<BufferedImage> frames, Optional<Integer> delay) {}

    public static void init() {
        imageCache = CacheBuilder.newBuilder()
                .maximumSize(config().cache_maximum_size)
                .expireAfterWrite(config().cache_expire_time, TimeUnit.MINUTES)
                .build();
        File localDir = new File(ImagePreviewer.getInstance().getDataFolder(), LOCAL_DIR_NAME);
        if (!localDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            localDir.mkdirs();
        }
    }

    public static ImageFetchTask imageAsData(String input) {
        if (config().enable_image_cache) {
            var cachedImage = imageCache.getIfPresent(input);
            if (cachedImage != null) {
                return new ImageFetchTask(input, CompletableFuture.completedFuture(cachedImage));
            }
        }

        CompletableFuture<ImageData> future = pendingTasks.computeIfAbsent(input, key ->
                CompletableFuture.supplyAsync(() -> {
                            try {
                                ImageData data;
                                if (key.startsWith(FILE_PREFIX)) {
                                    String fileName = key.substring(FILE_PREFIX.length());
                                    data = processImageFromFile(fileName);
                                } else {
                                    URL url = new URI(key).toURL();
                                    data = processImageFromUrl(url);
                                }

                                if (config().enable_image_cache) {
                                    imageCache.put(key, data);
                                }
                                return data;
                            } catch (IOException | URISyntaxException | IllegalArgumentException e) {
                                throw new CompletionException(e);
                            }
                        }, executor)
                        .whenComplete((result, ex) -> pendingTasks.remove(input))
        );

        return new ImageFetchTask(input, future);
    }

    private static ImageData processImageFromFile(String fileName) throws IOException {
        File dataFolder = ImagePreviewer.getInstance().getDataFolder();
        File localDir = new File(dataFolder, LOCAL_DIR_NAME);
        File imageFile = new File(localDir, fileName);

        if (!imageFile.getCanonicalPath().startsWith(localDir.getCanonicalPath())) {
            throw new IOException("Access denied: Invalid file path.");
        }

        if (!imageFile.exists()) {
            throw new IOException("File not found: " + fileName);
        }

        if (imageFile.isDirectory()) {
            throw new IOException("Path is a directory, not a file.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(imageFile)) {
            if (input == null) throw new IOException("Unable to create ImageInputStream from file.");
            return processImageStream(input);
        }
    }

    private static ImageData processImageFromUrl(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
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
            return processImageStream(input);
        }
    }

    private static ImageData processImageStream(ImageInputStream input) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new IOException("No suitable ImageReader found for the image format.");
        }

        ImageReader reader = readers.next();
        try {
            reader.setInput(input, false);

            boolean isAnimated = reader.getFormatName().equalsIgnoreCase("gif")
                    && !Config.isReloading
                    && config().process_multi_frame_gif;

            if (isAnimated) {
                AnimatedImage animatedImage = readAnimatedGif(reader);
                if (animatedImage.frames().isEmpty()) {
                    throw new IllegalArgumentException("The provided data is not a valid animated image");
                }
                List<byte[]> imageDataList = animatedImage.frames().stream()
                        .map(MapPalette::resizeImage)
                        .map(MapImageUtil::imageToBytes)
                        .collect(Collectors.toList());
                return new ImageData(imageDataList, true, animatedImage.delay());
            } else {
                BufferedImage originalImage = reader.read(0);
                if (originalImage == null) {
                    throw new IllegalArgumentException("The provided data is not a valid image");
                }
                BufferedImage resizedImage = MapPalette.resizeImage(originalImage);
                List<byte[]> imageDataList = List.of(MapImageUtil.imageToBytes(resizedImage));
                return new ImageData(imageDataList);
            }
        } finally {
            reader.dispose();
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

    public record ImageFetchTask(String urlString, CompletableFuture<ImageData> future) {
        public ImageFetchTask thenAcceptOnMain(Consumer<ImageData> consumer) {
            future.thenAccept(data -> ImagePreviewer.getScheduler().runTask(() -> consumer.accept(data)));
            return this;
        }

        public ImageFetchTask thenAccept(Consumer<ImageData> consumer) {
            future.thenAccept(consumer);
            return this;
        }

        public ImageFetchTask exceptionally(Function<Throwable, ImageData> function) {
            future.exceptionally(function);
            return this;
        }

        public ImageFetchTask whenComplete(BiConsumer<ImageData, Throwable> consumer) {
            future.whenComplete(consumer);
            return this;
        }
    }
}