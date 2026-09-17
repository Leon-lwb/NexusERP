package com.nexus.common.sign.support.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 盖章/验签测试的公共夹具与像素回读探针。
 *
 * <p><b>为什么用像素回读而不是「断言调用参数」：</b>坐标公式存在两个独立的易错点
 * （y 翻转、再减自身高度），直接断言 {@code drawImage} 的入参只能证明代码「按自己写的
 * 公式调用」，证明不了<b>印章真的画到了页面上该在的地方</b>。这里渲染成位图后按象限
 * 取色，是对坐标正确性的端到端验证（与 spike Q4 的 40/40 命中同一手法）。</p>
 *
 * <p>印章图固定做成 2×2 颜色象限：左上红 / 右上绿 / 左下蓝 / 右下黄。
 * 这样一次采样就能同时判定「位置对不对」与「有没有画反/画歪」。</p>
 *
 * @author nexus
 */
public final class PdfTestSupport {

    /** 象限序号：左上 */
    static final int TL = 0;
    /** 象限序号：右上 */
    static final int TR = 1;
    /** 象限序号：左下 */
    static final int BL = 2;
    /** 象限序号：右下 */
    static final int BR = 3;

    static final Color C_TL = new Color(255, 0, 0);
    static final Color C_TR = new Color(0, 200, 0);
    static final Color C_BL = new Color(0, 0, 255);
    static final Color C_BR = new Color(255, 220, 0);

    /**
     * 写进内容流的 ASCII 标记（PDF 内容流里的一行 {@code %} 注释）。
     *
     * <p>用于「覆盖区间内等长改写」篡改：找一处<b>已知内容、且与 PDF 结构无关</b>的字节，
     * 改它 1 字节既能让摘要变化，又不会破坏文档可解析性。内容流注释正好满足
     * （等长改写不影响任何对象偏移，注释也不参与渲染）。</p>
     *
     * <p><b>为什么不用 {@code /Info /Producer}：</b>实测 PDFBox 在<b>全量 save</b> 时会把自己的
     * 生产者字符串写进 /Info，把夹具标记覆盖掉；而内容流里的注释是原样保留的。夹具必须
     * 不依赖这种会被库改写的字段，否则「找不到标记」会让篡改用例静默退化成空跑。</p>
     */
    static final String MARKER = "NEXUSMARKER0123456789ABCDEF";

    /** 背景色容差：渲染管线可能有轻微色彩管理偏移，但不该大到把两种颜色混为一谈。 */
    private static final int CHANNEL_TOLERANCE = 40;

    private PdfTestSupport() {
    }

