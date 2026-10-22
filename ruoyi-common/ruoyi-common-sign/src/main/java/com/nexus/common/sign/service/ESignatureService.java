package com.nexus.common.sign.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.domain.dto.DocSignCommand;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.DocumentVerifyVo;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;

import java.util.List;

/**
 * 电子签名统一入口。
 *
 * <p>P1 是「逻辑签名」（不产出新文件）；P2 通过 {@link #signDocument} 扩展出
 * 「PDF 盖章 + 自签证书数字签名」，并用 {@link #verifyDocument} 做文档级验签。</p>
 *
 * @author nexus
 */
public interface ESignatureService {

    /**
     * 逻辑签名：审批签批 / SOP 查阅 / NCR 处置。
     *
     * @param cmd 签名入参
     * @return 签名ID（回填业务表的 signature_id 字段）
     */
    Long sign(SignCommand cmd);

    /**
     * 独立事务签名（REQUIRES_NEW）。
     * <p>供"签署失败可降级、不得拖垮调用方事务"的场景使用（如 MES SOP 查阅确认）。
     * 失败只回滚自身事务，不会把调用方事务标记为 rollback-only。</p>
     *
     * <p><b>为什么必须存在这个入口（勿删勿简化）：</b>{@link #sign} 的默认传播行为是
     * {@code REQUIRED} —— 它会<b>加入</b>调用方事务。若调用方仅用 try/catch 包住
     * {@code sign(...)} 以求"失败即降级"，签名方法抛出的
     * {@code RuntimeException} 会在抛出前先把<b>共享</b>事务标记为 rollback-only，
     * 调用方的 catch 只能吞掉异常、吞不掉这个标记：调用方后续的写入看起来成功，
     * 提交时却抛 {@code UnexpectedRollbackException}，把本该被保护的业务写
     * （如查阅记录落库）一并回滚 —— 即"降级"落空。改用本方法后签名自开事务，
     * 失败只影响它自己，调用方的 {@code try/catch} 才真正具备降级语义。</p>
     *
     * <p>注意：事务边界只在 Spring 代理调用处生效，同类内部方法调用不会开启新事务。</p>
     *
     * @param cmd 签名入参
     * @return 签名ID
     */
    Long signInNewTransaction(SignCommand cmd);

    /**
     * 验签：校验单条记录自身哈希 + 与上一条的链接。
     */
    VerifyResultVo verify(Long signatureId);

    /**
     * 全链校验：对该业务单据的全部签名按序重算并校验链接。
     */
    VerifyResultVo verifyChain(String bizType, Long bizId);

    /**
     * 按业务查询签名列表（按签署时间正序）。
     */
    List<SignatureVo> listByBiz(String bizType, Long bizId);

    /**
     * 文档签章：给 {@code es_sign_document} 里的 PDF 盖章并施加数字签名，产出新文件。
     *
     * <p><b>流程</b>（全程内存，无临时文件）：</p>
     * <ol>
     *   <li>校验入参（业务类型/签署含义/认证方式非空、落位非空且比例合法）；</li>
     *   <li>按 {@link DocSignCommand#placements()} 去重后的每个印章做<b>用章授权</b>
     *       （与 {@link #sign} 共用同一段判定逻辑，不复制规则）；</li>
     *   <li>二级认证（{@link SignAuthVerifier}，认证失败即拒绝、不降级）；</li>
     *   <li>读源正文，服务端校验 <b>{@code %PDF-} 魔数</b>（非 PDF 一律拒绝，
     *       不能只靠前端/后缀名），并重算 SM3 与 {@code es_sign_document.file_hash}
     *       比对 —— 源文件被换过就报错，绝不「签一个不是它该签的东西」；</li>
     *   <li>读印章原图（{@code es_seal.seal_oss_id}，服务端私有桶）→ 盖章 → 数字签名；</li>
     *   <li>签署后文件写入对象存储，得到新的 {@code ossId}；</li>
     *   <li>在<b>同一事务</b>内、在<b>同一把链锁</b>内写 {@code es_signature}
     *       （复用哈希链路径，落 {@code srcDocOssId/srcDocHash/docOssId/docHash/docId}），
     *       并回填 {@code es_sign_document.signed_oss_id/sign_status}。</li>
     * </ol>
     *
     * <p><b>为什么「签名」与「台账写入」必须同一事务：</b>否则会出现「有签名文件、
     * 没有签名台账」的状态 —— 业务方拿到一份无法追溯的文件，比签章失败严重得多。
     * 反过来，台账写入失败会连文件一起回滚（对象存储里的字节无法回滚，会留下
     * 一个无台账引用的孤儿对象，这是可接受的代价）。</p>
     *
     * <p><b>证书决策（P2a-2 明确取舍）：</b>每次签署现场生成 RSA-2048 密钥对 +
     * <b>自签</b> X.509 证书，证书内嵌进 CMS（{@code Certs} 字段），因此签名是
     * <b>自包含</b>的 —— 不需要证书表、不需要改库表结构。<b>信任链校验明确不在本阶段范围</b>：
     * 自签证书在 Acrobat 中显示「有效性未知/信任未知」，但<b>完整性（文档是否被改动）
     * 校验完全正常</b>，这正是本阶段的目标。将来要做 CA 签发/密钥托管，只需替换
     * {@code SelfSignedCertificateFactory}，本方法签名不变。</p>
     *
     * <p><b>事务与数据源边界：</b>与 {@link #sign} 相同（类级 {@code @DS("master")}、
     * {@code REQUIRED}）。若调用方已在<b>另一个数据源</b>上开着事务，本方法会加入该事务
     * 并打到业务库 —— 那种场景请先用 {@link #signInNewTransaction} 的思路另开独立事务，
     * 不要指望类级注解穿透已绑定连接的事务。</p>
     *
     * @param cmd 文档签章入参
     * @return 新写入的签名ID（同时是 {@code es_signature} 台账主键）
     */
    Long signDocument(DocSignCommand cmd);

    /**
     * 文档级验签：校验 {@code es_signature.doc_oss_id} 指向的签署后 PDF 的完整性。
     *
     * <p>做<b>两条互补且缺一不可</b>的检查（PDFBox 没有任何验签 API，全部自研）：
     *
     * <ol>
     *   <li><b>CMS/PKCS#7 验签</b> —— 覆盖区间内被等长改写时抛 {@code
     *       CMSSignerDigestMismatchException}，这是「文档已被修改」的精确信号；
     *   <li><b>ByteRange 覆盖 + 增量修订检查</b> —— 签名后追加裸字节或追加真实修订时 CMS
     *       验签<b>依然通过</b>，只有覆盖检查能发现；并用「未覆盖区是否出现新的 {@code startxref}/{@code
     *       %%EOF}」区分「追加修订」（实质篡改）与「裸字节」（提示级）。
     * </ol>
     *
     * <p>另叠加一条台账级旁证：重算签署后文件的 SM3 与 {@code es_signature.doc_hash} 比对。
     *
     * @param signatureId 签名记录ID（必须是 {@link #signDocument} 产出的含文档签名）
     * @return 验签结果（含结论、两条检查的通过情况与观测值）
     * @throws ServiceException 记录不存在 / 不含文档 / 文档内容缺失 / 文档不是 PDF 或无法解析
     */
    DocumentVerifyVo verifyDocument(Long signatureId);
}
