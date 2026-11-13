/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.stickers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-Java & Android Bitmap image processor for creating custom stickers from static images
 * (with automatic or color-picked background removal, anti-aliased edge feathering, die-cut
 * sticker outline border, and transparent margin trimming) and from animated GIFs.
 */
public final class GboardStickerImageProcessor {
    public static final int DEFAULT_MAX_STICKER_DIMENSION_PX = 512;
    public static final int DEFAULT_TOLERANCE = 32;
    public static final int DEFAULT_OUTLINE_RADIUS_PX = 6;
    public static final int DEFAULT_TRIM_PADDING_PX = 12;

    private GboardStickerImageProcessor() {
    }

    /**
     * Configuration options for creating a sticker.
     */
    public static final class StickerOptions {
        private final boolean removeBackground;
        private final int tolerance;
        private final boolean contiguousFloodFill;
        private final Integer manualTargetColor;
        private final boolean addOutline;
        private final int outlineColor;
        private final int outlineRadiusPx;
        private final boolean autoTrim;
        private final int maxDimensionPx;

        public StickerOptions(
                boolean removeBackground,
                int tolerance,
                boolean contiguousFloodFill,
                Integer manualTargetColor,
                boolean addOutline,
                int outlineColor,
                int outlineRadiusPx,
                boolean autoTrim,
                int maxDimensionPx) {
            this.removeBackground = removeBackground;
            this.tolerance = Math.max(0, Math.min(100, tolerance));
            this.contiguousFloodFill = contiguousFloodFill;
            this.manualTargetColor = manualTargetColor;
            this.addOutline = addOutline;
            this.outlineColor = outlineColor;
            this.outlineRadiusPx = Math.max(1, Math.min(24, outlineRadiusPx));
            this.autoTrim = autoTrim;
            this.maxDimensionPx = Math.max(64, Math.min(1024, maxDimensionPx));
        }

        public static StickerOptions defaults() {
            return new StickerOptions(
                    true,
                    DEFAULT_TOLERANCE,
                    true,
                    null,
                    true,
                    Color.WHITE,
                    DEFAULT_OUTLINE_RADIUS_PX,
                    true,
                    DEFAULT_MAX_STICKER_DIMENSION_PX);
        }

        public boolean shouldRemoveBackground() {
            return removeBackground;
        }

        public int getTolerance() {
            return tolerance;
        }

        public boolean isContiguousFloodFill() {
            return contiguousFloodFill;
        }

        public Integer getManualTargetColor() {
            return manualTargetColor;
        }

        public boolean shouldAddOutline() {
            return addOutline;
        }

        public int getOutlineColor() {
            return outlineColor;
        }

        public int getOutlineRadiusPx() {
            return outlineRadiusPx;
        }

        public boolean shouldAutoTrim() {
            return autoTrim;
        }

        public int getMaxDimensionPx() {
            return maxDimensionPx;
        }

        public StickerOptions withRemoveBackground(boolean value) {
            return new StickerOptions(
                    value, tolerance, contiguousFloodFill, manualTargetColor,
                    addOutline, outlineColor, outlineRadiusPx, autoTrim, maxDimensionPx);
        }

        public StickerOptions withTolerance(int value) {
            return new StickerOptions(
                    removeBackground, value, contiguousFloodFill, manualTargetColor,
                    addOutline, outlineColor, outlineRadiusPx, autoTrim, maxDimensionPx);
        }

        public StickerOptions withContiguousFloodFill(boolean value) {
            return new StickerOptions(
                    removeBackground, tolerance, value, manualTargetColor,
                    addOutline, outlineColor, outlineRadiusPx, autoTrim, maxDimensionPx);
        }

        public StickerOptions withManualTargetColor(Integer color) {
            return new StickerOptions(
                    removeBackground, tolerance, contiguousFloodFill, color,
                    addOutline, outlineColor, outlineRadiusPx, autoTrim, maxDimensionPx);
        }

        public StickerOptions withAddOutline(boolean value) {
            return new StickerOptions(
                    removeBackground, tolerance, contiguousFloodFill, manualTargetColor,
                    value, outlineColor, outlineRadiusPx, autoTrim, maxDimensionPx);
        }

        public StickerOptions withAutoTrim(boolean value) {
            return new StickerOptions(
                    removeBackground, tolerance, contiguousFloodFill, manualTargetColor,
                    addOutline, outlineColor, outlineRadiusPx, value, maxDimensionPx);
        }
    }

    /**
     * Pure pixel buffer representation for platform-independent image manipulation and testing.
     */
    public static final class PixelImage {
        private final int[] pixels;
        private final int width;
        private final int height;

        public PixelImage(int[] pixels, int width, int height) {
            if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
                throw new IllegalArgumentException("Invalid PixelImage dimensions or pixel array.");
            }
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }

