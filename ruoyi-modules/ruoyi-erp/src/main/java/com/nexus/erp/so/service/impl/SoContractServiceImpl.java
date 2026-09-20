package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.erp.so.domain.SoContract;
import com.nexus.erp.so.domain.SoContractLine;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.domain.vo.SoContractLineVo;
import com.nexus.erp.so.domain.vo.SoContractVo;
import com.nexus.erp.so.mapper.SoContractLineMapper;
import com.nexus.erp.so.mapper.SoContractMapper;
import com.nexus.erp.so.service.ISoContractService;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售合同Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soContractService")
public class SoContractServiceImpl implements ISoContractService {

    private final SoContractMapper baseMapper;
    private final SoContractLineMapper lineMapper;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;
    /** 正文签署状态补充器（es_sign_document 在主数据源，见该类注释） */
    private final SoContractSignStatusFiller signStatusFiller;

    /**
     * 分页查询销售合同列表
     */
    @Override
    public TableDataInfo<SoContractVo> selectPageList(SoContractBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoContract> lqw = buildQueryWrapper(bo);
        Page<SoContractVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 签署状态不在 so_contract 表里（在 es_sign_document），由补充器一次 IN 查询批量回填，无 N+1
        signStatusFiller.fillDocSignStatus(page.getRecords(), SignBizType.SO_CONTRACT);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售合同列表（不分页）
     */
    @Override
    public List<SoContractVo> selectList(SoContractBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售合同（含明细行）
     */
    @Override
    public SoContractVo selectById(Long id) {
        SoContractVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<SoContractLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoContractLine::getContractId, vo.getContractId());
            lqw.orderByAsc(SoContractLine::getSortOrder);
            // 转 VO 返回，避免把 tenantId/delFlag/createBy 等审计字段暴露给前端
            vo.setSoContractLineList(MapstructUtils.convert(lineMapper.selectList(lqw), SoContractLineVo.class));
        }
        return vo;
    }

