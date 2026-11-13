/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.extension.stickers;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.akshaykadam.pixelboard.extension.settings.GboardPatchesSettingsActivity;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Runtime bridge for PixelBoard Custom Stickers:
 * <ul>
 *   <li>Tracks active {@link InputConnection} and {@link EditorInfo} during keyboard sessions.</li>
 *   <li>Commits custom static cutout stickers and animated GIF stickers directly into target apps
 *       via {@link InputConnection#commitContent} with automatic URI permission grants and
 *       clipboard fallback.</li>
 *   <li>Injects a quick-access Custom Stickers bar &amp; in-keyboard sticker picker drawer into
 *       Gboard's Features Menu ({@code AccessPointsPanel} / {@code AccessPointsPanelV2}).</li>
 * </ul>
 */
public final class GboardCustomStickerRuntime {
    private static final String TAG = "PixelBoardStickers";
    public static final String STICKER_BAR_VIEW_TAG = "pixelboard_custom_stickers_bar";
    public static final String STICKER_DRAWER_VIEW_TAG = "pixelboard_custom_stickers_drawer";

    public static final String ACTION_CUSTOM_STICKERS =
            "com.akshaykadam.pixelboard.action.CUSTOM_STICKERS";
    public static final String EXTRA_CREATE_MODE =
            "com.akshaykadam.pixelboard.extra.STICKER_CREATE_MODE";
    public static final String CREATE_MODE_IMAGE = "image";
    public static final String CREATE_MODE_GIF = "gif";

    public enum CommitResult {
        COMMITTED_DIRECTLY,
        COPIED_TO_CLIPBOARD,
        FAILED
    }

    private static volatile WeakReference<Context> activeContextRef = new WeakReference<Context>(null);
    private static volatile WeakReference<InputConnection> activeInputConnectionRef =
            new WeakReference<InputConnection>(null);
    private static volatile EditorInfo activeEditorInfo;
    public static boolean forceInjectPanelForTesting = false;

    private GboardCustomStickerRuntime() {
    }

    public static void resetForTesting() {
        activeContextRef = new WeakReference<Context>(null);
        activeInputConnectionRef = new WeakReference<InputConnection>(null);
        activeEditorInfo = null;
        forceInjectPanelForTesting = false;
    }

    /**
     * Called from bytecode hook on InputConnection wrapper methods to capture the active InputConnection.
     */
    public static void onInputConnectionActive(Object inputConnectionCandidate) {
        if (inputConnectionCandidate instanceof InputConnection) {
            activeInputConnectionRef =
                    new WeakReference<InputConnection>((InputConnection) inputConnectionCandidate);
        }
    }

    /**
     * Called when Gboard starts an input view session.
     */
    public static void onStartInputView(Context context, Object editorInfoCandidate) {
        if (context != null) {
            Context appCtx = context.getApplicationContext();
            activeContextRef = new WeakReference<Context>(appCtx != null ? appCtx : context);
        }
        if (editorInfoCandidate instanceof EditorInfo) {
            activeEditorInfo = (EditorInfo) editorInfoCandidate;
        }
    }

    public static InputConnection getActiveInputConnection() {
        return activeInputConnectionRef.get();
    }

    public static EditorInfo getActiveEditorInfo() {
        return activeEditorInfo;
    }

    /**
     * Commits a custom sticker or GIF to the currently active editor via {@code commitContent},
     * and copies its content URI to the system clipboard if configured or needed as fallback.
     */
    public static CommitResult commitSticker(Context context, CustomStickerItem item) {
        Context effectiveContext = resolveContext(context);
        if (effectiveContext == null || item == null) {
            return CommitResult.FAILED;
        }
        if (!GboardCustomStickerSettings.isEnabled(effectiveContext)) {
            return CommitResult.FAILED;
        }

        Uri contentUri = GboardCustomStickerRepository.buildStickerContentUri(effectiveContext, item);
        if (contentUri == null) {
            return CommitResult.FAILED;
        }

        EditorInfo editorInfo = activeEditorInfo;
        if (editorInfo != null && editorInfo.packageName != null && !editorInfo.packageName.isEmpty()) {
            try {
                effectiveContext.grantUriPermission(
                        editorInfo.packageName,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
        }

        boolean committedDirectly = false;
        InputConnection ic = activeInputConnectionRef.get();
        if (ic != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                ClipDescription description = new ClipDescription(
                        item.getName(),
                        new String[] { item.getMimeType() });
                InputContentInfo contentInfo = new InputContentInfo(contentUri, description, null);
                int flags = InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
                committedDirectly = ic.commitContent(contentInfo, flags, null);
            } catch (Throwable t) {
                try {
                    Log.w(TAG, "Direct commitContent failed, falling back to clipboard", t);
                } catch (Throwable ignored) {
                }
            }
        }

        boolean copyToClipboard = GboardCustomStickerSettings.readCopyToClipboardOnSend(
                GboardCustomStickerSettings.preferences(effectiveContext));
        boolean copied = false;
        if (!committedDirectly || copyToClipboard) {
            copied = copyStickerUriToClipboard(effectiveContext, item, contentUri);
        }

        if (committedDirectly) {
            return CommitResult.COMMITTED_DIRECTLY;
        }
        if (copied) {
            return CommitResult.COPIED_TO_CLIPBOARD;
        }
        return CommitResult.FAILED;
    }

    public static boolean copyStickerUriToClipboard(
            Context context,
            CustomStickerItem item,
            Uri contentUri) {
        if (context == null || item == null || contentUri == null) {
            return false;
        }
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            ClipData clip = ClipData.newUri(context.getContentResolver(), item.getName(), contentUri);
            clipboard.setPrimaryClip(clip);
            return true;
        } catch (Throwable t) {
            try {
                Log.w(TAG, "Failed to copy sticker URI to clipboard", t);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    public static boolean shareSticker(Context context, CustomStickerItem item) {
        Context effectiveContext = resolveContext(context);
        if (effectiveContext == null || item == null) {
            return false;
        }
        Uri contentUri = GboardCustomStickerRepository.buildStickerContentUri(effectiveContext, item);
        if (contentUri == null) {
            return false;
        }
        try {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType(item.getMimeType());
            sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            sendIntent.setClipData(ClipData.newRawUri(item.getName(), contentUri));
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(sendIntent, item.getName());
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            effectiveContext.startActivity(chooser);
            return true;
        } catch (Throwable t) {
            try {
                Log.w(TAG, "Failed to share custom sticker", t);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    public static void launchStickerStudio(Context context, String createMode) {
        Context effectiveContext = resolveContext(context);
        if (effectiveContext == null) {
            return;
        }
        try {
            Intent intent = new Intent(ACTION_CUSTOM_STICKERS);
            intent.setComponent(new ComponentName(
                    effectiveContext.getPackageName(),
                    GboardPatchesSettingsActivity.class.getName()));
            if (createMode != null && !createMode.isEmpty()) {
                intent.putExtra(EXTRA_CREATE_MODE, createMode);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            effectiveContext.startActivity(intent);
        } catch (Throwable t) {
            try {
                Log.w(TAG, "Failed to launch Custom Sticker Studio", t);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Called when Gboard's Features Menu ({@code AccessPointsPanel} / {@code AccessPointsPanelV2})
     * is inflated or becomes visible. Injects a compact Custom Stickers bar &amp; expandable
     * in-keyboard sticker picker drawer.
     */
    public static void onAccessPointsPanelReady(View panelView) {
        if (!(panelView instanceof ViewGroup)) {
            return;
        }
        ViewGroup panelGroup = (ViewGroup) panelView;
        Context context = panelGroup.getContext();
        if (context == null) {
            return;
        }
        if (!shouldInjectIntoPanel(panelGroup)) {
            return;
        }
        if (!GboardCustomStickerSettings.isEnabled(context)
                || !GboardCustomStickerSettings.isShowInFeaturesMenuEnabled(context)) {
            View existingBar = panelGroup.findViewWithTag(STICKER_BAR_VIEW_TAG);
            if (existingBar != null) {
                panelGroup.removeView(existingBar);
            }
            return;
        }

        try {
            View existingBar = panelGroup.findViewWithTag(STICKER_BAR_VIEW_TAG);
            if (existingBar != null) {
                refreshStickerBarContent(context, (ViewGroup) existingBar);
                return;
            }
            LinearLayout container = buildInKeyboardStickerContainer(context);
            panelGroup.addView(container);
        } catch (Throwable t) {
            try {
                Log.w(TAG, "Failed to inject Custom Stickers bar into AccessPointsPanel", t);
            } catch (Throwable ignored) {
            }
        }
    }

    static boolean shouldInjectIntoPanel(View panelView) {
        if (panelView == null) {
            return false;
        }
        if (forceInjectPanelForTesting) {
            return true;
        }
        String className = panelView.getClass().getName();
        return className.contains("AccessPointsPanel");
    }

    private static LinearLayout buildInKeyboardStickerContainer(Context context) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setTag(STICKER_BAR_VIEW_TAG);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.BOTTOM);

        FrameLayout.LayoutParams wrapperParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        int marginH = dp(context, 10);
        int marginB = dp(context, 6);
        wrapperParams.setMargins(marginH, dp(context, 4), marginH, marginB);
        wrapper.setLayoutParams(wrapperParams);

        refreshStickerBarContent(context, wrapper);
        return wrapper;
    }

    private static void refreshStickerBarContent(Context context, ViewGroup wrapper) {
        wrapper.removeAllViews();
        List<CustomStickerItem> stickers = GboardCustomStickerRepository.listStickers(context);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xEE1E2228);
        cardBg.setCornerRadius(dp(context, 16));
        cardBg.setStroke(dp(context, 1), 0x33FFFFFF);
        card.setBackground(cardBg);
        int pad = dp(context, 8);
        card.setPadding(pad, dp(context, 6), pad, dp(context, 6));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Top header bar: "Custom Stickers (N)" + "+ Image" + "+ GIF"
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout drawerContainer = new LinearLayout(context);
        drawerContainer.setTag(STICKER_DRAWER_VIEW_TAG);
        drawerContainer.setOrientation(LinearLayout.VERTICAL);
        drawerContainer.setVisibility(View.GONE);

        TextView titleBtn = new TextView(context);
        titleBtn.setText("Custom Stickers (" + stickers.size() + ")");
        titleBtn.setTextColor(0xFFE8EAED);
        titleBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        titleBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleBtn.setSingleLine(true);
        titleBtn.setEllipsize(TextUtils.TruncateAt.END);
        titleBtn.setPadding(dp(context, 6), dp(context, 4), dp(context, 6), dp(context, 4));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f);
        titleBtn.setLayoutParams(titleParams);
        titleBtn.setOnClickListener(v -> {
            if (stickers.isEmpty()) {
                launchStickerStudio(context, null);
            } else {
                boolean show = drawerContainer.getVisibility() != View.VISIBLE;
                drawerContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });

        TextView addImageChip = buildActionChip(context, "+ Image", 0xFF8AB4F8, 0x268AB4F8);
        addImageChip.setOnClickListener(v -> launchStickerStudio(context, CREATE_MODE_IMAGE));

        TextView addGifChip = buildActionChip(context, "+ GIF", 0xFF81C995, 0x2681C995);
        LinearLayout.LayoutParams gifParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gifParams.leftMargin = dp(context, 6);
        addGifChip.setLayoutParams(gifParams);
        addGifChip.setOnClickListener(v -> launchStickerStudio(context, CREATE_MODE_GIF));

        TextView manageChip = buildActionChip(context, "Studio", 0xFFD2E3FC, 0x1FFFFFFF);
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        manageParams.leftMargin = dp(context, 6);
        manageChip.setLayoutParams(manageParams);
        manageChip.setOnClickListener(v -> launchStickerStudio(context, null));

        headerRow.addView(titleBtn);
        headerRow.addView(addImageChip);
        headerRow.addView(addGifChip);
        headerRow.addView(manageChip);
        card.addView(headerRow);

        if (!stickers.isEmpty()) {
            HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            scrollParams.topMargin = dp(context, 6);
            scroll.setLayoutParams(scrollParams);

            LinearLayout strip = new LinearLayout(context);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setGravity(Gravity.CENTER_VERTICAL);

            int maxInStrip = Math.min(20, stickers.size());
            for (int i = 0; i < maxInStrip; i++) {
                final CustomStickerItem item = stickers.get(i);
                View tile = buildStickerTileView(context, item);
                strip.addView(tile);
            }
            scroll.addView(strip);
            drawerContainer.addView(scroll);
            card.addView(drawerContainer);
        }

        wrapper.addView(card);
    }

    private static View buildStickerTileView(Context context, final CustomStickerItem item) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        int tileSize = dp(context, 62);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(tileSize, tileSize);
        params.rightMargin = dp(context, 6);
        tile.setLayoutParams(params);

        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setColor(0x22FFFFFF);
        tileBg.setCornerRadius(dp(context, 12));
        tile.setBackground(tileBg);
        int pad = dp(context, 4);
        tile.setPadding(pad, pad, pad, pad);
        tile.setContentDescription(item.getName());

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        File file = GboardCustomStickerRepository.getStickerFile(context, item.getFileName());
        if (file != null && file.isFile()) {
            Bitmap thumb = decodeThumbnail(file, dp(context, 56));
            if (thumb != null) {
                imageView.setImageBitmap(thumb);
            }
        }
        tile.addView(imageView);

        tile.setOnClickListener(v -> {
            CommitResult res = commitSticker(context, item);
            String msg;
            if (res == CommitResult.COMMITTED_DIRECTLY) {
                msg = "Sticker inserted: " + item.getName();
            } else if (res == CommitResult.COPIED_TO_CLIPBOARD) {
                msg = "Sticker copied to clipboard: " + item.getName();
            } else {
                msg = "Unable to send sticker";
            }
            try {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
        tile.setOnLongClickListener(v -> {
            shareSticker(context, item);
            return true;
        });
        return tile;
    }

    private static Bitmap decodeThumbnail(File file, int targetSizePx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > targetSizePx * 2) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static TextView buildActionChip(Context context, String label, int textColor, int bgColor) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextColor(textColor);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        chip.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(context, 999));
        chip.setBackground(bg);
        return chip;
    }

    private static Context resolveContext(Context candidate) {
        if (candidate != null) {
            return candidate;
        }
        return activeContextRef.get();
    }

    private static int dp(Context context, float value) {
        if (context == null || context.getResources() == null) {
            return Math.round(value);
        }
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