    /**
     * 生成 2×2 颜色象限的 PNG 印章图。
     *
     * @param size 边长（像素）
     * @return PNG 字节
     */
    public static byte[] quadrantSealPng(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        int half = size / 2;
        int rest = size - half;
        g.setColor(C_TL);
        g.fillRect(0, 0, half, half);
        g.setColor(C_TR);
        g.fillRect(half, 0, rest, half);
        g.setColor(C_BL);
        g.fillRect(0, half, half, rest);
        g.setColor(C_BR);
        g.fillRect(half, half, rest, rest);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            assertTrue(ImageIO.write(img, "png", bos), "测试夹具必须能写出 PNG");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    /**
     * 用 PDFBox 现场生成一份测试 PDF（无外部夹具）。
     *
     * @param size  页面尺寸
     * @param pages 页数
     * @return PDF 字节
     */
    public static byte[] newPdf(PDRectangle size, int pages) {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
                // compress=false：内容流以明文写入，MARKER 才能在文件字节里被搜到
                try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.OVERWRITE, false)) {
                    // PDF 内容流里的注释行（% 到行尾），合法且不参与渲染；
                    // PDFBox 3 的 writeComment 不是 public，用原始指令写入
                    cs.appendRawCommands("% " + MARKER + "\n");
                    // 画一点内容，让「签名后追加修订」有可改的东西，也避免出现空页
                    cs.addRect(60, 60, 200, 100);
                    cs.fill();
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 生成自定义尺寸的多页 PDF。
     *
     * @param width  页宽（点）
     * @param height 页高（点）
     * @param pages  页数
     * @return PDF 字节
     */
    static byte[] newPdf(float width, float height, int pages) {
        return newPdf(new PDRectangle(width, height), pages);
    }

    /**
     * 渲染指定页为位图。
     *
     * @param pdf       PDF 字节
     * @param pageIndex 页序号（从 0 开始）
     * @param dpi       渲染精度
     * @return 位图
     */
    static BufferedImage render(byte[] pdf, int pageIndex, int dpi) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFRenderer(doc).renderImageWithDPI(pageIndex, dpi);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 按印章矩形取某一象限中心的颜色（模拟人眼回读）。
     *
     * @param rendered 渲染后的位图
     * @param page     页面尺寸（点）
     * @param dpi      渲染精度（须与 {@link #render} 一致）
     * @param box      印章在 PDF 用户空间中的矩形
     * @param quadrant 象限序号（{@link #TL}/{@link #TR}/{@link #BL}/{@link #BR}）
     * @return 该象限中心的颜色
     */
    static Color sampleQuadrant(BufferedImage rendered, PDRectangle page, int dpi,
                                PdfStampGeometry.StampBox box, int quadrant) {
        float scale = dpi / 72f;
        // PDF 用户空间原点左下 → 位图原点左上，故 top = pageHeight - y - height
        float left = box.x() * scale;
        float top = (page.getHeight() - box.y() - box.height()) * scale;
        float w = box.width() * scale;
        float h = box.height() * scale;
        float fx = (quadrant == TL || quadrant == BL) ? left + w * 0.25f : left + w * 0.75f;
        float fy = (quadrant == TL || quadrant == TR) ? top + h * 0.25f : top + h * 0.75f;
        return new Color(rendered.getRGB(Math.round(fx), Math.round(fy)));
    }

    /**
     * 断言某矩形处确实是印章（四个象限颜色都对得上）。
     *
     * @param what     断言失败时的场景描述
     * @param rendered 渲染后的位图
     * @param page     页面尺寸（点）
     * @param dpi      渲染精度
     * @param box      印章矩形
     */
    static void assertSealAt(String what, BufferedImage rendered, PDRectangle page, int dpi,
                             PdfStampGeometry.StampBox box) {
        assertQuadrant(what + " 左上", C_TL, sampleQuadrant(rendered, page, dpi, box, TL));
        assertQuadrant(what + " 右上", C_TR, sampleQuadrant(rendered, page, dpi, box, TR));
        assertQuadrant(what + " 左下", C_BL, sampleQuadrant(rendered, page, dpi, box, BL));
        assertQuadrant(what + " 右下", C_BR, sampleQuadrant(rendered, page, dpi, box, BR));
    }

    /**
     * 断言某矩形处<b>不是</b>印章（用于证伪：错误公式算出的位置必须落空）。
     *
     * @param what     断言失败时的场景描述
     * @param rendered 渲染后的位图
     * @param page     页面尺寸（点）
     * @param dpi      渲染精度
     * @param box      印章矩形
     */
    static void assertNoSealAt(String what, BufferedImage rendered, PDRectangle page, int dpi,
                               PdfStampGeometry.StampBox box) {
        Color tl = sampleQuadrant(rendered, page, dpi, box, TL);
        Color br = sampleQuadrant(rendered, page, dpi, box, BR);
        assertTrue(!close(tl, C_TL) && !close(br, C_BR),
            what + " 本不该有印章，却采到了印章色（TL=" + tl + ", BR=" + br + "）");
    }

    private static void assertQuadrant(String what, Color expected, Color actual) {
        assertTrue(close(expected, actual), what + " 颜色不符：期望 " + expected + "，实际 " + actual);
    }

    private static boolean close(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) <= CHANNEL_TOLERANCE
            && Math.abs(a.getGreen() - b.getGreen()) <= CHANNEL_TOLERANCE
            && Math.abs(a.getBlue() - b.getBlue()) <= CHANNEL_TOLERANCE;
    }

    /**
     * 在<b>被签名覆盖的区间内</b>等长改写 1 个字节（模拟「改完文件字数不变」的精细篡改）。
     *
     * <p>改写目标是内容流里的 {@link #MARKER} 注释：明文、不参与渲染、位于首段覆盖区间内。
     * 等长改写不改变任何对象偏移，因此文档仍可正常解析 —— 这正是要害：<b>PDF 能打开，
     * 内容却已不是签名时的那份</b>，只有 CMS 摘要比对发现得了。</p>
     *
     * @param signed 干净的已签名 PDF
     * @return 等长改写后的 PDF
     */
    public static byte[] editCoveredByteInPlace(byte[] signed) {
        byte[] out = signed.clone();
        int coveredEnd = coveredEndOf(signed);

        byte[] marker = MARKER.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int at = indexOf(out, marker);
        assertTrue(at > 0, "测试夹具标记必须存在于文档中（找不到标记会让本用例静默退化成空跑）");
        assertTrue(at + marker.length <= coveredEnd,
            "标记必须落在被签名覆盖的区间内（标记偏移 " + at + " vs 覆盖末端 " + coveredEnd + "）");

        out[at] ^= 0x01;
        assertTrue(signed[at] != out[at], "字节必须真的被改掉了");
        return out;
    }

    /**
     * 取签名的覆盖区间末端（{@code byteRange[2] + byteRange[3]}）。
     *
     * @param pdf 已签名 PDF
     * @return 覆盖末端偏移
     */
    public static int coveredEndOf(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int[] range = doc.getSignatureDictionaries().get(0).getByteRange();
            return range[2] + range[3];
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
