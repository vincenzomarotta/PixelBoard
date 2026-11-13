package com.akshaykadam.pixelboard.extension.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import com.akshaykadam.pixelboard.extension.stickers.GboardCustomStickerRepository;
import java.io.File;
import java.io.FileNotFoundException;

public final class GboardPatchesSettingsProvider extends ContentProvider {
    private static final String TAG = "PixelBoard";
    public static final String AUTHORITY_SUFFIX = ".gboard_patches";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        return new Bundle();
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        String stickerFileName = extractStickerFileName(uri);
        if (stickerFileName != null) {
            Context context = getContext();
            if (context == null) {
                return null;
            }
            File stickerFile = GboardCustomStickerRepository.getStickerFile(context, stickerFileName);
            if (stickerFile == null || !stickerFile.isFile()) {
                return null;
            }
            String[] cols = (projection != null && projection.length > 0)
                    ? projection
                    : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
            MatrixCursor cursor = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                String col = cols[i];
                if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                    row[i] = stickerFile.getName();
                } else if (OpenableColumns.SIZE.equals(col)) {
                    row[i] = Long.valueOf(stickerFile.length());
                } else {
                    row[i] = null;
                }
            }
            cursor.addRow(row);
            return cursor;
        }
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        String stickerFileName = extractStickerFileName(uri);
        if (stickerFileName != null) {
            if (context == null) {
                throw new FileNotFoundException("Context unavailable for sticker URI");
            }
            File stickerFile = GboardCustomStickerRepository.getStickerFile(context, stickerFileName);
            if (stickerFile != null && stickerFile.isFile()) {
                return ParcelFileDescriptor.open(stickerFile, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            throw new FileNotFoundException("Sticker file not found: " + stickerFileName);
        }
        if (context != null) {
            File apk = new File(context.getApplicationInfo().sourceDir);
            if (apk.exists()) {
                return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        }
        throw new FileNotFoundException("APK file not found");
    }

    @Override
    public String getType(Uri uri) {
        String stickerFileName = extractStickerFileName(uri);
        if (stickerFileName != null) {
            return GboardCustomStickerRepository.resolveMimeTypeForFileName(stickerFileName);
        }
        return "application/vnd.android.package-archive";
    }

    static String extractStickerFileName(Uri uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith(GboardCustomStickerRepository.URI_PATH_PREFIX)) {
            return null;
        }
        String fileName = path.substring(GboardCustomStickerRepository.URI_PATH_PREFIX.length());
        return GboardCustomStickerRepository.isSafeFileName(fileName) ? fileName : null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
