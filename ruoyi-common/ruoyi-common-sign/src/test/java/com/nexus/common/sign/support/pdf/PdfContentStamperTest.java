package com.nexus.common.sign.support.pdf;

import com.nexus.common.core.exception.ServiceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.nexus.common.sign.support.pdf.PdfTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF 盖章测试（渲染 + 像素回读，非「断言入参」）。
 *
 * <p>测试 PDF 与印章图都在测试内用 PDFBox / JDK 现场生成，不引入任何二进制夹具。</p>
 *
 * @author nexus
 */
class PdfContentStamperTest {

    private static final byte[] SEAL = quadrantSealPng(240);

    private static PdfContentStamper.StampSpec spec(int page, float x, float y, float w, float h) {
        return new PdfContentStamper.StampSpec(page, x, y, w, h, 0, SEAL);
    }

    @Test
    @DisplayName("A4 盖章：72dpi 与 150dpi 下四个象限颜色全部落在正确位置")
    void a4StampLandsCorrectly() {
        byte[] pdf = newPdf(PDRectangle.A4, 1);
        byte[] stamped = PdfContentStamper.stamp(pdf, List.of(spec(0, 0.1f, 0.6f, 0.25f, 0.15f)));

        PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(
            PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight(), 0.1f, 0.6f, 0.25f, 0.15f);

        for (int dpi : new int[]{72, 150}) {
            BufferedImage rendered = render(stamped, 0, dpi);
            assertSealAt("A4 @" + dpi + "dpi", rendered, PDRectangle.A4, dpi, box);
        }
    }

    @Test
    @DisplayName("证伪探针：用「忘记减自身高度」的错误公式算出的位置必须落空")
    void falsificationProbeWrongFormulaMissesTheSeal() {
        byte[] pdf = newPdf(PDRectangle.A4, 1);
        byte[] stamped = PdfContentStamper.stamp(pdf, List.of(spec(0, 0.1f, 0.6f, 0.25f, 0.15f)));
        BufferedImage rendered = render(stamped, 0, 72);

        PdfStampGeometry.StampBox correct = PdfStampGeometry.computeBox(
            PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight(), 0.1f, 0.6f, 0.25f, 0.15f);
        // 错误公式：y = pageHeight - yRatio*pageHeight（漏掉 "- height"）→ 矩形整体上移一个自身高度
        PdfStampGeometry.StampBox wrong = new PdfStampGeometry.StampBox(
            correct.x(), PDRectangle.A4.getHeight() - 0.6f * PDRectangle.A4.getHeight(),
            correct.width(), correct.height());

        assertSealAt("正确公式位置", rendered, PDRectangle.A4, 72, correct);
        assertNoSealAt("错误公式（漏减自身高度）位置", rendered, PDRectangle.A4, 72, wrong);
    }

    @Test
    @DisplayName("自定义页面尺寸 612×1008：同样命中")
    void customPageSizeStampLandsCorrectly() {
        byte[] pdf = newPdf(612f, 1008f, 1);
        byte[] stamped = PdfContentStamper.stamp(pdf, List.of(spec(0, 0.2f, 0.5f, 0.3f, 0.1f)));

        BufferedImage rendered = render(stamped, 0, 120);
        assertSealAt("自定义尺寸", rendered, new PDRectangle(612f, 1008f), 120,
            PdfStampGeometry.computeBox(612f, 1008f, 0.2f, 0.5f, 0.3f, 0.1f));
    }

    @Test
    @DisplayName("多页多章：3 页 4 章（含同页 2 章）逐页命中，页数不变")
    void multiPageMultiSeal() {
        PDRectangle a4 = PDRectangle.A4;
        byte[] pdf = newPdf(a4, 3);
        byte[] stamped = PdfContentStamper.stamp(pdf, List.of(
            spec(0, 0.05f, 0.05f, 0.2f, 0.1f),
            spec(0, 0.6f, 0.8f, 0.2f, 0.1f),
            spec(1, 0.4f, 0.3f, 0.15f, 0.1f),
            spec(2, 0.3f, 0.4f, 0.25f, 0.12f)));

        try (PDDocument doc = Loader.loadPDF(stamped)) {
            assertEquals(3, doc.getNumberOfPages(), "盖章不应改变页数");
        } catch (Exception e) {
            fail("盖章结果必须可被 PDFBox 解析：" + e.getMessage());
        }

        assertSealAt("第1页 章A", render(stamped, 0, 100), a4, 100,
            PdfStampGeometry.computeBox(a4.getWidth(), a4.getHeight(), 0.05f, 0.05f, 0.2f, 0.1f));
        assertSealAt("第1页 章B", render(stamped, 0, 100), a4, 100,
            PdfStampGeometry.computeBox(a4.getWidth(), a4.getHeight(), 0.6f, 0.8f, 0.2f, 0.1f));
        assertSealAt("第2页", render(stamped, 1, 100), a4, 100,
            PdfStampGeometry.computeBox(a4.getWidth(), a4.getHeight(), 0.4f, 0.3f, 0.15f, 0.1f));
        assertSealAt("第3页", render(stamped, 2, 100), a4, 100,
            PdfStampGeometry.computeBox(a4.getWidth(), a4.getHeight(), 0.3f, 0.4f, 0.25f, 0.12f));
    }

