package com.nexus.common.sign.domain.dto;

/**
 * 印章落位（P2 PDF 盖章）。
 *
 * <p><b>坐标约定（本类唯一易错点，务必先读）：</b>五个比例全部是
 * <b>归一化到 [0,1] 的小数</b>，原点在页面 <b>左上角</b>，x 向右、y 向下
 * —— 与前端画布/浏览器 CSS 的坐标系一致，UI 不需要做任何换算。</p>
 *
 * <pre>
 * (0,0) ┌──────────────────┐
 *       │  ┌────┐          │   xRatio     印章左边缘   / 页宽
 *       │  │印章│          │   yRatio     印章上边缘   / 页高   ← 从上往下量
 *       │  └────┘          │   wRatio     印章宽度     / 页宽
 *       │                  │   hRatio     印章高度     / 页高
 *       └──────────────────┘ (1,1)
 * </pre>
 *
 * <p><b>PDF 用户空间的原点在左下角、y 向上</b>，因此服务端必须翻转 y：
 * <b>先把 y 翻过来，再减去印章自身高度</b>（{@code y = pageHeight - yRatio * pageHeight - h}）。
 * 漏掉「再减自身高度」会让印章整体上移一个自身高度，这是本功能最常见的坐标缺陷；
 * 换算公式与证伪结论见 {@code PdfStampGeometry#computeBox} 与
 * {@code doc/system/specs/2026-09-16-pdfbox-spike-report.md}（Q4：40/40 像素回读命中）。</p>
 *
 * @param pageIndex 页序号（<b>从 0 开始</b>，与 PDFBox {@code PDDocument#getPage} 一致）
 * @param xRatio    左边缘位置比例（0..1，原点左上）
 * @param yRatio    上边缘位置比例（0..1，原点左上，<b>向下为正</b>）
 * @param wRatio    印章宽度比例（&gt;0 且 xRatio+wRatio ≤ 1）
 * @param hRatio    印章高度比例（&gt;0 且 yRatio+hRatio ≤ 1）
 * @param rotation  旋转角度（度；<b>正值为屏幕上逆时针</b>，非 0 时绕印章自身中心旋转，0 表示不旋转）
 * @param sealId    所用印章ID（必填，见 {@code es_seal.seal_id}）
 * @author nexus
 */
public record SealPlacement(
    int pageIndex,
    float xRatio,
    float yRatio,
    float wRatio,
    float hRatio,
    int rotation,
    Long sealId
) {
}
