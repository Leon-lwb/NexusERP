package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 印章预览图渲染器测试（纯 JDK，无 Spring 上下文）。
 *
 * <p>覆盖：等比缩放上限、小图不放大、PNG 合法性、水印确实落墨、透明通道保留、
 * 非法输入拒绝，以及<b>水印文案在当前运行环境确实可被所选字体绘制</b>
 * （CJK 缺字体时降级为 ASCII，见 {@link SealPreviewRenderer#watermarkText()}）。</p>
 *
 * @author nexus
 */
class SealPreviewRendererTest {

    /** 生成纯色 PNG。 */
    private static byte[] solidPng(int w, int h, Color color) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return toPng(img);
    }

    /** 生成随机噪声 PNG（压缩率低，用于「输出字节数更小」断言）。 */
    private static byte[] noisyPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(20260830L);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        return toPng(img);
    }

    /** 生成带透明背景 + 不透明方块的 ARGB PNG。 */
    private static byte[] transparentPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 30, 30));
        g.fillRect(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        return toPng(img);
    }

    private static byte[] toPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "png", bos), "测试夹具必须能写出 PNG");
        return bos.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("超大图：最长边压到 240px 且保持宽高比、字节数更小")
    void oversizedImageIsDownscaledKeepingAspectRatio() throws Exception {
        byte[] original = noisyPng(800, 600);

        byte[] preview = SealPreviewRenderer.renderPreview(original);

        BufferedImage img = decode(preview);
        assertNotNull(img, "输出必须是可解码的图片");
        assertEquals(SealPreviewRenderer.maxSide(), Math.max(img.getWidth(), img.getHeight()),
            "最长边应被压到上限");
        assertEquals(240, img.getWidth());
        assertEquals(180, img.getHeight());
        assertTrue(preview.length < original.length,
            "压缩后字节数应明显小于原图：" + preview.length + " vs " + original.length);
    }

    @Test
    @DisplayName("非正方形超长边：按最长边等比缩放")
    void aspectRatioPreservedForWideImage() throws Exception {
        BufferedImage img = decode(SealPreviewRenderer.renderPreview(solidPng(1000, 250, Color.BLUE)));
        assertEquals(240, img.getWidth());
        assertEquals(60, img.getHeight(), "1000x250 等比缩放后应为 240x60");
    }

    @Test
    @DisplayName("小图不放大：低于上限时保持原始尺寸")
    void smallImageIsNotUpscaled() throws Exception {
        BufferedImage img = decode(SealPreviewRenderer.renderPreview(solidPng(100, 50, Color.GREEN)));
        assertEquals(100, img.getWidth());
        assertEquals(50, img.getHeight());
    }

    @Test
    @DisplayName("输出是合法 PNG")
    void outputIsValidPng() throws Exception {
        byte[] preview = SealPreviewRenderer.renderPreview(solidPng(600, 300, Color.WHITE));

        assertEquals((byte) 0x89, preview[0]);
        assertEquals((byte) 'P', preview[1]);
        assertEquals((byte) 'N', preview[2]);
        assertEquals((byte) 'G', preview[3]);
        BufferedImage img = decode(preview);
        assertNotNull(img);
        assertEquals("png", ImageIO.getImageReaders(ImageIO.createImageInputStream(
            new ByteArrayInputStream(preview))).next().getFormatName().toLowerCase());
    }

    @Test
    @DisplayName("水印确实落墨：纯白底图上出现非白像素")
    void watermarkActuallyDrawsInk() throws Exception {
        byte[] preview = SealPreviewRenderer.renderPreview(solidPng(600, 600, Color.WHITE));

        BufferedImage img = decode(preview);
        int ink = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) {
                    ink++;
                }
            }
        }
        assertTrue(ink > 200, "白底图上水印应有足量非白像素（实际 " + ink + "），否则说明文字未绘制");
    }

    @Test
    @DisplayName("透明底图：输出仍保留透明像素（不把透明压成黑底）")
    void transparencyIsPreserved() throws Exception {
        byte[] preview = SealPreviewRenderer.renderPreview(transparentPng(600, 300));

        BufferedImage img = decode(preview);
        int transparent = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) == 0) {
                    transparent++;
                }
            }
        }
        assertTrue(transparent > 100, "透明像素应被保留（实际 " + transparent + "）");
    }

    @Test
    @DisplayName("非法输入：null / 空数组 / 非图片字节 一律 ServiceException")
    void invalidInputIsRejected() {
        assertThrows(ServiceException.class, () -> SealPreviewRenderer.renderPreview(null));
        assertThrows(ServiceException.class, () -> SealPreviewRenderer.renderPreview(new byte[0]));
        assertThrows(ServiceException.class,
            () -> SealPreviewRenderer.renderPreview("这不是图片".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("水印文案在本地环境确实能被所选字体绘制（CJK 或 ASCII 兜底）")
    void watermarkTextIsRenderableByResolvedFont() {
        String text = SealPreviewRenderer.watermarkText();
        assertNotNull(text);
        assertFalse(text.isBlank(), "水印文案不能为空");

        Font font = new Font(SealPreviewRenderer.watermarkFontFamily(), Font.BOLD, 20);
        assertEquals(-1, font.canDisplayUpTo(text),
            "所选字体【" + SealPreviewRenderer.watermarkFontFamily() + "】无法绘制水印文案【" + text
                + "】，会在图上渲染成方块/空白");
    }

    @Test
    @DisplayName("最长边上限为 240px")
    void maxSideIs240() {
        assertEquals(240, SealPreviewRenderer.maxSide());
    }
}