    /**
     * 新增销售合同（服务端强制草稿态）并插入明细行。
     * <p>状态一律由服务端置 {@link #STATUS_DRAFT}，忽略客户端传入值，防止绕过审批直接造出已生效合同。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoContractBo bo) {
        SoContract entity = MapstructUtils.convert(bo, SoContract.class);
        entity.setStatus(STATUS_DRAFT);
        List<SoContractLine> lines = normalizeLines(bo.getSoContractLineList());
        applyTotalAmount(entity, lines);
        int rows = baseMapper.insert(entity);
        if (lines != null) {
            for (SoContractLine line : lines) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改销售合同（仅草稿/驳回可改），差量更新明细行。
     * <p>状态沿用库中现值，忽略客户端传入值——状态只能经审批回调（{@link #afterFlowHandle}）流转。
     * 明细行为差量重建，已发货/已开票数量等执行进度字段以本次提交为准重置。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoContractBo bo) {
        SoContract existing = baseMapper.selectById(bo.getContractId());
        if (existing == null) {
            throw new ServiceException("销售合同不存在");
        }
        if (!STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改合同", existing.getStatus());
        }
        SoContract entity = MapstructUtils.convert(bo, SoContract.class);
        entity.setStatus(existing.getStatus());
        List<SoContractLine> lines = normalizeLines(bo.getSoContractLineList());
        applyTotalAmount(entity, lines);
        int rows = baseMapper.updateById(entity);
        if (bo.getSoContractLineList() != null) {
            lineMapper.delete(Wrappers.lambdaQuery(SoContractLine.class)
                .eq(SoContractLine::getContractId, entity.getContractId()));
            for (SoContractLine line : lines) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 批量删除销售合同（仅草稿可删）。
     * <p>删除条件带上 {@code status = DRAFT} 做条件更新（CAS）：先查后删的间隙里状态被并发流转时，
     * 影响行数为 0 即抛异常回滚，避免删掉一份已进入审批/生效的合同。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        int rows = 0;
        for (Long id : ids) {
            SoContract existing = baseMapper.selectById(id);
            if (existing == null) {
                continue;
            }
            if (!STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("合同【{}】当前状态不允许删除", existing.getContractNo());
            }
            int deleted = baseMapper.delete(Wrappers.lambdaQuery(SoContract.class)
                .eq(SoContract::getContractId, id)
                .eq(SoContract::getStatus, STATUS_DRAFT));
            if (deleted == 0) {
                throw new ServiceException("合同【{}】状态已变更，请刷新后重试", existing.getContractNo());
            }
            lineMapper.delete(Wrappers.lambdaQuery(SoContractLine.class)
                .eq(SoContractLine::getContractId, id));
            rows += deleted;
        }
        return rows;
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 发起审批流程（Flowable 工作流回调）：校验合同存在且处于可提交状态（草稿/驳回），启动审批流并注入流程变量。
     *
     * @param contractIdStr 合同ID（字符串）
     * @param selectFlowId  流程定义ID
     * @return 启动成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String contractIdStr, String selectFlowId) {
        Long contractId = parseContractId(contractIdStr);
        SoContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("销售合同不存在");
        }
        if (!STATUS_DRAFT.equals(contract.getStatus()) && !STATUS_REJECTED.equals(contract.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许提交审批", contract.getStatus());
        }

        if (StringUtils.isBlank(selectFlowId)) {
            if (contract.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(contract.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", contractIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("contractId", contract.getContractId());
        variables.put("contractNo", contract.getContractNo());
        variables.put("contractName", contract.getContractName());
        variables.put("clientName", contract.getClientName());
        variables.put("totalAmount", contract.getTotalAmount());
        R<Void> result = processService.startProcessByDataId(contractIdStr, selectFlowId, "soContractService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    /**
     * 获取合同对应的流程实例ID（运行时优先，其次历史）。
     *
     * @param contractIdStr 合同ID（字符串）
     * @return 流程实例ID，无则返回 null
     */
    @Override
    public String getProcInsId(String contractIdStr) {
        return procInsIdHelper.resolve(contractIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(contractIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    /**
     * 审批流节点处理完成后的回调：根据审批动作更新合同状态。
     *
     * @param business 工作流业务对象（含审批状态与数据ID）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long contractId = Long.parseLong(business.getDataId());
        SoContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            return;
        }
        String actStatus = business.getActStatus();
        String newStatus;
        if (ActStatus.doing.equals(actStatus)) {
            newStatus = STATUS_APPROVING;
        } else if (ActStatus.pass.equals(actStatus)) {
            newStatus = STATUS_EFFECTIVE;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            newStatus = STATUS_REJECTED;
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            newStatus = STATUS_DRAFT;
        } else {
            return;
        }
        // 条件更新：只在合同仍处于「流程在途」状态时才改写，不把已生效（EFFECTIVE）的合同改回 DRAFT
        // （与销售订单/发票/退货的 afterFlowHandle 口径一致）
        int updated = baseMapper.update(null,
            Wrappers.<SoContract>lambdaUpdate()
                .set(SoContract::getStatus, newStatus)
                .eq(SoContract::getContractId, contractId)
                .in(SoContract::getStatus, STATUS_DRAFT, STATUS_APPROVING, STATUS_REJECTED));
        if (updated == 0) {
            log.warn("销售合同[{}]当前状态[{}]不允许流程回调改写为[{}]，忽略本次变更",
                contractId, contract.getStatus(), newStatus);
        }
    }

    /**
     * 终止合同（仅 EFFECTIVE 可终止）。
     *
     * <p>状态由 {@code EFFECTIVE -> TERMINATED}，条件更新做 CAS 防并发。
     * 终止原因写入 {@code remark}（{@code so_contract} 无独立的终止原因列，复用备注并保留原备注）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long contractId, String reason) {
        SoContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("销售合同不存在");
        }
        if (STATUS_TERMINATED.equals(contract.getStatus())) {
            throw new ServiceException("合同【{}】已终止，请勿重复操作", contract.getContractNo());
        }
        if (!STATUS_EFFECTIVE.equals(contract.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许终止，仅已生效的合同可终止", contract.getStatus());
        }
        String suffix = "【终止】" + (StringUtils.isBlank(reason) ? "未填写原因" : reason);
        String newRemark = StringUtils.isBlank(contract.getRemark()) ? suffix : contract.getRemark() + "；" + suffix;
        int updated = baseMapper.update(null,
            Wrappers.<SoContract>lambdaUpdate()
                .set(SoContract::getStatus, STATUS_TERMINATED)
                .set(SoContract::getRemark, newRemark)
                .eq(SoContract::getContractId, contractId)
                .eq(SoContract::getStatus, STATUS_EFFECTIVE));
        if (updated == 0) {
            throw new ServiceException("合同【{}】状态已变更，请刷新后重试", contract.getContractNo());
        }
        log.info("销售合同终止, contractId={}, contractNo={}, reason={}", contractId, contract.getContractNo(), reason);
    }

    /**
     * 把已到期（{@code expiry_date < 今天}）的已生效合同批量置为 EXPIRED。
     *
     * <p>条件更新一次完成，避免逐行读改写；已终止/草稿等状态不受影响。
     * 到期日**当天仍算有效**（{@code expiry_date} 为「有效期至」的含端语义），
     * 因此判据是严格小于今天。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireOverdueContracts() {
        Date startOfToday = startOfToday();
        int updated = baseMapper.update(null,
            Wrappers.<SoContract>lambdaUpdate()
                .set(SoContract::getStatus, STATUS_EXPIRED)
                .eq(SoContract::getStatus, STATUS_EFFECTIVE)
                .isNotNull(SoContract::getExpiryDate)
                .lt(SoContract::getExpiryDate, startOfToday));
        if (updated > 0) {
            log.info("销售合同到期扫描：{} 份合同已置为 EXPIRED", updated);
        }
        return updated;
    }

    /** 取「今天 00:00:00」，用于到期判定（当天仍算有效） */
    private Date startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /**
     * 根据数据ID返回合同详情（供审批详情展示）。
     *
     * @param dataId 业务数据ID
     * @return 合同业务对象
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

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoContract> buildQueryWrapper(SoContractBo bo) {
        LambdaQueryWrapper<SoContract> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getContractNo()), SoContract::getContractNo, bo.getContractNo());
        lqw.like(StringUtils.isNotBlank(bo.getContractName()), SoContract::getContractName, bo.getContractName());
        lqw.eq(bo.getClientId() != null, SoContract::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoContract::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoContract::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getContractType()), SoContract::getContractType, bo.getContractType());
        // 关键字模糊搜索：合同号 / 合同名称 / 客户名称 任一匹配（移动端单输入框搜索）
        lqw.and(StringUtils.isNotBlank(bo.getKeyword()),
            w -> w.like(SoContract::getContractNo, bo.getKeyword())
                .or()
                .like(SoContract::getContractName, bo.getKeyword())
                .or()
                .like(SoContract::getClientName, bo.getKeyword()));
        lqw.orderByDesc(SoContract::getCreateTime);
        return lqw;
    }

    /**
     * 把客户端提交的明细行重建为只含业务字段的干净列表。
     * <p>BO 的明细直接复用了实体类型，若原样入库，请求体里的 lineId/tenantId/del_flag/审计字段、
     * 以及已发货/已开票数量等执行进度字段都会被一并写库（批量赋值）。这里逐字段白名单拷贝，
     * 并做数值校验与金额重算：行金额 = 单价×数量、税额 = 行金额×税率÷100（均 2 位小数，
     * 与库表精度一致），以服务端计算为准，不信任客户端金额。</p>
     *
     * @param lines 客户端明细行（可为 null）
     * @return 净化后的明细行列表（入参 null 时返回 null）
     */
    private List<SoContractLine> normalizeLines(List<SoContractLine> lines) {
        if (lines == null) {
            return null;
        }
        List<SoContractLine> normalized = new ArrayList<>(lines.size());
        int seq = 1;
        for (SoContractLine line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getQuantity() != null && line.getQuantity().signum() < 0) {
                throw new ServiceException("合同明细数量不能为负数");
            }
            if (line.getUnitPrice() != null && line.getUnitPrice().signum() < 0) {
                throw new ServiceException("合同明细单价不能为负数");
            }
            if (line.getTaxRate() != null && (line.getTaxRate().signum() < 0 || line.getTaxRate().compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new ServiceException("合同明细税率必须在 0~100 之间");
            }
            SoContractLine clean = new SoContractLine();
            clean.setLineNo(line.getLineNo());
            clean.setMaterialId(line.getMaterialId());
            clean.setMaterialCode(line.getMaterialCode());
            clean.setMaterialName(line.getMaterialName());
            clean.setMaterialSpec(line.getMaterialSpec());
            clean.setQuantity(line.getQuantity());
            clean.setUnit(line.getUnit());
            clean.setUnitPrice(line.getUnitPrice());
            clean.setTaxRate(line.getTaxRate());
            clean.setDeliveryDate(line.getDeliveryDate());
            clean.setSortOrder(line.getSortOrder() != null ? line.getSortOrder() : seq);
            clean.setRemark(line.getRemark());
            clean.setAttr1(line.getAttr1());
            clean.setAttr2(line.getAttr2());
            clean.setAttr3(line.getAttr3());
            clean.setAttr4(line.getAttr4());
            if (clean.getUnitPrice() != null && clean.getQuantity() != null) {
                BigDecimal lineAmount = clean.getUnitPrice().multiply(clean.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                clean.setLineAmount(lineAmount);
                if (clean.getTaxRate() != null) {
                    clean.setTaxAmount(lineAmount.multiply(clean.getTaxRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                }
            } else {
                clean.setLineAmount(line.getLineAmount());
                clean.setTaxAmount(line.getTaxAmount());
            }
            normalized.add(clean);
            seq++;
        }
        return normalized;
    }

    /**
     * 明细行非空时以行金额合计覆盖合同总金额。
     *
     * @param entity 合同实体（原地覆盖 totalAmount）
     * @param lines  净化后的明细行（null 表示本次未提交明细，保持原值不动）
     */
    private void applyTotalAmount(SoContract entity, List<SoContractLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SoContractLine line : lines) {
            if (line.getLineAmount() != null) {
                total = total.add(line.getLineAmount());
            }
        }
        entity.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
    }

    /** 解析合同ID字符串，非数字时给出业务异常而非 500 */
    private Long parseContractId(String contractIdStr) {
        if (StringUtils.isBlank(contractIdStr)) {
            throw new ServiceException("合同ID不能为空");
        }
        try {
            return Long.parseLong(contractIdStr.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("合同ID格式错误: {}", contractIdStr);
        }
    }
}