        public int[] getPixels() {
            return pixels;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    /**
     * Output of processing a static image or animated GIF into a ready-to-save sticker.
     */
    public static final class ProcessedStickerResult {
        private final byte[] encodedBytes;
        private final String mimeType;
        private final String fileExtension;
        private final int width;
        private final int height;
        private final boolean animatedGif;
        private final boolean backgroundRemoved;
        private final boolean outlineApplied;
        private final Bitmap previewBitmap;

        public ProcessedStickerResult(
                byte[] encodedBytes,
                String mimeType,
                String fileExtension,
                int width,
                int height,
                boolean animatedGif,
                boolean backgroundRemoved,
                boolean outlineApplied,
                Bitmap previewBitmap) {
            this.encodedBytes = encodedBytes;
            this.mimeType = mimeType;
            this.fileExtension = fileExtension;
            this.width = width;
            this.height = height;
            this.animatedGif = animatedGif;
            this.backgroundRemoved = backgroundRemoved;
            this.outlineApplied = outlineApplied;
            this.previewBitmap = previewBitmap;
        }

        public byte[] getEncodedBytes() {
            return encodedBytes;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public boolean isAnimatedGif() {
            return animatedGif;
        }

        public boolean isBackgroundRemoved() {
            return backgroundRemoved;
        }

        public boolean isOutlineApplied() {
            return outlineApplied;
        }

        public Bitmap getPreviewBitmap() {
            return previewBitmap;
        }
    }

    /**
     * Checks whether the given byte array starts with a valid GIF87a or GIF89a header.
     */
    public static boolean isGifBytes(byte[] data) {
        if (data == null || data.length < 13) {
            return false;
        }
        return data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a';
    }

    /**
     * Extracts [width, height] from a GIF Logical Screen Descriptor.
     */
    public static int[] extractGifDimensions(byte[] data) {
        if (!isGifBytes(data)) {
            return new int[] { 0, 0 };
        }
        int width = (data[6] & 0xFF) | ((data[7] & 0xFF) << 8);
        int height = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
        return new int[] { Math.max(1, width), Math.max(1, height) };
    }

    /**
     * Processes raw media bytes (either animated GIF or static image PNG/JPEG/WEBP/BMP) into a sticker.
     */
    public static ProcessedStickerResult processMediaBytes(
            byte[] rawBytes,
            StickerOptions options) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new IllegalArgumentException("Input media bytes must not be empty.");
        }
        StickerOptions effectiveOptions = options != null ? options : StickerOptions.defaults();
        if (isGifBytes(rawBytes)) {
            return processGifBytes(
                    rawBytes,
                    effectiveOptions.shouldRemoveBackground(),
                    effectiveOptions.getTolerance(),
                    effectiveOptions.getManualTargetColor());
        }
        return processStaticImageBytes(rawBytes, effectiveOptions);
    }

    /**
     * Processes an animated GIF byte array, optionally applying GIF palette/GCE background removal.
     */
    public static ProcessedStickerResult processGifBytes(
            byte[] gifBytes,
            boolean removeBackground,
            int tolerance,
            Integer manualTargetColor) {
        if (!isGifBytes(gifBytes)) {
            throw new IllegalArgumentException("Input is not a valid GIF file.");
        }
        int[] dims = extractGifDimensions(gifBytes);
        byte[] outputBytes = gifBytes;
        boolean bgRemoved = false;
        if (removeBackground && tolerance > 0) {
            byte[] processed = removeGifSolidBackground(gifBytes, tolerance, manualTargetColor);
            if (processed != null && processed.length > 0 && processed != gifBytes) {
                outputBytes = processed;
                bgRemoved = true;
            }
        }
        Bitmap previewFrame = null;
        try {
            previewFrame = ensureArgb8888(decodeBoundedBitmap(outputBytes, DEFAULT_MAX_STICKER_DIMENSION_PX));
            if (previewFrame != null && bgRemoved) {
                // Also apply pixel-level cutout to the static preview bitmap if BitmapFactory
                // decoded the first frame without GCE transparency on older Android versions.
                int w = previewFrame.getWidth();
                int h = previewFrame.getHeight();
                if (w > 0 && h > 0) {
                    int[] px = new int[w * h];
                    previewFrame.getPixels(px, 0, w, 0, 0, w, h);
                    int[] cleaned = removeBackgroundFromPixels(
                            px, w, h, tolerance, true, manualTargetColor);
                    previewFrame = Bitmap.createBitmap(cleaned, w, h, Bitmap.Config.ARGB_8888);
                }
            }
        } catch (Throwable ignored) {
        }
        return new ProcessedStickerResult(
                outputBytes,
                "image/gif",
                ".gif",
                dims[0],
                dims[1],
                true,
                bgRemoved,
                false,
                previewFrame);
    }

