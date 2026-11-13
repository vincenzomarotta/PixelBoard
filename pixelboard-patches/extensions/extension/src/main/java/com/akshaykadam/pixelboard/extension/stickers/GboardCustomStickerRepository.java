/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.stickers;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Thread-safe repository managing custom stickers and animated GIF stickers on device storage.
 */
public final class GboardCustomStickerRepository {
    private static final String TAG = "PixelBoardStickers";
    public static final String STICKERS_DIR_NAME = "custom_stickers";
    public static final String INDEX_FILE_NAME = "stickers_index_v1.dat";
    public static final String URI_PATH_PREFIX = "/stickers/";

    private static final Object LOCK = new Object();
    private static volatile File testingStorageDir;

    private GboardCustomStickerRepository() {
    }

    public static void setStorageDirectoryForTesting(File directory) {
        synchronized (LOCK) {
            testingStorageDir = directory;
        }
    }

    public static File resolveStickersDirectory(Context context) {
        File override = testingStorageDir;
        if (override != null) {
            if (!override.exists()) {
                override.mkdirs();
            }
            return override;
        }
        if (context == null) {
            throw new IllegalArgumentException("Context required to resolve stickers directory.");
        }
        Context appContext = context.getApplicationContext();
        Context targetContext = appContext != null ? appContext : context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (!targetContext.isDeviceProtectedStorage()) {
                    Context deContext = targetContext.createDeviceProtectedStorageContext();
                    if (deContext != null) {
                        targetContext = deContext;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        File baseFilesDir = targetContext.getFilesDir();
        File stickersDir = new File(baseFilesDir, STICKERS_DIR_NAME);
        if (!stickersDir.exists()) {
            stickersDir.mkdirs();
        }
        return stickersDir;
    }

    public static boolean isSafeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return false;
        }
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-'
                    || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static File getStickerFile(Context context, String fileName) {
        if (!isSafeFileName(fileName)) {
            return null;
        }
        File dir = resolveStickersDirectory(context);
        return new File(dir, fileName);
    }

    public static String resolveMimeTypeForFileName(String fileName) {
        if (fileName == null) {
            return "image/png";
        }
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "image/png";
    }

    public static Uri buildStickerContentUri(Context context, CustomStickerItem item) {
        if (context == null || item == null) {
            return null;
        }
        String pkg = context.getPackageName();
        if (pkg == null || pkg.isEmpty()) {
            pkg = "com.akshaykadam.pixelboard";
        }
        return Uri.parse("content://" + pkg + ".gboard_patches" + URI_PATH_PREFIX + item.getFileName());
    }

    public static List<CustomStickerItem> listStickers(Context context) {
        synchronized (LOCK) {
            List<CustomStickerItem> items = readIndexLocked(resolveStickersDirectory(context));
            items.sort((a, b) -> {
                if (a.isFavorite() != b.isFavorite()) {
                    return a.isFavorite() ? -1 : 1;
                }
                return Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis());
            });
            return Collections.unmodifiableList(items);
        }
    }

    public static CustomStickerItem getStickerById(Context context, String stickerId) {
        if (stickerId == null || stickerId.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            List<CustomStickerItem> items = readIndexLocked(resolveStickersDirectory(context));
            for (int i = 0; i < items.size(); i++) {
                CustomStickerItem item = items.get(i);
                if (stickerId.equals(item.getId())) {
                    return item;
                }
            }
            return null;
        }
    }

    public static byte[] readStickerBytes(Context context, CustomStickerItem item) throws IOException {
        if (item == null) {
            throw new IllegalArgumentException("Sticker item must not be null.");
        }
        File file = getStickerFile(context, item.getFileName());
        if (file == null || !file.isFile()) {
            throw new IOException("Sticker file not found: " + item.getFileName());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(1024L, file.length()));
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
        return out.toByteArray();
    }

    public static CustomStickerItem createStickerFromImageBytes(
            Context context,
            byte[] rawBytes,
            String name,
            GboardStickerImageProcessor.StickerOptions options) throws IOException {
        GboardStickerImageProcessor.ProcessedStickerResult processed =
                GboardStickerImageProcessor.processStaticImageBytes(rawBytes, options);
        return saveProcessedSticker(context, name, processed);
    }

