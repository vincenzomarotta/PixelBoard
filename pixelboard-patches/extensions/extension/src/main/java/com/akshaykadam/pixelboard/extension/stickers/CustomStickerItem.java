/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.stickers;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable metadata model representing a saved custom sticker (static PNG/WEBP cutout or animated GIF).
 */
public final class CustomStickerItem {
    private final String id;
    private final String name;
    private final String fileName;
    private final String mimeType;
    private final int width;
    private final int height;
    private final long fileSizeBytes;
    private final boolean animatedGif;
    private final boolean backgroundRemoved;
    private final boolean outlineApplied;
    private final boolean favorite;
    private final long createdAtMillis;

    public CustomStickerItem(
            String id,
            String name,
            String fileName,
            String mimeType,
            int width,
            int height,
            long fileSizeBytes,
            boolean animatedGif,
            boolean backgroundRemoved,
            boolean outlineApplied,
            boolean favorite,
            long createdAtMillis) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Sticker id must not be blank.");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sticker fileName must not be blank.");
        }
        this.id = id.trim();
        this.name = (name == null || name.trim().isEmpty()) ? "Custom Sticker" : name.trim();
        this.fileName = fileName.trim();
        this.mimeType = (mimeType == null || mimeType.trim().isEmpty())
                ? (animatedGif ? "image/gif" : "image/png")
                : mimeType.trim();
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.fileSizeBytes = Math.max(0L, fileSizeBytes);
        this.animatedGif = animatedGif;
        this.backgroundRemoved = backgroundRemoved;
        this.outlineApplied = outlineApplied;
        this.favorite = favorite;
        this.createdAtMillis = createdAtMillis;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
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

    public boolean isFavorite() {
        return favorite;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public CustomStickerItem withFavorite(boolean newFavorite) {
        return new CustomStickerItem(
                id,
                name,
                fileName,
                mimeType,
                width,
                height,
                fileSizeBytes,
                animatedGif,
                backgroundRemoved,
                outlineApplied,
                newFavorite,
                createdAtMillis);
    }

    public CustomStickerItem withName(String newName) {
        return new CustomStickerItem(
                id,
                newName,
                fileName,
                mimeType,
                width,
                height,
                fileSizeBytes,
                animatedGif,
                backgroundRemoved,
                outlineApplied,
                favorite,
                createdAtMillis);
    }

    public String getFormatBadge() {
        if (animatedGif) {
            return backgroundRemoved ? "GIF • BG Removed" : "GIF";
        }
        if (backgroundRemoved && outlineApplied) {
            return "PNG • Cutout + Border";
        }
        if (backgroundRemoved) {
            return "PNG • Cutout";
        }
        return "PNG";
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        if (favorite) {
            sb.append("★ Favorite • ");
        }
        sb.append(getFormatBadge());
        sb.append(" • ").append(width).append("×").append(height);
        if (fileSizeBytes > 0L) {
            sb.append(" • ").append(formatFileSize(fileSizeBytes));
        }
        return sb.toString();
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.US, "%.2f MB", mb);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomStickerItem)) return false;
        CustomStickerItem that = (CustomStickerItem) o;
        return width == that.width
                && height == that.height
                && fileSizeBytes == that.fileSizeBytes
                && animatedGif == that.animatedGif
                && backgroundRemoved == that.backgroundRemoved
                && outlineApplied == that.outlineApplied
                && favorite == that.favorite
                && createdAtMillis == that.createdAtMillis
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(fileName, that.fileName)
                && Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                name,
                fileName,
                mimeType,
                width,
                height,
                fileSizeBytes,
                animatedGif,
                backgroundRemoved,
                outlineApplied,
                favorite,
                createdAtMillis);
    }
}