    /**
     * Pure-Java GIF87a/89a background color transparency processor.
     * Identifies the background color in the Global Color Table (either from manualTargetColor
     * or the GIF's Logical Screen Descriptor background color / first-frame border) and sets/updates
     * Graphic Control Extension (GCE) transparency indices so matching background colors become
     * transparent across frames while preserving full animation.
     */
    public static byte[] removeGifSolidBackground(
            byte[] gifBytes,
            int tolerance,
            Integer manualTargetColor) {
        if (!isGifBytes(gifBytes) || gifBytes.length < 20) {
            return gifBytes;
        }
        try {
            int packed = gifBytes[10] & 0xFF;
            boolean hasGct = (packed & 0x80) != 0;
            if (!hasGct) {
                return gifBytes;
            }
            int gctSize = 1 << ((packed & 0x07) + 1);
            int gctByteLength = gctSize * 3;
            int gctStart = 13;
            if (gifBytes.length < gctStart + gctByteLength) {
                return gifBytes;
            }

            int bgColorIndex = gifBytes[11] & 0xFF;
            if (bgColorIndex >= gctSize) {
                bgColorIndex = 0;
            }

            int targetColor;
            if (manualTargetColor != null) {
                targetColor = manualTargetColor.intValue();
            } else {
                int offset = gctStart + bgColorIndex * 3;
                int r = gifBytes[offset] & 0xFF;
                int g = gifBytes[offset + 1] & 0xFF;
                int b = gifBytes[offset + 2] & 0xFF;
                targetColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }

            // Find best matching palette index in the Global Color Table
            int bestIndex = -1;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < gctSize; i++) {
                int offset = gctStart + i * 3;
                int r = gifBytes[offset] & 0xFF;
                int g = gifBytes[offset + 1] & 0xFF;
                int b = gifBytes[offset + 2] & 0xFF;
                int paletteColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
                double dist = perceptualColorDistance(targetColor, paletteColor);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0 || bestDist > Math.max(tolerance, 15)) {
                return gifBytes;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(gifBytes.length + 64);
            // Write GIF89a header (required for Graphic Control Extension transparency)
            out.write('G');
            out.write('I');
            out.write('F');
            out.write('8');
            out.write('9');
            out.write('a');
            // Write Logical Screen Descriptor + Global Color Table
            out.write(gifBytes, 6, 7 + gctByteLength);

            int pos = gctStart + gctByteLength;
            boolean sawGceForNextFrame = false;

            while (pos < gifBytes.length) {
                int blockId = gifBytes[pos] & 0xFF;
                if (blockId == 0x3B) {
                    // GIF Trailer
                    out.write(0x3B);
                    break;
                } else if (blockId == 0x21) {
                    // Extension block
                    if (pos + 2 >= gifBytes.length) {
                        out.write(gifBytes, pos, gifBytes.length - pos);
                        break;
                    }
                    int extLabel = gifBytes[pos + 1] & 0xFF;
                    if (extLabel == 0xF9 && pos + 7 < gifBytes.length && (gifBytes[pos + 2] & 0xFF) == 4) {
                        // Graphic Control Extension (8 bytes total)
                        int gcePacked = gifBytes[pos + 3] & 0xFF;
                        int delayLo = gifBytes[pos + 4] & 0xFF;
                        int delayHi = gifBytes[pos + 5] & 0xFF;
                        int existingTransIdx = gifBytes[pos + 6] & 0xFF;
                        boolean hadTrans = (gcePacked & 0x01) != 0;

                        // Enable transparency bit; if disposal is 0/1, set to 2 (restore to background)
                        int disposal = (gcePacked >> 2) & 0x07;
                        if (disposal == 0 || disposal == 1) {
                            disposal = 2;
                        }
                        int newGcePacked = (gcePacked & 0xE0) | (disposal << 2) | 0x01;
                        int newTransIdx = hadTrans ? existingTransIdx : bestIndex;

                        out.write(0x21);
                        out.write(0xF9);
                        out.write(0x04);
                        out.write(newGcePacked);
                        out.write(delayLo);
                        out.write(delayHi);
                        out.write(newTransIdx);
                        out.write(0x00);
                        pos += 8;
                        sawGceForNextFrame = true;
                    } else {
                        // Other extension (Application / Comment / Plain Text)
                        out.write(0x21);
                        out.write(extLabel);
                        pos += 2;
                        pos = copySubBlocks(gifBytes, pos, out);
                    }
                } else if (blockId == 0x2C) {
                    // Image Descriptor
                    if (pos + 10 > gifBytes.length) {
                        out.write(gifBytes, pos, gifBytes.length - pos);
                        break;
                    }
                    if (!sawGceForNextFrame) {
                        // Inject a Graphic Control Extension enabling transparency for this frame
                        out.write(0x21);
                        out.write(0xF9);
                        out.write(0x04);
                        out.write(0x09); // disposal = 2 (restore to bg), transparent flag = 1
                        out.write(0x00);
                        out.write(0x00);
                        out.write(bestIndex);
                        out.write(0x00);
                    }
                    sawGceForNextFrame = false;

                    // Copy 10-byte Image Descriptor
                    out.write(gifBytes, pos, 10);
                    int imgPacked = gifBytes[pos + 9] & 0xFF;
                    pos += 10;

                    // Local Color Table if present
                    if ((imgPacked & 0x80) != 0) {
                        int lctSize = 1 << ((imgPacked & 0x07) + 1);
                        int lctBytes = lctSize * 3;
                        if (pos + lctBytes > gifBytes.length) {
                            out.write(gifBytes, pos, gifBytes.length - pos);
                            break;
                        }
                        out.write(gifBytes, pos, lctBytes);
                        pos += lctBytes;
                    }

                    // LZW Minimum Code Size byte + image data sub-blocks
                    if (pos < gifBytes.length) {
                        out.write(gifBytes[pos] & 0xFF);
                        pos++;
                        pos = copySubBlocks(gifBytes, pos, out);
                    }
                } else {
                    // Unknown byte, copy remaining and exit loop safely
                    out.write(gifBytes, pos, gifBytes.length - pos);
                    break;
                }
            }
            return out.toByteArray();
        } catch (Throwable ignored) {
            return gifBytes;
        }
    }