    public static CustomStickerItem createStickerFromGifBytes(
            Context context,
            byte[] gifBytes,
            String name,
            boolean removeBackground,
            int tolerance,
            Integer manualTargetColor) throws IOException {
        GboardStickerImageProcessor.ProcessedStickerResult processed =
                GboardStickerImageProcessor.processGifBytes(
                        gifBytes, removeBackground, tolerance, manualTargetColor);
        return saveProcessedSticker(context, name, processed);
    }

    public static CustomStickerItem saveProcessedSticker(
            Context context,
            String name,
            GboardStickerImageProcessor.ProcessedStickerResult processed) throws IOException {
        if (processed == null || processed.getEncodedBytes() == null
                || processed.getEncodedBytes().length == 0) {
            throw new IllegalArgumentException("Processed sticker result has no encoded bytes.");
        }
        synchronized (LOCK) {
            File dir = resolveStickersDirectory(context);
            long now = System.currentTimeMillis();
            String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String id = "stk_" + now + "_" + shortUuid;
            String ext = processed.getFileExtension();
            if (ext == null || !ext.startsWith(".")) {
                ext = processed.isAnimatedGif() ? ".gif" : ".png";
            }
            String fileName = id + ext;
            File outFile = new File(dir, fileName);

            byte[] bytes = processed.getEncodedBytes();
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes);
                fos.flush();
            }

            String cleanName = sanitizeDisplayName(name, processed.isAnimatedGif());
            CustomStickerItem newItem = new CustomStickerItem(
                    id,
                    cleanName,
                    fileName,
                    processed.getMimeType(),
                    processed.getWidth(),
                    processed.getHeight(),
                    bytes.length,
                    processed.isAnimatedGif(),
                    processed.isBackgroundRemoved(),
                    processed.isOutlineApplied(),
                    false,
                    now);

