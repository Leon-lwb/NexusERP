package com.nexus.common.sign.support.pdf;

import com.nexus.common.sign.constants.DocVerifyVerdict;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯 PDF 层验签结果（不涉及数据库；由服务层补上 signatureId/docId/台账摘要后返回给前端）。
 *
 * <p>字段名与 {@code DocumentVerifyVo} 一一对应，服务层按属性名拷贝。</p>
 *
 * @author nexus
 */
@Data
public class PdfVerifyOutcome {

    /** 综合结论 */
    private DocVerifyVerdict verdict;

    /** 检查①：CMS/PKCS#7 验签是否通过 */
    private boolean cmsPassed;

    /** 检查②：签名是否覆盖整个文件 */
    private boolean coveragePassed;

    /** 是否检出「覆盖区间内被等长改写」（{@code CMSSignerDigestMismatchException}） */
    private boolean inPlaceModified;

    /** 是否检出「签名之后又追加了新的 PDF 修订」 */
    private boolean revisionAppended;

    /** 文件总字节数 */
    private int fileSize;

    /** 签名覆盖区间末端偏移 */
    private int coveredEnd;

    /** PDF 签名原始 ByteRange（{@code [start1, len1, start2, len2]}） */
    private int[] byteRange;

    /** 未被签名覆盖的尾部字节数 */
    private int trailingBytes;

    /** 全文件 {@code %%EOF} 次数 */
    private int eofCount;

    /** 未覆盖尾部内的 {@code %%EOF} 次数 */
    private int eofCountInTail;

    /** 全文件 {@code startxref} 次数 */
    private int startxrefCount;

    /** 未覆盖尾部内的 {@code startxref} 次数 */
    private int startxrefCountInTail;

    /** 文档中的签名字典个数 */
    private int signatureCount;

    /** 签名者证书主题 */
    private String signerSubject;

    /** 签名者证书颁发者 */
    private String signerIssuer;

    /** 摘要算法 */
    private String digestAlgorithm;

    /** 签名算法 */
    private String signatureAlgorithm;

    /** PDF 签名域的签署时间（{@code /M}） */
    private String signedAt;

    /** 明细说明 */
    private List<String> messages = new ArrayList<>();
}
