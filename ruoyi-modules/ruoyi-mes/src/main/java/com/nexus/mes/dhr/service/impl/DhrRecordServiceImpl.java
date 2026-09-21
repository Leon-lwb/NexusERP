package com.nexus.mes.dhr.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.util.Sm3Utils;
import com.nexus.mes.dhr.domain.DhrRecord;
import com.nexus.mes.dhr.domain.DhrSection;
import com.nexus.mes.dhr.domain.DhrSectionCode;
import com.nexus.mes.dhr.domain.DhrStatus;
import com.nexus.mes.dhr.domain.bo.DhrBuildBo;
import com.nexus.mes.dhr.domain.bo.DhrRecordBo;
import com.nexus.mes.dhr.domain.bo.DhrSignBo;
import com.nexus.mes.dhr.domain.vo.DhrRecordVo;
import com.nexus.mes.dhr.domain.vo.DhrSectionVo;
import com.nexus.mes.dhr.domain.vo.DhrVerifyVo;
import com.nexus.mes.dhr.mapper.DhrRecordMapper;
import com.nexus.mes.dhr.mapper.DhrSectionMapper;
import com.nexus.mes.dhr.service.IDhrAggregateService;
import com.nexus.mes.dhr.service.IDhrRecordService;
import com.nexus.mes.dhr.support.DhrSignatureAccessor;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DHR 批记录 Service 实现。
 *
 * <p>状态机见 {@link DhrStatus}：{@code DRAFT -> SUBMITTED -> (RELEASED | REJECTED)}、
 * {@code REJECTED -> DRAFT}、{@code RELEASED -> ARCHIVED}。所有流转一律 CAS 条件更新
 * （{@code eq(status, from)} + 判影响行数），影响 0 行即抛异常，不做读-改-写。</p>
 *
 * <h3>电子签名与数据源边界（照抄 NcrOrderServiceImpl 的既有正确写法，勿改）</h3>
 * <p>本类 {@code @DS("mes")}，而 {@code es_signature} <b>只存在于 master(ry-vue)</b>。
 * {@code ESignatureService#sign} 的传播行为是 {@code REQUIRED}：它会<b>加入</b>调用方事务，
 * 而 dynamic-datasource 的 @DS 切面虽然 order 最外层，却只在「开事务、取连接之前」压栈才生效 ——
 * 一旦调用方已开着 ry-mes 事务，连接已绑定，@DS 切到 master 也不生效，签名会写到 ry-mes，
 * 运行期报 {@code Table 'ry-mes.es_signature' doesn't exist}（单测 mock 不会暴露，只有真跑才炸）。</p>
 * <p>因此提交/放行/驳回三个合规动作：</p>
 * <ol>
 *   <li>用 {@code signInNewTransaction}（REQUIRES_NEW）：@DS 切面在开新事务前把 master 压栈，
 *       签署真正落到 ry-vue，且不被调用方事务回滚牵连；</li>
 *   <li>顺序是<b>先签名（含二级认证校验），后做状态 CAS</b> —— 认证不通过时业务库一行未动，
 *       不会出现「先改了状态、再发现授权失败」的中间态；</li>
 *   <li><b>不允许降级</b>：不 try/catch 签名异常，签名失败即整单失败（状态流转不执行）；</li>
 *   <li>签名ID 回填与状态 CAS 放在<b>调用方同一个 ry-mes 事务</b>里（单条 UPDATE 语句同时 SET
 *       状态/时间/签名ID），「已流转但未绑定签名」不可能出现。残余窗口只有反方向：
 *       签名已提交、CAS 影响 0 行 → 「有签名、无流转」，业务状态未被污染、可重新发起，
 *       属安全失败方向（与 NCR 处置同一取舍）。</li>
 * </ol>
 *
 * <h3>读路径不得加 @Transactional</h3>
 * <p>{@link #listSignatures} / {@link #verify} 同时要读 master 的 {@code es_signature} 与
 * ry-mes 的 {@code dhr_section}。<b>这两个方法绝不能标 {@code @Transactional}</b>：有活动事务时
 * @DS 不切换，读 {@code es_signature} 会打到 ry-mes 报错。没有事务时每次 mapper 调用各自取连接、
 * @DS 按次切换，跨库读才成立（签名侧另经 {@link DhrSignatureAccessor} 的 REQUIRES_NEW 兜底）。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("dhrRecordService")
public class DhrRecordServiceImpl implements IDhrRecordService {

    /** 签署含义（合规动作固定话术） */
    private static final String MEANING_SUBMIT = "提交批记录复核";
    private static final String MEANING_RELEASE = "批记录放行";
    private static final String MEANING_REJECT = "批记录驳回";

    /** DHR 编号前缀与最大长度（dhr_no varchar(64)） */
    private static final String DHR_NO_PREFIX = "DHR-";
    private static final int DHR_NO_MAX_LEN = 64;

    private final DhrRecordMapper baseMapper;
    private final DhrSectionMapper sectionMapper;
    private final IDhrAggregateService aggregateService;
    private final DhrSignatureAccessor signatureAccessor;
    private final ProWorkOrderMapper workOrderMapper;
    private final ESignatureService eSignatureService;

    // ==================== 查询 ====================

    @Override
    public TableDataInfo<DhrRecordVo> selectPageList(DhrRecordBo bo, PageQuery pageQuery) {
        Page<DhrRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<DhrRecordVo> selectList(DhrRecordBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public DhrRecordVo selectById(Long dhrId) {
        DhrRecordVo vo = baseMapper.selectVoById(dhrId);
        if (vo == null) {
            throw new ServiceException("DHR 批记录不存在: " + dhrId);
        }
        vo.setSections(selectSections(dhrId));
        return vo;
    }

    @Override
    public List<DhrSectionVo> selectSections(Long dhrId) {
        requireExists(dhrId);
        return sectionMapper.selectVoList(Wrappers.<DhrSection>lambdaQuery()
            .eq(DhrSection::getDhrId, dhrId)
            .orderByAsc(DhrSection::getSortOrder));
    }

    /**
     * 签名链列表（DHR_SUBMIT / DHR_RELEASE / DHR_REJECT 合并）。
     * <p><b>刻意不加 {@code @Transactional}</b>：需要跨 master(es_signature) 与
     * ry-mes(dhr_record) 读，有事务时 @DS 不切换会读错库（见类注释）。</p>
     */
    @Override
    public List<SignatureVo> listSignatures(Long dhrId) {
        requireExists(dhrId);
        List<SignatureVo> all = new ArrayList<>();
        for (String bizType : DHR_BIZ_TYPES) {
            List<SignatureVo> rows = signatureAccessor.listByBiz(bizType, dhrId);
            if (rows != null) {
                all.addAll(rows);
            }
        }
        all.sort(Comparator.comparing(SignatureVo::getSignedAt,
            Comparator.nullsLast(Comparator.naturalOrder())));
        return all;
    }

    /**
     * 校验快照哈希与签名链。
     * <p>重算口径：分册正文哈希 = {@code SM3(content_json)}；总哈希 = {@code SM3(按 sort_order
     * 升序拼接 section_code + '\n' + 重算正文哈希 + '\n')}。用<b>重算值</b>而非落库的
     * content_hash 复算总哈希，才能同时检出「正文被改」与「哈希被改」。</p>
     * <p><b>刻意不加 {@code @Transactional}</b>（同 {@link #listSignatures}）。</p>
     */
    @Override
    public DhrVerifyVo verify(Long dhrId) {
        DhrRecord record = requireExists(dhrId);
        List<DhrSection> sections = sectionMapper.selectList(Wrappers.<DhrSection>lambdaQuery()
            .eq(DhrSection::getDhrId, dhrId)
            .orderByAsc(DhrSection::getSortOrder));

        List<String> problems = new ArrayList<>();
        for (DhrSection section : sections) {
            String recomputedContentHash = Sm3Utils.hashHex(section.getContentJson());
            if (!recomputedContentHash.equals(section.getContentHash())) {
                problems.add("分册 " + section.getSectionCode() + " 正文哈希不一致"
                    + "（落库 " + section.getContentHash() + "，重算 " + recomputedContentHash + "）");
            }
        }
        String recomputed = recomputeSnapshotHash(sections);
        boolean snapshotMatched = recomputed.equals(record.getSnapshotHash());

        DhrVerifyVo vo = new DhrVerifyVo();
        vo.setSnapshotHash(record.getSnapshotHash());
        vo.setRecomputedHash(recomputed);

        VerifyResultVo chain = mergeChainVerify(dhrId);
        vo.setChainVerify(chain);
        boolean passed = problems.isEmpty() && snapshotMatched && Boolean.TRUE.equals(chain.getPassed());
        vo.setHashMatched(problems.isEmpty() && snapshotMatched);
        vo.setVerdict(passed ? "PASS" : "FAIL");
        if (!passed) {
            StringBuilder reason = new StringBuilder("DHR 校验未通过:");
            if (!snapshotMatched) {
                reason.append(" 快照总哈希不一致;");
            }
            for (String problem : problems) {
                reason.append(' ').append(problem).append(';');
            }
            if (!Boolean.TRUE.equals(chain.getPassed())) {
                reason.append(" 签名链校验未通过;");
            }
            log.warn("{} dhrId={}", reason, dhrId);
        }
        return vo;
    }

    // ==================== 构建 / 重建 / 删除（仅 DRAFT） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DhrRecordVo build(DhrBuildBo bo) {
        if (bo == null || bo.getWorkOrderId() == null) {
            throw new ServiceException("工单ID不能为空");
        }
        ProWorkOrder workOrder = workOrderMapper.selectById(bo.getWorkOrderId());
        if (workOrder == null) {
            throw new ServiceException("MES 工单不存在，无法构建 DHR 批记录: " + bo.getWorkOrderId());
        }
        Long dup = baseMapper.selectCount(Wrappers.<DhrRecord>lambdaQuery()
            .eq(DhrRecord::getWorkOrderId, workOrder.getMesWoId())
            .eq(StringUtils.isNotBlank(bo.getLotNo()), DhrRecord::getLotNo, bo.getLotNo())
            .eq(StringUtils.isNotBlank(bo.getSn()), DhrRecord::getSn, bo.getSn())
            .ne(DhrRecord::getStatus, DhrStatus.ARCHIVED));
        if (dup != null && dup > 0) {
            throw new ServiceException("该工单已存在未归档的 DHR 批记录，请先归档或直接使用既有记录");
        }

        DhrRecord record = new DhrRecord();
        record.setDhrNo(generateDhrNo(workOrder, bo));
        record.setWorkOrderId(workOrder.getMesWoId());
        record.setWorkOrderNo(workOrder.getWoNo());
        record.setMaterialId(workOrder.getMaterialId());
        record.setMaterialCode(workOrder.getMaterialCode());
        record.setMaterialName(workOrder.getMaterialName());
        record.setLotNo(bo.getLotNo());
        record.setSn(bo.getSn());
        record.setPlannedQty(workOrder.getPlannedQty());
        record.setCompletedQty(workOrder.getCompletedQty());
        record.setScrapQty(workOrder.getScrapQty());
        record.setStatus(DhrStatus.DRAFT);
        record.setVersion(1);
        record.setRemark(bo.getRemark());

        List<DhrSection> sections = buildSections(workOrder, bo.getLotNo(), bo.getSn());
        record.setSnapshotHash(recomputeSnapshotHash(sections));
        baseMapper.insert(record);
        for (DhrSection section : sections) {
            section.setDhrId(record.getDhrId());
            sectionMapper.insert(section);
        }
        log.info("DHR 构建完成: dhrId={}, dhrNo={}, workOrderId={}, 分册数={}",
            record.getDhrId(), record.getDhrNo(), record.getWorkOrderId(), sections.size());
        return toVo(record, sections);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DhrRecordVo rebuild(Long dhrId) {
        DhrRecord record = requireExists(dhrId);
        if (!DhrStatus.DRAFT.equals(record.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态可重建 DHR 分册（进入 SUBMITTED 后快照与分册不可变），"
                + "当前状态：" + record.getStatus());
        }
        ProWorkOrder workOrder = workOrderMapper.selectById(record.getWorkOrderId());
        if (workOrder == null) {
            throw new ServiceException("MES 工单不存在，无法重建 DHR: " + record.getWorkOrderId());
        }
        // 重建只允许发生在冻结之前：DRAFT 期分册是草稿，可整体覆盖（物理删除）
        sectionMapper.delete(Wrappers.<DhrSection>lambdaQuery().eq(DhrSection::getDhrId, dhrId));
        List<DhrSection> sections = buildSections(workOrder, record.getLotNo(), record.getSn());
        for (DhrSection section : sections) {
            section.setDhrId(dhrId);
            sectionMapper.insert(section);
        }
        String snapshotHash = recomputeSnapshotHash(sections);
        int rows = baseMapper.update(null, Wrappers.<DhrRecord>lambdaUpdate()
            .set(DhrRecord::getSnapshotHash, snapshotHash)
            .set(DhrRecord::getRemark, record.getRemark())
            .setSql("version = version + 1")
            .eq(DhrRecord::getDhrId, dhrId)
            .eq(DhrRecord::getStatus, DhrStatus.DRAFT));
        if (rows == 0) {
            throw new ServiceException("DHR 状态已变更，重建失败（当前状态：" + currentStatus(dhrId) + "）");
        }
        record.setSnapshotHash(snapshotHash);
        log.info("DHR 重建完成: dhrId={}, 分册数={}", dhrId, sections.size());
        return toVo(record, sections);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteById(Long dhrId) {
        DhrRecord record = requireExists(dhrId);
        if (!DhrStatus.DRAFT.equals(record.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态可删除 DHR（已冻结的批记录属审计台账，禁止删除），"
                + "当前状态：" + record.getStatus());
        }
        int rows = baseMapper.delete(Wrappers.<DhrRecord>lambdaQuery()
            .eq(DhrRecord::getDhrId, dhrId)
            .eq(DhrRecord::getStatus, DhrStatus.DRAFT));
        if (rows == 0) {
            throw new ServiceException("DHR 状态已变更，删除失败（当前状态：" + currentStatus(dhrId) + "）");
        }
        sectionMapper.delete(Wrappers.<DhrSection>lambdaQuery().eq(DhrSection::getDhrId, dhrId));
        log.info("DHR 删除完成(DRAFT): dhrId={}", dhrId);
        return true;
    }

    // ==================== 状态流转（提交/放行/驳回必须电子签名） ====================

    @Lock4j(keys = {"#dhrId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submit(Long dhrId, DhrSignBo bo) {
        // 认证等级先于任何落库动作校验（失败即拒，业务库一行未动）
        assertTwoFactor(bo);
        DhrRecord record = requireExists(dhrId);
        if (!DhrStatus.DRAFT.equals(record.getStatus())) {
            throw new ServiceException("仅 DRAFT 状态可提交复核，当前状态：" + record.getStatus());
        }
        assertSectionsComplete(dhrId);
        // 先签名（独立事务、master 库），失败即整单失败，不降级
        Long signatureId = eSignatureService.signInNewTransaction(new SignCommand(
            SignBizType.DHR_SUBMIT, dhrId, record.getDhrNo(), MEANING_SUBMIT,
            null, bo.getAuthMethod(), bo.getCredential(), null, record.getSnapshotHash()));
        Date now = new Date();
        Long userId = LoginHelper.getUserId();
        int rows = baseMapper.update(null, Wrappers.<DhrRecord>lambdaUpdate()
            .set(DhrRecord::getStatus, DhrStatus.SUBMITTED)
            .set(DhrRecord::getSubmittedAt, now)
            .set(DhrRecord::getSubmittedBy, userId)
            .set(DhrRecord::getFrozenAt, now)
            .set(DhrRecord::getFrozenBy, userId)
            .set(DhrRecord::getSubmitSignatureId, signatureId)
            .setSql("version = version + 1")
            .eq(DhrRecord::getDhrId, dhrId)
            .eq(DhrRecord::getStatus, DhrStatus.DRAFT));
        if (rows == 0) {
            throw new ServiceException("DHR 状态已变更，提交失败（当前状态：" + currentStatus(dhrId)
                + "）。签名已单独落台账（append-only），业务状态未被污染，可重新发起。");
        }
        log.info("DHR 提交复核: dhrId={}, signatureId={}, authMethod={}", dhrId, signatureId, bo.getAuthMethod());
        return true;
    }

    @Lock4j(keys = {"#dhrId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean release(Long dhrId, DhrSignBo bo) {
        assertTwoFactor(bo);
        DhrRecord record = requireExists(dhrId);
        if (!DhrStatus.SUBMITTED.equals(record.getStatus())) {
            throw new ServiceException("仅 SUBMITTED 状态可放行，当前状态：" + record.getStatus());
        }
        Long signatureId = eSignatureService.signInNewTransaction(new SignCommand(
            SignBizType.DHR_RELEASE, dhrId, record.getDhrNo(), MEANING_RELEASE,
            null, bo.getAuthMethod(), bo.getCredential(), null, record.getSnapshotHash()));
        Date now = new Date();
        Long userId = LoginHelper.getUserId();
        int rows = baseMapper.update(null, Wrappers.<DhrRecord>lambdaUpdate()
            .set(DhrRecord::getStatus, DhrStatus.RELEASED)
            .set(DhrRecord::getReleasedAt, now)
            .set(DhrRecord::getReleasedBy, userId)
            .set(DhrRecord::getReleaseSignatureId, signatureId)
            .setSql("version = version + 1")
            .eq(DhrRecord::getDhrId, dhrId)
            .eq(DhrRecord::getStatus, DhrStatus.SUBMITTED));
        if (rows == 0) {
            throw new ServiceException("DHR 状态已变更，放行失败（当前状态：" + currentStatus(dhrId)
                + "）。签名已单独落台账（append-only），业务状态未被污染，可重新发起。");
        }
        log.info("DHR 放行: dhrId={}, signatureId={}, authMethod={}", dhrId, signatureId, bo.getAuthMethod());
        return true;
    }

    @Lock4j(keys = {"#dhrId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reject(Long dhrId, DhrSignBo bo) {
        assertTwoFactor(bo);
        if (bo == null || StringUtils.isBlank(bo.getReason())) {
            throw new ServiceException("驳回原因不能为空（批记录驳回属合规动作，必须留痕理由）");
        }
        DhrRecord record = requireExists(dhrId);
        if (!DhrStatus.SUBMITTED.equals(record.getStatus())) {
            throw new ServiceException("仅 SUBMITTED 状态可驳回，当前状态：" + record.getStatus());
        }
        Long signatureId = eSignatureService.signInNewTransaction(new SignCommand(
            SignBizType.DHR_REJECT, dhrId, record.getDhrNo(), MEANING_REJECT,
            null, bo.getAuthMethod(), bo.getCredential(), null, record.getSnapshotHash()));
        Date now = new Date();
        Long userId = LoginHelper.getUserId();
        int rows = baseMapper.update(null, Wrappers.<DhrRecord>lambdaUpdate()
            .set(DhrRecord::getStatus, DhrStatus.REJECTED)
            .set(DhrRecord::getRejectedAt, now)
            .set(DhrRecord::getRejectedBy, userId)
            .set(DhrRecord::getRejectReason, bo.getReason())
            .set(DhrRecord::getRejectSignatureId, signatureId)
            .setSql("version = version + 1")
            .eq(DhrRecord::getDhrId, dhrId)
            .eq(DhrRecord::getStatus, DhrStatus.SUBMITTED));
        if (rows == 0) {
            throw new ServiceException("DHR 状态已变更，驳回失败（当前状态：" + currentStatus(dhrId)
                + "）。签名已单独落台账（append-only），业务状态未被污染，可重新发起。");
        }
        log.info("DHR 驳回: dhrId={}, signatureId={}, reason={}", dhrId, signatureId, bo.getReason());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean archive(Long dhrId) {
        DhrRecord record = requireExists(dhrId);
        if (!DhrStatus.RELEASED.equals(record.getStatus())) {
            throw new ServiceException("仅 RELEASED 状态可归档，当前状态：" + record.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<DhrRecord>lambdaUpdate()
            .set(DhrRecord::getStatus, DhrStatus.ARCHIVED)
            .set(DhrRecord::getArchivedAt, new Date())
            .setSql("version = version + 1")
            .eq(DhrRecord::getDhrId, dhrId)
            .eq(DhrRecord::getStatus, DhrStatus.RELEASED));
        if (rows == 0) {
            throw new ServiceException("DHR 状态已变更，归档失败（当前状态：" + currentStatus(dhrId) + "）");
        }
        log.info("DHR 归档(终态): dhrId={}", dhrId);
        return true;
    }

    // ==================== 内部方法 ====================

    /** DHR 三类签署业务类型（签名链合并用） */
    private static final List<String> DHR_BIZ_TYPES =
        List.of(SignBizType.DHR_SUBMIT, SignBizType.DHR_RELEASE, SignBizType.DHR_REJECT);

    private LambdaQueryWrapper<DhrRecord> buildQueryWrapper(DhrRecordBo bo) {
        DhrRecordBo query = bo == null ? new DhrRecordBo() : bo;
        return Wrappers.<DhrRecord>lambdaQuery()
            .eq(query.getDhrId() != null, DhrRecord::getDhrId, query.getDhrId())
            .like(StringUtils.isNotBlank(query.getDhrNo()), DhrRecord::getDhrNo, query.getDhrNo())
            .eq(query.getWorkOrderId() != null, DhrRecord::getWorkOrderId, query.getWorkOrderId())
            .like(StringUtils.isNotBlank(query.getWorkOrderNo()), DhrRecord::getWorkOrderNo, query.getWorkOrderNo())
            .eq(query.getMaterialId() != null, DhrRecord::getMaterialId, query.getMaterialId())
            .like(StringUtils.isNotBlank(query.getMaterialCode()), DhrRecord::getMaterialCode, query.getMaterialCode())
            .like(StringUtils.isNotBlank(query.getLotNo()), DhrRecord::getLotNo, query.getLotNo())
            .like(StringUtils.isNotBlank(query.getSn()), DhrRecord::getSn, query.getSn())
            .eq(StringUtils.isNotBlank(query.getStatus()), DhrRecord::getStatus, query.getStatus())
            .ge(StringUtils.isNotBlank(query.getBeginTime()), DhrRecord::getCreateTime, query.getBeginTime())
            .le(StringUtils.isNotBlank(query.getEndTime()), DhrRecord::getCreateTime, query.getEndTime())
            .orderByDesc(DhrRecord::getCreateTime);
    }

    private DhrRecord requireExists(Long dhrId) {
        if (dhrId == null) {
            throw new ServiceException("DHR ID 不能为空");
        }
        DhrRecord record = baseMapper.selectById(dhrId);
        if (record == null) {
            throw new ServiceException("DHR 批记录不存在: " + dhrId);
        }
        return record;
    }

    /**
     * 读取当前状态（仅用于失败信息，让「状态已变更」可定位到实际状态）。
     */
    private String currentStatus(Long dhrId) {
        DhrRecord latest = baseMapper.selectById(dhrId);
        return latest == null ? "已删除" : latest.getStatus();
    }

    /**
     * 二级认证前置校验：DHR 提交/放行/驳回是<b>授权</b>动作，不接受 LOGIN_ONLY。
     */
    private void assertTwoFactor(DhrSignBo bo) {
        if (bo == null || StringUtils.isBlank(bo.getAuthMethod())) {
            throw new ServiceException("认证方式不能为空");
        }
        if (SignAuthMethod.LOGIN_ONLY.equals(bo.getAuthMethod())) {
            throw new ServiceException("DHR 提交/放行/驳回必须进行二级认证（不接受 LOGIN_ONLY）");
        }
        if (!SignAuthMethod.isSupported(bo.getAuthMethod())) {
            throw new ServiceException("不支持的签署认证方式：" + bo.getAuthMethod());
        }
    }

    /**
     * 分册完整性校验：10 个固定分册必须齐全（缺分册本身就是可检出的篡改信号）。
     */
    private void assertSectionsComplete(Long dhrId) {
        List<DhrSection> rows = sectionMapper.selectList(Wrappers.<DhrSection>lambdaQuery()
            .select(DhrSection::getSectionCode)
            .eq(DhrSection::getDhrId, dhrId));
        List<String> present = new ArrayList<>();
        for (DhrSection row : rows) {
            present.add(row.getSectionCode());
        }
        List<String> missing = new ArrayList<>();
        for (String code : DhrSectionCode.ordered()) {
            if (!present.contains(code)) {
                missing.add(code);
            }
        }
        if (!missing.isEmpty()) {
            throw new ServiceException("DHR 分册不完整，禁止提交（缺少：" + String.join(", ", missing) + "）");
        }
    }

    /**
     * 聚合分册并计算哈希。<b>无数据的分册也会落一条 item_count=0 的空分册</b>，
     * 保证分册集合完整可校验。
     */
    private List<DhrSection> buildSections(ProWorkOrder workOrder, String lotNo, String sn) {
        Map<String, List<Map<String, Object>>> aggregated = aggregateService.aggregate(workOrder, lotNo, sn);
        String tenantId = LoginHelper.getTenantId();
        Date now = new Date();
        List<DhrSection> sections = new ArrayList<>();
        int order = 1;
        for (String code : DhrSectionCode.ordered()) {
            List<Map<String, Object>> items = aggregated.get(code);
            if (items == null) {
                items = new ArrayList<>();
            }
            String contentJson = JsonUtils.toJsonString(items);
            DhrSection section = new DhrSection();
            section.setTenantId(tenantId);
            section.setSectionCode(code);
            section.setSectionName(DhrSectionCode.nameOf(code));
            section.setItemCount(items.size());
            section.setContentJson(contentJson);
            section.setContentHash(Sm3Utils.hashHex(contentJson));
            section.setSortOrder(order++);
            section.setCreateTime(now);
            sections.add(section);
        }
        return sections;
    }

    /**
     * 快照总哈希 = {@code SM3(按 sort_order 升序拼接 section_code + '\n' + content_hash + '\n')}。
     */
    private String recomputeSnapshotHash(List<DhrSection> sections) {
        List<DhrSection> ordered = new ArrayList<>(sections);
        ordered.sort(Comparator.comparing(section -> section.getSortOrder() == null ? 0 : section.getSortOrder()));
        StringBuilder builder = new StringBuilder();
        for (DhrSection section : ordered) {
            builder.append(section.getSectionCode()).append('\n')
                .append(section.getContentHash()).append('\n');
        }
        return Sm3Utils.hashHex(builder.toString());
    }

    /**
     * 合并三条业务链的验签结果（提交/放行/驳回分属不同 bizType，签名链本身是租户全局链）。
     */
    private VerifyResultVo mergeChainVerify(Long dhrId) {
        VerifyResultVo merged = new VerifyResultVo();
        merged.setPassed(true);
        merged.setChainPassed(true);
        merged.setCheckedCount(0);
        for (String bizType : DHR_BIZ_TYPES) {
            VerifyResultVo one = signatureAccessor.verifyChain(bizType, dhrId);
            if (one == null) {
                continue;
            }
            merged.setCheckedCount(merged.getCheckedCount() + (one.getCheckedCount() == null ? 0 : one.getCheckedCount()));
            if (!Boolean.TRUE.equals(one.getPassed())) {
                merged.setPassed(false);
                merged.setChainPassed(false);
                if (merged.getBrokenAt() == null) {
                    merged.setBrokenAt(one.getBrokenAt());
                }
            }
            if (one.getMessages() != null) {
                for (String message : one.getMessages()) {
                    merged.getMessages().add(bizType + ": " + message);
                }
            }
        }
        return merged;
    }

    /**
     * 生成 DHR 编号：{@code DHR-<工单号>-<SN|批次|NA>}；编号冲突时追加序号（归档后重建同一工单的场景）。
     */
    private String generateDhrNo(ProWorkOrder workOrder, DhrBuildBo bo) {
        String scope = StringUtils.isNotBlank(bo.getSn()) ? bo.getSn()
            : (StringUtils.isNotBlank(bo.getLotNo()) ? bo.getLotNo() : "NA");
        String woPart = StringUtils.isBlank(workOrder.getWoNo())
            ? String.valueOf(workOrder.getMesWoId()) : workOrder.getWoNo();
        String base = DHR_NO_PREFIX + woPart + "-" + scope;
        if (base.length() > DHR_NO_MAX_LEN - 4) {
            base = base.substring(0, DHR_NO_MAX_LEN - 4);
        }
        String candidate = base;
        int seq = 2;
        while (Boolean.TRUE.equals(baseMapper.exists(Wrappers.<DhrRecord>lambdaQuery()
            .eq(DhrRecord::getDhrNo, candidate)))) {
            candidate = base + "-" + seq++;
        }
        return candidate;
    }

    /**
     * 实体 -> VO（含分册）。用 hutool 同名属性拷贝，避免依赖运行期 MapStruct 转换器查找。
     */
    private DhrRecordVo toVo(DhrRecord record, List<DhrSection> sections) {
        DhrRecordVo vo = new DhrRecordVo();
        BeanUtil.copyProperties(record, vo);
        List<DhrSectionVo> sectionVos = new ArrayList<>();
        for (DhrSection section : sections) {
            DhrSectionVo sectionVo = new DhrSectionVo();
            BeanUtil.copyProperties(section, sectionVo);
            sectionVos.add(sectionVo);
        }
        vo.setSections(sectionVos);
        return vo;
    }
}
