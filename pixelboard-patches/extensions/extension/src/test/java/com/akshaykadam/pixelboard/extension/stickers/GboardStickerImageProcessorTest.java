package com.akshaykadam.pixelboard.extension.stickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GboardStickerImageProcessorTest {

    @Test
    public void testRemoveBackgroundFromPixels_floodFillRemovesBorderBackgroundAndKeepsSubject() {
        int width = 40;
        int height = 40;
        int[] pixels = new int[width * height];
        int bgColor = 0xFF10A020; // green background
        int fgColor = 0xFFE03040; // red foreground square in center

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x >= 12 && x < 28 && y >= 12 && y < 28) {
                    pixels[y * width + x] = fgColor;
                } else {
                    pixels[y * width + x] = bgColor;
                }
            }
        }

        int[] out = GboardStickerImageProcessor.removeBackgroundFromPixels(
                pixels, width, height, 30, true, null);

        // Corners must be completely transparent
        assertEquals(0, (out[0] >>> 24) & 0xFF);
        assertEquals(0, (out[width - 1] >>> 24) & 0xFF);
        assertEquals(0, (out[(height - 1) * width] >>> 24) & 0xFF);

        // Center subject must remain opaque red
        int centerPixel = out[20 * width + 20];
        assertEquals(255, (centerPixel >>> 24) & 0xFF);
        assertEquals(fgColor, centerPixel);
    }

    @Test
    public void testRemoveBackgroundFromPixels_contiguousVsInteriorHoles() {
        int width = 32;
        int height = 32;
        int[] pixels = new int[width * height];
        int bgColor = 0xFFFFFFFF; // white background
        int ringColor = 0xFF2040D0; // blue ring enclosing an interior white hole

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean inOuterBox = x >= 6 && x < 26 && y >= 6 && y < 26;
                boolean inInnerHole = x >= 12 && x < 20 && y >= 12 && y < 20;
                if (inOuterBox && !inInnerHole) {
                    pixels[y * width + x] = ringColor;
                } else {
                    pixels[y * width + x] = bgColor;
                }
            }
        }

        // Contiguous-only mode preserves the enclosed white hole
        int[] contiguousOut = GboardStickerImageProcessor.removeBackgroundFromPixels(
                pixels, width, height, 28, true, null);
        assertEquals(0, (contiguousOut[0] >>> 24) & 0xFF);
        assertEquals(255, (contiguousOut[16 * width + 16] >>> 24) & 0xFF);

        // Non-contiguous mode removes the interior hole as well
        int[] allMatchingOut = GboardStickerImageProcessor.removeBackgroundFromPixels(
                pixels, width, height, 28, false, null);
        assertEquals(0, (allMatchingOut[0] >>> 24) & 0xFF);
        assertEquals(0, (allMatchingOut[16 * width + 16] >>> 24) & 0xFF);
        assertEquals(255, (allMatchingOut[8 * width + 8] >>> 24) & 0xFF);
    }

    @Test
    public void testApplyStickerOutline_addsOpaqueBorderAroundSubject() {
        int width = 30;
        int height = 30;
        int[] pixels = new int[width * height];
        for (int y = 10; y < 20; y++) {
            for (int x = 10; x < 20; x++) {
                pixels[y * width + x] = 0xFFFF0000;
            }
        }

        int[] outlined = GboardStickerImageProcessor.applyStickerOutline(
                pixels, width, height, Color.WHITE, 3);

        // Pixel 2px outside subject (x=8, y=15) was transparent, now has white outline
        int borderPixel = outlined[15 * width + 8];
        assertTrue(((borderPixel >>> 24) & 0xFF) > 200);
        assertEquals(255, (borderPixel >> 16) & 0xFF);
        assertEquals(255, (borderPixel >> 8) & 0xFF);
        assertEquals(255, borderPixel & 0xFF);

        // Far corner (x=0, y=0) remains transparent
        assertEquals(0, (outlined[0] >>> 24) & 0xFF);
    }

    @Test
    public void testProcessStaticBitmap_cropsAndAddsOutline() {
        Bitmap src = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        src.eraseColor(0xFF00FF00); // solid green background
        for (int y = 25; y < 55; y++) {
            for (int x = 25; x < 55; x++) {
                src.setPixel(x, y, 0xFF112233);
            }
        }

        GboardStickerImageProcessor.StickerOptions options =
                new GboardStickerImageProcessor.StickerOptions(
                        true,
                        34,
                        true,
                        null,
                        true,
                        Color.WHITE,
                        4,
                        true,
                        512);
        GboardStickerImageProcessor.ProcessedStickerResult result =
                GboardStickerImageProcessor.processStaticBitmap(src, options);
        assertNotNull(result);
        Bitmap preview = result.getPreviewBitmap();
        assertNotNull(preview);
        // Because transparent margins are trimmed, dimensions should be smaller than 80x80
        assertTrue(preview.getWidth() < 80);
        assertTrue(preview.getHeight() < 80);
        // Corner of cropped sticker should be transparent
        assertEquals(0, Color.alpha(preview.getPixel(0, 0)));
        // Center of cropped sticker should be opaque subject color
        int center = preview.getPixel(preview.getWidth() / 2, preview.getHeight() / 2);
        assertEquals(255, Color.alpha(center));
    }

    @Test
    public void testGifProcessing_detectsHeaderDimensionsAndInjectsTransparency() throws Exception {
        byte[] sampleGif = buildMinimalSolidGif(16, 12, 0x12, 0x34, 0x56);

        assertTrue(GboardStickerImageProcessor.isGifBytes(sampleGif));
        int[] dims = GboardStickerImageProcessor.extractGifDimensions(sampleGif);
        assertEquals(16, dims[0]);
        assertEquals(12, dims[1]);

        // Process without background removal
        GboardStickerImageProcessor.ProcessedStickerResult kept =
                GboardStickerImageProcessor.processGifBytes(sampleGif, false, 30, null);
        assertNotNull(kept);
        assertTrue(kept.isAnimatedGif());
        assertFalse(kept.isBackgroundRemoved());
        assertEquals("image/gif", kept.getMimeType());
        assertEquals(16, kept.getWidth());
        assertEquals(12, kept.getHeight());

        // Process with solid background transparency enabled
        GboardStickerImageProcessor.ProcessedStickerResult transparentGif =
                GboardStickerImageProcessor.processGifBytes(sampleGif, true, 30, null);
        assertNotNull(transparentGif);
        assertTrue(transparentGif.isAnimatedGif());
        assertTrue(transparentGif.isBackgroundRemoved());
        assertTrue(GboardStickerImageProcessor.isGifBytes(transparentGif.getEncodedBytes()));

        // Verify Graphic Control Extension (0x21 0xF9 0x04) has transparency bit 0x01 set
        byte[] outBytes = transparentGif.getEncodedBytes();
        boolean foundTransparentGce = false;
        for (int i = 0; i + 6 < outBytes.length; i++) {
            if ((outBytes[i] & 0xFF) == 0x21
                    && (outBytes[i + 1] & 0xFF) == 0xF9
                    && (outBytes[i + 2] & 0xFF) == 0x04) {
                int packed = outBytes[i + 3] & 0xFF;
                if ((packed & 0x01) != 0) {
                    foundTransparentGce = true;
                    break;
                }
            }
        }
        assertTrue("Expected Graphic Control Extension with transparency flag set", foundTransparentGce);
    }

    private static byte[] buildMinimalSolidGif(int width, int height, int bgR, int bgG, int bgB)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        // Logical Screen Descriptor
        out.write(width & 0xFF);
        out.write((width >> 8) & 0xFF);
        out.write(height & 0xFF);
        out.write((height >> 8) & 0xFF);
        out.write(0x80 | 0x01); // GCT present, 4 colors (2^(1+1) = 4)
        out.write(0); // background color index 0
        out.write(0); // pixel aspect ratio

        // Global Color Table (4 entries * 3 bytes = 12 bytes)
        out.write(new byte[] { (byte) bgR, (byte) bgG, (byte) bgB }); // color 0 (bg)
        out.write(new byte[] { (byte) 0xFF, (byte) 0x00, (byte) 0x00 }); // color 1 (fg)
        out.write(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0x00 }); // color 2
        out.write(new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0xFF }); // color 3

        // Graphic Control Extension
        out.write(new byte[] {
                0x21, (byte) 0xF9, 0x04,
                0x04, // no transparency initially
                0x0A, 0x00, // delay
                0x00, // transparent index
                0x00 // block terminator
        });

        // Image Descriptor
        out.write(0x2C);
        out.write(new byte[] { 0x00, 0x00, 0x00, 0x00 });
        out.write(width & 0xFF);
        out.write((width >> 8) & 0xFF);
        out.write(height & 0xFF);
        out.write((height >> 8) & 0xFF);
        out.write(0x00); // no local color table

        // LZW minimum code size + dummy image data block + block terminator
        out.write(0x02);
        out.write(0x02);
        out.write(new byte[] { 0x44, 0x01 });
        out.write(0x00);

        // Trailer
        out.write(0x3B);
        return out.toByteArray();
    }
}
