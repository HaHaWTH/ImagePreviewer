package io.wdsj.imagepreviewer.image;

import java.util.List;
import java.util.Optional;

/**
 * Represents image data with frame data, animation status, and frame delay.
 */
public record ImageData(List<byte[]> frameData, boolean animated, Optional<Integer> frameDelay) {
    public int parseFrameDelay() {
        if (frameDelay.isPresent()) {
            int delay = frameDelay.get();
            return delay > 0 ? delay * 10 : -1; // convert to milliseconds
        } else {
            return -1;
        }
    }

    public ImageData(List<byte[]> data) {
        this(data, false, Optional.empty());
    }
}
