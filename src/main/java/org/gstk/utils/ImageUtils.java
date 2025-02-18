package org.gstk.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class ImageUtils {
    public static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89,
            (byte) 0x50,
            (byte) 0x4E,
            (byte) 0x47,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x1A,
            (byte) 0x0A
    };

    /**
     * Check for image magic.
     *
     * @param data The image bytes.
     * @param magic The magic bytes.
     * @return Whether the magic matches the data or not.
     */
    public static boolean doesMagicMatch(byte[] data, byte[] magic) {
        if (data == null || data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if image bytes are png format.
     *
     * @param data The image bytes.
     * @return Whether the data is png format or not.
     */
    public static boolean isPng(byte[] data) {
        return doesMagicMatch(data, PNG_MAGIC);
    }

    /**
     * Attempts to detect image type from bytes and convert it to png bytes if possible.
     *
     * @param data Unknown image bytes.
     * @return Png bytes or null if the image bytes are invalid.
     */
    public static byte[] convertBytesToPng(byte[] data) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            BufferedImage image = ImageIO.read(in);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);

            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}