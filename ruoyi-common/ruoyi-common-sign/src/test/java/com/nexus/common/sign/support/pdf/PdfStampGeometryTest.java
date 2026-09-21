package com.nexus.common.sign.support.pdf;

import com.nexus.common.sign.domain.dto.SealPlacement;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 印章落位坐标换算测试。
 *
 * <p><b>本测试锁死的是本项目最经典的坐标缺陷：</b>UI 坐标原点在左上、PDF 用户空间原点
 * 在左下，换算必须「先翻转 y，<b>再减掉印章自身高度</b>」。漏掉最后一步会让印章整体
 * 上移一个自身高度 —— 页面看着「差不多对」，实际盖歪了。因此这里既断言公式的数值，
 * 也断言「错误公式」确实会落空（{@link PdfContentStamperTest} 里的像素级证伪探针）。</p>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class PdfStampGeometryTest {

    /** PDFBox 的 A4 是按 mm 精确换算的 595.2756×841.8898，浮点比较需要留余量。 */
    private static final float DELTA = 0.01f;

    @Test
    @DisplayName("A4：归一化比例换算为用户空间矩形（y 翻转后还要减自身高度）")
    void a4Box() {
        float pw = PDRectangle.A4.getWidth();
        float ph = PDRectangle.A4.getHeight();
        PdfStampGeometry.StampBox box =
            PdfStampGeometry.computeBox(pw, ph, 0.1f, 0.6f, 0.25f, 0.15f);

        assertEquals(0.1f * pw, box.x(), DELTA);
        assertEquals(0.25f * pw, box.width(), DELTA);
        assertEquals(0.15f * ph, box.height(), DELTA);
        // y = pageHeight - yRatio*pageHeight - height  （先翻转，再减自身高度）
        float expectedY = ph - 0.6f * ph - 0.15f * ph;
        assertEquals(expectedY, box.y(), DELTA);
        assertEquals(0.25f * ph, expectedY, DELTA, "A4 下该公式应等价于 (1 - 0.6 - 0.15) * 页高");
    }

    @Test
    @DisplayName("自定义页面尺寸 612×1008：换算随页面尺寸线性变化")
    void customPageBox() {
        PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(612f, 1008f, 0.2f, 0.5f, 0.3f, 0.1f);

        assertEquals(122.4f, box.x(), DELTA);
        assertEquals(183.6f, box.width(), DELTA);
        assertEquals(100.8f, box.height(), DELTA);
        assertEquals(1008f - 504f - 100.8f, box.y(), DELTA);
    }

    @Test
    @DisplayName("A3 横放：与 A4 同公式，仅尺寸不同")
    void a3LandscapeBox() {
        PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(1190.55f, 841.89f, 0.5f, 0.25f, 0.2f, 0.2f);

        assertEquals(595.275f, box.x(), DELTA);
        assertEquals(238.11f, box.width(), DELTA);
        assertEquals(168.378f, box.height(), DELTA);
        assertEquals(841.89f - 210.4725f - 168.378f, box.y(), DELTA);
    }

    @Test
    @DisplayName("顶部落位：yRatio 与 xRatio 为 0 时贴上边缘，y = 页高 - 自身高度")
    void topAnchoredBox() {
        PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(600f, 800f, 0f, 0f, 0.5f, 0.2f);

        assertEquals(0f, box.x(), DELTA);
        assertEquals(300f, box.width(), DELTA);
        assertEquals(160f, box.height(), DELTA);
        assertEquals(640f, box.y(), DELTA, "yRatio=0 表示印章上边缘贴页顶，用户空间下边缘应为 页高-高度");
    }

    @Test
    @DisplayName("满页落位：矩形正好等于整页，y 恰为 0")
    void fullPageBox() {
        PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(600f, 800f, 0f, 0f, 1f, 1f);

        assertEquals(0f, box.x(), DELTA);
        assertEquals(0f, box.y(), DELTA, "满页落位时 y 必须回到 0（多减/少减自身高度都会在此暴露）");
        assertEquals(600f, box.width(), DELTA);
        assertEquals(800f, box.height(), DELTA);
    }

    @Test
    @DisplayName("底部落位：贴下边缘时 y 恰为 0")
    void bottomAnchoredBox() {
        PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(600f, 800f, 0.1f, 0.9f, 0.2f, 0.1f);

        assertEquals(0f, box.y(), DELTA);
        assertEquals(60f, box.x(), DELTA);
    }

    @Test
    @DisplayName("落位对象重载与逐参数重载结果一致")
    void placementOverloadMatchesRawOverload() {
        SealPlacement p = new SealPlacement(0, 0.3f, 0.4f, 0.2f, 0.1f, 0, 9L);

        PdfStampGeometry.StampBox a = PdfStampGeometry.computeBox(600f, 800f, p);
        PdfStampGeometry.StampBox b = PdfStampGeometry.computeBox(600f, 800f, 0.3f, 0.4f, 0.2f, 0.1f);

        assertEquals(a.x(), b.x(), DELTA);
        assertEquals(a.y(), b.y(), DELTA);
        assertEquals(a.width(), b.width(), DELTA);
        assertEquals(a.height(), b.height(), DELTA);
    }

    @Test
    @DisplayName("旋转：0 度走无变换快路径，返回 null")
    void zeroRotationNeedsNoMatrix() {
        assertNull(PdfStampGeometry.rotationAboutCenter(
            new PdfStampGeometry.StampBox(10f, 20f, 100f, 50f), 0));
        assertNull(PdfStampGeometry.rotationAboutCenter(
            new PdfStampGeometry.StampBox(10f, 20f, 100f, 50f), 360));
        assertNull(PdfStampGeometry.rotationAboutCenter(
            new PdfStampGeometry.StampBox(10f, 20f, 100f, 50f), -360));
    }

    @Test
    @DisplayName("旋转：绕印章自身中心，中心点是不动点")
    void rotationKeepsCenterFixed() {
        PdfStampGeometry.StampBox box = new PdfStampGeometry.StampBox(10f, 20f, 100f, 50f);
        Matrix m = PdfStampGeometry.rotationAboutCenter(box, 90);
        assertNotNull(m);

        float cx = 60f;
        float cy = 45f;
        // 把中心点代入变换，应还原为自身（平移量 t = c - R·c 的构造目的）
        float x = m.getScaleX() * cx + m.getShearX() * cy + m.getTranslateX();
        float y = m.getShearY() * cx + m.getScaleY() * cy + m.getTranslateY();
        assertEquals(cx, x, DELTA);
        assertEquals(cy, y, DELTA);
    }

    @Test
    @DisplayName("旋转：90 度矩阵元素与逆时针旋转约定一致")
    void rotationMatrixValues() {
        Matrix m = PdfStampGeometry.rotationAboutCenter(
            new PdfStampGeometry.StampBox(10f, 20f, 100f, 50f), 90);

        assertEquals(0f, m.getScaleX(), DELTA);
        assertEquals(1f, m.getShearY(), DELTA);
        assertEquals(-1f, m.getShearX(), DELTA);
        assertEquals(0f, m.getScaleY(), DELTA);
    }

    @Test
    @DisplayName("旋转：负角度与等价正角度结果一致")
    void negativeAngleNormalized() {
        PdfStampGeometry.StampBox box = new PdfStampGeometry.StampBox(0f, 0f, 10f, 10f);
        Matrix a = PdfStampGeometry.rotationAboutCenter(box, -90);
        Matrix b = PdfStampGeometry.rotationAboutCenter(box, 270);
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.getScaleX(), b.getScaleX(), DELTA);
        assertEquals(a.getShearY(), b.getShearY(), DELTA);
        assertEquals(a.getShearX(), b.getShearX(), DELTA);
        assertEquals(a.getScaleY(), b.getScaleY(), DELTA);
        assertEquals(a.getTranslateX(), b.getTranslateX(), DELTA);
        assertEquals(a.getTranslateY(), b.getTranslateY(), DELTA);
    }

    @Test
    @DisplayName("校验：合法落位返回 null")
    void validPlacementAccepted() {
        assertNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, 0.2f, 0.3f, 0.4f, 0, 9L)));
        // 贴边（x+w == 1、y+h == 1）必须合法，不应被浮点误差判死
        assertNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.7f, 0.6f, 0.3f, 0.4f, 0, 9L)));
    }

    @Test
    @DisplayName("校验：null / 缺印章ID / 负页码 一律拒绝")
    void invalidBasicsRejected() {
        assertNotNull(PdfStampGeometry.validatePlacement(null));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, 0.1f, 0.1f, 0.1f, 0, null)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(-1, 0.1f, 0.1f, 0.1f, 0.1f, 0, 9L)));
    }

    @Test
    @DisplayName("校验：位置比例越界一律拒绝")
    void outOfRangePositionRejected() {
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, -0.5f, 0.1f, 0.1f, 0.1f, 0, 9L)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 1.5f, 0.1f, 0.1f, 0.1f, 0, 9L)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, -0.2f, 0.1f, 0.1f, 0, 9L)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, 1.2f, 0.1f, 0.1f, 0, 9L)));
    }

    @Test
    @DisplayName("校验：宽高非正或大于 1 一律拒绝")
    void invalidSizeRejected() {
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, 0.1f, 0f, 0.1f, 0, 9L)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, 0.1f, 0.1f, 0f, 0, 9L)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, 0.1f, -0.1f, 0.1f, 0, 9L)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0f, 0f, 1.2f, 0.1f, 0, 9L)));
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0f, 0f, 1f, 1.2f, 0, 9L)));
    }

    @Test
    @DisplayName("校验：印章越出页面右/下边缘一律拒绝")
    void overflowRejected() {
        // x + w = 1.1 > 1：右边缘越界
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.8f, 0.1f, 0.3f, 0.1f, 0, 9L)));
        // y + h = 1.1 > 1：下边缘越界
        assertNotNull(PdfStampGeometry.validatePlacement(new SealPlacement(0, 0.1f, 0.8f, 0.1f, 0.3f, 0, 9L)));
    }
}
