package com.nexus.common.sign.service.impl;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.ServletUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.support.SealAvailabilityResolver;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
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
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ESignatureServiceImpl implements ESignatureService {

    /** 哈希链串行化锁 key 前缀（按租户隔离） */
    private static final String CHAIN_LOCK_PREFIX = "es:sign:chain:";
    /** 锁持有时长(ms) */
    private static final long LOCK_EXPIRE = 30_000L;
    /** 获取锁超时(ms) */
    private static final long LOCK_ACQUIRE_TIMEOUT = 10_000L;
    /** 签署含义最大长度 */
    private static final int MEANING_MAX_LEN = 128;

    private final EsSignatureMapper signatureMapper;
    private final EsSealMapper sealMapper;
    private final EsSealGrantMapper grantMapper;
    private final SignAuthVerifier authVerifier;
    private final LockTemplate lockTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sign(SignCommand cmd) {
        validate(cmd);
        Long userId = LoginHelper.getUserId();
        String tenantId = LoginHelper.getTenantId();
        Long deptId = LoginHelper.getDeptId();

        EsSeal seal = null;
        if (cmd.sealId() != null) {
            seal = sealMapper.selectById(cmd.sealId());
            if (seal == null) {
                throw new ServiceException("印章不存在");
            }
            List<EsSealGrant> grants = grantMapper.selectList(Wrappers.<EsSealGrant>lambdaQuery()
                .eq(EsSealGrant::getSealId, seal.getSealId()));
            if (!SealAvailabilityResolver.isUsable(seal, grants, userId, deptId, cmd.bizType())) {
                throw new ServiceException("当前用户无权使用印章【{}】", seal.getSealName());
            }
        }

        // 认证失败即拒绝，不允许降级为「无凭证签名」
        authVerifier.verify(userId, cmd.authMethod(), cmd.credential());

        LockInfo lock = lockTemplate.lock(CHAIN_LOCK_PREFIX + (tenantId == null ? "" : tenantId),
            LOCK_EXPIRE, LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("签名通道繁忙，请稍后重试");
        }
        try {
            return appendSignature(cmd, seal, userId, deptId);
        } finally {
            lockTemplate.releaseLock(lock);
        }
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
        String recomputed = SignatureChainCalculator.compute(
            cur.getPrevHash(), cur.getSignatureId(), cur.getBizType(), cur.getBizId(),
            cur.getSignerUserId(), cur.getSealId(), cur.getSignatureMeaning(),
            cur.getDocHash(), signedAtMs);
        if (!Objects.equals(recomputed, cur.getChainHash())) {
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
     * 取同租户、全局顺序下紧邻本条的记录（tenant_id 条件由多租户插件注入 SQL）。
     *
     * <p>按 signature_id 倒序取：雪花 ID 时间有序，且签名链尾读取 + 写入在同一把
     * 租户锁的临界区内完成，因此该顺序与 {@code appendSignature} 的
     * {@code orderByDesc(signedAt)} 一致。</p>
     *
     * @param signatureId 本条签名ID
     * @return 前驱记录；本条即链首时返回 null
     */
    private EsSignature selectTruePredecessor(Long signatureId) {
        return signatureMapper.selectOne(Wrappers.<EsSignature>lambdaQuery()
            .lt(EsSignature::getSignatureId, signatureId)
            .orderByDesc(EsSignature::getSignatureId)
            .last("LIMIT 1"));
    }

    /**
     * 追加一条签名记录（临界区内调用）。
     *
     * @param cmd    签名入参
     * @param seal   已校验可用的印章（无章为 null）
     * @param userId 签署人用户ID
     * @param deptId 签署人部门ID
     * @return 签名ID
     */
    private Long appendSignature(SignCommand cmd, EsSeal seal, Long userId, Long deptId) {
        EsSignature last = signatureMapper.selectOne(Wrappers.<EsSignature>lambdaQuery()
            .orderByDesc(EsSignature::getSignedAt)
            .orderByDesc(EsSignature::getSignatureId)
            .last("LIMIT 1"));
        String prevHash = last == null ? null : last.getChainHash();

        Date signedAt = new Date();
        Long signatureId = IdWorker.getId();

        EsSignature entity = new EsSignature();
        entity.setSignatureId(signatureId);
        entity.setBizType(cmd.bizType());
        entity.setBizId(cmd.bizId());
        entity.setBizNo(cmd.bizNo());
        entity.setSignerUserId(userId);
        entity.setSignerName(resolveSignerName(userId));
        entity.setSignerDeptId(deptId);
        if (seal != null) {
            entity.setSealId(seal.getSealId());
            entity.setSealType(seal.getSealType());
        }
        entity.setSignatureMeaning(cmd.signatureMeaning());
        entity.setAuthMethod(cmd.authMethod());
        entity.setSignedAt(signedAt);
        entity.setSignIp(currentClientIp());
        entity.setSignUa(currentUserAgent());
        entity.setPrevHash(prevHash);
        entity.setChainHash(SignatureChainCalculator.compute(
            prevHash, signatureId, cmd.bizType(), cmd.bizId(), userId, entity.getSealId(),
            cmd.signatureMeaning(), entity.getDocHash(), signedAt.getTime()));

        signatureMapper.insert(entity);
        log.info("电子签名入库: signatureId={}, bizType={}, bizId={}, signerUserId={}, sealId={}",
            signatureId, cmd.bizType(), cmd.bizId(), userId, entity.getSealId());
        return signatureId;
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
}
