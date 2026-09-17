package com.nexus.common.sign.support.pdf;

import com.nexus.common.sign.domain.dto.SealPlacement;
import org.apache.pdfbox.util.Matrix;

/**
 * 印章落位坐标换算（纯函数，无 IO，便于单测）。
 *
 * <p><b>唯一公式（spike 40/40 像素回读命中，勿改）：</b></p>
 * <pre>
 * float pw = page.getMediaBox().getWidth();
 * float ph = page.getMediaBox().getHeight();
 * float h  = hRatio * ph;
 * float x  = xRatio * pw;
 * float y  = ph - yRatio * ph - h;      // 先把 y 翻过来，再减自身高度
 * </pre>
 *
 * <p>两处易错点：</p>
 * <ul>
 *   <li><b>翻转 y</b>：UI 原点在左上（y 向下），PDF 用户空间原点在左下（y 向上）。</li>
 *   <li><b>翻转后还要减掉自身高度</b>：{@code yRatio} 指的是印章<b>上边缘</b>的位置，
 *       而 {@code drawImage} 的 y 是印章<b>下边缘</b>。漏掉这一项，印章会整体上移一个自身高度。</li>
 * </ul>
 *
 * <p><b>已知未覆盖项：</b>页面级 {@code /Rotate}（旋转页面）不做补偿 —— 本公式按
 * {@code MediaBox} 的未旋转坐标系落位。若将来遇到扫描件带 /Rotate 的合同，
 * 需要按 {@code page.getRotation()} 再叠一次矩阵，届时请补测试。</p>
 *
 * @author nexus
 */
public final class PdfStampGeometry {

    /** 归一化比例的容差（前端浮点往返可能带来 1e-6 级误差） */
    private static final float EPSILON = 1e-4f;

    private PdfStampGeometry() {
    }

    /**
     * 印章在 PDF 用户空间中的矩形（原点左下、y 向上）。
     *
     * @param x      左边缘
     * @param y      下边缘
     * @param width  宽度
     * @param height 高度
     */
    public record StampBox(float x, float y, float width, float height) {
    }

    /**
     * 归一化比例 → PDF 用户空间矩形。
     *
     * @param pageWidth  页面宽度（{@code MediaBox} 宽度）
     * @param pageHeight 页面高度（{@code MediaBox} 高度）
     * @param xRatio     左边缘比例（原点左上）
     * @param yRatio     上边缘比例（原点左上，向下为正）
     * @param wRatio     宽度比例
     * @param hRatio     高度比例
     * @return 用户空间矩形
     */
    public static StampBox computeBox(float pageWidth, float pageHeight,
                                      float xRatio, float yRatio, float wRatio, float hRatio) {
        float height = hRatio * pageHeight;
        return new StampBox(
            xRatio * pageWidth,
            pageHeight - yRatio * pageHeight - height,
            wRatio * pageWidth,
            height);
    }

    /**
     * 归一化比例 → PDF 用户空间矩形（入参为落位对象）。
     *
     * @param pageWidth  页面宽度
     * @param pageHeight 页面高度
     * @param placement  印章落位
     * @return 用户空间矩形
     */
    public static StampBox computeBox(float pageWidth, float pageHeight, SealPlacement placement) {
        return computeBox(pageWidth, pageHeight, placement.xRatio(), placement.yRatio(),
            placement.wRatio(), placement.hRatio());
    }

    /**
     * 绕印章自身中心旋转的变换矩阵（{@code rotation} 为 0 时返回 null，走无变换的快路径）。
     *
     * <p><b>方向约定（取色实测，勿凭直觉改）：</b>PDF 用户空间的 y 轴向上，这里用的是标准
     * 逆时针旋转矩阵 {@code [a b; c d] = [cos, sin; -sin, cos]}，因此在屏幕上<b>正角度看起来
     * 也是逆时针</b>（PDF 的 +x 方向是屏幕向右，PDF 的 +y 方向是屏幕向上，右→上即逆时针）。</p>
     *
     * <p>实测证据：印章图做成「左上红/右上绿/左下蓝/右下黄」四象限，{@code rotation=+90}
     * 渲染后左上角呈<b>绿</b>（原右上象限移到左上），与「逆时针 90°」一致。</p>
     *
     * @param box      印章矩形
     * @param rotation 旋转角度（度，正值为屏幕上<b>逆时针</b>）
     * @return 变换矩阵；无需旋转时返回 null
     */
    public static Matrix rotationAboutCenter(StampBox box, int rotation) {
        int normalized = ((rotation % 360) + 360) % 360;
        if (normalized == 0) {
            return null;
        }
        double theta = Math.toRadians(normalized);
        float cx = box.x() + box.width() / 2f;
        float cy = box.y() + box.height() / 2f;
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);
        // p' = R·p + t，其中 t = c - R·c（把旋转中心从原点挪到印章中心）
        float e = cx - (cx * cos - cy * sin);
        float f = cy - (cx * sin + cy * cos);
        return new Matrix(cos, sin, -sin, cos, e, f);
    }

    /**
     * 落位参数合法性校验（服务端准入，不依赖前端校验）。
     *
     * @param placement 印章落位
     * @return 合法返回 null，否则返回可直接展示给用户的中文原因
     */
    public static String validatePlacement(SealPlacement placement) {
        if (placement == null) {
            return "印章落位不能为空";
        }
        if (placement.sealId() == null) {
            return "印章落位缺少印章ID";
        }
        if (placement.pageIndex() < 0) {
            return "页码不能为负数（页码从 0 开始）";
        }
        if (!inUnitRange(placement.xRatio()) || !inUnitRange(placement.yRatio())) {
            return "印章位置比例必须在 0..1 之间";
        }
        if (placement.wRatio() <= 0f || placement.hRatio() <= 0f) {
            return "印章宽高比例必须大于 0";
        }
        if (placement.wRatio() > 1f + EPSILON || placement.hRatio() > 1f + EPSILON) {
            return "印章宽高比例不能大于 1";
        }
        if (placement.xRatio() + placement.wRatio() > 1f + EPSILON) {
            return "印章右边缘超出页面（xRatio + wRatio 必须不大于 1）";
        }
        if (placement.yRatio() + placement.hRatio() > 1f + EPSILON) {
            return "印章下边缘超出页面（yRatio + hRatio 必须不大于 1）";
        }
        return null;
    }

    private static boolean inUnitRange(float ratio) {
        return ratio >= -EPSILON && ratio <= 1f + EPSILON;
    }
}