    private static int copySubBlocks(byte[] src, int pos, ByteArrayOutputStream out) {
        while (pos < src.length) {
            int blockSize = src[pos] & 0xFF;
            out.write(blockSize);
            pos++;
            if (blockSize == 0) {
                break;
            }
            int available = Math.min(blockSize, src.length - pos);
            out.write(src, pos, available);
            pos += available;
        }
        return pos;
    }

    /**
     * Processes a static image byte array (PNG, JPEG, WEBP, BMP) into a transparent PNG sticker.
     */
    public static ProcessedStickerResult processStaticImageBytes(
            byte[] rawBytes,
            StickerOptions options) {
        StickerOptions effectiveOptions = options != null ? options : StickerOptions.defaults();
        Bitmap decoded = decodeBoundedBitmap(rawBytes, effectiveOptions.getMaxDimensionPx() * 2);
        if (decoded == null) {
            throw new IllegalArgumentException("Unable to decode image file.");
        }
        return processStaticBitmap(decoded, effectiveOptions);
    }

    /**
     * Processes an Android {@link Bitmap} into a transparent PNG sticker according to {@link StickerOptions}.
     */
    public static ProcessedStickerResult processStaticBitmap(
            Bitmap sourceBitmap,
            StickerOptions options) {
        if (sourceBitmap == null) {
            throw new IllegalArgumentException("Source bitmap must not be null.");
        }
        Bitmap readableBitmap = ensureArgb8888(sourceBitmap);
        StickerOptions effectiveOptions = options != null ? options : StickerOptions.defaults();
        int width = readableBitmap.getWidth();
        int height = readableBitmap.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Source bitmap has invalid dimensions.");
        }
        int[] pixels = new int[width * height];
        readableBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        PixelImage processed = processPixelImage(new PixelImage(pixels, width, height), effectiveOptions);
        Bitmap outputBitmap = Bitmap.createBitmap(
                processed.getPixels(),
                processed.getWidth(),
                processed.getHeight(),
                Bitmap.Config.ARGB_8888);

