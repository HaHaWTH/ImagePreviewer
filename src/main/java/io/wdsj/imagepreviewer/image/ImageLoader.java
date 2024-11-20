package io.wdsj.imagepreviewer.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.util.Pair;
import io.wdsj.imagepreviewer.util.Util;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ImageLoader {

    private static final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("ImagePreviewer ImageLoader")
            .setThreadFactory(Thread.ofVirtual().factory())
            .build()
    );

    private static Cache<String, ImageData> imageCache;

    public static void init() {
        imageCache = CacheBuilder.newBuilder()
                .maximumSize(ImagePreviewer.config().cache_maximum_size)
                .expireAfterWrite(ImagePreviewer.config().cache_expire_time, TimeUnit.MINUTES)
                .build();
    }
    public static CompletableFuture<ImageData> imageAsData(String urlString) {
        if (ImagePreviewer.config().enable_image_cache) {
            var cachedImage = imageCache.getIfPresent(urlString);
            if (cachedImage != null) {
                return CompletableFuture.completedFuture(cachedImage);
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = new URI(urlString);
                URL url = uri.toURL();
                boolean isAnimated = getFormatName(url).equalsIgnoreCase("gif") && !Config.isReloading && ImagePreviewer.config().process_multi_frame_gif;
                if (isAnimated) {
                    var pairs = readAllFramesGif(url);
                    BufferedImage[] originalImages = pairs.left();
                    if (originalImages.length == 0) {
                        throw new IllegalArgumentException("The provided URL is not a valid image: " + urlString);
                    }
                    List<BufferedImage> resizedImagesList = new ArrayList<>();
                    for (BufferedImage originalImage : originalImages) {
                        resizedImagesList.add(MapPalette.resizeImage(originalImage));
                    }

                    List<byte[]> imageDataList = new ArrayList<>();
                    for (BufferedImage resizedImage : resizedImagesList) {
                        imageDataList.add(MapPalette.imageToBytes(resizedImage));
                    }
                    ImageData data = new ImageData(imageDataList, true, pairs.right());
                    if (ImagePreviewer.config().enable_image_cache) {
                        imageCache.put(urlString, data);
                    }
                    return data;
                } else {
                    BufferedImage originalImage = ImageIO.read(url);
                    BufferedImage resizedImage = MapPalette.resizeImage(originalImage);
                    List<byte[]> imageDataList = List.of(MapPalette.imageToBytes(resizedImage));
                    ImageData data = new ImageData(imageDataList);
                    if (ImagePreviewer.config().enable_image_cache) {
                        imageCache.put(urlString, data);
                    }
                    return data;
                }
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException("Failed to download or process the image from URL: " + urlString, e);
            }
        }, executor);
    }

    private static String getFormatName(URL url) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(url.openStream())) {
            ImageReader reader = ImageIO.getImageReaders(iis).next();
            return reader.getFormatName();
        }
    }

    private static Pair<BufferedImage[], Optional<Integer>> readAllFramesGif(URL url) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        try (ImageInputStream input = ImageIO.createImageInputStream(url.openStream())) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(input, false);

            int frameCount = reader.getNumImages(true);
            BufferedImage previousFrame = null;

            for (int i = 0; i < frameCount; i++) {
                BufferedImage currentFrame = reader.read(i);

                IIOMetadata metadata = reader.getImageMetadata(i);
                String metaFormatName = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

                IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
                String disposeMethod = "none";
                if (graphicsControlExtensionNode != null) {
                    disposeMethod = graphicsControlExtensionNode.getAttribute("disposalMethod");
                }

                IIOMetadataNode imageDescriptorNode = getNode(root, "ImageDescriptor");
                int imageLeft = 0, imageTop = 0;
                if (imageDescriptorNode != null) {
                    imageLeft = Integer.parseInt(imageDescriptorNode.getAttribute("imageLeftPosition"));
                    imageTop = Integer.parseInt(imageDescriptorNode.getAttribute("imageTopPosition"));
                }

                if (previousFrame == null) {
                    previousFrame = new BufferedImage(
                            reader.getWidth(i),
                            reader.getHeight(i),
                            BufferedImage.TYPE_INT_ARGB
                    );
                }
                BufferedImage combinedFrame = new BufferedImage(
                        previousFrame.getWidth(),
                        previousFrame.getHeight(),
                        BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D g = combinedFrame.createGraphics();
                g.drawImage(previousFrame, 0, 0, null);
                g.drawImage(currentFrame, imageLeft, imageTop, null);
                g.dispose();

                frames.add(combinedFrame);

                switch (disposeMethod) {
                    case "restoreToBackgroundColor":
                        previousFrame = new BufferedImage(
                                previousFrame.getWidth(),
                                previousFrame.getHeight(),
                                BufferedImage.TYPE_INT_ARGB
                        );
                        break;
                    case "restoreToPrevious":
                        break;
                    default:
                        previousFrame = combinedFrame;
                        break;
                }
            }

            IIOMetadata imageMetaData = reader.getImageMetadata(0);
            String metaFormatName = imageMetaData.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) imageMetaData.getAsTree(metaFormatName);
            IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
            if (graphicsControlExtensionNode != null) {
                return Pair.of(frames.toArray(new BufferedImage[0]), Optional.of(Util.toInt(graphicsControlExtensionNode.getAttribute("delayTime"), -1)));
            } else {
                return Pair.of(frames.toArray(new BufferedImage[0]), Optional.empty());
            }
        }
    }

    @SuppressWarnings("all")
    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        int nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        return null;
    }

}