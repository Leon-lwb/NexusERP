package com.nexus.common.sign.support.pdf;

import com.nexus.common.core.exception.ServiceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PDF 盖章（把印章图片画到指定页的指定位置）。
 *
 * <p><b>为什么盖章和签名分成两步（勿合并）：</b>{@code PDDocument#saveIncremental}
 * 有硬前置 —— 新建的内存文档直接增量保存会抛
 * {@code IllegalStateException: document was not loaded from a file or a stream}。
 * 所以流程必须是「盖完章先全量 save 一次拿到字节 → 再 loadPDF 回来做增量签名」，
 * 见 {@link PdfCmsSigner}。全程内存，无临时文件。</p>
 *
 * <p><b>不画任何文字：</b>本类只用 {@code drawImage}，因此
 * <b>不需要内置中文字体</b>（PDFBox 只支持 TrueType 字体，仓库里没有 CJK TTF，
 * 打包一个 8MB 字体属于独立决策，P2a 明确不做）。签署人/含义等信息一律写进
 * 数字签名的 X.509 主题与 PDF 签名域 {@code /Reason}，而不是画在页面上。</p>
 *
 * @author nexus
 */
public final class PdfContentStamper {

    /** PDF 文件魔数 */
    public static final String PDF_MAGIC = "%PDF-";

    private PdfContentStamper() {
    }

    /**
     * 一次盖章任务：一页 + 一个归一化落位 + 一张印章图片。
     *
     * @param pageIndex 页序号（从 0 开始）
     * @param xRatio    左边缘比例（原点左上）
     * @param yRatio    上边缘比例（原点左上，向下为正）
     * @param wRatio    宽度比例
     * @param hRatio    高度比例
     * @param rotation  旋转角度（度；<b>正值为屏幕上逆时针</b>，见 {@link PdfStampGeometry#rotationAboutCenter}）
     * @param image     印章图片字节（PNG/JPEG 等，PDFBox 可解码即可）
     */
    public record StampSpec(int pageIndex, float xRatio, float yRatio, float wRatio, float hRatio,
                            int rotation, byte[] image) {
    }

    /**
     * PDF 魔数校验。
     *
     * <p><b>为什么必须在服务端做：</b>验收标准要求「非 PDF 文件必须被服务端拒绝」——
     * 前端后缀名/预览都不可信，只有文件头是硬证据。放宽到「允许文件头前 1KB 内出现魔数」
     * 是因为 PDF 规范允许文件头之前有少量二进制垃圾，Acrobat 亦如此解析。</p>
     *
     * @param content 文件字节
     * @return 是 PDF 返回 true
     */
    public static boolean isPdf(byte[] content) {
        if (content == null || content.length < PDF_MAGIC.length()) {
            return false;
        }
        byte[] magic = PDF_MAGIC.getBytes(StandardCharsets.US_ASCII);
        int limit = Math.min(content.length - magic.length, 1024);
        for (int offset = 0; offset <= limit; offset++) {
            boolean hit = true;
            for (int i = 0; i < magic.length; i++) {
                if (content[offset + i] != magic[i]) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                return true;
            }
        }
        return false;
    }

    /**
     * 盖章：把每张印章图片画到对应页的对应位置，返回盖章后的完整 PDF 字节。
     *
     * <p>多页多章在一次文档遍历内完成：对每个 {@code (pageIndex, rect)} 开一次
     * {@code PDPageContentStream(doc, page, APPEND, true)} 调 {@code drawImage}，
     * 中途不需要多次保存。</p>
     *
     * @param srcPdf 原始 PDF 字节
     * @param specs  盖章任务列表（必须非空）
     * @return 盖章后的 PDF 字节
     * @throws ServiceException 非 PDF / 页码越界 / 图片无法解码 / 写出失败
     */
    public static byte[] stamp(byte[] srcPdf, List<StampSpec> specs) {
        if (!isPdf(srcPdf)) {
            throw new ServiceException("仅支持 PDF 文件");
        }
        if (specs == null || specs.isEmpty()) {
            throw new ServiceException("盖章位置不能为空");
        }
        try (PDDocument doc = Loader.loadPDF(srcPdf)) {
            int pageCount = doc.getNumberOfPages();
            for (StampSpec spec : specs) {
                if (spec.pageIndex() < 0 || spec.pageIndex() >= pageCount) {
                    throw new ServiceException("盖章页码超出文档范围（文档共 {} 页，请求第 {} 页）",
                        pageCount, spec.pageIndex() + 1);
                }
                PDPage page = doc.getPage(spec.pageIndex());
                PDImageXObject image;
                try {
                    image = PDImageXObject.createFromByteArray(doc, spec.image(), "seal");
                } catch (IOException | IllegalArgumentException e) {
                    // PDFBox 对「认不出格式/损坏」的图片抛的是 IllegalArgumentException 而非 IOException
                    // （javax.imageio 的 ImageIO.read 返回 null 后继续走 JPEG 分支）。
                    // 不捕获它就会把一个 500 + 内部异常名暴露给前端，而这是纯业务错误：印章图坏了。
                    throw new ServiceException("印章图片无法解析：{}", e.getMessage());
                }
                PdfStampGeometry.StampBox box = PdfStampGeometry.computeBox(
                    page.getMediaBox().getWidth(), page.getMediaBox().getHeight(),
                    spec.xRatio(), spec.yRatio(), spec.wRatio(), spec.hRatio());
                // 每次落位单独开一个内容流并立即关闭：PDFBox 明确不支持同一页同时打开多个
                // 内容流（后关闭的会重建 /Contents 数组，把先关闭的覆盖掉）。
                try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                    Matrix rotation = PdfStampGeometry.rotationAboutCenter(box, spec.rotation());
                    if (rotation == null) {
                        cs.drawImage(image, box.x(), box.y(), box.width(), box.height());
                    } else {
                        cs.saveGraphicsState();
                        cs.transform(rotation);
                        cs.drawImage(image, box.x(), box.y(), box.width(), box.height());
                        cs.restoreGraphicsState();
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ServiceException("PDF 盖章失败：{}", e.getMessage());
        }
    }
}
