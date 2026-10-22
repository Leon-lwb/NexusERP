package com.nexus.common.sign.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.domain.vo.EsSignDocumentVo;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 待签文档正文服务（P2a-3）。
 *
 * <p><b>这个接口解决什么：</b>{@link ESignatureService#signDocument} 与
 * {@link ESignatureService#verifyDocument} 在 P2a-2 就已实现，但 {@code es_sign_document}
 * 里从来没有过行 —— 没有入口把合同 PDF 登记进去，签章能力等于不可达。
 * 本接口就是那条入口：上传 → 校验 → 落对象存储 → 落版本行，之后签章/验签才有对象可用。</p>
 *
 * <p><b>版本语义：</b>同一 {@code (bizType, bizId)} 每次上传都追加一行、{@code doc_version} 递增，
 * 旧行 {@code is_current} 置 '0'；任一时刻该业务键下最多只有一行 {@code is_current='1'}。
 * 换版不删除、不覆盖旧正文，且旧正文的 SM3 仍留在行里 —— 已签出的历史文件随时可回溯到它当初签的是哪一版。</p>
 *
 * <p><b>正文来源（P2a-3 的取舍）：multipart 直传，不接受客户端提交的 ossId。</b>
 * 理由见 {@link #uploadDocument}。</p>
 *
 * @author nexus
 */
public interface IESSignDocumentService {

    /**
     * 上传/登记待签文档正文（PDF）。
     *
     * <p><b>为什么是 multipart 直传，而不是「前端先传 OSS，再提交 ossId」：</b>
     *
     * <ul>
     *   <li><b>校验不可能被绕过</b>：字节在同一次请求里被服务端读到并当场验魔数， 不存在「先落了一个任意格式的对象、再事后登记」的时间窗；
     *   <li><b>不把别人的对象登记成自己的合同</b>：{@code ossId} 是全局自增的， 任何登录用户都能通过既有 OSS 上传接口拿到 ossId。若凭 ossId 登记，
     *       调用方只需猜/换一个数字就能把别的租户/别人的对象挂到自己的业务单据上；
     *   <li>一次往返完成，前端不用先调 OSS 再调签章。
     * </ul>
     *
     * <p>字段名沿用本仓库既有的 multipart 约定 {@code file}（与 {@code SysOssController}/{@code
     * CvDetectController} 一致）。
     *
     * <p><b>服务端准入（全部排在第一次写入之前，被拒绝的请求天然什么都不写）：</b> 文件名非空且以 {@code .pdf} 结尾 → 声明的内容类型若为具体类型必须是 PDF →
     * 读到的字节数不超过 10MB → 字节头必须是 {@code %PDF-} → 能被 PDFBox 解析出页数。任何一条不过都抛 {@code
     * ServiceException}，绝不降级登记。
     *
     * @param bizType 业务类型（必填，见 {@code SignBizType}）
     * @param bizId 业务单据ID（必填）
     * @param file 上传的 PDF 文件（multipart 字段名 {@code file}）
     * @return 新登记的文档ID（{@code es_sign_document.doc_id}）
     * @throws ServiceException 入参/文件不合法、 取不到换版锁、对象存储 SPI 缺失
     */
    Long uploadDocument(String bizType, Long bizId, MultipartFile file);

    /**
     * 按业务键查询文档列表（版本倒序，最新的排在最前）。
     *
     * @param bizType 业务类型（必填）
     * @param bizId   业务单据ID（可选；为空时返回该业务类型下的全部文档）
     * @return 文档列表（无数据时为空列表，不返回 null）
     */
    List<EsSignDocumentVo> listByBiz(String bizType, Long bizId);

    /**
     * 查询业务键下当前生效版本的文档。
     *
     * @param bizType 业务类型（必填）
     * @param bizId   业务单据ID（必填）
     * @return 当前生效文档；该业务键尚无文档时返回 {@code null}
     */
    EsSignDocumentVo getCurrent(String bizType, Long bizId);

    /**
     * 按文档ID查询。
     *
     * @param docId 文档ID
     * @return 文档详情
     * @throws ServiceException 文档不存在（含已逻辑删除）
     */
    EsSignDocumentVo getByDocId(Long docId);
}
