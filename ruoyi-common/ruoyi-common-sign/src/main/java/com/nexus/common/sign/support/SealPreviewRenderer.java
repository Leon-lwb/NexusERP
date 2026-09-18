package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * 印章预览图渲染器：<b>等比降采样 + 对角半透明水印</b>。
 *
 * <p><b>为什么存在</b>：{@code es_seal.preview_oss_id} 是印章原图（{@code seal_oss_id}）唯一的对外出口，
 * 原图本身没有任何 REST 接口。若直接把原图发给前端，印章一旦泄露即可被高精度复用，
 * 「印章防滥用」这条安全设计就失效了。故预览图必须在<b>服务端</b>生成：分辨率压到肉眼可辨识、
 * 但不足以复刻的档位，并叠加难以抹除的水印。</p>
 *
 * <p><b>纯函数、纯 JDK</b>：本类只用 {@code javax.imageio} / {@code java.awt}，不引入任何字体或图像依赖
 * （新增依赖会使得 common 模块向下游传染）。实例方法一概没有，全部为静态方法，便于单测。</p>
 *
 * <p><b>中文水印的坑</b>：{@code java.awt.Font} 的默认逻辑字体在缺少 CJK 字体的机器上会把汉字画成
 * 空白或方块（tofu），且<b>不会报错</b>。本类因此在初始化时枚举
 * {@link GraphicsEnvironment#getAvailableFontFamilyNames()} 逐个 {@code canDisplayUpTo} 探测：
 * 命中「物理」中文字体则用 {@code 仅供查看}；一个都没有（典型：精简版 Linux 容器）则退回 ASCII
 * {@code PREVIEW}，保证任何环境下水印都真实可见。实测结论见 {@link #watermarkText()} /
 * {@link #watermarkFontFamily()}（运维排障时可直接打日志）。</p>
 *
 * <p><b>无头环境</b>：{@code BufferedImage} / {@code Graphics2D} / 字体枚举在
 * {@code -Djava.awt.headless=true} 下均可正常工作（本类已实测），因此服务端无需 X11、无需额外 JVM 参数；
 * Spring Boot 默认即把 {@code java.awt.headless} 置为 {@code true}。</p>
 *
 * @author nexus
 */
public final class SealPreviewRenderer {

    /** 预览图最长边上限（px）。 */
    private static final int MAX_SIDE_PX = 240;

    /** 输入图片字节数上限：超过此值的输入直接拒绝，避免解码阶段拖垮服务。 */
    private static final int MAX_INPUT_BYTES = 20 * 1024 * 1024;

    /** 输入图片单边像素上限：在<b>解码前</b>用 {@link ImageReader} 读取头部尺寸校验。 */
    private static final int MAX_INPUT_SIDE_PX = 20000;

    /**
     * 输入图片<b>像素面积</b>上限（4000×4000）：单边校验挡不住「细长/方形巨图」——
     * 20000×20000 的合法 PNG 约 10MB，但全分辨率解码要分配约 1.6GB ARGB 堆，
     * 共享 JVM 直接 OOM。面积上限把解码内存钉死在 ~64MB 量级。
     */
    private static final long MAX_INPUT_PIXELS = 16_000_000L;

    /** 水印文案（中文）。 */
    private static final String CJK_WATERMARK = "仅供查看";

    /** 水印文案（ASCII 兜底，用于缺中文字体的环境）。 */
    private static final String ASCII_WATERMARK = "PREVIEW";

    /** 水印旋转角度（度）：负值表示逆时针，视觉上为左下→右上。 */
    private static final double WATERMARK_ANGLE_DEG = -30d;

    /** 水印整体不透明度。 */
    private static final float WATERMARK_ALPHA = 0.5f;

    /** 探测字体可用性时使用的字号。 */
    private static final int PROBE_FONT_SIZE = 16;

    /** 最小字号，防止极小图上算出 0 号字。 */
    private static final float MIN_FONT_SIZE = 8f;

    /** 优先使用的中文字体族（跨平台常见顺序）。 */
    private static final String[] PREFERRED_CJK_FAMILIES = {
        "Microsoft YaHei", "微软雅黑", "Microsoft YaHei UI", "SimHei", "黑体",
        "SimSun", "宋体", "Noto Sans CJK SC", "Noto Sans SC", "Source Han Sans SC", "思源黑体",
        "WenQuanYi Micro Hei", "WenQuanYi Zen Hei", "PingFang SC", "Hiragino Sans GB", "Arial Unicode MS"
    };

    /** 水印字体解析结果（字体族 + 文案），进程内解析一次后缓存。 */
    private static volatile WatermarkFont watermarkFont;

    private SealPreviewRenderer() {
    }

    /**
     * 生成水印预览图。
     *
     * @param original 印章原图字节（PNG/JPEG/GIF/BMP 等 ImageIO 可解码格式）
     * @return 降采样 + 水印后的 PNG 字节
     * @throws ServiceException 输入为空/超限/格式不受支持/解码失败等（调用方按「预览图生成失败」降级处理）
     */
    public static byte[] renderPreview(byte[] original) {
        if (original == null || original.length == 0) {
            throw new ServiceException("印章图片内容为空，无法生成预览图");
        }
        if (original.length > MAX_INPUT_BYTES) {
            throw new ServiceException("印章图片过大（{} 字节），拒绝生成预览图", original.length);
        }
        BufferedImage source = readImage(original);
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new ServiceException("印章图片尺寸非法：{}x{}", sourceWidth, sourceHeight);
        }
        if (sourceWidth > MAX_INPUT_SIDE_PX || sourceHeight > MAX_INPUT_SIDE_PX) {
            throw new ServiceException("印章图片尺寸过大：{}x{}，拒绝生成预览图", sourceWidth, sourceHeight);
        }

        // 等比缩放到最长边 <= MAX_SIDE_PX；小于上限的图不放大（放大只会让水印变糊、不增加安全性）
        int longestSide = Math.max(sourceWidth, sourceHeight);
        double scale = longestSide > MAX_SIDE_PX ? (double) MAX_SIDE_PX / longestSide : 1d;
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        // ARGB：保留原图透明通道（印章 PNG 通常是透明底，压成黑底等于毁图）
        BufferedImage preview = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preview.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            drawWatermark(g, targetWidth, targetHeight);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, targetWidth * targetHeight / 2));
        try {
            if (!ImageIO.write(preview, "png", out)) {
                throw new ServiceException("预览图编码失败：无可用 PNG 编码器");
            }
        } catch (IOException e) {
            throw new ServiceException("预览图编码失败：{}", e.getMessage());
        }
        return out.toByteArray();
    }

    /**
     * 预览图最长边上限（px）。
     *
     * @return 最长边上限
     */
    public static int maxSide() {
        return MAX_SIDE_PX;
    }

    /**
     * 当前运行环境实际使用的水印文案（中文或 ASCII 兜底）。
     *
     * @return 水印文案
     */
    public static String watermarkText() {
        return resolveWatermarkFont().text;
    }

    /**
     * 当前运行环境实际使用的水印字体族名（运维排障用；可用 {@code Font.canDisplayUpTo} 复核）。
     *
     * @return 字体族名
     */
    public static String watermarkFontFamily() {
        return resolveWatermarkFont().family;
    }

    /**
     * 绘制对角水印：字体大小按对角线自适应，文案居中后整体旋转。
     *
     * <p>先描白边再填深色：白边让深色章面/照片底也能看清，深色填充让浅色/透明底也能看清，
     * 只画单色水印在某种底色上必然「隐身」。</p>
     *
     * @param g 预览图画笔
     * @param w 预览图宽
     * @param h 预览图高
     */
    private static void drawWatermark(Graphics2D g, int w, int h) {
        String text = watermarkText();
        double diagonal = Math.hypot(w, h);
        Font probeFont = new Font(watermarkFontFamily(), Font.BOLD, PROBE_FONT_SIZE);
        FontMetrics probeMetrics = g.getFontMetrics(probeFont);
        int probeWidth = Math.max(1, probeMetrics.stringWidth(text));
        // 字号自适应：让文案宽度约占对角线 85%，小图大字、大图不至于水印缩成一条线
        float fontSize = (float) Math.max(MIN_FONT_SIZE,
            PROBE_FONT_SIZE * (diagonal * 0.85d) / probeWidth);
        Font font = probeFont.deriveFont(fontSize);

        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector glyphVector = font.createGlyphVector(frc, text);
        Rectangle2D glyphBounds = glyphVector.getVisualBounds();
        Shape shape = glyphVector.getOutline(
            (float) (w / 2d - glyphBounds.getCenterX()),
            (float) (h / 2d - glyphBounds.getCenterY()));

        AffineTransform oldTransform = g.getTransform();
        try {
            g.rotate(Math.toRadians(WATERMARK_ANGLE_DEG), w / 2d, h / 2d);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_ALPHA));
            g.setColor(new Color(255, 255, 255, 220));
            g.setStroke(new BasicStroke(Math.max(1f, fontSize / 12f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
            g.setColor(new Color(60, 60, 60, 255));
            g.fill(shape);
        } finally {
            g.setTransform(oldTransform);
            g.setComposite(AlphaComposite.SrcOver);
        }
    }

    /**
     * 解码图片：先用 {@link ImageReader} 读头部尺寸，超限的输入在<b>分配像素缓冲之前</b>就拒绝。
     *
     * @param bytes 图片字节
     * @return 解码后的图片
     */
    private static BufferedImage readImage(byte[] bytes) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageInput == null) {
                throw new ServiceException("印章图片无法读取");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new ServiceException("印章图片格式不受支持，无法生成预览图");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new ServiceException("印章图片尺寸非法：{}x{}", width, height);
                }
                if (width > MAX_INPUT_SIDE_PX || height > MAX_INPUT_SIDE_PX) {
                    throw new ServiceException("印章图片尺寸过大：{}x{}，拒绝生成预览图", width, height);
                }
                if ((long) width * height > MAX_INPUT_PIXELS) {
                    throw new ServiceException("印章图片像素总量过大：{}x{}（上限 {} 百万像素），请缩放后再上传",
                        width, height, MAX_INPUT_PIXELS / 1_000_000);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new ServiceException("印章图片解析失败：解码结果为空");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ServiceException("印章图片解析失败：{}", e.getMessage());
        }
    }

    /**
     * 解析水印字体（进程内只解析一次）。
     *
     * @return 水印字体与文案
     */
    private static WatermarkFont resolveWatermarkFont() {
        WatermarkFont cached = watermarkFont;
        if (cached != null) {
            return cached;
        }
        synchronized (SealPreviewRenderer.class) {
            if (watermarkFont == null) {
                watermarkFont = detectWatermarkFont();
            }
            return watermarkFont;
        }
    }

    /**
     * 探测本机可用字体：优先表 → 全量扫描 → ASCII 兜底。
     *
     * @return 水印字体与文案
     */
    private static WatermarkFont detectWatermarkFont() {
        String[] families;
        try {
            families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        } catch (Exception | LinkageError e) {
            // 极端环境下 AWT 字体子系统不可用：直接 ASCII 兜底，绝不让印章保存因水印探测失败
            return new WatermarkFont(Font.SANS_SERIF, ASCII_WATERMARK);
        }
        Set<String> available = new HashSet<>();
        for (String family : families) {
            available.add(family.toLowerCase(Locale.ROOT));
        }
        for (String family : PREFERRED_CJK_FAMILIES) {
            if (available.contains(family.toLowerCase(Locale.ROOT)) && canDisplayCjk(family)) {
                return new WatermarkFont(family, CJK_WATERMARK);
            }
        }
        for (String family : families) {
            if (canDisplayCjk(family)) {
                return new WatermarkFont(family, CJK_WATERMARK);
            }
        }
        return new WatermarkFont(Font.SANS_SERIF, ASCII_WATERMARK);
    }

    /**
     * 判断字体族能否完整绘制中文水印。
     *
     * @param family 字体族名
     * @return true 表示四个汉字均有字形
     */
    private static boolean canDisplayCjk(String family) {
        return new Font(family, Font.BOLD, PROBE_FONT_SIZE).canDisplayUpTo(CJK_WATERMARK) == -1;
    }

    /** 水印字体解析结果。 */
    private record WatermarkFont(String family, String text) {
    }
}