    @Test
    @DisplayName("盖章是「追加」而非「替换」：原有页面内容仍在")
    void stampingPreservesOriginalContent() {
        PDRectangle a4 = PDRectangle.A4;
        byte[] stamped = PdfContentStamper.stamp(newPdf(a4, 1), List.of(spec(0, 0.6f, 0.05f, 0.2f, 0.1f)));

        BufferedImage rendered = render(stamped, 0, 72);
        // 夹具在 PDF 用户空间 (60,60)-(260,160) 画了黑色实心矩形
        Color content = new Color(rendered.getRGB(120, Math.round(a4.getHeight() - 100f)));
        assertTrue(content.getRed() < 60 && content.getGreen() < 60 && content.getBlue() < 60,
            "原有黑色内容矩形应保留（实际 " + content + "）");
    }

    @Test
    @DisplayName("旋转 90°：印章仍在同一矩形内，但图像内容转过 90°")
    void rotatedStampRotatesImageContent() {
        // 正方形矩形（120pt × 120pt），旋转后与原矩形完全重合，便于逐象限取色
        byte[] pdf = newPdf(600f, 800f, 1);
        byte[] stamped = PdfContentStamper.stamp(pdf, List.of(
            new PdfContentStamper.StampSpec(0, 0.2f, 0.3f, 0.2f, 0.15f, 90, SEAL)));

        PDRectangle page = new PDRectangle(600f, 800f);
        PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(600f, 800f, 0.2f, 0.3f, 0.2f, 0.15f);
        assertEquals(box.width(), box.height(), 1e-3f, "本用例要求矩形为正方形");

        BufferedImage rendered = render(stamped, 0, 120);
        // 取色实测（本测试即证据）：rotation=+90 时左上角呈现原「右上」象限（绿），
        // 即图像在屏幕上是**逆时针**转了 90°。正向旋转角 = 屏幕上逆时针（PDF 用户空间 y 轴向上）。
        // 逆时针 90°：原左上(红)→左下，原右上(绿)→左上，原右下(黄)→右上，原左下(蓝)→右下
        assertEquals(C_TR, sampleQuadrant(rendered, page, 120, box, TL), "逆时针 90° 后左上应为原右上（绿）");
        assertEquals(C_BR, sampleQuadrant(rendered, page, 120, box, TR), "逆时针 90° 后右上应为原右下（黄）");
        assertEquals(C_BL, sampleQuadrant(rendered, page, 120, box, BR), "逆时针 90° 后右下应为原左下（蓝）");
        assertEquals(C_TL, sampleQuadrant(rendered, page, 120, box, BL), "逆时针 90° 后左下应为原左上（红）");
    }

    @Test
    @DisplayName("服务端拒绝非 PDF：魔数不符一律 ServiceException")
    void nonPdfRejected() {
        byte[] notPdf = "这不是一个 PDF 文件".getBytes(StandardCharsets.UTF_8);

        assertFalse(PdfContentStamper.isPdf(notPdf));
        assertThrows(ServiceException.class, () -> PdfContentStamper.stamp(notPdf, List.of(spec(0, 0f, 0f, 0.1f, 0.1f))));
    }

    @Test
    @DisplayName("魔数校验：null / 空 / 过短 / 被截断的文件头一律判非 PDF")
    void magicNumberEdgeCases() {
        assertFalse(PdfContentStamper.isPdf(null));
        assertFalse(PdfContentStamper.isPdf(new byte[0]));
        assertFalse(PdfContentStamper.isPdf("%PD".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(PdfContentStamper.isPdf("%PDF".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(PdfContentStamper.isPdf("PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(PdfContentStamper.isPdf(newPdf(PDRectangle.A4, 1)));
        // 规范允许文件头之前有少量二进制垃圾（Acrobat 亦如此解析）
        byte[] withJunk = new byte[16 + 6];
        System.arraycopy("%PDF-1".getBytes(StandardCharsets.US_ASCII), 0, withJunk, 16, 6);
        assertTrue(PdfContentStamper.isPdf(withJunk));
    }

    @Test
    @DisplayName("页码越界：超出文档页数一律 ServiceException（不能让 drawImage 静默跳过）")
    void pageIndexOutOfRangeRejected() {
        byte[] pdf = newPdf(PDRectangle.A4, 2);

        assertThrows(ServiceException.class, () -> PdfContentStamper.stamp(pdf, List.of(spec(2, 0.1f, 0.1f, 0.1f, 0.1f))));
        assertThrows(ServiceException.class, () -> PdfContentStamper.stamp(pdf, List.of(spec(99, 0.1f, 0.1f, 0.1f, 0.1f))));
        assertThrows(ServiceException.class, () -> PdfContentStamper.stamp(pdf, List.of(spec(-1, 0.1f, 0.1f, 0.1f, 0.1f))));
    }

    @Test
    @DisplayName("落位列表为空 / null：一律 ServiceException")
    void emptySpecsRejected() {
        byte[] pdf = newPdf(PDRectangle.A4, 1);

        assertThrows(ServiceException.class, () -> PdfContentStamper.stamp(pdf, null));
        assertThrows(ServiceException.class, () -> PdfContentStamper.stamp(pdf, List.of()));
    }

    @Test
    @DisplayName("印章图片无法解码：给出明确异常而不是产出坏 PDF")
    void undecodableSealImageRejected() {
        byte[] pdf = newPdf(PDRectangle.A4, 1);
        byte[] broken = "not an image".getBytes(StandardCharsets.UTF_8);

        assertThrows(ServiceException.class, () -> PdfContentStamper.stamp(pdf,
            List.of(new PdfContentStamper.StampSpec(0, 0.1f, 0.1f, 0.1f, 0.1f, 0, broken))));
    }
}
