package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionMapper;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 采购申请服务实现类
 * <p>
 * 实现采购申请的核心业务逻辑，包括：
 * <ul>
 *   <li>CRUD 操作：分页查询、新增（自动编号/默认状态）、修改（状态校验）、删除</li>
 *   <li>状态流转：提交（草稿→已提交）、审批（已提交→已批准）、关闭、作废</li>
 *   <li>转单操作：将已批准的采购申请转换为采购订单</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("puPurchaseRequisitionService")
public class PuPurchaseRequisitionServiceImpl implements IPuPurchaseRequisitionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PuPurchaseRequisitionMapper baseMapper;
    private final PuPurchaseRequisitionLineMapper lineMapper;
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final WmMaterialStockMapper materialStockMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;

    /**
     * 分页查询采购申请列表
     */
    @Override
    public TableDataInfo<PuPurchaseRequisitionVo> selectPageList(PuPurchaseRequisitionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuPurchaseRequisition> lqw = buildQueryWrapper(bo);
        Page<PuPurchaseRequisition> page = baseMapper.selectPage(pageQuery.build(), lqw);
        List<PuPurchaseRequisitionVo> records = page.getRecords().stream().map(this::copyToVo).collect(Collectors.toList());
        Page<PuPurchaseRequisitionVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return TableDataInfo.build(voPage);
    }

    /**
     * 查询采购申请列表（不分页）
     */
    @Override
    public List<PuPurchaseRequisitionVo> selectList(PuPurchaseRequisitionBo bo) {
        List<PuPurchaseRequisition> list = baseMapper.selectList(buildQueryWrapper(bo));
        return list.stream().map(this::copyToVo).collect(Collectors.toList());
    }

    /**
     * 根据主键查询采购申请，附带明细行列表
     */
    @Override
    public PuPurchaseRequisitionVo selectById(Long id) {
        PuPurchaseRequisition entity = baseMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        PuPurchaseRequisitionVo vo = copyToVo(entity);
        LambdaQueryWrapper<PuPurchaseRequisitionLine> lqw = Wrappers.lambdaQuery();
        lqw.eq(PuPurchaseRequisitionLine::getRequisitionId, vo.getRequisitionId());
        vo.setPuPurchaseRequisitionLineList(lineMapper.selectList(lqw));
        return vo;
    }

    /** 决策创建后同一事务中对主表与明细执行 current read。 */
    @Override
    public PuPurchaseRequisitionVo selectByIdForDecision(Long id) {
        PuPurchaseRequisition entity = baseMapper.selectOne(Wrappers.lambdaQuery(PuPurchaseRequisition.class)
            .eq(PuPurchaseRequisition::getRequisitionId, id).last("FOR UPDATE"));
        if (entity == null) {
            return null;
        }
        PuPurchaseRequisitionVo vo = copyToVo(entity);
        vo.setPuPurchaseRequisitionLineList(lineMapper.selectList(Wrappers.lambdaQuery(PuPurchaseRequisitionLine.class)
            .eq(PuPurchaseRequisitionLine::getRequisitionId, id).last("FOR UPDATE")));
        return vo;
    }

    /**
     * 将实体转换为 VO 对象
     */
    private PuPurchaseRequisitionVo copyToVo(PuPurchaseRequisition entity) {
        if (entity == null) {
            return null;
        }
        PuPurchaseRequisitionVo vo = new PuPurchaseRequisitionVo();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 新增采购申请。BO 中存在 ID 时拒绝，已有单据必须显式调用更新入口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuPurchaseRequisitionBo bo) {
        return createDraftInternal(bo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(PuPurchaseRequisitionBo bo) {
        createDraftInternal(bo);
        return bo.getRequisitionId();
    }

    private int createDraftInternal(PuPurchaseRequisitionBo bo) {
        validateNewDraft(bo);
        List<PuPurchaseRequisitionLine> lines = bo.getPuPurchaseRequisitionLineList();
        String decisionKey = normalizeDecisionKey(bo.getDecisionIdempotencyKey());
        if (decisionKey != null) {
            validateDecisionIdentity(bo);
            PuPurchaseRequisition existing = findByDecisionKey(decisionKey);
            if (existing != null) {
                reuseExistingDraft(bo, existing, lines);
                return 0;
            }
        }

        PuPurchaseRequisition entity = MapstructUtils.convert(bo, PuPurchaseRequisition.class);
        if (StringUtils.isBlank(entity.getRequisitionNo())) {
            entity.setRequisitionNo(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null));
        }
        if (entity.getRequisitionDate() == null) {
            entity.setRequisitionDate(new Date());
        }
        entity.setDecisionIdempotencyKey(decisionKey);
        entity.setStatus(STATUS_DRAFT);
        entity.setVersion(0);

        final int rows;
        try {
            rows = baseMapper.insert(entity);
        } catch (DuplicateKeyException duplicate) {
            if (decisionKey == null) {
                throw duplicate;
            }
            PuPurchaseRequisition existing = findByDecisionKeyForUpdate(decisionKey);
            if (existing == null) {
                if (isCompensatedDecisionKey(decisionKey)) {
                    throw new ServiceException("采购申请已补偿，幂等键永久占用，不可重试");
                }
                throw duplicate;
            }
            reuseExistingDraft(bo, existing, lines);
            return 0;
        }
        if (rows != 1) {
            throw new ServiceException("采购申请主表写入失败");
        }
        if (entity.getRequisitionId() == null || entity.getRequisitionId() <= 0
            || StringUtils.isBlank(entity.getRequisitionNo())) {
            throw new ServiceException("采购申请主表未生成有效ID或编号");
        }

        writeBackBo(bo, entity);
        insertLines(entity.getRequisitionId(), lines);
        return rows;
    }

    private void validateNewDraft(PuPurchaseRequisitionBo bo) {
        if (bo == null) {
            throw new IllegalArgumentException("采购申请不能为空");
        }
        if (bo.getRequisitionId() != null) {
            throw new ServiceException("新建采购申请时ID必须为空");
        }
        List<PuPurchaseRequisitionLine> lines = bo.getPuPurchaseRequisitionLineList();
        if (lines == null || lines.isEmpty() || lines.stream().anyMatch(line ->
            line == null || line.getMaterialId() == null || line.getMaterialId() <= 0
                || line.getApplyQuantity() == null || line.getApplyQuantity().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ServiceException("采购申请至少需要一条有效明细");
        }
    }

    private void validateDecisionIdentity(PuPurchaseRequisitionBo bo) {
        if (bo.getDecisionRunId() == null || bo.getDecisionRunId() <= 0
            || bo.getDecisionActionId() == null || bo.getDecisionActionId() <= 0) {
            throw new ServiceException("AI决策采购申请缺少有效runId/actionId");
        }
        if (bo.getDecisionIdempotencyKey().length() > 128) {
            throw new ServiceException("AI决策幂等键长度不能超过128");
        }
    }

    private String normalizeDecisionKey(String key) {
        return StringUtils.isBlank(key) ? null : key;
    }

    private PuPurchaseRequisition findByDecisionKey(String key) {
        return baseMapper.selectOne(Wrappers.lambdaQuery(PuPurchaseRequisition.class)
            .eq(PuPurchaseRequisition::getDecisionIdempotencyKey, key));
    }

    private PuPurchaseRequisition findByDecisionKeyForUpdate(String key) {
        return baseMapper.selectOne(Wrappers.lambdaQuery(PuPurchaseRequisition.class)
            .eq(PuPurchaseRequisition::getDecisionIdempotencyKey, key)
            .last("FOR UPDATE"));
    }

    private boolean isCompensatedDecisionKey(String key) {
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("租户上下文缺失，无法确认采购申请幂等冲突");
        }
        return baseMapper.findCompensatedDecisionId(tenantId, key) != null;
    }

    private void reuseExistingDraft(PuPurchaseRequisitionBo requested,
                                    PuPurchaseRequisition existing,
                                    List<PuPurchaseRequisitionLine> requestedLines) {
        if (!matchesDecisionHeader(requested, existing)) {
            throw new ServiceException("幂等键对应的采购申请来源或载荷不一致");
        }
        List<PuPurchaseRequisitionLine> existingLines = lineMapper.selectList(
            Wrappers.lambdaQuery(PuPurchaseRequisitionLine.class)
                .eq(PuPurchaseRequisitionLine::getRequisitionId, existing.getRequisitionId())
                .last("FOR UPDATE"));
        if (!lineFingerprints(requestedLines).equals(lineFingerprints(existingLines))) {
            throw new ServiceException("幂等键对应的采购申请来源或载荷不一致");
        }
        writeBackBo(requested, existing);
        requested.setPuPurchaseRequisitionLineList(existingLines);
    }

    private boolean matchesDecisionHeader(PuPurchaseRequisitionBo requested,
                                          PuPurchaseRequisition existing) {
        return Objects.equals(requested.getDecisionRunId(), existing.getDecisionRunId())
            && Objects.equals(requested.getDecisionActionId(), existing.getDecisionActionId())
            && Objects.equals(requested.getDecisionIdempotencyKey(), existing.getDecisionIdempotencyKey())
            && Objects.equals(requested.getRequisitionName(), existing.getRequisitionName())
            && Objects.equals(requested.getApplicantId(), existing.getApplicantId())
            && Objects.equals(requested.getApplicantName(), existing.getApplicantName())
            && Objects.equals(toBusinessDate(requested.getRequiredDate()), toBusinessDate(existing.getRequiredDate()))
            && Objects.equals(requested.getSourceType(), existing.getSourceType());
    }

    private List<LineFingerprint> lineFingerprints(List<PuPurchaseRequisitionLine> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream().map(line -> new LineFingerprint(
                line.getMaterialId(),
                line.getMaterialCode(),
                line.getMaterialName(),
                normalizedDecimal(line.getApplyQuantity()),
                normalizedDecimal(line.getShortageQuantity()),
                line.getRequiredDate() == null ? null : toBusinessDate(line.getRequiredDate()),
                line.getSourceType(),
                line.getSourceId()))
            .sorted()
            .toList();
    }

    private BigDecimal normalizedDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private record LineFingerprint(Long materialId, String materialCode, String materialName,
                                   BigDecimal applyQuantity, BigDecimal shortageQuantity,
                                   LocalDate requiredDate, String sourceType, Long sourceId)
        implements Comparable<LineFingerprint> {

        private static final Comparator<LineFingerprint> ORDER = Comparator
            .comparing(LineFingerprint::materialId, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::materialCode, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::materialName, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::applyQuantity, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::shortageQuantity, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::requiredDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::sourceType, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::sourceId, Comparator.nullsFirst(Comparator.naturalOrder()));

        @Override
        public int compareTo(LineFingerprint other) {
            return ORDER.compare(this, other);
        }
    }

    private LocalDate toBusinessDate(Date value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return Instant.ofEpochMilli(value.getTime()).atZone(BUSINESS_ZONE).toLocalDate();
    }

    private void writeBackBo(PuPurchaseRequisitionBo bo, PuPurchaseRequisition entity) {
        bo.setRequisitionId(entity.getRequisitionId());
        bo.setRequisitionNo(entity.getRequisitionNo());
        bo.setStatus(entity.getStatus());
        bo.setDecisionRunId(entity.getDecisionRunId());
        bo.setDecisionActionId(entity.getDecisionActionId());
        bo.setDecisionIdempotencyKey(entity.getDecisionIdempotencyKey());
        bo.setVersion(entity.getVersion());
        bo.setCreateTime(entity.getCreateTime());
        bo.setUpdateTime(entity.getUpdateTime());
    }

    /**
     * 修改采购申请。父表先以 @Version 乐观更新，成功后才替换明细。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuPurchaseRequisitionBo bo) {
        if (bo == null || bo.getRequisitionId() == null || bo.getRequisitionId() <= 0) {
            throw new ServiceException("采购申请ID无效");
        }
        PuPurchaseRequisition current = baseMapper.selectById(bo.getRequisitionId());
        if (current == null) {
            throw new ServiceException("采购申请单不存在");
        }
        if (!STATUS_DRAFT.equals(current.getStatus()) && !STATUS_SUBMITTED.equals(current.getStatus())) {
            throw new ServiceException("当前状态不允许编辑采购申请单");
        }
        PuPurchaseRequisition entity = MapstructUtils.convert(bo, PuPurchaseRequisition.class);
        entity.setStatus(null);
        entity.setDecisionRunId(current.getDecisionRunId());
        entity.setDecisionActionId(current.getDecisionActionId());
        entity.setDecisionIdempotencyKey(current.getDecisionIdempotencyKey());
        entity.setVersion(currentVersion(current));
        int rows = baseMapper.updateById(entity);
        if (rows != 1) {
            throw new ServiceException("采购申请已被其他操作修改，请刷新后重试");
        }
        deleteLinesByRequisitionId(entity.getRequisitionId());
        insertLines(entity.getRequisitionId(), bo.getPuPurchaseRequisitionLineList());
        return rows;
    }

    /**
     * 批量删除采购申请，同时删除关联的明细行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            throw new ServiceException("采购申请ID不能为空");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0 || !uniqueIds.add(id)) {
                throw new ServiceException("采购申请ID无效或重复");
            }
        }
        for (Long id : uniqueIds) {
            PuPurchaseRequisition current = ensureRequisition(id);
            if (STATUS_APPROVING.equals(current.getStatus())) {
                throw new ServiceException("采购申请正在审批中，请先通过流程撤回或终止再删除");
            }
            if (current.getVersion() == null || StringUtils.isBlank(current.getStatus())) {
                throw new ServiceException("采购申请状态或版本无效，无法删除");
            }
            int deleted = baseMapper.delete(Wrappers.lambdaQuery(PuPurchaseRequisition.class)
                .eq(PuPurchaseRequisition::getRequisitionId, id)
                .eq(PuPurchaseRequisition::getVersion, current.getVersion())
                .eq(PuPurchaseRequisition::getStatus, current.getStatus()));
            if (deleted != 1) {
                throw new ServiceException("采购申请状态或版本已变化，删除冲突");
            }
        }
        for (Long id : uniqueIds) {
            deleteLinesByRequisitionId(id);
        }
        return uniqueIds.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDraftIfUnchanged(Long requisitionId, String versionMarker,
                                          Long decisionRunId, Long decisionActionId,
                                          String decisionIdempotencyKey) {
        Integer version = parseVersionMarker(requisitionId, versionMarker);
        if (version == null || decisionRunId == null || decisionRunId <= 0
            || decisionActionId == null || decisionActionId <= 0
            || StringUtils.isBlank(decisionIdempotencyKey)) {
            return false;
        }
        LambdaQueryWrapper<PuPurchaseRequisition> wrapper = Wrappers.lambdaQuery(PuPurchaseRequisition.class)
            .eq(PuPurchaseRequisition::getRequisitionId, requisitionId)
            .eq(PuPurchaseRequisition::getStatus, STATUS_DRAFT)
            .eq(PuPurchaseRequisition::getVersion, version)
            .eq(PuPurchaseRequisition::getDecisionRunId, decisionRunId)
            .eq(PuPurchaseRequisition::getDecisionActionId, decisionActionId)
            .eq(PuPurchaseRequisition::getDecisionIdempotencyKey, decisionIdempotencyKey);
        if (baseMapper.delete(wrapper) != 1) {
            return false;
        }
        deleteLinesByRequisitionId(requisitionId);
        return true;
    }

    // /**
    //  * 提交采购申请（已废弃，请使用 Flowable 工作流提交审批）
    //  */
    // @Override
    // @Transactional(rollbackFor = Exception.class)
    // public void submit(Long requisitionId) {
    //     PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
    //     if (!STATUS_DRAFT.equals(requisition.getStatus())) {
    //         throw new ServiceException("只有草稿状态的采购申请才能提交");
    //     }
    //     requisition.setStatus(STATUS_SUBMITTED);
    //     baseMapper.updateById(requisition);
    // }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 提交流程审批：先以 DRAFT + version 原子抢占为 APPROVING，再启动流程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String requisitionIdStr, String selectFlowId) {
        Long requisitionId = Long.parseLong(requisitionIdStr);
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (!STATUS_DRAFT.equals(requisition.getStatus())) {
            throw new ServiceException("只有草稿状态的采购申请才能提交");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (requisition.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(requisition.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        int version = currentVersion(requisition);
        int claimed = baseMapper.update(null, Wrappers.lambdaUpdate(PuPurchaseRequisition.class)
            .eq(PuPurchaseRequisition::getRequisitionId, requisitionId)
            .eq(PuPurchaseRequisition::getStatus, STATUS_DRAFT)
            .eq(PuPurchaseRequisition::getVersion, version)
            .set(PuPurchaseRequisition::getStatus, STATUS_APPROVING)
            .set(PuPurchaseRequisition::getVersion, version + 1)
            .set(PuPurchaseRequisition::getUpdateTime, new Date()));
        if (claimed != 1) {
            throw new ServiceException("采购申请状态已变化，无法启动审批");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", requisitionIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("requisitionId", requisition.getRequisitionId());
        variables.put("requisitionNo", requisition.getRequisitionNo());
        variables.put("requisitionName", requisition.getRequisitionName());
        variables.put("applicantName", requisition.getApplicantName());
        variables.put("requisitionDate", requisition.getRequisitionDate());
        R<Void> result = processService.startProcessByDataId(
            requisitionIdStr, selectFlowId, "puPurchaseRequisitionService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    /**
     * 获取采购申请对应的流程实例ID（运行时优先，其次历史）。
     *
     * @param requisitionIdStr 采购申请ID（字符串）
     * @return 流程实例ID，无则返回 null
     */
    @Override
    public String getProcInsId(String requisitionIdStr) {
        return procInsIdHelper.resolve(requisitionIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(requisitionIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 审批流节点处理完成后的回调：根据审批动作更新采购申请状态。
     *
     * @param business 工作流业务对象（含审批状态与数据ID）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        String actStatus = business.getActStatus();
        // submit 已在外层事务 CAS 抢占 APPROVING；同步 doing 回调不能再访问持锁行。
        if (ActStatus.doing.equals(actStatus)) {
            return;
        }
        Long requisitionId = Long.parseLong(business.getDataId());
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        String nextStatus;
        if (ActStatus.pass.equals(actStatus)) {
            nextStatus = STATUS_APPROVED;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            nextStatus = STATUS_REJECTED;
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus)
            || ActStatus.recall.equals(actStatus)) {
            nextStatus = STATUS_DRAFT;
        } else {
            return;
        }
        if (!STATUS_APPROVING.equals(requisition.getStatus())) {
            throw new ServiceException("采购申请不在审批中，不能处理审批回调");
        }
        int version = currentVersion(requisition);
        int updated = baseMapper.update(null, Wrappers.lambdaUpdate(PuPurchaseRequisition.class)
            .eq(PuPurchaseRequisition::getRequisitionId, requisitionId)
            .eq(PuPurchaseRequisition::getStatus, STATUS_APPROVING)
            .eq(PuPurchaseRequisition::getVersion, version)
            .set(PuPurchaseRequisition::getStatus, nextStatus)
            .set(PuPurchaseRequisition::getVersion, version + 1)
            .set(PuPurchaseRequisition::getUpdateTime, new Date()));
        if (updated != 1) {
            throw new ServiceException("采购申请审批状态已被并发修改");
        }
    }

    /**
     * 根据数据ID返回采购申请详情（供审批详情展示）。
     *
     * @param dataId 业务数据ID
     * @return 采购申请业务对象
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取审批任务所需的表单变量（当前实现无需额外变量，返回 null）。
     *
     * @param taskNameId 任务节点ID
     * @param values     已有流程变量
     * @return 任务表单变量，未实现返回 null
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取审批任务的候选处理人（当前实现未指定，返回 null）。
     *
     * @param taskNameId 任务节点ID
     * @param values     流程变量
     * @return 候选用户名列表，未实现返回 null
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    // /**
    //  * 审批采购申请（已废弃，请使用 Flowable 工作流审批）
    //  */
    // @Override
    // @Transactional(rollbackFor = Exception.class)
    // public void approve(Long requisitionId) {
    //     PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
    //     if (!STATUS_SUBMITTED.equals(requisition.getStatus()) && !STATUS_APPROVING.equals(requisition.getStatus())) {
    //         throw new ServiceException("当前状态不允许审批采购申请");
    //     }
    //     requisition.setStatus(STATUS_APPROVED);
    //     baseMapper.updateById(requisition);
    // }

    /**
     * 关闭采购申请。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (STATUS_APPROVING.equals(requisition.getStatus())) {
            throw new ServiceException("采购申请正在审批中，请先通过流程撤回或终止再关闭");
        }
        requisition.setStatus(STATUS_CLOSED);
        updateExistingOrThrow(requisition, "采购申请关闭时版本已变化");
    }

    /**
     * 作废采购申请：已转单的申请不允许作废。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (STATUS_APPROVING.equals(requisition.getStatus())) {
            throw new ServiceException("采购申请正在审批中，请先通过流程撤回或终止再作废");
        }
        if (STATUS_ORDERED.equals(requisition.getStatus())) {
            throw new ServiceException("已转单的采购申请不能作废");
        }
        requisition.setStatus(STATUS_CANCELLED);
        updateExistingOrThrow(requisition, "采购申请作废时版本已变化");
    }

    /**
     * 将已批准采购申请转换为采购订单；任一写入或最终版本更新失败均抛异常回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToPurchaseOrder(Long requisitionId) {
        PuPurchaseRequisition requisition = ensureRequisition(requisitionId);
        if (!STATUS_APPROVED.equals(requisition.getStatus())) {
            throw new ServiceException("只有已批准的采购申请才能转采购订单");
        }
        List<PuPurchaseRequisitionLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(PuPurchaseRequisitionLine.class)
                .eq(PuPurchaseRequisitionLine::getRequisitionId, requisitionId));
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("采购申请无明细行");
        }

        PuPurchaseOrder order = new PuPurchaseOrder();
        order.setOrderNo(autoCodeUtil.genSerialCode("PU_ORDER_CODE", null));
        order.setRequisitionId(requisition.getRequisitionId());
        order.setRequisitionNo(requisition.getRequisitionNo());
        order.setOrderDate(new Date());
        order.setStatus("DRAFT");
        order.setRemark("由采购申请【" + requisition.getRequisitionNo() + "】转单生成");
        if (purchaseOrderMapper.insert(order) != 1 || order.getOrderId() == null) {
            throw new ServiceException("采购订单主表写入失败");
        }

        for (PuPurchaseRequisitionLine reqLine : lines) {
            PuPurchaseOrderLine orderLine = new PuPurchaseOrderLine();
            orderLine.setOrderId(order.getOrderId());
            orderLine.setRequisitionLineId(reqLine.getLineId());
            orderLine.setMaterialId(reqLine.getMaterialId());
            orderLine.setMaterialCode(reqLine.getMaterialCode());
            orderLine.setMaterialName(reqLine.getMaterialName());
            orderLine.setMaterialSpec(reqLine.getMaterialSpec());
            orderLine.setUnit(reqLine.getUnit());
            orderLine.setOrderQuantity(reqLine.getApplyQuantity());
            orderLine.setReceivedQuantity(BigDecimal.ZERO);
            orderLine.setSortOrder(reqLine.getAttr3() != null ? reqLine.getAttr3().intValue() : null);
            orderLine.setRemark(reqLine.getRemark());
            if (purchaseOrderLineMapper.insert(orderLine) != 1) {
                throw new ServiceException("采购订单明细写入失败");
            }
        }

        requisition.setStatus(STATUS_ORDERED);
        updateExistingOrThrow(requisition, "采购申请转单时版本已变化");
        log.info("采购申请 {} 已转采购订单 {}", requisitionId, order.getOrderId());
        return order.getOrderId();
    }

    private Integer parseVersionMarker(Long id, String marker) {
        if (id == null || id <= 0 || marker == null || !marker.startsWith("V:")) {
            return null;
        }
        String versionText = marker.substring(2);
        if (versionText.isEmpty() || !versionText.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int version = Integer.parseInt(versionText);
            return version < 0 ? null : version;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int currentVersion(PuPurchaseRequisition requisition) {
        return requisition.getVersion() == null ? 0 : requisition.getVersion();
    }

    private void updateExistingOrThrow(PuPurchaseRequisition requisition, String message) {
        requisition.setVersion(currentVersion(requisition));
        if (baseMapper.updateById(requisition) != 1) {
            throw new ServiceException(message);
        }
    }

    /**
     * 批量插入采购申请明细行，每行必须恰好写入一条。
     */
    private void insertLines(Long requisitionId, List<PuPurchaseRequisitionLine> lineList) {
        if (requisitionId == null || lineList == null || lineList.isEmpty()) {
            return;
        }
        for (PuPurchaseRequisitionLine line : lineList) {
            line.setLineId(null);
            line.setRequisitionId(requisitionId);
            if (lineMapper.insert(line) != 1) {
                throw new ServiceException("采购申请明细写入失败");
            }
        }
    }

    /**
     * 删除指定采购申请的所有明细行
     *
     * @param requisitionId 采购申请ID
     */
    private void deleteLinesByRequisitionId(Long requisitionId) {
        if (requisitionId == null) {
            return;
        }
        lineMapper.delete(Wrappers.lambdaQuery(PuPurchaseRequisitionLine.class).eq(PuPurchaseRequisitionLine::getRequisitionId, requisitionId));
    }

    /**
     * 确保采购申请存在，否则抛出异常
     *
     * @return 采购申请实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generateFromLowStock() {
        List<BdMaterial> materials = bdMaterialMapper.selectList(
            new LambdaQueryWrapper<BdMaterial>()
                // ★ ERP-BD-02: bd_material.status 是 tinyint(0=停用/1=启用)，
                // 不能与供应商状态字符串 'ACTIVE' 比较（MySQL 隐式转换为 0，只会命中停用物料）。
                // 与 WmStockAlertServiceImpl:154 保持一致，用数值比较。
                .eq(BdMaterial::getStatus, 1L)
                .isNotNull(BdMaterial::getSafetyStock)
                .gt(BdMaterial::getSafetyStock, BigDecimal.ZERO));

        if (materials == null || materials.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, BigDecimal> stockMap = new HashMap<>();
        List<Long> materialIds = materials.stream()
            .map(BdMaterial::getMaterialId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (!materialIds.isEmpty()) {
            List<WmMaterialStock> stocks = materialStockMapper.selectList(
                new LambdaQueryWrapper<WmMaterialStock>()
                    .in(WmMaterialStock::getMaterialId, materialIds)
                    .eq(WmMaterialStock::getFrozenFlag, 0L));
            if (stocks != null) {
                for (WmMaterialStock stock : stocks) {
                    BigDecimal current = stockMap.getOrDefault(stock.getMaterialId(), BigDecimal.ZERO);
                    stockMap.put(stock.getMaterialId(), current.add(stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO));
                }
            }
        }

        Map<String, Long> existingRequisitionMap = new HashMap<>();
        List<PuPurchaseRequisition> existingRequisitions = baseMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseRequisition>()
                .in(PuPurchaseRequisition::getStatus, "DRAFT", "SUBMITTED", "APPROVED")
                .eq(PuPurchaseRequisition::getSourceType, "LOW_STOCK"));
        if (existingRequisitions != null) {
            for (PuPurchaseRequisition req : existingRequisitions) {
                existingRequisitionMap.put(req.getRequisitionNo(), req.getRequisitionId());
            }
        }
        // 已有未完结低库存申请单的物料集合（用于去重：同一物料已生成申请则不再重复生成）
        java.util.Set<Long> alreadyRequestedMaterialIds = new java.util.HashSet<>();
        if (!existingRequisitionMap.isEmpty()) {
            List<PuPurchaseRequisitionLine> existingLines = lineMapper.selectList(
                new LambdaQueryWrapper<PuPurchaseRequisitionLine>()
                    .in(PuPurchaseRequisitionLine::getRequisitionId, existingRequisitionMap.values()));
            if (existingLines != null) {
                for (PuPurchaseRequisitionLine line : existingLines) {
                    if (line.getMaterialId() != null) {
                        alreadyRequestedMaterialIds.add(line.getMaterialId());
                    }
                }
            }
        }

        List<Long> generatedIds = new ArrayList<>();
        List<PuPurchaseRequisitionLine> lowStockLines = new ArrayList<>();

        for (BdMaterial material : materials) {
            // 去重：该物料已有未完结的低库存申请单则跳过
            if (alreadyRequestedMaterialIds.contains(material.getMaterialId())) {
                continue;
            }
            BigDecimal currentStock = stockMap.getOrDefault(material.getMaterialId(), BigDecimal.ZERO);
            BigDecimal safetyStock = material.getSafetyStock();

            if (currentStock.compareTo(safetyStock) < 0) {
                BigDecimal maxStock = material.getMaxStock() != null ? material.getMaxStock() : safetyStock.multiply(new BigDecimal("2"));
                BigDecimal suggestQty = maxStock.subtract(currentStock);

                if (suggestQty.compareTo(BigDecimal.ZERO) > 0) {
                    PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
                    line.setMaterialId(material.getMaterialId());
                    line.setMaterialCode(material.getMaterialCode());
                    line.setMaterialName(material.getMaterialName());
                    line.setMaterialSpec(material.getMaterialSpec());
                    line.setApplyQuantity(suggestQty);
                    line.setSourceType("LOW_STOCK");
                    line.setSourceId(material.getMaterialId());
                    lowStockLines.add(line);
                }
            }
        }

        if (lowStockLines.isEmpty()) {
            return new ArrayList<>();
        }

        // 修复：原为 "pu_purchase_requisition"（小写），库中不存在该规则编码，
        // 会抛「未获取到指定类型:[pu_purchase_requisition]的业务编码生成规则」。
        // 与 L147 保持一致，复用 PU_REQUISITION_CODE 规则。
        String reqCode = autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null);
        PuPurchaseRequisition requisition = new PuPurchaseRequisition();
        requisition.setRequisitionNo(reqCode);
        requisition.setSourceType("LOW_STOCK");
        requisition.setRequisitionDate(new Date());
        requisition.setStatus("DRAFT");
        if (baseMapper.insert(requisition) != 1 || requisition.getRequisitionId() == null) {
            throw new ServiceException("低库存采购申请主表写入失败");
        }

        for (PuPurchaseRequisitionLine line : lowStockLines) {
            line.setRequisitionId(requisition.getRequisitionId());
            if (lineMapper.insert(line) != 1) {
                throw new ServiceException("低库存采购申请明细写入失败");
            }
        }

        generatedIds.add(requisition.getRequisitionId());
        return generatedIds;
    }

    /**
     * 加载采购申请单，不存在则抛异常。
     *
     * @param requisitionId 采购申请单ID
     * @return 采购申请单实体
     */
    private PuPurchaseRequisition ensureRequisition(Long requisitionId) {
        PuPurchaseRequisition requisition = baseMapper.selectById(requisitionId);
        if (requisition == null) {
            throw new ServiceException("采购申请单不存在");
        }
        return requisition;
    }

    /**
     * 构建查询条件 Wrapper
     * <p>
     * 支持按申请单号精确匹配、按申请名称模糊匹配、按状态精确匹配，默认按创建时间降序排列。
     *
     * @param bo 查询条件
     * @return 查询条件 Wrapper
     */
    private LambdaQueryWrapper<PuPurchaseRequisition> buildQueryWrapper(PuPurchaseRequisitionBo bo) {
        LambdaQueryWrapper<PuPurchaseRequisition> lqw = Wrappers.<PuPurchaseRequisition>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getRequisitionNo()), PuPurchaseRequisition::getRequisitionNo, bo.getRequisitionNo())
            .like(StringUtils.isNotBlank(bo.getRequisitionName()), PuPurchaseRequisition::getRequisitionName, bo.getRequisitionName())
            .eq(bo.getDeptId() != null, PuPurchaseRequisition::getDeptId, bo.getDeptId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), PuPurchaseRequisition::getStatus, bo.getStatus())
            .orderByDesc(PuPurchaseRequisition::getCreateTime);
        return lqw;
    }
}
