package com.akshaykadam.pixelboard.extension.stickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputContentInfo;
import android.widget.FrameLayout;
import com.akshaykadam.pixelboard.extension.settings.GboardPatchesSettingsContract;
import com.akshaykadam.pixelboard.extension.settings.GboardPatchesSettingsProvider;
import com.akshaykadam.pixelboard.extension.settings.GboardPatchesSettingsTestHost;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GboardCustomStickersEndToEndTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        GboardCustomStickerRepository.clearAllStickers(context);
        GboardCustomStickerRuntime.resetForTesting();
    }

    @After
    public void tearDown() {
        GboardCustomStickerRepository.clearAllStickers(context);
        GboardCustomStickerRuntime.resetForTesting();
    }

    @Test
    public void testRepository_createPersistFavoriteRenameAndDeleteStickers() throws Exception {
        byte[] pngBytes = createSamplePngBytes();
        CustomStickerItem imageSticker = GboardCustomStickerRepository.createStickerFromImageBytes(
                context,
                pngBytes,
                "Cat Cutout",
                new GboardStickerImageProcessor.StickerOptions(
                        true, 32, true, null, true, Color.WHITE, 4, true, 512));
        assertNotNull(imageSticker);
        assertEquals("Cat Cutout", imageSticker.getName());
        assertEquals("image/png", imageSticker.getMimeType());
        assertFalse(imageSticker.isAnimatedGif());
        assertTrue(imageSticker.isBackgroundRemoved());
        assertTrue(imageSticker.isOutlineApplied());

        byte[] gifBytes = createMinimalGifBytes();
        CustomStickerItem gifSticker = GboardCustomStickerRepository.createStickerFromGifBytes(
                context,
                gifBytes,
                "Party Parrot",
                true,
                30,
                null);
        assertNotNull(gifSticker);
        assertEquals("Party Parrot", gifSticker.getName());
        assertEquals("image/gif", gifSticker.getMimeType());
        assertTrue(gifSticker.isAnimatedGif());

        List<CustomStickerItem> reloaded = GboardCustomStickerRepository.listStickers(context);
        assertEquals(2, reloaded.size());

        // Favorite the first created sticker and verify it moves to index 0
        assertNotNull(GboardCustomStickerRepository.toggleFavorite(context, imageSticker.getId()));
        List<CustomStickerItem> afterFav = GboardCustomStickerRepository.listStickers(context);
        assertEquals(imageSticker.getId(), afterFav.get(0).getId());
        assertTrue(afterFav.get(0).isFavorite());

        // Rename sticker
        assertNotNull(GboardCustomStickerRepository.renameSticker(
                context, imageSticker.getId(), "Super Cat"));
        CustomStickerItem renamed = GboardCustomStickerRepository.getStickerById(
                context, imageSticker.getId());
        assertNotNull(renamed);
        assertEquals("Super Cat", renamed.getName());

        // Path traversal protection
        assertNull(GboardCustomStickerRepository.getStickerFile(context, "../secret.png"));

        // Delete sticker
        assertTrue(GboardCustomStickerRepository.deleteSticker(context, imageSticker.getId()));
        assertEquals(1, GboardCustomStickerRepository.listStickers(context).size());
    }

    @Test
    public void testContentProvider_servesStickerMimeQueryAndFileDescriptor() throws Exception {
        byte[] pngBytes = createSamplePngBytes();
        CustomStickerItem sticker = GboardCustomStickerRepository.createStickerFromImageBytes(
                context,
                pngBytes,
                "Test Sticker",
                GboardStickerImageProcessor.StickerOptions.defaults());
        assertNotNull(sticker);

        GboardPatchesSettingsProvider provider =
                Robolectric.setupContentProvider(
                        GboardPatchesSettingsProvider.class,
                        context.getPackageName() + ".gboard_patches");

        Uri uri = GboardCustomStickerRepository.buildStickerContentUri(context, sticker);
        assertNotNull(uri);

        assertEquals("image/png", provider.getType(uri));

        try (Cursor cursor = provider.query(uri, null, null, null, null)) {
            assertNotNull(cursor);
            assertTrue(cursor.moveToFirst());
            int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
            assertTrue(nameIdx >= 0);
            assertTrue(sizeIdx >= 0);
            assertTrue(cursor.getLong(sizeIdx) > 0L);
        }

        try (ParcelFileDescriptor pfd = provider.openFile(uri, "r")) {
            assertNotNull(pfd);
            assertTrue(pfd.getStatSize() > 0L);
        }
    }

    @Test
    public void testRuntime_commitsStickerToActiveInputConnectionAndInjectsPanelBar() throws Exception {
        byte[] pngBytes = createSamplePngBytes();
        CustomStickerItem sticker = GboardCustomStickerRepository.createStickerFromImageBytes(
                context,
                pngBytes,
                "Smile Cutout",
                GboardStickerImageProcessor.StickerOptions.defaults());
        assertNotNull(sticker);

        final AtomicReference<InputContentInfo> committedInfo = new AtomicReference<>();
        View dummyEditor = new View(context);
        BaseInputConnection ic = new BaseInputConnection(dummyEditor, true) {
            @Override
            public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
                committedInfo.set(inputContentInfo);
                return true;
            }
        };

        GboardCustomStickerRuntime.onStartInputView(context, null);
        GboardCustomStickerRuntime.onInputConnectionActive(ic);

        GboardCustomStickerRuntime.CommitResult result =
                GboardCustomStickerRuntime.commitSticker(context, sticker);
        assertEquals(GboardCustomStickerRuntime.CommitResult.COMMITTED_DIRECTLY, result);
        assertNotNull(committedInfo.get());
        assertEquals(
                GboardCustomStickerRepository.buildStickerContentUri(context, sticker),
                committedInfo.get().getContentUri());

        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        assertNotNull(cm);
        ClipData primaryClip = cm.getPrimaryClip();
        assertNotNull(primaryClip);
        assertEquals(1, primaryClip.getItemCount());

        // Verify AccessPointsPanel injection when enabled
        GboardCustomStickerRuntime.forceInjectPanelForTesting = true;
        FrameLayout fakeAccessPointsPanel = new FrameLayout(context);
        GboardCustomStickerRuntime.onAccessPointsPanelReady(fakeAccessPointsPanel);
        assertNotNull(fakeAccessPointsPanel.findViewWithTag(GboardCustomStickerRuntime.STICKER_BAR_VIEW_TAG));
    }

    @Test
    public void testFeature_buildsSettingsScreenWithStudioLibraryAndPreferences() throws Exception {
        GboardCustomStickersFeature feature = new GboardCustomStickersFeature(context);
        assertTrue(feature.isAvailable(context));
        assertEquals("Custom Stickers", feature.getEntryTitle());

        GboardPatchesSettingsTestHost host = new GboardPatchesSettingsTestHost() {
            @Override
            public Context getContext() {
                return context;
            }
        };

        // Empty state screen
        GboardPatchesSettingsContract.Screen emptyScreen = feature.buildScreen(host);
        assertNotNull(emptyScreen);
        assertEquals("Custom Stickers", emptyScreen.getToolbarTitle());
        assertEquals(3, emptyScreen.getSections().size());

        // Add a sticker and rebuild screen
        GboardCustomStickerRepository.createStickerFromImageBytes(
                context,
                createSamplePngBytes(),
                "Hero Sticker",
                GboardStickerImageProcessor.StickerOptions.defaults());

        GboardPatchesSettingsContract.Screen populatedScreen = feature.buildScreen(host);
        assertEquals(3, populatedScreen.getSections().size());
        assertTrue(populatedScreen.getSections().get(1).getTitle().contains("(1)"));
    }

    private static byte[] createSamplePngBytes() {
        Bitmap bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFFFFFFFF);
        for (int y = 6; y < 18; y++) {
            for (int x = 6; x < 18; x++) {
                bmp.setPixel(x, y, 0xFF3366FF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    private static byte[] createMinimalGifBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[] { 0x08, 0x00, 0x08, 0x00, (byte) 0x80, 0x00, 0x00 });
        out.write(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF });
        out.write(new byte[] { 0x2C, 0x00, 0x00, 0x00, 0x00, 0x08, 0x00, 0x08, 0x00, 0x00 });
        out.write(new byte[] { 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B });
        return out.toByteArray();
    }
}
