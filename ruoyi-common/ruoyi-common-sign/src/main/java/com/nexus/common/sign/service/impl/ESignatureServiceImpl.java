package com.nexus.common.sign.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.ServletUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.DocVerifyVerdict;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.DocSignCommand;
import com.nexus.common.sign.domain.dto.SealPlacement;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.DocumentVerifyVo;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.common.sign.support.SealAvailabilityResolver;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;
import com.nexus.common.sign.support.pdf.PdfCmsSigner;
import com.nexus.common.sign.support.pdf.PdfContentStamper;
import com.nexus.common.sign.support.pdf.PdfDocumentVerifier;
import com.nexus.common.sign.support.pdf.PdfStampGeometry;
import com.nexus.common.sign.support.pdf.PdfVerifyOutcome;
import com.nexus.common.sign.support.pdf.SelfSignedCertificateFactory;
import com.nexus.common.sign.util.Sm3Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 电子签名核心实现。
 *
 * <p>签名记录 <b>append-only</b>：ID 由 {@link IdWorker} 在 insert 之前预生成，
 * 因为 chain_hash 的取值包含 signature_id —— 若依赖数据库回填自增 ID，
 * 就必须 insert 后再 update 一次，与「签名记录一经写入不可更改」相冲突。</p>
 *
 * <p>哈希链并发控制：链尾读取 + 写入必须在同一临界区内完成，否则并发签名会
 * 读到同一个前驱从而分叉。临界区按租户加锁，且锁内只做「读链尾 + 写一条」。</p>
 *
 * <p>事务边界：{@link #sign} 加入调用方事务（REQUIRED），{@link #signInNewTransaction}
 * 自开新事务（REQUIRES_NEW）—— 后者供「签署失败可降级、不得拖垮调用方事务」的场景使用。
 * 两者共用 {@link #doSign}，签名逻辑只有一份。</p>
 *
 * <p><b>数据源（类级 {@code @DS("master")}，勿删）：</b>es_seal / es_seal_grant /
 * es_sign_document / es_signature 四张表<b>只存在于 ry-vue 库</b>，即本项目的
 * <b>主数据源 master</b>（{@code dynamic.datasource.primary: master}）。若不固定，
 * 本服务的查询会继承调用方的数据源上下文：MES 模块类级 {@code @DS("mes")} 会让
 * {@code appendSignature} 打到 ry-mes 库，报
 * {@code Table 'ry-mes.es_signature' doesn't exist}（线上实测：SOP 查阅登记
 * 因此静默降级、signature_id 长留 NULL）。签名库与业务库<b>不共库</b>，
 * 故此处必须与调用方解耦。</p>
 *
 * <p><b>为什么是类级而不是逐方法：</b>dynamic-datasource 4.3.1 的
 * {@code DataSourceClassResolver#computeDatasource} 在「实现类方法」之后即回落到
 * 「实现类本身」的注解，且 {@code DynamicDataSourceAnnotationAdvisor} 的切点
 * 由 {@code AnnotationMatchingPointcut(DS.class, true)} 构建（类级注解匹配整类全部方法），
 * 故类级注解对每一个<b>外部经代理调用</b>的公开方法都生效 —— 后续新增方法不会漏挂。
 * 本仓库大量既有先例（如 {@code WfProcessServiceImpl}/IotProductServiceImpl 等的类级 @DS）
 * 也印证该版本支持类级。切面 order 为 {@code Ordered.HIGHEST_PRECEDENCE}
 * （{@code DynamicDatasourceAopProperties.order}），即 @DS 比 @Transactional
 * <b>更外层</b>：数据源键在事务开启、取连接之前就已入栈，因此
 * {@link #signInNewTransaction} 的 REQUIRES_NEW 新事务会真正落到 master。</p>
 *
 * <p><b>同类自调用不触发 AOP：</b>{@link #doSign} 是 private、由两个公开入口内部调用，
 * 它本身不需要注解 —— 数据源键由<b>最外层公开方法</b>（{@code sign} /
 * {@code signInNewTransaction} / {@code verify} / {@code verifyChain} / {@code listByBiz}）
 * 的类级注解压栈，贯穿整段调用链。切勿把这些公开入口改成内部调用而绕过代理。</p>
 *
 * <p><b>已知边界（REQUIRED 的固有性质）：</b>{@link #sign} 传播行为是 REQUIRED，
 * 若调用方已在<b>另一个数据源</b>上开着事务（连接已绑定，@DS 切换在事务内不生效），
 * {@code sign} 仍会加入该事务而打到业务库。当前唯一调用方
 * {@code WfTaskServiceImpl#completeTask} 无类级 @DS（默认即 master），不受影响。
 * 若将来出现「MES/ERP 事务内调用 sign」的场景，必须改用
 * {@link #signInNewTransaction}（独立事务）或参照
 * {@code MessageRecordService} 的 {@code @DS("master") + REQUIRES_NEW} 模式，
 * 不能指望类级注解穿透一个已在异库绑定连接的事务。</p>
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS("master")
public class ESignatureServiceImpl implements ESignatureService {

    /** 哈希链串行化锁 key 前缀（按租户隔离） */
    private static final String CHAIN_LOCK_PREFIX = "es:sign:chain:";
    /** 锁持有时长(ms) */
    private static final long LOCK_EXPIRE = 30_000L;
    /** 获取锁超时(ms) */
    private static final long LOCK_ACQUIRE_TIMEOUT = 10_000L;
    /** 签署含义最大长度 */
    private static final int MEANING_MAX_LEN = 128;
    /** {@code es_sign_document.sign_status}：已签 */
    private static final String SIGN_STATUS_SIGNED = "2";
    /** {@code es_sign_document.is_current}：当前生效版本（非当前版本禁止签署） */
    private static final String IS_CURRENT_YES = "1";
    /** {@code es_signature.sign_ua} 列宽（varchar(512)）：超长 UA 会因严格模式 insert 失败卡死签署 */
    private static final int SIGN_UA_MAX_LEN = 512;
    /** 自签证书主题的组织单元 */
    private static final String CERT_ORG_UNIT = "Nexus-ERP 电子签章";

    private final EsSignatureMapper signatureMapper;
    private final EsSealMapper sealMapper;
    private final EsSealGrantMapper grantMapper;
    private final SignAuthVerifier authVerifier;
    private final LockTemplate lockTemplate;
    private final EsSignDocumentMapper documentMapper;
    /**
     * 对象存储读写 SPI。
     * <p>按 SPI 契约用 {@link ObjectProvider} 注入：实现方在 {@code ruoyi-system}，
     * 缺失时给出明确异常而不是启动期炸弹/NPE。P1 的逻辑签名不需要它，所以允许为 null。</p>
     */
    private final ObjectProvider<OssContentAccessor> ossAccessorProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sign(SignCommand cmd) {
        return doSign(cmd);
    }

    /**
     * 独立事务签名（REQUIRES_NEW）。
     *
     * <p>与 {@link #sign} 共用 {@link #doSign}，签名逻辑只有一份、不会漂移；
     * 唯一差别是事务边界：本方法挂起调用方事务、自开一个新事务，因此失败只回滚自身，
     * 不会把调用方事务污染成 rollback-only。供"签署失败可降级"的场景使用（如 SOP 查阅确认）。</p>
     *
     * @param cmd 签名入参
     * @return 签名ID
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long signInNewTransaction(SignCommand cmd) {
        return doSign(cmd);
    }

    /**
     * 签名主体（两个入口共用，保证事务边界之外的行为完全一致）。
     *
     * <p>不做任何事务声明：事务语义完全由公开入口决定。</p>
     *
     * @param cmd 签名入参
     * @return 签名ID
     */
    private Long doSign(SignCommand cmd) {
        validate(cmd);
        Long userId = LoginHelper.getUserId();
        String tenantId = LoginHelper.getTenantId();
        Long deptId = LoginHelper.getDeptId();
        assertSigningContext(tenantId, userId);

        EsSeal seal = null;
        if (cmd.sealId() != null) {
            seal = authorizeSeal(cmd.sealId(), userId, deptId, cmd.bizType());
        }

        // 认证失败即拒绝，不允许降级为「无凭证签名」
        authVerifier.verify(userId, cmd.authMethod(), cmd.credential());

        LockInfo lock = lockTemplate.lock(CHAIN_LOCK_PREFIX + (tenantId == null ? "" : tenantId),
            LOCK_EXPIRE, LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("签名通道繁忙，请稍后重试");
        }
        boolean releaseDeferred = false;
        try {
            Long signatureId = appendSignature(cmd, seal, userId, deptId);
            releaseDeferred = releaseLockAfterTransaction(lock);
            return signatureId;
        } finally {
            if (!releaseDeferred) {
                lockTemplate.releaseLock(lock);
            }
        }
    }

    /**
     * 签署上下文守卫：租户/用户缺失时<b>快速失败</b>，而不是静默写出脏数据。
     *
     * <p>es_signature 的 {@code tenant_id}/{@code signer_user_id} 均为 NOT NULL，
     * 而非请求线程（MQ 消费者、@Async 等）调用签名时 Sa-Token 的 ThreadLocal 不会传播：
     * 不守卫的表现是锁 key 退化为公共前缀、多租户插件失去租户过滤、insert 直接报错 ——
     * 调用方（如 SOP 查阅登记）捕获异常降级后 {@code signature_id} 长留 NULL，
     * 与本模块曾实测的「数据源漂移」是同型故障。这里把失败点前移到入口并给出可定位的报错。</p>
     *
     * @param tenantId 当前租户ID
     * @param userId   当前用户ID
     */
    private void assertSigningContext(String tenantId, Long userId) {
        if (StringUtils.isBlank(tenantId) || userId == null) {
            throw new ServiceException("签署上下文缺失（租户或用户未识别）：异步线程调用签名前必须显式传播租户与用户上下文");
        }
    }

    /**
     * 把链锁的释放推迟到当前事务结束（提交或回滚）之后。
     *
     * <p><b>为什么不能直接在 finally 里放锁：</b>finally 在<b>提交之前</b>执行，锁一放，
     * 排队的下一个签名者立刻进来，但 MySQL 默认 REPEATABLE READ 下它读到的仍是
     * 「上一条链记录尚未提交」的快照 —— 两条记录取到同一个 {@code prev_hash}，链就此分叉，
     * 且 {@code prev_hash} 无唯一约束拦截，此后 verifyChain 对分叉后代永远报断链。
     * 对 {@link #sign}（REQUIRED 加入调用方事务）窗口尤其大：返回后调用方还要做完
     * 整段 Flowable 办结才提交。与 {@code EsSignDocumentServiceImpl#uploadDocument}
     * 的换版锁同一处理方式（该处注释详述了原理）。</p>
     *
     * <p>无事务上下文时（直连调用、单元测试）同步机制不可用，返回 {@code false}
     * 由调用方在 {@code finally} 里立即释放。</p>
     *
     * @param lock 待释放的锁
     * @return true 表示已注册延迟释放（调用方不得再释放一次）
     */
    private boolean releaseLockAfterTransaction(LockInfo lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                lockTemplate.releaseLock(lock);
            }
        });
        return true;
    }

    /**
     * 用章授权校验（<b>{@link #sign} 与 {@link #signDocument} 共用，防止两处规则漂移</b>）。
     *
     * <p>规则本身仍只有一份：{@link SealAvailabilityResolver#isUsable}（个人章仅归属人可用；
     * 公司章必须有该用户/其部门在有效期内的启用授权）。本方法只负责「取印章 + 取授权 + 判定 +
     * 抛业务异常」这段样板，任何新增的盖章入口都必须走这里，不得自己再写一遍。</p>
     *
     * @param sealId  印章ID
     * @param userId  当前用户ID
     * @param deptId  当前用户部门ID
     * @param bizType 本次业务类型
     * @return 校验通过的印章实体
     */
    private EsSeal authorizeSeal(Long sealId, Long userId, Long deptId, String bizType) {
        EsSeal seal = sealMapper.selectById(sealId);
        if (seal == null) {
            throw new ServiceException("印章不存在");
        }
        List<EsSealGrant> grants = grantMapper.selectList(Wrappers.<EsSealGrant>lambdaQuery()
            .eq(EsSealGrant::getSealId, seal.getSealId()));
        if (!SealAvailabilityResolver.isUsable(seal, grants, userId, deptId, bizType)) {
            throw new ServiceException("当前用户无权使用印章【{}】", seal.getSealName());
        }
        return seal;
    }

    @Override
    public VerifyResultVo verify(Long signatureId) {
        EsSignature cur = signatureMapper.selectById(signatureId);
        if (cur == null) {
            throw new ServiceException("签名记录不存在");
        }
        VerifyResultVo result = new VerifyResultVo();
        result.setCheckedCount(1);
        String broken = checkRecord(cur);
        if (broken != null) {
            result.setPassed(false);
            result.setChainPassed(false);
            result.setBrokenAt(signatureId);
            result.getMessages().add(broken);
            return result;
        }
        result.setPassed(true);
        result.setChainPassed(true);
        result.getMessages().add("签名记录完整，链哈希校验通过");
        return result;
    }

    @Override
    public VerifyResultVo verifyChain(String bizType, Long bizId) {
        List<EsSignature> records = signatureMapper.selectList(Wrappers.<EsSignature>lambdaQuery()
            .eq(EsSignature::getBizType, bizType)
            .eq(bizId != null, EsSignature::getBizId, bizId)
            .orderByAsc(EsSignature::getSignatureId));

        VerifyResultVo result = new VerifyResultVo();
        if (records == null || records.isEmpty()) {
            result.setPassed(true);
            result.setChainPassed(true);
            result.setCheckedCount(0);
            result.getMessages().add("该业务暂无签名记录，无需校验");
            return result;
        }
        // 逐条独立校验：自身哈希可复现 + 与本条在「租户全局链」上的真实前驱衔接。
        // 绝不能拿「同业务范围内上一条」当预期前驱 —— 见 checkRecord 的说明。
        int checked = 0;
        for (EsSignature cur : records) {
            checked++;
            String broken = checkRecord(cur);
            if (broken != null) {
                result.setPassed(false);
                result.setChainPassed(false);
                result.setBrokenAt(cur.getSignatureId());
                result.setCheckedCount(checked);
                result.getMessages().add("链校验在 signatureId=" + cur.getSignatureId() + " 处断裂");
                result.getMessages().add(broken);
                return result;
            }
        }
        result.setPassed(true);
        result.setChainPassed(true);
        result.setCheckedCount(records.size());
        result.getMessages().add("共校验 " + records.size() + " 条签名记录，链完整");
        return result;
    }

    @Override
    public List<SignatureVo> listByBiz(String bizType, Long bizId) {
        return signatureMapper.selectVoList(Wrappers.<EsSignature>lambdaQuery()
            .eq(EsSignature::getBizType, bizType)
            .eq(bizId != null, EsSignature::getBizId, bizId)
            .orderByAsc(EsSignature::getSignedAt));
    }

    /**
     * 文档签章：盖章 + 自签证书数字签名（详见接口 javadoc）。
     *
     * <p><b>顺序是刻意设计的：</b>校验与授权 → 认证 → 读源正文并核对摘要 → 盖章/签名
     * （纯内存，不产生任何副作用）→ 写对象存储 → 加链锁写台账 + 回填文档表。
     * 所有可能失败的<b>判定</b>都排在第一次写入之前，因此「被拒绝」的操作天然什么都不写。</p>
     *
     * @param cmd 文档签章入参
     * @return 签名ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long signDocument(DocSignCommand cmd) {
        validateDocCommand(cmd);
        Long userId = LoginHelper.getUserId();
        String tenantId = LoginHelper.getTenantId();
        Long deptId = LoginHelper.getDeptId();
        assertSigningContext(tenantId, userId);

        EsSignDocument doc = documentMapper.selectById(cmd.docId());
        if (doc == null) {
            throw new ServiceException("待签文档不存在");
        }
        if (!IS_CURRENT_YES.equals(doc.getIsCurrent())) {
            throw new ServiceException("该正文版本已被新版本取代，不能签署（当前生效版本请在详情页重新打开）");
        }
        if (StringUtils.isNotBlank(cmd.bizType()) && !cmd.bizType().equals(doc.getBizType())) {
            throw new ServiceException("文档与业务类型不匹配");
        }
        if (cmd.bizId() != null && !cmd.bizId().equals(doc.getBizId())) {
            throw new ServiceException("文档与业务单据不匹配");
        }

        // 用章授权：按印章去重（同一次签章可能多处盖同一个章），规则与 sign 完全同一段代码
        Map<Long, EsSeal> seals = new LinkedHashMap<>();
        for (SealPlacement placement : cmd.placements()) {
            seals.computeIfAbsent(placement.sealId(),
                id -> authorizeSeal(id, userId, deptId, cmd.bizType()));
        }

        // 认证失败即拒绝，不允许降级为「无凭证签名」
        authVerifier.verify(userId, cmd.authMethod(), cmd.credential());

        // 多方签署基准：已产生签署后文件时在其上<b>增量</b>盖章（保留先前签署人的章与 CMS 签名），
        // 首次签署才以原始上传件为基准。若始终基于原始件重盖，后签者的产物会静默丢弃先签者的全部签章。
        Long baseOssId = doc.getSignedOssId() != null ? doc.getSignedOssId() : doc.getFileOssId();
        boolean reSigning = doc.getSignedOssId() != null;

        // 服务端准入：先验魔数（不信任前端/后缀名），再核对摘要（换过文件就拒绝签署）。
        // 摘要口径随基准走：首次签署对 doc.file_hash（原始件）；追加签署对「产生当前签署后文件的那条
        // 签名记录」的 doc_hash —— 台账按 doc_oss_id 可回查，查不到时跳过比对（不做无据拒绝）。
        byte[] srcPdf = readRequired(baseOssId, "待签文档正文");
        if (!PdfContentStamper.isPdf(srcPdf)) {
            throw new ServiceException("仅支持 PDF 文件");
        }
        String srcHash = Sm3Utils.hashHexBytes(srcPdf);
        String expectedSrcHash = reSigning ? signedArtifactHash(doc) : doc.getFileHash();
        if (StringUtils.isNotBlank(expectedSrcHash) && !expectedSrcHash.equalsIgnoreCase(srcHash)) {
            throw new ServiceException("文档正文与登记时的摘要不一致，疑似已被替换，拒绝签署");
        }

        List<PdfContentStamper.StampSpec> specs = new ArrayList<>();
        for (SealPlacement placement : cmd.placements()) {
            EsSeal seal = seals.get(placement.sealId());
            byte[] sealImage = readRequired(seal.getSealOssId(), "印章原图【" + seal.getSealName() + "】");
            specs.add(new PdfContentStamper.StampSpec(placement.pageIndex(), placement.xRatio(),
                placement.yRatio(), placement.wRatio(), placement.hRatio(),
                placement.rotation(), sealImage));
        }

        // 盖章 → （先全量 save 拿到字节）→ 自签证书 → detached CMS 增量签名。全程内存，无临时文件
        String signerName = resolveSignerName(userId);
        byte[] stamped = PdfContentStamper.stamp(srcPdf, specs);
        SelfSignedCertificateFactory.SigningIdentity identity =
            SelfSignedCertificateFactory.generate(signerName, CERT_ORG_UNIT, 0);
        byte[] signedPdf = PdfCmsSigner.sign(stamped, identity.privateKey(), identity.certificate(),
            signerName, cmd.signatureMeaning());

        // 先写对象存储拿到 ossId，再在同一事务里写台账：
        // 反之会出现「台账指向一个不存在的文件」。对象存储的写入无法随事务回滚，
        // 极端情况下会留下一个无台账引用的孤儿对象 —— 可接受，绝不接受有台账没文件。
        Long signedOssId = ossAccessor().store(signedPdf, signedFileName(doc.getFileName()), "application/pdf");
        String signedHash = Sm3Utils.hashHexBytes(signedPdf);

        LockInfo lock = lockTemplate.lock(CHAIN_LOCK_PREFIX + (tenantId == null ? "" : tenantId),
            LOCK_EXPIRE, LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("签名通道繁忙，请稍后重试");
        }
        boolean releaseDeferred = false;
        try {
            // 台账只有一列 seal_id：多章场景登记「第一个落位所用印章」，落位顺序即业务主次
            EsSeal primarySeal = seals.get(cmd.placements().get(0).sealId());
            Long signatureId = appendLedger(cmd.bizType(), cmd.bizId(), null, cmd.signatureMeaning(),
                cmd.authMethod(), primarySeal, userId, deptId,
                new DocLink(baseOssId, srcHash, signedOssId, signedHash, doc.getDocId()));
            markDocumentSigned(doc.getDocId(), signedOssId);
            releaseDeferred = releaseLockAfterTransaction(lock);
            log.info("文档签章完成: signatureId={}, docId={}, placements={}, base={}, reSigning={}, signedOssId={}, signedSize={}",
                signatureId, doc.getDocId(), cmd.placements().size(), baseOssId, reSigning, signedOssId, signedPdf.length);
            return signatureId;
        } finally {
            if (!releaseDeferred) {
                lockTemplate.releaseLock(lock);
            }
        }
    }

    /**
     * 追加签署时取「当前签署后文件」在台账登记的 SM3，用于核对基准文件未被替换。
     *
     * <p>按 {@code doc_oss_id} 回查产生该文件的签名记录（取 signatureId 最大的一条），
     * 查不到返回 null（调用方跳过比对 —— 无登记依据时不做无据拒绝）。</p>
     *
     * @param doc 待签文档
     * @return 台账登记的签署后文件 SM3；无记录时 null
     */
    private String signedArtifactHash(EsSignDocument doc) {
        EsSignature producer = signatureMapper.selectOne(Wrappers.<EsSignature>lambdaQuery()
            .eq(EsSignature::getDocId, doc.getDocId())
            .eq(EsSignature::getDocOssId, doc.getSignedOssId())
            .orderByDesc(EsSignature::getSignatureId)
            .last("LIMIT 1"));
        return producer == null ? null : producer.getDocHash();
    }

    /**
     * 文档级验签：CMS 验签 + ByteRange 覆盖/修订检查 + 台账摘要复核（详见接口 javadoc）。
     *
     * @param signatureId 签名记录ID
     * @return 验签结果
     */
    @Override
    public DocumentVerifyVo verifyDocument(Long signatureId) {
        EsSignature record = signatureMapper.selectById(signatureId);
        if (record == null) {
            throw new ServiceException("签名记录不存在");
        }
        if (record.getDocOssId() == null) {
            throw new ServiceException("该签名记录不含签署后文档，无法进行文档级验签（请使用 verify）");
        }
        byte[] pdf = readRequired(record.getDocOssId(), "签署后文档");
        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(pdf);

        String actualHash = Sm3Utils.hashHexBytes(pdf);
        boolean hashPassed = StringUtils.isBlank(record.getDocHash())
            || record.getDocHash().equalsIgnoreCase(actualHash);

        DocumentVerifyVo vo = new DocumentVerifyVo();
        BeanUtils.copyProperties(outcome, vo);
        vo.setSignatureId(signatureId);
        vo.setDocId(record.getDocId());
        vo.setDocHash(record.getDocHash());
        vo.setActualDocHash(actualHash);
        vo.setHashPassed(hashPassed);
        if (!hashPassed) {
            // 台账摘要对不上是独立于 CMS 的旁证：即便签名本身完整，文件也不是台账登记的那一份
            if (outcome.getVerdict() == DocVerifyVerdict.OK) {
                vo.setVerdict(DocVerifyVerdict.MODIFIED_IN_PLACE);
            }
            vo.getMessages().add("签署后文件的 SM3 与台账登记值不一致（台账 " + record.getDocHash()
                + "，实际 " + actualHash + "），文件已被替换");
        }
        vo.setPassed(hashPassed && outcome.getVerdict() == DocVerifyVerdict.OK);
        return vo;
    }

    /**
     * 取对象存储字节，缺失即拒绝（不做「跳过」兜底：签一份没盖全章的合同比失败更糟）。
     *
     * @param ossId 对象ID
     * @param label 用于报错的业务名（如「印章原图【公章】」）
     * @return 字节内容
     */
    private byte[] readRequired(Long ossId, String label) {
        if (ossId == null) {
            throw new ServiceException("{} 缺失，无法继续", label);
        }
        byte[] content = ossAccessor().read(ossId);
        if (content == null || content.length == 0) {
            throw new ServiceException("{} 内容不存在或为空", label);
        }
        return content;
    }

    /**
     * 取对象存储 SPI 实现。
     *
     * <p>实现方在 {@code ruoyi-system}（避免 Maven 循环依赖，见 {@link OssContentAccessor} 的说明），
     * 缺失时给出明确业务异常而不是 NPE；P1 的逻辑签名路径不会走到这里。</p>
     *
     * @return SPI 实现
     */
    private OssContentAccessor ossAccessor() {
        OssContentAccessor accessor =
            ossAccessorProvider == null ? null : ossAccessorProvider.getIfAvailable();
        if (accessor == null) {
            throw new ServiceException("对象存储内容访问实现缺失，无法读写文档正文");
        }
        return accessor;
    }

    /**
     * 回填 {@code es_sign_document} 的签署后文件与签署状态。
     *
     * @param docId        文档ID
     * @param signedOssId  签署后文件OSS ID
     */
    private void markDocumentSigned(Long docId, Long signedOssId) {
        EsSignDocument update = new EsSignDocument();
        update.setDocId(docId);
        update.setSignedOssId(signedOssId);
        update.setSignStatus(SIGN_STATUS_SIGNED);
        documentMapper.updateById(update);
    }

    /**
     * 签署后文件的落库文件名（保留原名基名，后缀统一为 {@code .pdf}）。
     *
     * @param original 原文件名（可空，可含目录）
     * @return 形如 {@code 采购合同-signed.pdf}
     */
    private static String signedFileName(String original) {
        String base = StringUtils.isBlank(original) ? "document" : original.trim();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        if (base.isBlank()) {
            base = "document";
        }
        return base + "-signed.pdf";
    }

    /**
     * 文档签章入参校验（服务端准入，不依赖前端；失败即拒绝，不做默认值兜底）。
     *
     * @param cmd 文档签章入参
     */
    private void validateDocCommand(DocSignCommand cmd) {
        if (cmd == null) {
            throw new ServiceException("签章入参不能为空");
        }
        if (StringUtils.isBlank(cmd.bizType())) {
            throw new ServiceException("业务类型不能为空");
        }
        if (cmd.docId() == null) {
            throw new ServiceException("待签文档ID不能为空");
        }
        if (StringUtils.isBlank(cmd.signatureMeaning())) {
            throw new ServiceException("签署含义不能为空");
        }
        if (cmd.signatureMeaning().length() > MEANING_MAX_LEN) {
            throw new ServiceException("签署含义长度不能超过 128 字符");
        }
        if (StringUtils.isBlank(cmd.authMethod())) {
            throw new ServiceException("签署认证方式不能为空");
        }
        if (cmd.placements() == null || cmd.placements().isEmpty()) {
            throw new ServiceException("盖章位置不能为空");
        }
        for (SealPlacement placement : cmd.placements()) {
            String reason = PdfStampGeometry.validatePlacement(placement);
            if (reason != null) {
                throw new ServiceException(reason);
            }
        }
    }

    /**
     * 单条记录的完整校验（{@link #verify} 与 {@link #verifyChain} 共用，避免两处逻辑漂移）：
     * <ol>
     *   <li>自身校验：用记录字段重算 chain_hash，与库中值比对（检出字段篡改）；</li>
     *   <li>链接校验：取本条在租户全局链上的<b>真实前驱</b>，其 chain_hash 必须等于本条的 prev_hash。</li>
     * </ol>
     *
     * <p><b>为什么必须取真实前驱：</b>链是<b>租户全局</b>的 —— {@code appendSignature} 取链尾时
     * 只按 signed_at/signature_id 排序，<b>不过滤 biz_type</b>，一条链横跨该租户的全部业务。
     * 若在业务范围内把「过滤后的相邻记录」当作前后驱关系，当多个业务的签名穿插时
     * （A1→B1→A2→B2→A3），A2 的 prev_hash 指向 B1 而非 A1，就会误报断链。</p>
     *
     * @param cur 待校验记录
     * @return 通过返回 null，否则返回失败原因
     */
    private String checkRecord(EsSignature cur) {
        long signedAtMs = cur.getSignedAt() == null ? 0L : cur.getSignedAt().getTime();
        if (!matchesStoredChainHash(cur, signedAtMs)) {
            return "签名记录内容与链哈希不一致，疑似被篡改";
        }
        EsSignature prev = selectTruePredecessor(cur.getSignatureId());
        String expectedPrevHash = prev == null ? null : prev.getChainHash();
        if (!Objects.equals(expectedPrevHash, cur.getPrevHash())) {
            return "与上一条签名的链哈希不衔接，链已断裂";
        }
        return null;
    }

    /**
     * 双公式自哈希验证：新记录按 v2（含 auth_method/biz_no）重算；对不上再按 v1（历史公式）重算，
     * 两个都命中不了才判篡改。
     *
     * <p>v2 把「认证方式」编进链哈希 —— 此前 auth_method 被 DB 直改无法检出，
     * 而它恰是决定证据效力（是否经过二级认证）的字段。v1 兼容保证公式升级前落库的
     * 存量链记录不因重算口径变化而被误报为篡改。</p>
     *
     * @param cur        待校验记录
     * @param signedAtMs 签署时间戳（毫秒）
     * @return 任一公式命中存储的 chain_hash 即 true
     */
    private boolean matchesStoredChainHash(EsSignature cur, long signedAtMs) {
        String stored = cur.getChainHash();
        String recomputedV2 = SignatureChainCalculator.computeV2(
            cur.getPrevHash(), cur.getSignatureId(), cur.getBizType(), cur.getBizId(),
            cur.getSignerUserId(), cur.getSealId(), cur.getSignatureMeaning(),
            cur.getDocHash(), signedAtMs, cur.getAuthMethod(), cur.getBizNo());
        if (Objects.equals(recomputedV2, stored)) {
            return true;
        }
        String recomputedV1 = SignatureChainCalculator.compute(
            cur.getPrevHash(), cur.getSignatureId(), cur.getBizType(), cur.getBizId(),
            cur.getSignerUserId(), cur.getSealId(), cur.getSignatureMeaning(),
            cur.getDocHash(), signedAtMs);
        return Objects.equals(recomputedV1, stored);
    }

    /**
     * 取同租户、全局顺序下紧邻本条的记录（tenant_id 条件由多租户插件注入 SQL）。
     *
     * <p>排序必须与 {@code appendSignature} 取链尾的排序<b>完全一致</b>
     * （{@code signed_at DESC, signature_id DESC}）：链尾即「按该顺序的最大者」，
     * 则本条的前驱就是「按同一顺序排在它前面的最近一条」。两处排序一旦漂移
     * （例如这里只用 signature_id 倒序），在签署时间与雪花 ID 顺序不一致时
     * 就会算出不同于 append 时的前驱，从而误报断链。</p>
     *
     * @param signatureId 本条签名ID
     * @return 前驱记录；本条即链首时返回 null
     */
    private EsSignature selectTruePredecessor(Long signatureId) {
        return signatureMapper.selectOne(Wrappers.<EsSignature>lambdaQuery()
            .lt(EsSignature::getSignatureId, signatureId)
            .orderByDesc(EsSignature::getSignedAt)
            .orderByDesc(EsSignature::getSignatureId)
            .last("LIMIT 1"));
    }

    /**
     * 追加一条逻辑签名记录（临界区内调用）。
     *
     * @param cmd    签名入参
     * @param seal   已校验可用的印章（无章为 null）
     * @param userId 签署人用户ID
     * @param deptId 签署人部门ID
     * @return 签名ID
     */
    private Long appendSignature(SignCommand cmd, EsSeal seal, Long userId, Long deptId) {
        // MES-SOP-05: 逻辑签名也可以绑定正文 —— 传了 docHash 就不再走 DocLink.NONE，
        // 摘要会被编入链哈希（computeV2 的 docHash 入参），从而「替换正文」可被检出。
        // 注意 docOssId 落 src_doc_oss_id（本次签署未产出新文档），doc_oss_id 保持 null，
        // 因此 verifyDocument 对这些记录仍会明确拒绝（「不含签署后文档」），不会误做 PDF/CMS 验签。
        DocLink link = (cmd.docOssId() == null && StringUtils.isBlank(cmd.docHash()))
            ? DocLink.NONE
            : new DocLink(cmd.docOssId(), cmd.docHash(), null, cmd.docHash(), null);
        return appendLedger(cmd.bizType(), cmd.bizId(), cmd.bizNo(), cmd.signatureMeaning(),
            cmd.authMethod(), seal, userId, deptId, link);
    }

    /**
     * 写 {@code es_signature} 台账（临界区内调用，逻辑签名与文档签章共用同一段链式计算）。
     *
     * <p>链哈希输入必须包含 {@code docHash}：文档签章把「签署后文件的 SM3」编进链，
     * 因此链上任何一个文件被换掉都会导致验链失败。</p>
     *
     * @param bizType          业务类型
     * @param bizId            业务单据ID
     * @param bizNo            业务单据编号
     * @param signatureMeaning 签署含义
     * @param authMethod       认证方式
     * @param seal             已校验可用的印章（无章为 null）
     * @param userId           签署人用户ID
     * @param deptId           签署人部门ID
     * @param docLink          文档关联字段（逻辑签名传 {@link DocLink#NONE}）
     * @return 签名ID
     */
    private Long appendLedger(String bizType, Long bizId, String bizNo, String signatureMeaning,
                              String authMethod, EsSeal seal, Long userId, Long deptId, DocLink docLink) {
        EsSignature last = signatureMapper.selectOne(Wrappers.<EsSignature>lambdaQuery()
            .orderByDesc(EsSignature::getSignedAt)
            .orderByDesc(EsSignature::getSignatureId)
            .last("LIMIT 1"));
        String prevHash = last == null ? null : last.getChainHash();

        // 秒级精度：es_signature.signed_at 为 MySQL datetime（无小数秒），
        // 毫秒会被静默截断，导致链哈希无法复现。此处先归零毫秒，保证落库往返后哈希一致。
        // 切勿改回 new Date() —— 毫秒级时间戳入哈希后，落库被截断，验签重算必然不一致
        // （线上实测：未篡改的新签名被判为「疑似被篡改」）。signedAt 必须同一实例同时用于
        // setSignedAt(...) 与 compute(...)，不得另取一次当前时间。
        Date signedAt = new Date((System.currentTimeMillis() / 1000L) * 1000L);
        Long signatureId = IdWorker.getId();

        EsSignature entity = new EsSignature();
        entity.setSignatureId(signatureId);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setBizNo(bizNo);
        entity.setSignerUserId(userId);
        entity.setSignerName(resolveSignerName(userId));
        entity.setSignerDeptId(deptId);
        if (seal != null) {
            entity.setSealId(seal.getSealId());
            entity.setSealType(seal.getSealType());
        }
        entity.setSignatureMeaning(signatureMeaning);
        entity.setAuthMethod(authMethod);
        entity.setSrcDocOssId(docLink.srcDocOssId());
        entity.setSrcDocHash(docLink.srcDocHash());
        entity.setDocOssId(docLink.docOssId());
        entity.setDocHash(docLink.docHash());
        entity.setDocId(docLink.docId());
        entity.setSignedAt(signedAt);
        entity.setSignIp(currentClientIp());
        entity.setSignUa(truncateUa(currentUserAgent()));
        entity.setPrevHash(prevHash);
        entity.setChainHash(SignatureChainCalculator.computeV2(
            prevHash, signatureId, bizType, bizId, userId, entity.getSealId(),
            signatureMeaning, entity.getDocHash(), signedAt.getTime(), authMethod, bizNo));

        signatureMapper.insert(entity);
        log.info("电子签名入库: signatureId={}, bizType={}, bizId={}, signerUserId={}, sealId={}, docId={}",
            signatureId, bizType, bizId, userId, entity.getSealId(), entity.getDocId());
        return signatureId;
    }

    /**
     * 台账上的文档关联字段（逻辑签名全为 null）。
     *
     * @param srcDocOssId 签署前文档OSS ID
     * @param srcDocHash  签署前文档SM3
     * @param docOssId    签署后文档OSS ID
     * @param docHash     签署后文档SM3
     * @param docId       关联 {@code es_sign_document.doc_id}
     */
    private record DocLink(Long srcDocOssId, String srcDocHash, Long docOssId, String docHash, Long docId) {
        /** 逻辑签名：不关联任何文档 */
        static final DocLink NONE = new DocLink(null, null, null, null, null);
    }

    /**
     * 入参校验（失败即拒绝，不做任何默认值兜底）。
     *
     * @param cmd 签名入参
     */
    private void validate(SignCommand cmd) {
        if (cmd == null) {
            throw new ServiceException("签名入参不能为空");
        }
        if (StringUtils.isBlank(cmd.bizType())) {
            throw new ServiceException("业务类型不能为空");
        }
        if (StringUtils.isBlank(cmd.signatureMeaning())) {
            throw new ServiceException("签署含义不能为空");
        }
        if (StringUtils.isBlank(cmd.authMethod())) {
            throw new ServiceException("签署认证方式不能为空");
        }
        if (cmd.signatureMeaning().length() > MEANING_MAX_LEN) {
            throw new ServiceException("签署含义长度不能超过 128 字符");
        }
    }

    /**
     * 签署人姓名快照；取不到登录态时退化为用户ID，不留空。
     *
     * @param userId 签署人用户ID
     * @return 姓名快照
     */
    private String resolveSignerName(Long userId) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser != null && StringUtils.isNotBlank(loginUser.getNickname())) {
            return loginUser.getNickname();
        }
        return userId == null ? null : String.valueOf(userId);
    }

    /**
     * 当前请求客户端 IP；无请求上下文（如单元测试、内部任务线程）时返回 null。
     *
     * @return 客户端 IP 或 null
     */
    private static String currentClientIp() {
        try {
            HttpServletRequest request = ServletUtils.getRequest();
            return request == null ? null : ServletUtils.getClientIP(request);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前请求 User-Agent；无请求上下文时返回 null。
     *
     * @return User-Agent 或 null
     */
    private static String currentUserAgent() {
        try {
            HttpServletRequest request = ServletUtils.getRequest();
            return request == null ? null : request.getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * UA 落库前截断到列宽：带大量浏览器扩展的 UA 可超过 {@code varchar(512)}，
     * MySQL 严格模式下超长 insert 直接报错 —— 签名失败会连带卡死「不签章不生效」的审批。
     * 截断只影响审计展示，不参与链哈希，无完整性损失。
     *
     * @param ua 原始 User-Agent
     * @return 截断到 512 字符的 UA
     */
    private static String truncateUa(String ua) {
        if (ua == null || ua.length() <= SIGN_UA_MAX_LEN) {
            return ua;
        }
        return ua.substring(0, SIGN_UA_MAX_LEN);
    }
}
