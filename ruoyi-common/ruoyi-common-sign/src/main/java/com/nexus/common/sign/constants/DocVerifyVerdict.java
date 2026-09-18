package com.nexus.common.sign.constants;

/**
 * 文档级验签结论（P2）。
 *
 * <p>PDFBox 核心包<b>没有任何验签 API</b>，本结论由两条<b>缺一不可</b>的检查合成
 * （见 {@code com.nexus.common.sign.support.pdf.PdfDocumentVerifier}）：</p>
 * <ol>
 *   <li><b>CMS/PKCS#7 验签</b> —— 检出「签名覆盖区间内被等长改写」。
 *       这是 {@code CMSSignerDigestMismatchException} 的精确触发点。</li>
 *   <li><b>ByteRange 覆盖 + 增量修订检查</b> —— 检出「签名之后又追加内容」。
 *       只做 CMS 验签会<b>漏报</b>这一类：spike 实测「尾部追加裸字节」与
 *       「签名后 saveIncremental 追加真实修订」两种情况下 {@code verify()} 仍为 true。</li>
 * </ol>
 *
 * @author nexus
 */
public enum DocVerifyVerdict {

    /** 签名完整：CMS 验签通过，且文件没有任何未被签名覆盖的字节。 */
    OK,

    /**
     * 覆盖区间内的内容被改写（{@code CMSSignerDigestMismatchException}）。
     * <b>最高级别的篡改信号</b> —— 被签名的字节已经与签名时的内容不一致。
     */
    MODIFIED_IN_PLACE,

    /**
     * 签名之后文档又被追加了一个<b>新的 PDF 修订</b>（尾部出现新的 {@code startxref}/{@code %%EOF}）。
     * CMS 摘要仍然对得上（覆盖区间未被改动），但文件已经不是签名时的那个文件：
     * 阅读器会把它读成「签名后又修改过」。属于<b>实质篡改</b>。
     */
    REVISION_APPENDED,

    /**
     * 签名之后追加了若干字节，但<i>没有</i>形成新的 PDF 修订（没有新的 {@code startxref}/{@code %%EOF}，
     * 通常也没有 xref 指向它们）。阅读器会忽略这些字节，Acrobat 大概率仍显示「未修改」，
     * 因此<b>与原地改写不是同一量级</b>：这里只提示「存在未被签名覆盖的字节」，不判定为篡改。
     */
    UNSIGNED_TRAILING_BYTES,

    /**
     * 签名覆盖范围超出文件长度 —— 文件被整体重写/截断（典型是「改完再全量 save 一次」），
     * 被签名的原始字节已经不存在，签名彻底失效。
     */
    TRUNCATED,

    /**
     * CMS 层就验不过，但不是摘要不匹配（证书缺失、签名算法不支持、CMS 结构损坏等）。
     */
    INVALID_SIGNATURE,

    /** 文件本身没有签名（或签名域存在但没有任何 CMS 内容）。 */
    NO_SIGNATURE
}