        ByteArrayOutputStream pngOut = new ByteArrayOutputStream(32768);
        outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOut);
        byte[] encodedBytes = pngOut.toByteArray();

        return new ProcessedStickerResult(
                encodedBytes,
                "image/png",
                ".png",
                processed.getWidth(),
                processed.getHeight(),
                false,
                effectiveOptions.shouldRemoveBackground(),
                effectiveOptions.shouldAddOutline(),
                outputBitmap);
    }

    /**
     * Runs the full pixel-level sticker pipeline (background removal -> auto-trim -> resize -> outline).
     * Pure Java implementation that can be unit-tested without Android framework native bitmaps.
     */
    public static PixelImage processPixelImage(PixelImage input, StickerOptions options) {
        if (input == null) {
            throw new IllegalArgumentException("Input PixelImage must not be null.");
        }
        StickerOptions effectiveOptions = options != null ? options : StickerOptions.defaults();
        int width = input.getWidth();
        int height = input.getHeight();
        int[] currentPixels = Arrays.copyOf(input.getPixels(), width * height);

        if (effectiveOptions.shouldRemoveBackground()) {
            currentPixels = removeBackgroundFromPixels(
                    currentPixels,
                    width,
                    height,
                    effectiveOptions.getTolerance(),
                    effectiveOptions.isContiguousFloodFill(),
                    effectiveOptions.getManualTargetColor());
        }

        PixelImage current = new PixelImage(currentPixels, width, height);

        if (effectiveOptions.shouldAutoTrim()) {
            int pad = effectiveOptions.shouldAddOutline()
                    ? Math.max(DEFAULT_TRIM_PADDING_PX, effectiveOptions.getOutlineRadiusPx() + 4)
                    : 6;
            current = trimTransparentBounds(current.getPixels(), current.getWidth(), current.getHeight(), pad);
        }

        // Resize to fit maxDimensionPx before applying outline so outline thickness is consistent
        int maxDim = effectiveOptions.getMaxDimensionPx();
        if (current.getWidth() > maxDim || current.getHeight() > maxDim) {
            current = resizeToFit(current, maxDim);
        }

        if (effectiveOptions.shouldAddOutline()) {
            int[] outlined = applyStickerOutline(
                    current.getPixels(),
                    current.getWidth(),
                    current.getHeight(),
                    effectiveOptions.getOutlineColor(),
                    effectiveOptions.getOutlineRadiusPx());
            current = new PixelImage(outlined, current.getWidth(), current.getHeight());
        }

        return current;
    }

    /**
     * Removes background pixels from an ARGB_8888 pixel array.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Samples perimeter pixels along all 4 borders (or uses {@code manualTargetColor} if provided)
     *       and clusters them into dominant background colors.</li>
     *   <li>If {@code contiguousOnly} is true, performs an 8-connected flood-fill from border background
     *       seeds, stepping across pixels that match any background centroid within {@code tolerance}
     *       or smooth background gradients.</li>
     *   <li>If {@code contiguousOnly} is false, removes all pixels matching the background centroids
     *       within {@code tolerance} (including interior holes like inside a mug handle or letter 'O').</li>
     *   <li>Applies soft alpha feathering to foreground boundary pixels adjacent to removed background
     *       so cutout edges are smooth and anti-aliased.</li>
     * </ol>
     */
    public static int[] removeBackgroundFromPixels(
            int[] pixels,
            int width,
            int height,
            int tolerance,
            boolean contiguousOnly,
            Integer manualTargetColor) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            throw new IllegalArgumentException("Invalid pixel array or dimensions.");
        }
        int total = width * height;
        int[] result = Arrays.copyOf(pixels, total);
        if (tolerance <= 0) {
            return result;
        }

        int[] bgCentroids = (manualTargetColor != null)
                ? new int[] { manualTargetColor.intValue() }
                : detectBorderBackgroundCentroids(pixels, width, height);

        if (bgCentroids.length == 0) {
            return result;
        }

        double strictThreshold = Math.max(2.0, tolerance);
        double featherOuterThreshold = strictThreshold * 1.30 + 3.0;
        double gradientStepLimit = Math.max(6.0, strictThreshold * 0.38);

        boolean[] isBackground = new boolean[total];
        double[] minBgDistance = new double[total];

        for (int i = 0; i < total; i++) {
            int c = pixels[i];
            int alpha = (c >>> 24) & 0xFF;
            if (alpha < 12) {
                isBackground[i] = true;
                minBgDistance[i] = 0.0;
            } else {
                minBgDistance[i] = minDistanceToCentroids(c, bgCentroids);
            }
        }

        if (!contiguousOnly) {
            for (int i = 0; i < total; i++) {
                if (minBgDistance[i] <= strictThreshold) {
                    isBackground[i] = true;
                }
            }
        } else {
            int[] queue = new int[total];
            int head = 0;
            int tail = 0;

            // Seed from all 4 image edges
            for (int x = 0; x < width; x++) {
                int topIdx = x;
                if (!isBackground[topIdx] && minBgDistance[topIdx] <= strictThreshold) {
                    isBackground[topIdx] = true;
                    queue[tail++] = topIdx;
                }
                int bottomIdx = (height - 1) * width + x;
                if (!isBackground[bottomIdx] && minBgDistance[bottomIdx] <= strictThreshold) {
                    isBackground[bottomIdx] = true;
                    queue[tail++] = bottomIdx;
                }
            }
            for (int y = 1; y < height - 1; y++) {
                int leftIdx = y * width;
                if (!isBackground[leftIdx] && minBgDistance[leftIdx] <= strictThreshold) {
                    isBackground[leftIdx] = true;
                    queue[tail++] = leftIdx;
                }
                int rightIdx = y * width + (width - 1);
                if (!isBackground[rightIdx] && minBgDistance[rightIdx] <= strictThreshold) {
                    isBackground[rightIdx] = true;
                    queue[tail++] = rightIdx;
                }
            }

            // If manualTargetColor was tapped inside the image and didn't touch the border,
            // also seed any pixel that closely matches manualTargetColor.
            if (manualTargetColor != null && tail == 0) {
                for (int i = 0; i < total; i++) {
                    if (!isBackground[i] && minBgDistance[i] <= strictThreshold * 0.5) {
                        isBackground[i] = true;
                        queue[tail++] = i;
                    }
                }
            }

            // 8-connected BFS flood fill
            while (head < tail) {
                int curr = queue[head++];
                int cx = curr % width;
                int cy = curr / width;
                int currColor = pixels[curr];

                for (int dy = -1; dy <= 1; dy++) {
                    int ny = cy + dy;
                    if (ny < 0 || ny >= height) continue;
                    int rowOffset = ny * width;
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cx + dx;
                        if (nx < 0 || nx >= width) continue;
                        int next = rowOffset + nx;
                        if (isBackground[next]) continue;

                        double distToCentroid = minBgDistance[next];
                        if (distToCentroid <= strictThreshold) {
                            isBackground[next] = true;
                            queue[tail++] = next;
                        } else if (distToCentroid <= featherOuterThreshold) {
                            double stepDist = perceptualColorDistance(currColor, pixels[next]);
                            if (stepDist <= gradientStepLimit) {
                                isBackground[next] = true;
                                queue[tail++] = next;
                            }
                        }
                    }
                }
            }
        }

        // Apply transparency and soft edge feathering on foreground boundary pixels
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int idx = rowOffset + x;
                if (isBackground[idx]) {
                    result[idx] = 0x00000000;
                    continue;
                }

                // Check if this foreground pixel touches any removed background pixel
                int bgNeighbors = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= height) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        if (nx < 0 || nx >= width) continue;
                        if (isBackground[ny * width + nx]) {
                            bgNeighbors++;
                        }
                    }
                }

                if (bgNeighbors > 0) {
                    int c = result[idx];
                    int origAlpha = (c >>> 24) & 0xFF;
                    double dist = minBgDistance[idx];
                    if (dist < featherOuterThreshold) {
                        double ratio = (dist - strictThreshold * 0.75)
                                / Math.max(1.0, featherOuterThreshold - strictThreshold * 0.75);
                        ratio = Math.max(0.12, Math.min(1.0, ratio));
                        // Slightly soften isolated 1px spurs sticking into the background
                        if (bgNeighbors >= 5) {
                            ratio *= 0.55;
                        } else if (bgNeighbors >= 3) {
                            ratio *= 0.80;
                        }
                        int newAlpha = (int) Math.round(origAlpha * ratio);
                        result[idx] = (newAlpha << 24) | (c & 0x00FFFFFF);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Samples the 4 borders of an image and clusters the perimeter colors to identify up to 4
     * dominant background colors.
     */
    public static int[] detectBorderBackgroundCentroids(int[] pixels, int width, int height) {
        List<Integer> samples = new ArrayList<Integer>();
        int stepX = Math.max(1, width / 48);
        int stepY = Math.max(1, height / 48);

        for (int x = 0; x < width; x += stepX) {
            addOpaqueSample(samples, pixels[x]);
            addOpaqueSample(samples, pixels[(height - 1) * width + x]);
        }
        for (int y = 0; y < height; y += stepY) {
            addOpaqueSample(samples, pixels[y * width]);
            addOpaqueSample(samples, pixels[y * width + (width - 1)]);
        }

        if (samples.isEmpty()) {
            return new int[0];
        }

        // Greedy clustering with perceptual distance radius 16
        final double clusterRadius = 16.0;
        List<long[]> clusters = new ArrayList<long[]>(); // [count, sumR, sumG, sumB, repColor]
        for (int i = 0; i < samples.size(); i++) {
            int color = samples.get(i).intValue();
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;

            long[] matched = null;
            for (int c = 0; c < clusters.size(); c++) {
                long[] cluster = clusters.get(c);
                int repColor = (int) cluster[4];
                if (perceptualColorDistance(color, repColor) <= clusterRadius) {
                    matched = cluster;
                    break;
                }
            }
            if (matched != null) {
                matched[0]++;
                matched[1] += r;
                matched[2] += g;
                matched[3] += b;
                int avgR = (int) (matched[1] / matched[0]);
                int avgG = (int) (matched[2] / matched[0]);
                int avgB = (int) (matched[3] / matched[0]);
                matched[4] = (0xFF << 24) | (avgR << 16) | (avgG << 8) | avgB;
            } else {
                clusters.add(new long[] { 1L, r, g, b, (0xFF << 24) | (color & 0x00FFFFFF) });
            }
        }

        // Sort clusters by sample count descending
        clusters.sort((a, b) -> Long.compare(b[0], a[0]));

        int totalSamples = samples.size();
        int maxClusters = Math.min(4, clusters.size());
        List<Integer> chosen = new ArrayList<Integer>(maxClusters);
        for (int i = 0; i < maxClusters; i++) {
            long[] cluster = clusters.get(i);
            // Include the primary cluster always, and secondary clusters if they represent >= 10% of border
            if (i == 0 || cluster[0] >= Math.max(2L, totalSamples / 10)) {
                chosen.add(Integer.valueOf((int) cluster[4]));
            }
        }

        int[] result = new int[chosen.size()];
        for (int i = 0; i < chosen.size(); i++) {
            result[i] = chosen.get(i).intValue();
        }
        return result;
    }

    private static void addOpaqueSample(List<Integer> samples, int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha >= 32) {
            samples.add(Integer.valueOf(color));
        }
    }

    private static double minDistanceToCentroids(int color, int[] centroids) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < centroids.length; i++) {
            double d = perceptualColorDistance(color, centroids[i]);
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    /**
     * Perceptual weighted Euclidean RGB distance normalized to the [0.0, 100.0] range.
     * Uses human-vision redmean weighting: (2 + rMean/256)*dR^2 + 4*dG^2 + (2 + (255-rMean)/256)*dB^2.
     */
    public static double perceptualColorDistance(int c1, int c2) {
        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int r2 = (c2 >>> 16) & 0xFF;
        int g2 = (c2 >>> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int rMean = (r1 + r2) >> 1;
        int dR = r1 - r2;
        int dG = g1 - g2;
        int dB = b1 - b2;

        double weightedSq = (((512 + rMean) * dR * dR) >> 8)
                + 4.0 * dG * dG
                + (((767 - rMean) * dB * dB) >> 8);
        // Max possible weightedSq for black vs white is ~ 3*255^2 + 4*255^2 + 2*255^2 = 9 * 65025 = 585225
        // sqrt(585225) = 765.0
        return (Math.sqrt(weightedSq) / 765.0) * 100.0;
    }

    /**
     * Adds a smooth die-cut sticker outline around non-transparent foreground pixels.
     */
    public static int[] applyStickerOutline(
            int[] pixels,
            int width,
            int height,
            int outlineColor,
            int outlineRadiusPx) {
        if (pixels == null || width <= 0 || height <= 0) {
            return pixels;
        }
        int radius = Math.max(1, Math.min(24, outlineRadiusPx));
        int total = width * height;

        // Check if image actually has any transparent background; if 100% opaque everywhere, skip outline
        boolean hasTransparentPixels = false;
        for (int i = 0; i < total; i++) {
            if (((pixels[i] >>> 24) & 0xFF) < 245) {
                hasTransparentPixels = true;
                break;
            }
        }
        if (!hasTransparentPixels) {
            return Arrays.copyOf(pixels, total);
        }

        int[] result = new int[total];
        int outR = (outlineColor >>> 16) & 0xFF;
        int outG = (outlineColor >>> 8) & 0xFF;
        int outB = outlineColor & 0xFF;
        float radiusF = radius;
        float outerFeatherRadius = radius + 1.25f;
        int searchRadius = (int) Math.ceil(outerFeatherRadius);

        for (int y = 0; y < height; y++) {
            int yOffset = y * width;
            for (int x = 0; x < width; x++) {
                int idx = yOffset + x;
                int fgColor = pixels[idx];
                int fgAlpha = (fgColor >>> 24) & 0xFF;
                if (fgAlpha == 255) {
                    result[idx] = fgColor;
                    continue;
                }

                // Find minimum distance to a solid foreground pixel within searchRadius
                float minDist = outerFeatherRadius + 1.0f;
                int yMin = Math.max(0, y - searchRadius);
                int yMax = Math.min(height - 1, y + searchRadius);
                int xMin = Math.max(0, x - searchRadius);
                int xMax = Math.min(width - 1, x + searchRadius);

                for (int ny = yMin; ny <= yMax; ny++) {
                    int dy = ny - y;
                    int dySq = dy * dy;
                    int nRow = ny * width;
                    for (int nx = xMin; nx <= xMax; nx++) {
                        int nAlpha = (pixels[nRow + nx] >>> 24) & 0xFF;
                        if (nAlpha < 48) continue;
                        int dx = nx - x;
                        float dist = (float) Math.sqrt(dx * dx + dySq);
                        // Weight slightly by neighbor alpha
                        if (nAlpha < 200) {
                            dist += (255 - nAlpha) / 255.0f;
                        }
                        if (dist < minDist) {
                            minDist = dist;
                            if (minDist <= 1.0f) break;
                        }
                    }
                    if (minDist <= 1.0f) break;
                }

                if (minDist > outerFeatherRadius) {
                    result[idx] = fgColor;
                    continue;
                }

                float outlineAlphaFactor;
                if (minDist <= radiusF) {
                    outlineAlphaFactor = 1.0f;
                } else {
                    outlineAlphaFactor = 1.0f - ((minDist - radiusF) / (outerFeatherRadius - radiusF));
                    outlineAlphaFactor = Math.max(0.0f, Math.min(1.0f, outlineAlphaFactor));
                }
                int outlineAlpha = Math.round(255 * outlineAlphaFactor);

                if (fgAlpha == 0) {
                    result[idx] = (outlineAlpha << 24) | (outR << 16) | (outG << 8) | outB;
                } else {
                    // Alpha-over composite foreground pixel over outline pixel
                    float fgA = fgAlpha / 255.0f;
                    float bgA = outlineAlpha / 255.0f;
                    float outA = fgA + bgA * (1.0f - fgA);
                    if (outA <= 0.001f) {
                        result[idx] = 0;
                    } else {
                        int fgR = (fgColor >>> 16) & 0xFF;
                        int fgG = (fgColor >>> 8) & 0xFF;
                        int fgB = fgColor & 0xFF;
                        int compR = Math.round((fgR * fgA + outR * bgA * (1.0f - fgA)) / outA);
                        int compG = Math.round((fgG * fgA + outG * bgA * (1.0f - fgA)) / outA);
                        int compB = Math.round((fgB * fgA + outB * bgA * (1.0f - fgA)) / outA);
                        int compA = Math.min(255, Math.round(outA * 255.0f));
                        result[idx] = (compA << 24) | (compR << 16) | (compG << 8) | compB;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Crops away transparent margins around the non-transparent foreground and adds uniform padding.
     */
    public static PixelImage trimTransparentBounds(
            int[] pixels,
            int width,
            int height,
            int paddingPx) {
        if (pixels == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid pixel array for trimming.");
        }
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int alpha = (pixels[rowOffset + x] >>> 24) & 0xFF;
                if (alpha > 10) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // If everything was transparent, return original image untouched
        if (maxX < minX || maxY < minY) {
            return new PixelImage(Arrays.copyOf(pixels, width * height), width, height);
        }

        int pad = Math.max(0, paddingPx);
        int contentWidth = maxX - minX + 1;
        int contentHeight = maxY - minY + 1;
        int newWidth = contentWidth + pad * 2;
        int newHeight = contentHeight + pad * 2;
        int[] cropped = new int[newWidth * newHeight];

        for (int y = 0; y < contentHeight; y++) {
            int srcOffset = (minY + y) * width + minX;
            int dstOffset = (pad + y) * newWidth + pad;
            System.arraycopy(pixels, srcOffset, cropped, dstOffset, contentWidth);
        }

        return new PixelImage(cropped, newWidth, newHeight);
    }

    /**
     * Resizes a {@link PixelImage} using bilinear interpolation so its largest dimension is at most {@code maxDim}.
     */
    public static PixelImage resizeToFit(PixelImage image, int maxDim) {
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        if (srcW <= maxDim && srcH <= maxDim) {
            return image;
        }
        float scale = Math.min((float) maxDim / srcW, (float) maxDim / srcH);
        int dstW = Math.max(1, Math.round(srcW * scale));
        int dstH = Math.max(1, Math.round(srcH * scale));

        int[] src = image.getPixels();
        int[] dst = new int[dstW * dstH];

        for (int y = 0; y < dstH; y++) {
            float srcY = (y + 0.5f) / scale - 0.5f;
            int y0 = Math.max(0, Math.min(srcH - 1, (int) Math.floor(srcY)));
            int y1 = Math.max(0, Math.min(srcH - 1, y0 + 1));
            float wy = Math.max(0.0f, srcY - y0);

            for (int x = 0; x < dstW; x++) {
                float srcX = (x + 0.5f) / scale - 0.5f;
                int x0 = Math.max(0, Math.min(srcW - 1, (int) Math.floor(srcX)));
                int x1 = Math.max(0, Math.min(srcW - 1, x0 + 1));
                float wx = Math.max(0.0f, srcX - x0);

                int c00 = src[y0 * srcW + x0];
                int c10 = src[y0 * srcW + x1];
                int c01 = src[y1 * srcW + x0];
                int c11 = src[y1 * srcW + x1];

                dst[y * dstW + x] = bilerpPremultiplied(c00, c10, c01, c11, wx, wy);
            }
        }
        return new PixelImage(dst, dstW, dstH);
    }

    private static int bilerpPremultiplied(int c00, int c10, int c01, int c11, float wx, float wy) {
        float w00 = (1.0f - wx) * (1.0f - wy);
        float w10 = wx * (1.0f - wy);
        float w01 = (1.0f - wx) * wy;
        float w11 = wx * wy;

        float a00 = ((c00 >>> 24) & 0xFF) / 255.0f;
        float a10 = ((c10 >>> 24) & 0xFF) / 255.0f;
        float a01 = ((c01 >>> 24) & 0xFF) / 255.0f;
        float a11 = ((c11 >>> 24) & 0xFF) / 255.0f;

        float outA = a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11;
        if (outA <= 0.001f) {
            return 0x00000000;
        }

        float r = (((c00 >>> 16) & 0xFF) * a00 * w00
                + ((c10 >>> 16) & 0xFF) * a10 * w10
                + ((c01 >>> 16) & 0xFF) * a01 * w01
                + ((c11 >>> 16) & 0xFF) * a11 * w11) / outA;
        float g = (((c00 >>> 8) & 0xFF) * a00 * w00
                + ((c10 >>> 8) & 0xFF) * a10 * w10
                + ((c01 >>> 8) & 0xFF) * a01 * w01
                + ((c11 >>> 8) & 0xFF) * a11 * w11) / outA;
        float b = ((c00 & 0xFF) * a00 * w00
                + (c10 & 0xFF) * a10 * w10
                + (c01 & 0xFF) * a01 * w01
                + (c11 & 0xFF) * a11 * w11) / outA;

        int ia = Math.min(255, Math.max(0, Math.round(outA * 255.0f)));
        int ir = Math.min(255, Math.max(0, Math.round(r)));
        int ig = Math.min(255, Math.max(0, Math.round(g)));
        int ib = Math.min(255, Math.max(0, Math.round(b)));
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    /**
     * Decodes a byte array into an ARGB_8888 Bitmap bounded to {@code maxDecodeDimensionPx}.
     */
    public static Bitmap decodeBoundedBitmap(byte[] rawBytes, int maxDecodeDimensionPx) {
        if (rawBytes == null || rawBytes.length == 0) {
            return null;
        }
        try {
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length, boundsOptions);

            int sampleSize = 1;
            int w = boundsOptions.outWidth;
            int h = boundsOptions.outHeight;
            int targetMax = Math.max(128, maxDecodeDimensionPx);
            while (w / sampleSize > targetMax || h / sampleSize > targetMax) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decodeOptions.inMutable = true;
            Bitmap decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length, decodeOptions);
            return ensureArgb8888(decoded);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Ensures the given {@link Bitmap} uses {@link Bitmap.Config#ARGB_8888} so pixel reads via
     * {@link Bitmap#getPixels} and {@link Bitmap#getPixel} never fail on hardware or F16 bitmaps.
     */
    public static Bitmap ensureArgb8888(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        try {
            if (bitmap.getConfig() == Bitmap.Config.ARGB_8888) {
                return bitmap;
            }
            Bitmap converted = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            return converted != null ? converted : bitmap;
        } catch (Throwable ignored) {
            return bitmap;
        }
    }
}
