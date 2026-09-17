package com.nexus.common.sign.domain.vo;

import com.nexus.common.sign.constants.DocVerifyVerdict;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档级验签结果（P2）。
 *
 * <p>字段命名与 {@code PdfVerifyOutcome} 一一对应（服务层按属性名拷贝），
 * 两者不要各自改名，否则 {@code BeanUtils.copyProperties} 会静默丢字段。</p>
 *
 * @author nexus
 */
@Data
public class DocumentVerifyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 签名记录ID */
    private Long signatureId;

    /** 关联文档ID（{@code es_sign_document.doc_id}） */
    private Long docId;

    /** 总体是否通过：{@link DocVerifyVerdict#OK} 且台账摘要一致时为 true */
    private Boolean passed;

    /** 台账摘要（{@code es_signature.doc_hash}，签署后文件的 SM3） */
    private String docHash;

    /** 实际算出的签署后文件 SM3 */
    private String actualDocHash;

    /** 实际文件内容是否仍与台账登记的 SM3 一致（台账级判断，与 CMS 互为旁证） */
    private Boolean hashPassed;

    /** 综合结论 */
    private DocVerifyVerdict verdict;

    /** CMS/PKCS#7 验签是否通过（检查①） */
    private boolean cmsPassed;

    /** 签名是否覆盖了整个文件（检查②：ByteRange 覆盖） */
    private boolean coveragePassed;

    /** 是否检出「覆盖区间内被等长改写」（{@code CMSSignerDigestMismatchException}） */
    private boolean inPlaceModified;

    /** 是否检出「签名之后又追加了新的 PDF 修订」 */
    private boolean revisionAppended;

    /** 文件总字节数 */
    private int fileSize;

    /** 签名覆盖区间的末端偏移（{@code byteRange[2] + byteRange[3]}） */
    private int coveredEnd;

    /** PDF 签名原始 ByteRange（{@code [start1, len1, start2, len2]}） */
    private int[] byteRange;

    /** 未被签名覆盖的尾部字节数 */
    private int trailingBytes;

    /** 全文件的 {@code %%EOF} 出现次数（签名后每追加一个修订 +1） */
    private int eofCount;

    /** 未覆盖尾部内的 {@code %%EOF} 次数（&gt;0 说明追加的是真实修订） */
    private int eofCountInTail;

    /** 全文件的 {@code startxref} 出现次数 */
    private int startxrefCount;

    /** 未覆盖尾部内的 {@code startxref} 次数 */
    private int startxrefCountInTail;

    /** 文件中签名域/签名字典的个数（本实现固定只签一个） */
    private int signatureCount;

    /** 签名者证书主题（自签证书，CN 为签署人姓名快照） */
    private String signerSubject;

    /** 签名者证书颁发者（自签时与主题相同） */
    private String signerIssuer;

    /** CMS 摘要算法（如 SHA-256） */
    private String digestAlgorithm;

    /** CMS 签名算法（如 SHA256withRSA） */
    private String signatureAlgorithm;

    /** PDF 签名域中记录的签署时间（{@code /M}） */
    private String signedAt;

    /** 明细说明（人可读，含观测到的具体数值） */
    private List<String> messages = new ArrayList<>();
}
