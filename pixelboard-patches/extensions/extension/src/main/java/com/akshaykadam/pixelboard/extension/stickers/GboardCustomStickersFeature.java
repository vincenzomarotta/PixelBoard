/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.stickers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.akshaykadam.pixelboard.extension.R;
import com.akshaykadam.pixelboard.extension.settings.GboardPatchesSettingsContract;
import com.akshaykadam.pixelboard.extension.settings.GboardSettingsText;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Settings screen and interactive Sticker Studio for creating custom stickers from
 * static images (with automatic/interactive background removal & die-cut outline)
 * and animated GIFs (with solid background transparency).
 */
public final class GboardCustomStickersFeature implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "PixelBoardStickers";

    private static final String[] IMAGE_MIME_TYPES = new String[] {
            "image/*"
    };

    private static final String[] GIF_MIME_TYPES = new String[] {
            "image/gif"
    };

    private final Context appContext;
    private volatile String pendingAutoLaunchMode;
    private volatile GboardPatchesSettingsContract.FeatureHost latestHost;

    public GboardCustomStickersFeature(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    public void setPendingAutoLaunchMode(String mode) {
        this.pendingAutoLaunchMode = mode;
    }

    private GboardPatchesSettingsContract.FeatureHost resolveActiveHost(
            GboardPatchesSettingsContract.FeatureHost candidate) {
        if (latestHost != null) {
            return latestHost;
        }
        return candidate;
    }

    private Context resolveContext(GboardPatchesSettingsContract.FeatureHost host) {
        GboardPatchesSettingsContract.FeatureHost active = resolveActiveHost(host);
        if (active != null && active.getContext() != null) {
            return active.getContext();
        }
        return appContext;
    }

    @Override
    public String getEntryTitle() {
        return GboardSettingsText.get(appContext, R.string.gboard_patches_custom_stickers_title);
    }

    @Override
    public String getEntrySummary() {
        if (appContext != null) {
            List<CustomStickerItem> items = GboardCustomStickerRepository.listStickers(appContext);
            if (!items.isEmpty()) {
                return items.size() + (items.size() == 1 ? " custom sticker saved • " : " custom stickers saved • ")
                        + GboardSettingsText.get(appContext, R.string.gboard_patches_custom_stickers_summary);
            }
        }
        return GboardSettingsText.get(appContext, R.string.gboard_patches_custom_stickers_summary);
    }

    @Override
    public boolean isAvailable(Context context) {
        return true;
    }

    @Override
    public void onVisible(GboardPatchesSettingsContract.FeatureHost host) {
        if (host != null) {
            latestHost = host;
        }
        String launchMode = pendingAutoLaunchMode;
        pendingAutoLaunchMode = null;
        if (launchMode == null || host == null) {
            return;
        }
        if ("image".equalsIgnoreCase(launchMode)) {
            promptImportImageSticker(host);
        } else if ("gif".equalsIgnoreCase(launchMode)) {
            promptImportGifSticker(host);
        }
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        if (host != null) {
            latestHost = host;
        }
        Context context = resolveContext(host);
        SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);
        if (prefs != null) {
            GboardCustomStickerSettings.ensureDefaults(prefs);
        }

        boolean enabled = GboardCustomStickerSettings.readEnabled(prefs);
        boolean showInMenu = GboardCustomStickerSettings.readShowInFeaturesMenu(prefs);
        boolean autoRemoveBg = GboardCustomStickerSettings.readAutoRemoveBg(prefs);
        boolean outlineEnabled = GboardCustomStickerSettings.readOutlineEnabled(prefs);
        int tolerance = GboardCustomStickerSettings.readBgTolerance(prefs);
        boolean copyToClipboard = GboardCustomStickerSettings.readCopyToClipboardOnSend(prefs);

        List<CustomStickerItem> stickers = context == null
                ? Collections.<CustomStickerItem>emptyList()
                : GboardCustomStickerRepository.listStickers(context);

        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        String statusSummary = stickers.isEmpty()
                ? GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_empty_summary)
                : formatLibraryStats(stickers);
        statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_title),
                statusSummary,
                stickers.isEmpty()
                        ? GboardPatchesSettingsContract.StatusTone.NEUTRAL
                        : GboardPatchesSettingsContract.StatusTone.INFO));

        // 1. Sticker Studio Section (Create from Image / GIF)
        List<GboardPatchesSettingsContract.Row> studioRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        studioRows.add(new GboardPatchesSettingsContract.PrimaryCommandRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_image_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_image_summary),
                true,
                () -> promptImportImageSticker(resolveActiveHost(host))));
        studioRows.add(new GboardPatchesSettingsContract.CommandRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_gif_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_gif_summary),
                true,
                () -> promptImportGifSticker(resolveActiveHost(host))));

        // 2. Sticker Library Section
        List<GboardPatchesSettingsContract.Row> libraryRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        if (stickers.isEmpty()) {
            libraryRows.add(new GboardPatchesSettingsContract.DetailRow(
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_empty_title),
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_empty_summary),
                    true,
                    false));
        } else {
            for (final CustomStickerItem item : stickers) {
                Drawable thumbDrawable = loadRowThumbnailDrawable(context, item);
                libraryRows.add(new GboardPatchesSettingsContract.CommandRow(
                        displayTitleFor(item),
                        item.getFormattedSummary() + " • Tap to send or edit cutout",
                        true,
                        () -> showStickerActionsDialog(resolveActiveHost(host), item),
                        null,
                        thumbDrawable));
            }
        }

        // 3. Preferences Section
        List<GboardPatchesSettingsContract.Row> prefRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_enabled_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_enabled_summary),
                true,
                enabled,
                value -> {
                    GboardCustomStickerSettings.writeEnabled(prefs, value);
                    GboardPatchesSettingsContract.refresh(resolveActiveHost(host));
                }));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_features_menu_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_features_menu_summary),
                enabled,
                showInMenu,
                value -> {
                    GboardCustomStickerSettings.writeShowInFeaturesMenu(prefs, value);
                    GboardPatchesSettingsContract.refresh(resolveActiveHost(host));
                }));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_auto_bg_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_auto_bg_summary),
                enabled,
                autoRemoveBg,
                value -> {
                    GboardCustomStickerSettings.writeAutoRemoveBg(prefs, value);
                    GboardPatchesSettingsContract.refresh(resolveActiveHost(host));
                }));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_outline_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_outline_summary),
                enabled,
                outlineEnabled,
                value -> {
                    GboardCustomStickerSettings.writeOutlineEnabled(prefs, value);
                    GboardPatchesSettingsContract.refresh(resolveActiveHost(host));
                }));
        prefRows.add(new GboardPatchesSettingsContract.SelectorRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_summary),
                GboardCustomStickerSettings.formatToleranceLabel(tolerance),
                enabled,
                () -> showToleranceSelectorDialog(resolveActiveHost(host), prefs, tolerance)));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                "Copy sticker to clipboard on create & send",
                "Automatically place custom stickers into Gboard's Clipboard & Suggestion Strip for 1-tap pasting",
                enabled,
                copyToClipboard,
                value -> {
                    GboardCustomStickerSettings.writeCopyToClipboardOnSend(prefs, value);
                    GboardPatchesSettingsContract.refresh(resolveActiveHost(host));
                }));

        if (!stickers.isEmpty()) {
            prefRows.add(new GboardPatchesSettingsContract.DangerRow(
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_title),
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_summary),
                    true,
                    () -> {
                        GboardCustomStickerRepository.clearAllStickers(context);
                        GboardPatchesSettingsContract.showMessage(
                                resolveActiveHost(host), "All custom stickers deleted.");
                        GboardPatchesSettingsContract.refresh(resolveActiveHost(host));
                    },
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_confirm_title),
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_confirm_message)));
        }

        List<GboardPatchesSettingsContract.Section> sections = Arrays.asList(
                new GboardPatchesSettingsContract.Section(
                        GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_section_create),
                        studioRows),
                new GboardPatchesSettingsContract.Section(
                        GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_section_library)
                                + " (" + stickers.size() + ")",
                        libraryRows),
                new GboardPatchesSettingsContract.Section(
                        GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_section_preferences),
                        prefRows));

        return new GboardPatchesSettingsContract.Screen(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_title),
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_header_summary),
                statusBlocks,
                sections);
    }

    private static Drawable loadRowThumbnailDrawable(Context context, CustomStickerItem item) {
        if (context == null || item == null) {
            return null;
        }
        try {
            File file = GboardCustomStickerRepository.getStickerFile(context, item.getFileName());
            if (file == null || !file.isFile()) {
                return null;
            }
            int sizePx = Math.max(64, dp(context, 44));
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxDim / sample > sizePx * 2) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (decoded == null) {
                return null;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(decoded, sizePx, sizePx, true);
            return new BitmapDrawable(context.getResources(), scaled);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String displayTitleFor(CustomStickerItem item) {
        if (item == null) {
            return "";
        }
        return item.isFavorite() ? ("★ " + item.getName()) : item.getName();
    }

    private static String formatLibraryStats(List<CustomStickerItem> stickers) {
        int gifCount = 0;
        int cutoutCount = 0;
        long totalBytes = 0L;
        for (CustomStickerItem item : stickers) {
            if (item.isAnimatedGif()) {
                gifCount++;
            }
            if (item.isBackgroundRemoved()) {
                cutoutCount++;
            }
            totalBytes += Math.max(0L, item.getFileSizeBytes());
        }
        int imageCount = stickers.size() - gifCount;
        return String.format(
                Locale.US,
                "%d saved (%d PNG, %d GIF • %d with cutout • %s) • Available in Keyboard Features Menu & Clipboard",
                stickers.size(),
                imageCount,
                gifCount,
                cutoutCount,
                CustomStickerItem.formatFileSize(totalBytes));
    }

    private void showToleranceSelectorDialog(
            GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences prefs,
            int currentTolerance) {
        GboardPatchesSettingsContract.FeatureHost activeHost = resolveActiveHost(host);
        if (activeHost == null) {
            return;
        }
        Context context = resolveContext(activeHost);
        String[] labels = new String[] {
                "Low (18%) — Strict edge matching",
                "Medium (32%) — Recommended default",
                "High (48%) — Textured or gradient backgrounds",
                "Aggressive (64%) — High-contrast subjects"
        };
        String[] values = new String[] { "18", "32", "48", "64" };
        GboardPatchesSettingsContract.showChoiceDialog(
                activeHost,
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_title),
                labels,
                values,
                Integer.toString(currentTolerance),
                "Custom (1–100)...",
                () -> GboardPatchesSettingsContract.showPositiveIntegerDialog(
                        activeHost,
                        GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_title),
                        "1 - 100",
                        currentTolerance,
                        val -> {
                            GboardCustomStickerSettings.writeBgTolerance(prefs, val);
                            GboardPatchesSettingsContract.refresh(activeHost);
                        }),
                selected -> {
                    try {
                        int parsed = Integer.parseInt(selected);
                        GboardCustomStickerSettings.writeBgTolerance(prefs, parsed);
                        GboardPatchesSettingsContract.refresh(activeHost);
                    } catch (NumberFormatException ignored) {
                    }
                });
    }

    public void promptImportImageSticker(GboardPatchesSettingsContract.FeatureHost host) {
        final GboardPatchesSettingsContract.FeatureHost requestHost = resolveActiveHost(host);
        if (requestHost == null) {
            return;
        }
        GboardPatchesSettingsContract.openBinaryDocument(requestHost, IMAGE_MIME_TYPES, document -> {
            GboardPatchesSettingsContract.FeatureHost activeHost = resolveActiveHost(requestHost);
            if (document == null || document.getData() == null || document.getData().length == 0) {
                GboardPatchesSettingsContract.showMessage(activeHost, "Failed to read selected image.");
                return;
            }
            handleImportedMediaBytes(
                    activeHost,
                    stripExtension(document.getDisplayName(), "Sticker"),
                    document.getData());
        });
    }

    public void promptImportGifSticker(GboardPatchesSettingsContract.FeatureHost host) {
        final GboardPatchesSettingsContract.FeatureHost requestHost = resolveActiveHost(host);
        if (requestHost == null) {
            return;
        }
        GboardPatchesSettingsContract.openBinaryDocument(requestHost, GIF_MIME_TYPES, document -> {
            GboardPatchesSettingsContract.FeatureHost activeHost = resolveActiveHost(requestHost);
            if (document == null || document.getData() == null || document.getData().length == 0) {
                GboardPatchesSettingsContract.showMessage(activeHost, "Failed to read selected GIF.");
                return;
            }
            handleImportedMediaBytes(
                    activeHost,
                    stripExtension(document.getDisplayName(), "GIF Sticker"),
                    document.getData());
        });
    }

    /**
     * Immediately creates and saves the custom sticker using the user's current settings,
     * copies it to Gboard's clipboard so it is ready to paste right away, refreshes the
     * Sticker Library screen, and opens the Sticker Studio dialog for optional live refinement.
     */
    private void handleImportedMediaBytes(
            final GboardPatchesSettingsContract.FeatureHost host,
            final String baseName,
            final byte[] data) {
        final GboardPatchesSettingsContract.FeatureHost activeHost = resolveActiveHost(host);
        final Context context = resolveContext(activeHost);
        if (context == null || data == null || data.length == 0) {
            return;
        }

        if (GboardStickerImageProcessor.isGifBytes(data)) {
            CustomStickerItem autoSaved = null;
            try {
                SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);
                int tolerance = GboardCustomStickerSettings.readBgTolerance(prefs);
                autoSaved = GboardCustomStickerRepository.createStickerFromGifBytes(
                        context, data, baseName, false, tolerance, null);
                if (autoSaved != null) {
                    copyStickerToClipboardIfEnabled(context, autoSaved);
                    GboardPatchesSettingsContract.refresh(activeHost);
                    GboardPatchesSettingsContract.showMessage(
                            activeHost,
                            "GIF sticker \"" + autoSaved.getName() + "\" created & copied to keyboard clipboard!");
                }
            } catch (Throwable t) {
                try {
                    Log.w(TAG, "Auto-save GIF sticker failed", t);
                } catch (Throwable ignored) {
                }
            }
            final String existingId = autoSaved != null ? autoSaved.getId() : null;
            showGifStickerStudioDialog(activeHost, baseName, data, existingId);
        } else {
            CustomStickerItem autoSaved = null;
            try {
                SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);
                boolean removeBg = GboardCustomStickerSettings.readAutoRemoveBg(prefs);
                boolean outline = GboardCustomStickerSettings.readOutlineEnabled(prefs);
                int tolerance = GboardCustomStickerSettings.readBgTolerance(prefs);
                GboardStickerImageProcessor.StickerOptions opts =
                        new GboardStickerImageProcessor.StickerOptions(
                                removeBg,
                                tolerance,
                                true,
                                null,
                                outline,
                                Color.WHITE,
                                GboardStickerImageProcessor.DEFAULT_OUTLINE_RADIUS_PX,
                                true,
                                GboardStickerImageProcessor.DEFAULT_MAX_STICKER_DIMENSION_PX);
                autoSaved = GboardCustomStickerRepository.createStickerFromImageBytes(
                        context, data, baseName, opts);
                if (autoSaved != null) {
                    copyStickerToClipboardIfEnabled(context, autoSaved);
                    GboardPatchesSettingsContract.refresh(activeHost);
                    GboardPatchesSettingsContract.showMessage(
                            activeHost,
                            "Sticker \"" + autoSaved.getName() + "\" created & copied to keyboard clipboard!");
                }
            } catch (Throwable t) {
                try {
                    Log.w(TAG, "Auto-save image sticker failed", t);
                } catch (Throwable ignored) {
                }
            }
            final String existingId = autoSaved != null ? autoSaved.getId() : null;
            showImageStickerStudioDialog(activeHost, baseName, data, existingId);
        }
    }

    private static void copyStickerToClipboardIfEnabled(Context context, CustomStickerItem item) {
        if (context == null || item == null) {
            return;
        }
        SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);
        if (GboardCustomStickerSettings.readCopyToClipboardOnSend(prefs)) {
            Uri uri = GboardCustomStickerRepository.buildStickerContentUri(context, item);
            if (uri != null) {
                GboardCustomStickerRuntime.copyStickerUriToClipboard(context, item, uri);
            }
        }
    }

    private void showImageStickerStudioDialog(
            final GboardPatchesSettingsContract.FeatureHost host,
            final String initialName,
            final byte[] rawImageBytes,
            final String existingStickerId) {
        final GboardPatchesSettingsContract.FeatureHost activeHost = resolveActiveHost(host);
        if (activeHost == null || rawImageBytes == null) {
            return;
        }
        final Context context = resolveContext(activeHost);
        if (context == null) {
            return;
        }
        final Bitmap studioSource = GboardStickerImageProcessor.decodeBoundedBitmap(
                rawImageBytes, GboardStickerImageProcessor.DEFAULT_MAX_STICKER_DIMENSION_PX);
        if (studioSource == null || studioSource.getWidth() <= 0 || studioSource.getHeight() <= 0) {
            GboardPatchesSettingsContract.showMessage(activeHost, "Unable to decode selected image.");
            return;
        }

        final SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);

        GboardPatchesSettingsContract.showManagedDialog(activeHost, onDismiss -> {
            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(true);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(context, 18);
            root.setPadding(pad, dp(context, 12), pad, dp(context, 12));
            scrollView.addView(root);

            if (existingStickerId != null) {
                TextView savedBanner = new TextView(context);
                savedBanner.setText("✓ Saved to Sticker Library & Keyboard Clipboard — customize cutout below");
                savedBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                savedBanner.setTextColor(0xFF81C995);
                savedBanner.setTypeface(Typeface.DEFAULT_BOLD);
                savedBanner.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
                GradientDrawable bannerBg = new GradientDrawable();
                bannerBg.setColor(0x2281C995);
                bannerBg.setCornerRadius(dp(context, 12));
                savedBanner.setBackground(bannerBg);
                LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                bannerLp.bottomMargin = dp(context, 10);
                savedBanner.setLayoutParams(bannerLp);
                root.addView(savedBanner);
            }

            FrameLayout previewFrame = new FrameLayout(context);
            previewFrame.setBackground(new CheckerboardDrawable(dp(context, 10), dp(context, 16)));
            LinearLayout.LayoutParams previewFrameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 210));
            previewFrameLp.bottomMargin = dp(context, 8);
            previewFrame.setLayoutParams(previewFrameLp);

            final ImageView previewImageView = new ImageView(context);
            previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER);
            int imgPad = dp(context, 12);
            previewImageView.setPadding(imgPad, imgPad, imgPad, imgPad);
            previewFrame.addView(previewImageView, imgLp);

            final TextView badgeOverlay = new TextView(context);
            badgeOverlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            badgeOverlay.setTextColor(Color.WHITE);
            badgeOverlay.setTypeface(Typeface.DEFAULT_BOLD);
            badgeOverlay.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(0xB81E1F22);
            badgeBg.setCornerRadius(dp(context, 12));
            badgeOverlay.setBackground(badgeBg);
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            badgeLp.rightMargin = dp(context, 10);
            badgeLp.bottomMargin = dp(context, 10);
            previewFrame.addView(badgeOverlay, badgeLp);
            root.addView(previewFrame);

            LinearLayout colorHintRow = new LinearLayout(context);
            colorHintRow.setOrientation(LinearLayout.HORIZONTAL);
            colorHintRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hintRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hintRowLp.bottomMargin = dp(context, 10);
            colorHintRow.setLayoutParams(hintRowLp);

            final TextView colorModeLabel = new TextView(context);
            colorModeLabel.setText("Tip: Tap background in preview to pick a specific color to remove");
            colorModeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            colorModeLabel.setTextColor(0xFF8E918F);
            colorModeLabel.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            colorHintRow.addView(colorModeLabel);

            final TextView resetColorButton = new TextView(context);
            resetColorButton.setText("Auto Edges");
            resetColorButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            resetColorButton.setTypeface(Typeface.DEFAULT_BOLD);
            resetColorButton.setTextColor(0xFFA8C7FA);
            resetColorButton.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
            resetColorButton.setVisibility(View.GONE);
            colorHintRow.addView(resetColorButton);
            root.addView(colorHintRow);

            TextView nameLabel = new TextView(context);
            nameLabel.setText("Sticker Name");
            nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            nameLabel.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(nameLabel);

            final EditText nameInput = new EditText(context);
            nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            nameInput.setText(initialName);
            nameInput.setHint("Sticker Name");
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nameLp.bottomMargin = dp(context, 8);
            nameInput.setLayoutParams(nameLp);
            root.addView(nameInput);

            final boolean[] removeBgState = new boolean[] {
                    GboardCustomStickerSettings.readAutoRemoveBg(prefs)
            };
            final boolean[] interiorHolesState = new boolean[] { false };
            final int[] toleranceState = new int[] {
                    GboardCustomStickerSettings.readBgTolerance(prefs)
            };
            final boolean[] outlineState = new boolean[] {
                    GboardCustomStickerSettings.readOutlineEnabled(prefs)
            };
            final boolean[] trimState = new boolean[] { true };
            final Integer[] manualColorState = new Integer[] { null };
            final GboardStickerImageProcessor.ProcessedStickerResult[] latestResult =
                    new GboardStickerImageProcessor.ProcessedStickerResult[1];

            final StudioToggleControl[] interiorControlRef = new StudioToggleControl[1];
            final TextView toleranceValueLabel = new TextView(context);

            final Runnable updatePreview = () -> {
                try {
                    toleranceValueLabel.setText("Background sensitivity: " + toleranceState[0] + "%");
                    GboardStickerImageProcessor.StickerOptions opts =
                            new GboardStickerImageProcessor.StickerOptions(
                                    removeBgState[0],
                                    toleranceState[0],
                                    !interiorHolesState[0],
                                    manualColorState[0],
                                    outlineState[0],
                                    Color.WHITE,
                                    GboardStickerImageProcessor.DEFAULT_OUTLINE_RADIUS_PX,
                                    trimState[0],
                                    GboardStickerImageProcessor.DEFAULT_MAX_STICKER_DIMENSION_PX);
                    GboardStickerImageProcessor.ProcessedStickerResult processed =
                            GboardStickerImageProcessor.processStaticBitmap(studioSource, opts);
                    if (processed != null && processed.getPreviewBitmap() != null) {
                        latestResult[0] = processed;
                        previewImageView.setImageBitmap(processed.getPreviewBitmap());
                        badgeOverlay.setText(processed.getWidth() + "×" + processed.getHeight()
                                + (removeBgState[0] ? " • Cutout" : " • Full")
                                + (outlineState[0] ? " • Border" : ""));
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to update sticker preview", t);
                }
            };

            final StudioToggleControl removeBgControl = addSafeToggleRow(
                    context,
                    root,
                    "Remove background",
                    "Isolate foreground subject onto transparent canvas",
                    removeBgState[0],
                    checked -> {
                        removeBgState[0] = checked;
                        if (interiorControlRef[0] != null) {
                            interiorControlRef[0].setEnabled(checked);
                        }
                        updatePreview.run();
                    });

            interiorControlRef[0] = addSafeToggleRow(
                    context,
                    root,
                    "Remove interior holes",
                    "Also clear matching background colors inside closed loops",
                    interiorHolesState[0],
                    checked -> {
                        interiorHolesState[0] = checked;
                        updatePreview.run();
                    });
            interiorControlRef[0].setEnabled(removeBgState[0]);

            toleranceValueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            toleranceValueLabel.setTypeface(Typeface.DEFAULT_BOLD);
            toleranceValueLabel.setPadding(0, dp(context, 6), 0, dp(context, 4));
            root.addView(toleranceValueLabel);

            addSensitivityPresetBar(context, root, toleranceState, updatePreview);

            addSafeToggleRow(
                    context,
                    root,
                    "Add white sticker border",
                    "Draw a smooth die-cut outline around the sticker",
                    outlineState[0],
                    checked -> {
                        outlineState[0] = checked;
                        updatePreview.run();
                    });

            addSafeToggleRow(
                    context,
                    root,
                    "Auto-trim transparent margins",
                    "Crop empty space and maximize sticker size",
                    trimState[0],
                    checked -> {
                        trimState[0] = checked;
                        updatePreview.run();
                    });

            previewImageView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP && studioSource != null) {
                    int vw = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight();
                    int vh = v.getHeight() - v.getPaddingTop() - v.getPaddingBottom();
                    if (vw > 0 && vh > 0) {
                        float relX = (event.getX() - v.getPaddingLeft()) / (float) vw;
                        float relY = (event.getY() - v.getPaddingTop()) / (float) vh;
                        int bx = Math.max(0, Math.min(studioSource.getWidth() - 1,
                                Math.round(relX * (studioSource.getWidth() - 1))));
                        int by = Math.max(0, Math.min(studioSource.getHeight() - 1,
                                Math.round(relY * (studioSource.getHeight() - 1))));
                        int sampled = studioSource.getPixel(bx, by);
                        manualColorState[0] = Integer.valueOf(sampled);
                        removeBgState[0] = true;
                        removeBgControl.setChecked(true);
                        if (interiorControlRef[0] != null) {
                            interiorControlRef[0].setEnabled(true);
                        }
                        String hex = String.format(Locale.US, "#%06X", (0xFFFFFF & sampled));
                        colorModeLabel.setText("Target background color: " + hex);
                        resetColorButton.setVisibility(View.VISIBLE);
                        updatePreview.run();
                    }
                }
                return true;
            });

            resetColorButton.setOnClickListener(v -> {
                manualColorState[0] = null;
                colorModeLabel.setText("Tip: Tap background in preview to pick a specific color to remove");
                resetColorButton.setVisibility(View.GONE);
                updatePreview.run();
            });

            updatePreview.run();

            final AlertDialog[] dialogRef = new AlertDialog[1];
            final Runnable saveAction = () -> {
                GboardStickerImageProcessor.ProcessedStickerResult result = latestResult[0];
                if (result == null) {
                    GboardStickerImageProcessor.StickerOptions opts =
                            new GboardStickerImageProcessor.StickerOptions(
                                    removeBgState[0],
                                    toleranceState[0],
                                    !interiorHolesState[0],
                                    manualColorState[0],
                                    outlineState[0],
                                    Color.WHITE,
                                    GboardStickerImageProcessor.DEFAULT_OUTLINE_RADIUS_PX,
                                    trimState[0],
                                    GboardStickerImageProcessor.DEFAULT_MAX_STICKER_DIMENSION_PX);
                    result = GboardStickerImageProcessor.processStaticBitmap(studioSource, opts);
                }
                String chosenName = nameInput.getText() == null
                        ? initialName
                        : nameInput.getText().toString().trim();
                if (chosenName.isEmpty()) {
                    chosenName = initialName;
                }
                try {
                    CustomStickerItem saved = existingStickerId != null
                            ? GboardCustomStickerRepository.updateProcessedSticker(
                                    context, existingStickerId, chosenName, result)
                            : GboardCustomStickerRepository.saveProcessedSticker(
                                    context, chosenName, result);
                    if (saved != null) {
                        copyStickerToClipboardIfEnabled(context, saved);
                        GboardPatchesSettingsContract.showMessage(
                                activeHost, "Sticker saved & ready in keyboard!");
                        GboardPatchesSettingsContract.refresh(activeHost);
                    }
                } catch (IOException e) {
                    GboardPatchesSettingsContract.showMessage(
                            activeHost, "Failed to save custom sticker.");
                }
            };

            View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 10)));
            root.addView(spacer);

            addActionSheetButton(
                    context,
                    root,
                    "Save Sticker & Copy to Keyboard",
                    true,
                    false,
                    () -> {
                        saveAction.run();
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                    });

            if (existingStickerId != null) {
                addActionSheetButton(
                        context,
                        root,
                        "Delete This Sticker",
                        false,
                        true,
                        () -> {
                            GboardCustomStickerRepository.deleteSticker(context, existingStickerId);
                            GboardPatchesSettingsContract.showMessage(activeHost, "Sticker deleted.");
                            GboardPatchesSettingsContract.refresh(activeHost);
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                        });
            }

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_image_title))
                    .setView(scrollView)
                    .setNegativeButton(GboardSettingsText.get(context, R.string.gboard_patches_dialog_close), null)
                    .setPositiveButton("Save Sticker", (d, which) -> saveAction.run())
                    .create();
            dialogRef[0] = dialog;
            dialog.setOnDismissListener(ignored -> onDismiss.run());
            dialog.show();
            return true;
        });
    }

    private void showGifStickerStudioDialog(
            final GboardPatchesSettingsContract.FeatureHost host,
            final String initialName,
            final byte[] rawGifBytes,
            final String existingStickerId) {
        final GboardPatchesSettingsContract.FeatureHost activeHost = resolveActiveHost(host);
        if (activeHost == null || rawGifBytes == null) {
            return;
        }
        final Context context = resolveContext(activeHost);
        if (context == null) {
            return;
        }
        final SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);

        GboardPatchesSettingsContract.showManagedDialog(activeHost, onDismiss -> {
            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(true);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(context, 18);
            root.setPadding(pad, dp(context, 12), pad, dp(context, 12));
            scrollView.addView(root);

            if (existingStickerId != null) {
                TextView savedBanner = new TextView(context);
                savedBanner.setText("✓ Saved to Sticker Library & Keyboard Clipboard — customize below");
                savedBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                savedBanner.setTextColor(0xFF81C995);
                savedBanner.setTypeface(Typeface.DEFAULT_BOLD);
                savedBanner.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
                GradientDrawable bannerBg = new GradientDrawable();
                bannerBg.setColor(0x2281C995);
                bannerBg.setCornerRadius(dp(context, 12));
                savedBanner.setBackground(bannerBg);
                LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                bannerLp.bottomMargin = dp(context, 10);
                savedBanner.setLayoutParams(bannerLp);
                root.addView(savedBanner);
            }

            FrameLayout previewFrame = new FrameLayout(context);
            previewFrame.setBackground(new CheckerboardDrawable(dp(context, 10), dp(context, 16)));
            LinearLayout.LayoutParams previewFrameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 200));
            previewFrameLp.bottomMargin = dp(context, 10);
            previewFrame.setLayoutParams(previewFrameLp);

            final ImageView previewImageView = new ImageView(context);
            previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER);
            int imgPad = dp(context, 12);
            previewImageView.setPadding(imgPad, imgPad, imgPad, imgPad);
            previewFrame.addView(previewImageView, imgLp);

            final TextView badgeOverlay = new TextView(context);
            badgeOverlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            badgeOverlay.setTextColor(Color.WHITE);
            badgeOverlay.setTypeface(Typeface.DEFAULT_BOLD);
            badgeOverlay.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(0xB81E1F22);
            badgeBg.setCornerRadius(dp(context, 12));
            badgeOverlay.setBackground(badgeBg);
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            badgeLp.rightMargin = dp(context, 10);
            badgeLp.bottomMargin = dp(context, 10);
            previewFrame.addView(badgeOverlay, badgeLp);
            root.addView(previewFrame);

            TextView nameLabel = new TextView(context);
            nameLabel.setText("Sticker Name");
            nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            nameLabel.setTypeface(Typeface.DEFAULT_BOLD);
            root.addView(nameLabel);

            final EditText nameInput = new EditText(context);
            nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            nameInput.setText(initialName);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            nameLp.bottomMargin = dp(context, 10);
            nameInput.setLayoutParams(nameLp);
            root.addView(nameInput);

            final boolean[] removeGifBgState = new boolean[] { false };
            final int[] gifToleranceState = new int[] {
                    GboardCustomStickerSettings.readBgTolerance(prefs)
            };
            final GboardStickerImageProcessor.ProcessedStickerResult[] latestGifResult =
                    new GboardStickerImageProcessor.ProcessedStickerResult[1];

            final TextView toleranceLabel = new TextView(context);

            final Runnable updateGifPreview = () -> {
                try {
                    toleranceLabel.setText("Palette color tolerance: " + gifToleranceState[0] + "%");
                    GboardStickerImageProcessor.ProcessedStickerResult processed =
                            GboardStickerImageProcessor.processGifBytes(
                                    rawGifBytes,
                                    removeGifBgState[0],
                                    gifToleranceState[0],
                                    null);
                    if (processed != null) {
                        latestGifResult[0] = processed;
                        bindStickerBytesToImageView(previewImageView, processed.getEncodedBytes(), true);
                        badgeOverlay.setText("GIF • " + processed.getWidth() + "×" + processed.getHeight()
                                + (processed.isBackgroundRemoved() ? " • Transparent BG" : " • Original")
                                + " • " + CustomStickerItem.formatFileSize(processed.getEncodedBytes().length));
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to update GIF preview", t);
                }
            };

            addSafeToggleRow(
                    context,
                    root,
                    "Make solid GIF background transparent",
                    "Detects background color in the GIF palette and marks it transparent while keeping animation",
                    removeGifBgState[0],
                    checked -> {
                        removeGifBgState[0] = checked;
                        updateGifPreview.run();
                    });

            toleranceLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            toleranceLabel.setTypeface(Typeface.DEFAULT_BOLD);
            toleranceLabel.setPadding(0, dp(context, 6), 0, dp(context, 4));
            root.addView(toleranceLabel);

            addSensitivityPresetBar(context, root, gifToleranceState, updateGifPreview);

            updateGifPreview.run();

            final AlertDialog[] dialogRef = new AlertDialog[1];
            final Runnable saveGifAction = () -> {
                GboardStickerImageProcessor.ProcessedStickerResult result = latestGifResult[0];
                if (result == null) {
                    result = GboardStickerImageProcessor.processGifBytes(
                            rawGifBytes,
                            removeGifBgState[0],
                            gifToleranceState[0],
                            null);
                }
                String chosenName = nameInput.getText() == null
                        ? initialName
                        : nameInput.getText().toString().trim();
                if (chosenName.isEmpty()) {
                    chosenName = initialName;
                }
                try {
                    CustomStickerItem saved = existingStickerId != null
                            ? GboardCustomStickerRepository.updateProcessedSticker(
                                    context, existingStickerId, chosenName, result)
                            : GboardCustomStickerRepository.saveProcessedSticker(
                                    context, chosenName, result);
                    if (saved != null) {
                        copyStickerToClipboardIfEnabled(context, saved);
                        GboardPatchesSettingsContract.showMessage(
                                activeHost, "GIF sticker saved & ready in keyboard!");
                        GboardPatchesSettingsContract.refresh(activeHost);
                    }
                } catch (IOException e) {
                    GboardPatchesSettingsContract.showMessage(
                            activeHost, "Failed to save GIF sticker.");
                }
            };

            View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 10)));
            root.addView(spacer);

            addActionSheetButton(
                    context,
                    root,
                    "Save GIF Sticker & Copy to Keyboard",
                    true,
                    false,
                    () -> {
                        saveGifAction.run();
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                    });

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_gif_title))
                    .setView(scrollView)
                    .setNegativeButton(GboardSettingsText.get(context, R.string.gboard_patches_dialog_close), null)
                    .setPositiveButton("Save GIF Sticker", (d, which) -> saveGifAction.run())
                    .create();
            dialogRef[0] = dialog;
            dialog.setOnDismissListener(ignored -> onDismiss.run());
            dialog.show();
            return true;
        });
    }

    private void showStickerActionsDialog(
            final GboardPatchesSettingsContract.FeatureHost host,
            final CustomStickerItem item) {
        final GboardPatchesSettingsContract.FeatureHost activeHost = resolveActiveHost(host);
        if (activeHost == null || item == null) {
            return;
        }
        final Context context = resolveContext(activeHost);
        if (context == null) {
            return;
        }
        byte[] loadedBytes = null;
        try {
            loadedBytes = GboardCustomStickerRepository.readStickerBytes(context, item);
        } catch (IOException ignored) {
        }
        final byte[] stickerBytes = loadedBytes;

        GboardPatchesSettingsContract.showManagedDialog(activeHost, onDismiss -> {
            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(true);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(context, 20);
            root.setPadding(pad, dp(context, 12), pad, dp(context, 8));
            scrollView.addView(root);

            FrameLayout previewFrame = new FrameLayout(context);
            previewFrame.setBackground(new CheckerboardDrawable(dp(context, 10), dp(context, 16)));
            LinearLayout.LayoutParams previewFrameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 190));
            previewFrameLp.bottomMargin = dp(context, 10);
            previewFrame.setLayoutParams(previewFrameLp);

            ImageView previewImageView = new ImageView(context);
            previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int imgPad = dp(context, 12);
            previewImageView.setPadding(imgPad, imgPad, imgPad, imgPad);
            bindStickerBytesToImageView(previewImageView, stickerBytes, item.isAnimatedGif());
            previewFrame.addView(previewImageView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            root.addView(previewFrame);

            TextView metaView = new TextView(context);
            metaView.setText(item.getFormattedSummary());
            metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            metaView.setTextColor(0xFF8E918F);
            metaView.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            metaLp.bottomMargin = dp(context, 12);
            metaView.setLayoutParams(metaLp);
            root.addView(metaView);

            final AlertDialog[] dialogRef = new AlertDialog[1];

            addActionSheetButton(
                    context,
                    root,
                    "Send / Copy to Keyboard Clipboard",
                    true,
                    false,
                    () -> {
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        GboardCustomStickerRuntime.CommitResult res =
                                GboardCustomStickerRuntime.commitSticker(context, item);
                        if (res == GboardCustomStickerRuntime.CommitResult.COMMITTED_DIRECTLY) {
                            GboardPatchesSettingsContract.showMessage(
                                    activeHost, "Sticker inserted!");
                        } else {
                            Uri uri = GboardCustomStickerRepository.buildStickerContentUri(context, item);
                            GboardCustomStickerRuntime.copyStickerUriToClipboard(context, item, uri);
                            GboardPatchesSettingsContract.showMessage(
                                    activeHost,
                                    "Copied \"" + item.getName() + "\" to Gboard Clipboard — paste in any app!");
                        }
                    });

            if (stickerBytes != null && stickerBytes.length > 0) {
                addActionSheetButton(
                        context,
                        root,
                        "Edit Cutout & Border in Studio",
                        false,
                        false,
                        () -> {
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                            if (item.isAnimatedGif()) {
                                showGifStickerStudioDialog(
                                        activeHost, item.getName(), stickerBytes, item.getId());
                            } else {
                                showImageStickerStudioDialog(
                                        activeHost, item.getName(), stickerBytes, item.getId());
                            }
                        });
            }

            addActionSheetButton(
                    context,
                    root,
                    "Share Sticker to App...",
                    false,
                    false,
                    () -> {
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        GboardCustomStickerRuntime.shareSticker(context, item);
                    });

            addActionSheetButton(
                    context,
                    root,
                    "Export Sticker File",
                    false,
                    false,
                    () -> {
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        if (stickerBytes != null && stickerBytes.length > 0) {
                            String ext = item.isAnimatedGif() ? ".gif" : ".png";
                            String safeFile = sanitizeFileName(item.getName()) + ext;
                            GboardPatchesSettingsContract.createBinaryDocument(
                                    activeHost,
                                    safeFile,
                                    item.getMimeType(),
                                    stickerBytes,
                                    () -> { });
                        }
                    });

            String favTitle = item.isFavorite()
                    ? "Remove from Favorites ★"
                    : "Add to Favorites ★";
            addActionSheetButton(
                    context,
                    root,
                    favTitle,
                    false,
                    false,
                    () -> {
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        GboardCustomStickerRepository.toggleFavorite(context, item.getId());
                        GboardPatchesSettingsContract.refresh(activeHost);
                    });

            addActionSheetButton(
                    context,
                    root,
                    "Rename Sticker",
                    false,
                    false,
                    () -> {
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        GboardPatchesSettingsContract.showTextInputDialog(
                                activeHost,
                                "Rename Sticker",
                                "Sticker Name",
                                item.getName(),
                                newName -> {
                                    GboardCustomStickerRepository.renameSticker(context, item.getId(), newName);
                                    GboardPatchesSettingsContract.refresh(activeHost);
                                });
                    });

            addActionSheetButton(
                    context,
                    root,
                    "Delete Sticker",
                    false,
                    true,
                    () -> {
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        GboardCustomStickerRepository.deleteSticker(context, item.getId());
                        GboardPatchesSettingsContract.showMessage(activeHost, "Sticker deleted.");
                        GboardPatchesSettingsContract.refresh(activeHost);
                    });

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(displayTitleFor(item))
                    .setView(scrollView)
                    .setNegativeButton(GboardSettingsText.get(context, R.string.gboard_patches_dialog_close), null)
                    .create();
            dialogRef[0] = dialog;
            dialog.setOnDismissListener(ignored -> onDismiss.run());
            dialog.show();
            return true;
        });
    }

    private interface BooleanChangeListener {
        void onChanged(boolean checked);
    }

    private static final class StudioToggleControl {
        private final LinearLayout row;
        private final TextView pill;
        private boolean checked;
        private boolean enabled = true;
        private final Context context;

        StudioToggleControl(Context context, LinearLayout row, TextView pill, boolean initialChecked) {
            this.context = context;
            this.row = row;
            this.pill = pill;
            this.checked = initialChecked;
            updateVisuals();
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value) {
            this.checked = value;
            updateVisuals();
        }

        void setEnabled(boolean value) {
            this.enabled = value;
            row.setEnabled(value);
            row.setAlpha(value ? 1.0f : 0.45f);
            updateVisuals();
        }

        boolean isEnabled() {
            return enabled;
        }

        private void updateVisuals() {
            pill.setText(checked ? "ON" : "OFF");
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(context, 999));
            if (checked) {
                bg.setColor(0xFFA8C7FA);
                pill.setTextColor(0xFF062E6F);
            } else {
                bg.setColor(0x22FFFFFF);
                bg.setStroke(dp(context, 1), 0x44FFFFFF);
                pill.setTextColor(0xFFC4C7C5);
            }
            pill.setBackground(bg);
        }
    }

    private static StudioToggleControl addSafeToggleRow(
            Context context,
            LinearLayout parent,
            String title,
            String subtitle,
            boolean initialChecked,
            BooleanChangeListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 7), 0, dp(context, 7));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(titleView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subView = new TextView(context);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            subView.setTextColor(0xFF8E918F);
            textCol.addView(subView);
        }

        TextView pill = new TextView(context);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.leftMargin = dp(context, 10);
        pill.setLayoutParams(pillLp);

        row.addView(textCol);
        row.addView(pill);

        final StudioToggleControl control = new StudioToggleControl(context, row, pill, initialChecked);
        row.setOnClickListener(v -> {
            if (!control.isEnabled()) {
                return;
            }
            boolean next = !control.isChecked();
            control.setChecked(next);
            if (listener != null) {
                listener.onChanged(next);
            }
        });
        parent.addView(row);
        return control;
    }

    private static void addSensitivityPresetBar(
            Context context,
            LinearLayout parent,
            final int[] toleranceState,
            final Runnable onUpdate) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.bottomMargin = dp(context, 8);
        bar.setLayoutParams(barLp);

        final int[] presets = new int[] { 18, 32, 48, 64 };
        final String[] labels = new String[] { "18%", "32%", "48%", "64%" };

        TextView minusBtn = buildSensitivityStepChip(context, "−5%");
        minusBtn.setOnClickListener(v -> {
            toleranceState[0] = Math.max(5, toleranceState[0] - 5);
            onUpdate.run();
        });
        bar.addView(minusBtn);

        for (int i = 0; i < presets.length; i++) {
            final int presetVal = presets[i];
            TextView chip = buildSensitivityStepChip(context, labels[i]);
            chip.setOnClickListener(v -> {
                toleranceState[0] = presetVal;
                onUpdate.run();
            });
            bar.addView(chip);
        }

        TextView plusBtn = buildSensitivityStepChip(context, "+5%");
        plusBtn.setOnClickListener(v -> {
            toleranceState[0] = Math.min(85, toleranceState[0] + 5);
            onUpdate.run();
        });
        bar.addView(plusBtn);

        parent.addView(bar);
    }

    private static TextView buildSensitivityStepChip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(0xFFE3E3E3);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22FFFFFF);
        bg.setCornerRadius(dp(context, 10));
        bg.setStroke(dp(context, 1), 0x33FFFFFF);
        chip.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        lp.rightMargin = dp(context, 4);
        chip.setLayoutParams(lp);
        return chip;
    }

    private static void addActionSheetButton(
            Context context,
            LinearLayout parent,
            String label,
            boolean primary,
            boolean danger,
            Runnable onClick) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        int vPad = dp(context, 12);
        btn.setPadding(dp(context, 16), vPad, dp(context, 16), vPad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(context, 14));
        if (primary) {
            bg.setColor(0xFFA8C7FA);
            btn.setTextColor(0xFF062E6F);
        } else if (danger) {
            bg.setColor(0x28F2B8B5);
            bg.setStroke(dp(context, 1), 0x66F2B8B5);
            btn.setTextColor(0xFFF2B8B5);
        } else {
            bg.setColor(0x1FFFFFFF);
            bg.setStroke(dp(context, 1), 0x33FFFFFF);
            btn.setTextColor(0xFFE3E3E3);
        }
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(context, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> onClick.run());
        parent.addView(btn);
    }

    private static void bindStickerBytesToImageView(
            ImageView imageView,
            byte[] bytes,
            boolean animatedGif) {
        if (imageView == null || bytes == null || bytes.length == 0) {
            return;
        }
        if (animatedGif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
                Drawable drawable = ImageDecoder.decodeDrawable(source);
                imageView.setImageDrawable(drawable);
                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                }
                return;
            } catch (Throwable ignored) {
            }
        }
        Bitmap bitmap = GboardStickerImageProcessor.decodeBoundedBitmap(
                bytes, GboardStickerImageProcessor.DEFAULT_MAX_STICKER_DIMENSION_PX);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
    }

    private static String stripExtension(String fileName, String fallback) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return fallback;
        }
        String name = new File(fileName.trim()).getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replace('_', ' ').replace('-', ' ').trim();
        return name.isEmpty() ? fallback : name;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "sticker";
        }
        String cleaned = name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isEmpty() ? "sticker" : cleaned;
    }

    private static int dp(Context context, int value) {
        if (context == null || context.getResources() == null) {
            return value;
        }
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Draws a rounded checkerboard pattern behind sticker previews so transparency is obvious.
     */
    static final class CheckerboardDrawable extends Drawable {
        private final int tileSizePx;
        private final float cornerRadiusPx;
        private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CheckerboardDrawable(int tileSizePx, float cornerRadiusPx) {
            this.tileSizePx = Math.max(4, tileSizePx);
            this.cornerRadiusPx = cornerRadiusPx;
            lightPaint.setColor(0xFF2B2D31);
            darkPaint.setColor(0xFF1E1F22);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(2f);
            borderPaint.setColor(0x38FFFFFF);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            int save = canvas.save();
            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addRoundRect(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    android.graphics.Path.Direction.CW);
            canvas.clipPath(clipPath);

            for (int y = bounds.top, row = 0; y < bounds.bottom; y += tileSizePx, row++) {
                for (int x = bounds.left, col = 0; x < bounds.right; x += tileSizePx, col++) {
                    Paint p = ((row + col) & 1) == 0 ? lightPaint : darkPaint;
                    canvas.drawRect(
                            x,
                            y,
                            Math.min(bounds.right, x + tileSizePx),
                            Math.min(bounds.bottom, y + tileSizePx),
                            p);
                }
            }
            canvas.restoreToCount(save);
            canvas.drawRoundRect(
                    bounds.left + 1f,
                    bounds.top + 1f,
                    bounds.right - 1f,
                    bounds.bottom - 1f,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    borderPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            lightPaint.setAlpha(alpha);
            darkPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            lightPaint.setColorFilter(colorFilter);
            darkPaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