            List<CustomStickerItem> existing = readIndexLocked(dir);
            existing.add(0, newItem);
            writeIndexLocked(dir, existing);
            return newItem;
        }
    }

    public static CustomStickerItem toggleFavorite(Context context, String stickerId) {
        if (stickerId == null || stickerId.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            File dir = resolveStickersDirectory(context);
            List<CustomStickerItem> items = readIndexLocked(dir);
            CustomStickerItem updated = null;
            for (int i = 0; i < items.size(); i++) {
                CustomStickerItem item = items.get(i);
                if (stickerId.equals(item.getId())) {
                    updated = item.withFavorite(!item.isFavorite());
                    items.set(i, updated);
                    break;
                }
            }
            if (updated != null) {
                writeIndexLocked(dir, items);
            }
            return updated;
        }
    }

    public static CustomStickerItem renameSticker(Context context, String stickerId, String newName) {
        if (stickerId == null || stickerId.isEmpty() || newName == null || newName.trim().isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            File dir = resolveStickersDirectory(context);
            List<CustomStickerItem> items = readIndexLocked(dir);
            CustomStickerItem updated = null;
            for (int i = 0; i < items.size(); i++) {
                CustomStickerItem item = items.get(i);
                if (stickerId.equals(item.getId())) {
                    updated = item.withName(sanitizeDisplayName(newName, item.isAnimatedGif()));
                    items.set(i, updated);
                    break;
                }
            }
            if (updated != null) {
                writeIndexLocked(dir, items);
            }
            return updated;
        }
    }

    public static boolean deleteSticker(Context context, String stickerId) {
        if (stickerId == null || stickerId.isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            File dir = resolveStickersDirectory(context);
            List<CustomStickerItem> items = readIndexLocked(dir);
            CustomStickerItem removed = null;
            for (int i = 0; i < items.size(); i++) {
                if (stickerId.equals(items.get(i).getId())) {
                    removed = items.remove(i);
                    break;
                }
            }
            if (removed == null) {
                return false;
            }
            File file = new File(dir, removed.getFileName());
            if (file.exists()) {
                file.delete();
            }
            writeIndexLocked(dir, items);
            return true;
        }
    }

    public static int clearAllStickers(Context context) {
        synchronized (LOCK) {
            File dir = resolveStickersDirectory(context);
            List<CustomStickerItem> items = readIndexLocked(dir);
            int count = items.size();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        f.delete();
                    }
                }
            }
            return count;
        }
    }

    public static String sanitizeDisplayName(String candidate, boolean isGif) {
        if (candidate == null) {
            return isGif ? "GIF Sticker" : "Custom Sticker";
        }
        String trimmed = candidate.trim();
        // Strip file extension if candidate came from a file name
        int dot = trimmed.lastIndexOf('.');
        if (dot > 0 && dot >= trimmed.length() - 5) {
            trimmed = trimmed.substring(0, dot).trim();
        }
        trimmed = trimmed.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        if (trimmed.isEmpty()) {
            return isGif ? "GIF Sticker" : "Custom Sticker";
        }
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48).trim();
        }
        return trimmed;
    }

    private static List<CustomStickerItem> readIndexLocked(File dir) {
        List<CustomStickerItem> result = new ArrayList<CustomStickerItem>();
        File indexFile = new File(dir, INDEX_FILE_NAME);
        if (!indexFile.isFile()) {
            return result;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(indexFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 12) {
                    continue;
                }
                try {
                    String id = parts[0];
                    String name = unescapeField(parts[1]);
                    String fileName = parts[2];
                    String mimeType = parts[3];
                    int width = Integer.parseInt(parts[4]);
                    int height = Integer.parseInt(parts[5]);
                    long fileSize = Long.parseLong(parts[6]);
                    boolean animatedGif = Boolean.parseBoolean(parts[7]);
                    boolean bgRemoved = Boolean.parseBoolean(parts[8]);
                    boolean outlineApplied = Boolean.parseBoolean(parts[9]);
                    boolean favorite = Boolean.parseBoolean(parts[10]);
                    long createdAt = Long.parseLong(parts[11]);

                    if (!isSafeFileName(fileName)) {
                        continue;
                    }
                    File stickerFile = new File(dir, fileName);
                    if (!stickerFile.isFile()) {
                        continue;
                    }
                    result.add(new CustomStickerItem(
                            id,
                            name,
                            fileName,
                            mimeType,
                            width,
                            height,
                            stickerFile.length() > 0 ? stickerFile.length() : fileSize,
                            animatedGif,
                            bgRemoved,
                            outlineApplied,
                            favorite,
                            createdAt));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            try {
                Log.w(TAG, "Failed to read custom stickers index", t);
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static void writeIndexLocked(File dir, List<CustomStickerItem> items) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File indexFile = new File(dir, INDEX_FILE_NAME);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(indexFile, false), StandardCharsets.UTF_8))) {
            for (int i = 0; i < items.size(); i++) {
                CustomStickerItem item = items.get(i);
                writer.write(item.getId());
                writer.write('\t');
                writer.write(escapeField(item.getName()));
                writer.write('\t');
                writer.write(item.getFileName());
                writer.write('\t');
                writer.write(item.getMimeType());
                writer.write('\t');
                writer.write(Integer.toString(item.getWidth()));
                writer.write('\t');
                writer.write(Integer.toString(item.getHeight()));
                writer.write('\t');
                writer.write(Long.toString(item.getFileSizeBytes()));
                writer.write('\t');
                writer.write(Boolean.toString(item.isAnimatedGif()));
                writer.write('\t');
                writer.write(Boolean.toString(item.isBackgroundRemoved()));
                writer.write('\t');
                writer.write(Boolean.toString(item.isOutlineApplied()));
                writer.write('\t');
                writer.write(Boolean.toString(item.isFavorite()));
                writer.write('\t');
                writer.write(Long.toString(item.getCreatedAtMillis()));
                writer.newLine();
            }
            writer.flush();
        } catch (Throwable t) {
            try {
                Log.w(TAG, "Failed to write custom stickers index", t);
            } catch (Throwable ignored) {
            }
        }
    }

    private static String escapeField(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\t", " ").replace("\n", " ").replace("\r", " ");
    }

    private static String unescapeField(String value) {
        if (value == null) return "";
        return value.replace("\\\\", "\\");
    }
}
