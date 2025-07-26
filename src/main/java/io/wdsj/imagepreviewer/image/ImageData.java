package io.wdsj.imagepreviewer.image;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record ImageData(List<byte[]> frameData, boolean animated, Optional<Integer> frameDelay) {
    public @Nullable Integer parseFrameDelay() {
        if (frameDelay.isPresent()) {
            int delay = frameDelay.get();
            return delay > 0 ? delay * 10 : null; // convert to milliseconds
        } else {
            return null;
        }
    }

    public ImageData(List<byte[]> data) {
        this(data, false, Optional.empty());
    }
}
