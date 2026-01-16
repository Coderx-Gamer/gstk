package org.gstk.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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

    public static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

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
