package io.wdsj.imagepreviewer.image;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.wdsj.imagepreviewer.ImagePreviewer;
import org.bukkit.map.MapPalette;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ImageLoader {

    private static final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("ImagePreviewer-ImageLoader")
            .setThreadFactory(Thread.ofVirtual().factory())
            .build()
    );

    private static Cache<String, List<byte[]>> imageCache;

    public static void init() {
        imageCache = CacheBuilder.newBuilder()
                .maximumSize(ImagePreviewer.config().cache_maximum_size)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public static CompletableFuture<List<byte[]>> imageAsBytes(String urlString) {
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
                boolean isGif = getFormatName(url).equalsIgnoreCase("gif");
                BufferedImage[] originalImages = isGif ? readAllFrames(url) : new BufferedImage[]{ImageIO.read(url)};
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
                if (ImagePreviewer.config().enable_image_cache) {
                    imageCache.put(urlString, imageDataList);
                }
                return imageDataList;
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

    private static BufferedImage[] readAllFrames(URL url) throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        try (ImageInputStream input = ImageIO.createImageInputStream(url.openStream())) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(input, false);

            int frameCount = reader.getNumImages(true);
            for (int i = 0; i < frameCount; i++) {
                frames.add(reader.read(i));
            }
        }
        return frames.toArray(new BufferedImage[0]);
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