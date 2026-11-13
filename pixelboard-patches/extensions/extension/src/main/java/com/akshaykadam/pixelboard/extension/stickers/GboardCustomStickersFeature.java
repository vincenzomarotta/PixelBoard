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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
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
import android.widget.SeekBar;
import android.widget.Switch;
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

    private static final String[] IMAGE_MIME_TYPES = new String[] {
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif",
            "image/*"
    };

    private static final String[] GIF_MIME_TYPES = new String[] {
            "image/gif",
            "image/*"
    };

    private final Context appContext;
    private volatile String pendingAutoLaunchMode;

    public GboardCustomStickersFeature(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    public void setPendingAutoLaunchMode(String mode) {
        this.pendingAutoLaunchMode = mode;
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
        Context context = host != null && host.getContext() != null ? host.getContext() : appContext;
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
                () -> promptImportImageSticker(host)));
        studioRows.add(new GboardPatchesSettingsContract.CommandRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_gif_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_gif_summary),
                true,
                () -> promptImportGifSticker(host)));

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
                libraryRows.add(new GboardPatchesSettingsContract.NavigationRow(
                        displayTitleFor(item),
                        item.getFormattedSummary(),
                        true,
                        () -> showStickerActionsDialog(host, item)));
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
                    GboardPatchesSettingsContract.refresh(host);
                }));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_features_menu_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_features_menu_summary),
                enabled,
                showInMenu,
                value -> {
                    GboardCustomStickerSettings.writeShowInFeaturesMenu(prefs, value);
                    GboardPatchesSettingsContract.refresh(host);
                }));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_auto_bg_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_auto_bg_summary),
                enabled,
                autoRemoveBg,
                value -> {
                    GboardCustomStickerSettings.writeAutoRemoveBg(prefs, value);
                    GboardPatchesSettingsContract.refresh(host);
                }));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_outline_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_outline_summary),
                enabled,
                outlineEnabled,
                value -> {
                    GboardCustomStickerSettings.writeOutlineEnabled(prefs, value);
                    GboardPatchesSettingsContract.refresh(host);
                }));
        prefRows.add(new GboardPatchesSettingsContract.SelectorRow(
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_title),
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_summary),
                GboardCustomStickerSettings.formatToleranceLabel(tolerance),
                enabled,
                () -> showToleranceSelectorDialog(host, prefs, tolerance)));
        prefRows.add(new GboardPatchesSettingsContract.ToggleRow(
                "Copy sticker to clipboard on send",
                "Also copy the sticker URI to the clipboard when inserting into an app",
                enabled,
                copyToClipboard,
                value -> {
                    GboardCustomStickerSettings.writeCopyToClipboardOnSend(prefs, value);
                    GboardPatchesSettingsContract.refresh(host);
                }));

        if (!stickers.isEmpty()) {
            prefRows.add(new GboardPatchesSettingsContract.DangerRow(
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_title),
                    GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_summary),
                    true,
                    () -> {
                        GboardCustomStickerRepository.clearAllStickers(context);
                        GboardPatchesSettingsContract.showMessage(host, "All custom stickers deleted.");
                        GboardPatchesSettingsContract.refresh(host);
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
                "%d total (%d PNG, %d GIF • %d with background removed • %s)",
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
        if (host == null) {
            return;
        }
        Context context = host.getContext() != null ? host.getContext() : appContext;
        String[] labels = new String[] {
                "Low (18%) — Strict edge matching",
                "Medium (32%) — Recommended default",
                "High (48%) — Textured or gradient backgrounds",
                "Aggressive (64%) — High-contrast subjects"
        };
        String[] values = new String[] { "18", "32", "48", "64" };
        GboardPatchesSettingsContract.showChoiceDialog(
                host,
                GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_title),
                labels,
                values,
                Integer.toString(currentTolerance),
                "Custom (1–100)...",
                () -> GboardPatchesSettingsContract.showPositiveIntegerDialog(
                        host,
                        GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_tolerance_title),
                        "1 - 100",
                        currentTolerance,
                        val -> {
                            GboardCustomStickerSettings.writeBgTolerance(prefs, val);
                            GboardPatchesSettingsContract.refresh(host);
                        }),
                selected -> {
                    try {
                        int parsed = Integer.parseInt(selected);
                        GboardCustomStickerSettings.writeBgTolerance(prefs, parsed);
                        GboardPatchesSettingsContract.refresh(host);
                    } catch (NumberFormatException ignored) {
                    }
                });
    }

    public void promptImportImageSticker(GboardPatchesSettingsContract.FeatureHost host) {
        if (host == null) {
            return;
        }
        GboardPatchesSettingsContract.openBinaryDocument(host, IMAGE_MIME_TYPES, document -> {
            if (document == null || document.getData() == null || document.getData().length == 0) {
                GboardPatchesSettingsContract.showMessage(host, "Failed to read image file.");
                return;
            }
            byte[] data = document.getData();
            String baseName = stripExtension(document.getDisplayName(), "Sticker");
            if (GboardStickerImageProcessor.isGifBytes(data)) {
                showGifStickerStudioDialog(host, baseName, data);
            } else {
                showImageStickerStudioDialog(host, baseName, data);
            }
        });
    }

    public void promptImportGifSticker(GboardPatchesSettingsContract.FeatureHost host) {
        if (host == null) {
            return;
        }
        GboardPatchesSettingsContract.openBinaryDocument(host, GIF_MIME_TYPES, document -> {
            if (document == null || document.getData() == null || document.getData().length == 0) {
                GboardPatchesSettingsContract.showMessage(host, "Failed to read GIF file.");
                return;
            }
            byte[] data = document.getData();
            String baseName = stripExtension(document.getDisplayName(), "GIF Sticker");
            if (GboardStickerImageProcessor.isGifBytes(data)) {
                showGifStickerStudioDialog(host, baseName, data);
            } else {
                showImageStickerStudioDialog(host, baseName, data);
            }
        });
    }

    private void showImageStickerStudioDialog(
            final GboardPatchesSettingsContract.FeatureHost host,
            final String initialName,
            final byte[] rawImageBytes) {
        if (host == null || rawImageBytes == null) {
            return;
        }
        final Context context = host.getContext();
        if (context == null) {
            return;
        }
        final Bitmap decodedSource = BitmapFactory.decodeByteArray(rawImageBytes, 0, rawImageBytes.length);
        if (decodedSource == null || decodedSource.getWidth() <= 0 || decodedSource.getHeight() <= 0) {
            GboardPatchesSettingsContract.showMessage(host, "Unable to decode selected image.");
            return;
        }

        final Bitmap studioSource = scaleDownIfNeeded(decodedSource, 512);
        final SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);

        GboardPatchesSettingsContract.showManagedDialog(host, onDismiss -> {
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
                    dp(context, 220));
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
            hintRowLp.bottomMargin = dp(context, 12);
            colorHintRow.setLayoutParams(hintRowLp);

            final TextView colorModeLabel = new TextView(context);
            colorModeLabel.setText("Tip: Tap background in preview to sample a target color");
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
            nameLp.bottomMargin = dp(context, 10);
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

            final Switch removeBgSwitch = addStudioSwitchRow(
                    context,
                    root,
                    "Remove background",
                    "Automatically isolate foreground subject",
                    removeBgState[0]);

            final Switch interiorHolesSwitch = addStudioSwitchRow(
                    context,
                    root,
                    "Remove interior holes",
                    "Also clear matching background colors inside closed loops",
                    interiorHolesState[0]);
            interiorHolesSwitch.setEnabled(removeBgState[0]);

            final TextView toleranceLabel = new TextView(context);
            toleranceLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            toleranceLabel.setTypeface(Typeface.DEFAULT_BOLD);
            toleranceLabel.setPadding(0, dp(context, 6), 0, dp(context, 2));
            root.addView(toleranceLabel);

            final SeekBar toleranceSeekBar = new SeekBar(context);
            toleranceSeekBar.setMax(85);
            toleranceSeekBar.setProgress(Math.max(5, Math.min(85, toleranceState[0])));
            toleranceSeekBar.setEnabled(removeBgState[0]);
            LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            seekLp.bottomMargin = dp(context, 6);
            toleranceSeekBar.setLayoutParams(seekLp);
            root.addView(toleranceSeekBar);

            final Switch outlineSwitch = addStudioSwitchRow(
                    context,
                    root,
                    "Add white sticker border",
                    "Draw a smooth die-cut outline around the sticker",
                    outlineState[0]);

            final Switch trimSwitch = addStudioSwitchRow(
                    context,
                    root,
                    "Auto-trim transparent margins",
                    "Crop empty space and maximize sticker size",
                    trimState[0]);

            final Runnable updatePreview = () -> {
                toleranceLabel.setText("Background sensitivity: " + toleranceState[0] + "%");
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
            };

            removeBgSwitch.setOnCheckedChangeListener((btn, checked) -> {
                removeBgState[0] = checked;
                interiorHolesSwitch.setEnabled(checked);
                toleranceSeekBar.setEnabled(checked);
                updatePreview.run();
            });
            interiorHolesSwitch.setOnCheckedChangeListener((btn, checked) -> {
                interiorHolesState[0] = checked;
                updatePreview.run();
            });
            outlineSwitch.setOnCheckedChangeListener((btn, checked) -> {
                outlineState[0] = checked;
                updatePreview.run();
            });
            trimSwitch.setOnCheckedChangeListener((btn, checked) -> {
                trimState[0] = checked;
                updatePreview.run();
            });
            toleranceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    toleranceState[0] = Math.max(5, progress);
                    toleranceLabel.setText("Background sensitivity: " + toleranceState[0] + "%");
                    if (fromUser) {
                        updatePreview.run();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    updatePreview.run();
                }
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
                        removeBgSwitch.setChecked(true);
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
                colorModeLabel.setText("Tip: Tap background in preview to sample a target color");
                resetColorButton.setVisibility(View.GONE);
                updatePreview.run();
            });

            updatePreview.run();

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_image_title))
                    .setView(scrollView)
                    .setNegativeButton(GboardSettingsText.get(context, R.string.gboard_patches_dialog_cancel), null)
                    .setPositiveButton("Save Sticker", (d, which) -> {
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
                            CustomStickerItem saved = GboardCustomStickerRepository.saveProcessedSticker(
                                    context, chosenName, result);
                            if (saved != null) {
                                GboardPatchesSettingsContract.showMessage(host, "Custom sticker saved!");
                                GboardPatchesSettingsContract.refresh(host);
                            } else {
                                GboardPatchesSettingsContract.showMessage(host, "Failed to save custom sticker.");
                            }
                        } catch (IOException e) {
                            GboardPatchesSettingsContract.showMessage(host, "Failed to save custom sticker.");
                        }
                    })
                    .create();
            dialog.setOnDismissListener(ignored -> onDismiss.run());
            dialog.show();
            return true;
        });
    }

    private void showGifStickerStudioDialog(
            final GboardPatchesSettingsContract.FeatureHost host,
            final String initialName,
            final byte[] rawGifBytes) {
        if (host == null || rawGifBytes == null) {
            return;
        }
        final Context context = host.getContext();
        if (context == null) {
            return;
        }
        final SharedPreferences prefs = GboardCustomStickerSettings.preferences(context);

        GboardPatchesSettingsContract.showManagedDialog(host, onDismiss -> {
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
                    dp(context, 210));
            previewFrameLp.bottomMargin = dp(context, 12);
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

            final Switch removeGifBgSwitch = addStudioSwitchRow(
                    context,
                    root,
                    "Make solid GIF background transparent",
                    "Detects background color in the GIF palette and marks it transparent while keeping animation",
                    removeGifBgState[0]);

            final TextView toleranceLabel = new TextView(context);
            toleranceLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            toleranceLabel.setTypeface(Typeface.DEFAULT_BOLD);
            toleranceLabel.setPadding(0, dp(context, 6), 0, dp(context, 2));
            root.addView(toleranceLabel);

            final SeekBar toleranceSeekBar = new SeekBar(context);
            toleranceSeekBar.setMax(75);
            toleranceSeekBar.setProgress(Math.max(5, Math.min(75, gifToleranceState[0])));
            toleranceSeekBar.setEnabled(removeGifBgState[0]);
            root.addView(toleranceSeekBar);

            final Runnable updateGifPreview = () -> {
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
            };

            removeGifBgSwitch.setOnCheckedChangeListener((btn, checked) -> {
                removeGifBgState[0] = checked;
                toleranceSeekBar.setEnabled(checked);
                updateGifPreview.run();
            });
            toleranceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    gifToleranceState[0] = Math.max(5, progress);
                    toleranceLabel.setText("Palette color tolerance: " + gifToleranceState[0] + "%");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    updateGifPreview.run();
                }
            });

            updateGifPreview.run();

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_create_gif_title))
                    .setView(scrollView)
                    .setNegativeButton(GboardSettingsText.get(context, R.string.gboard_patches_dialog_cancel), null)
                    .setPositiveButton("Save GIF Sticker", (d, which) -> {
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
                            CustomStickerItem saved = GboardCustomStickerRepository.saveProcessedSticker(
                                    context, chosenName, result);
                            if (saved != null) {
                                GboardPatchesSettingsContract.showMessage(host, "Custom GIF sticker saved!");
                                GboardPatchesSettingsContract.refresh(host);
                            } else {
                                GboardPatchesSettingsContract.showMessage(host, "Failed to save GIF sticker.");
                            }
                        } catch (IOException e) {
                            GboardPatchesSettingsContract.showMessage(host, "Failed to save GIF sticker.");
                        }
                    })
                    .create();
            dialog.setOnDismissListener(ignored -> onDismiss.run());
            dialog.show();
            return true;
        });
    }

    private void showStickerActionsDialog(
            final GboardPatchesSettingsContract.FeatureHost host,
            final CustomStickerItem item) {
        if (host == null || item == null) {
            return;
        }
        final Context context = host.getContext();
        if (context == null) {
            return;
        }
        byte[] loadedBytes = null;
        try {
            loadedBytes = GboardCustomStickerRepository.readStickerBytes(context, item);
        } catch (IOException ignored) {
        }
        final byte[] stickerBytes = loadedBytes;

        GboardPatchesSettingsContract.showManagedDialog(host, onDismiss -> {
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
                    "Insert / Copy to Clipboard",
                    true,
                    false,
                    () -> {
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        GboardCustomStickerRuntime.commitSticker(context, item);
                    });

            addActionSheetButton(
                    context,
                    root,
                    "Share Sticker",
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
                                    host,
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
                        GboardPatchesSettingsContract.refresh(host);
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
                                host,
                                "Rename Sticker",
                                "Sticker Name",
                                item.getName(),
                                newName -> {
                                    GboardCustomStickerRepository.renameSticker(context, item.getId(), newName);
                                    GboardPatchesSettingsContract.refresh(host);
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
                        GboardPatchesSettingsContract.showMessage(host, "Sticker deleted.");
                        GboardPatchesSettingsContract.refresh(host);
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

    private void showClearAllConfirmationDialog(final GboardPatchesSettingsContract.FeatureHost host) {
        if (host == null) {
            return;
        }
        final Context context = host.getContext();
        if (context == null) {
            return;
        }
        GboardPatchesSettingsContract.showManagedDialog(host, onDismiss -> {
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_confirm_title))
                    .setMessage(GboardSettingsText.get(context, R.string.gboard_patches_custom_stickers_clear_all_confirm_message))
                    .setNegativeButton(GboardSettingsText.get(context, R.string.gboard_patches_dialog_cancel), null)
                    .setPositiveButton("Delete All", (d, w) -> {
                        GboardCustomStickerRepository.clearAllStickers(context);
                        GboardPatchesSettingsContract.showMessage(host, "All custom stickers deleted.");
                        GboardPatchesSettingsContract.refresh(host);
                    })
                    .create();
            dialog.setOnDismissListener(ignored -> onDismiss.run());
            dialog.show();
            return true;
        });
    }

    private static Switch addStudioSwitchRow(
            Context context,
            LinearLayout parent,
            String title,
            String subtitle,
            boolean initialChecked) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 6), 0, dp(context, 6));

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

        final Switch sw = new Switch(context);
        sw.setChecked(initialChecked);
        row.addView(textCol);
        row.addView(sw);
        row.setOnClickListener(v -> {
            if (sw.isEnabled()) {
                sw.setChecked(!sw.isChecked());
            }
        });
        parent.addView(row);
        return sw;
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
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
    }

    private static Bitmap scaleDownIfNeeded(Bitmap source, int maxDimension) {
        if (source == null) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= maxDimension && h <= maxDimension) {
            return source;
        }
        float scale = Math.min((float) maxDimension / w, (float) maxDimension / h);
        int targetW = Math.max(1, Math.round(w * scale));
        int targetH = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(source, targetW, targetH, true);
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
