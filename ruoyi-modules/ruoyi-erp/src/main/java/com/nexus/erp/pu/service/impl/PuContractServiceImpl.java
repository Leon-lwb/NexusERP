package com.nexus.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.nexus.erp.pu.domain.PuContract;
import com.nexus.erp.pu.domain.PuContractLine;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.bo.PuContractBo;
import com.nexus.erp.pu.domain.vo.PuContractVo;
import com.nexus.erp.pu.mapper.PuContractLineMapper;
import com.nexus.erp.pu.mapper.PuContractMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.service.IPuContractService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购合同Service业务层处理
 *
 * @author nexus
 * @date 2026-06-15
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("puContractService")
public class PuContractServiceImpl implements IPuContractService {

    private final PuContractMapper baseMapper;
    private final PuContractLineMapper lineMapper;
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;

    /**
     * 分页查询采购合同列表。
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 采购合同分页列表
     */
    @Override
    public TableDataInfo<PuContractVo> selectPageList(PuContractBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuContract> lqw = buildQueryWrapper(bo);
        Page<PuContractVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询采购合同列表。
     *
     * @param bo 查询参数
     * @return 采购合同列表
     */
    @Override
    public List<PuContractVo> selectList(PuContractBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询采购合同详情（含明细行）。
     *
     * @param id 合同ID
     * @return 采购合同详情
     */
    @Override
    public PuContractVo selectById(Long id) {
        PuContractVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<PuContractLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(PuContractLine::getContractId, vo.getContractId());
            lqw.orderByAsc(PuContractLine::getSortOrder);
            vo.setPuContractLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增采购合同（默认草稿）并插入明细行。
     *
     * @param bo 采购合同业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuContractBo bo) {
        PuContract entity = MapstructUtils.convert(bo, PuContract.class);
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        int rows = baseMapper.insert(entity);
        if (bo.getPuContractLineList() != null && !bo.getPuContractLineList().isEmpty()) {
            for (PuContractLine line : bo.getPuContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改采购合同（仅草稿/驳回可改），差量更新明细行。
     *
     * @param bo 采购合同业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuContractBo bo) {
        PuContract existing = baseMapper.selectById(bo.getContractId());
        if (existing != null && !STATUS_DRAFT.equals(existing.getStatus()) && !STATUS_REJECTED.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改合同", existing.getStatus());
        }
        PuContract entity = MapstructUtils.convert(bo, PuContract.class);
        int rows = baseMapper.updateById(entity);
        if (bo.getPuContractLineList() != null) {
            lineMapper.delete(Wrappers.lambdaQuery(PuContractLine.class)
                .eq(PuContractLine::getContractId, entity.getContractId()));
            for (PuContractLine line : bo.getPuContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 批量删除采购合同（仅草稿可删，非草稿抛异常并清理明细行）。
     *
     * @param ids 合同ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            PuContract existing = baseMapper.selectById(id);
            if (existing != null && !STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("合同【{}】当前状态不允许删除", existing.getContractNo());
            }
            lineMapper.delete(Wrappers.lambdaQuery(PuContractLine.class)
                .eq(PuContractLine::getContractId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 发起审批流程（Flowable 工作流回调）：校验合同存在并启动对应审批流，注入流程变量。
     *
     * @param contractIdStr 采购合同ID（字符串）
     * @param selectFlowId  流程定义ID
     * @return 启动成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String contractIdStr, String selectFlowId) {
        Long contractId = Long.parseLong(contractIdStr);
        PuContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("采购合同不存在");
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
        variables.put("vendorName", contract.getVendorName());
        variables.put("totalAmount", contract.getTotalAmount());
        R<Void> result = processService.startProcessByDataId(contractIdStr, selectFlowId, "puContractService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    /**
     * 获取采购合同对应的流程实例ID（运行时优先，其次历史）。
     *
     * @param contractIdStr 采购合同ID（字符串）
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
     * 审批流节点处理完成后的回调：根据审批动作更新合同状态（审批中/已批准/已驳回/草稿）。
     *
     * @param business 工作流业务对象（含审批状态与数据ID）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long contractId = Long.parseLong(business.getDataId());
        PuContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            contract.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            contract.setStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            contract.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            contract.setStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(contract);
    }

    /**
     * 根据数据ID返回采购合同详情（供审批详情展示）。
     *
     * @param dataId 业务数据ID
     * @return 采购合同业务对象
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
     * 根据采购合同生成采购订单：复制合同头与明细行为采购订单草稿，并把合同状态置为执行中。
     * <p>仅 APPROVED/EXECUTING 状态的合同可生成。</p>
     *
     * @param contractId 采购合同ID
     * @return 生成的采购订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchaseOrderFromContract(Long contractId) {
        // 1. 校验合同存在且状态为 APPROVED（EXECUTING 表示已生成过采购订单，禁止重复生成）
        PuContract contract = baseMapper.selectById(contractId);
        if (contract == null) {
            throw new ServiceException("合同不存在: " + contractId);
        }
        if (!STATUS_APPROVED.equals(contract.getStatus())) {
            throw new ServiceException("合同状态不允许生成采购订单, 仅 APPROVED 可生成(当前:" + contract.getStatus() + ")");
        }

        // 2. 查合同明细
        List<PuContractLine> lines = lineMapper.selectList(
            Wrappers.<PuContractLine>lambdaQuery().eq(PuContractLine::getContractId, contractId));
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("合同明细为空, 无法生成采购订单");
        }

        // 3. 创建采购订单主表
        PuPurchaseOrder order = new PuPurchaseOrder();
        order.setOrderNo(autoCodeUtil.genSerialCode("PU_ORDER_CODE", null));
        order.setVendorId(contract.getVendorId());
        order.setVendorCode(contract.getVendorCode());
        order.setVendorName(contract.getVendorName());
        order.setOrderDate(new Date());
        order.setStatus("DRAFT");
        order.setContractId(contract.getContractId());
        order.setContractNo(contract.getContractNo());
        purchaseOrderMapper.insert(order);

        // 4. 逐行创建采购订单明细
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PuContractLine line : lines) {
            PuPurchaseOrderLine orderLine = new PuPurchaseOrderLine();
            orderLine.setOrderId(order.getOrderId());
            orderLine.setMaterialId(line.getMaterialId());
            orderLine.setMaterialCode(line.getMaterialCode());
            orderLine.setMaterialName(line.getMaterialName());
            orderLine.setMaterialSpec(line.getMaterialSpec());
            orderLine.setUnit(line.getUnit());
            orderLine.setUnitPrice(line.getUnitPrice());
            orderLine.setOrderQuantity(line.getQuantity());
            orderLine.setReceivedQuantity(BigDecimal.ZERO);
            orderLine.setLineAmount(line.getLineAmount());
            orderLine.setSortOrder(line.getSortOrder());
            purchaseOrderLineMapper.insert(orderLine);
            if (line.getLineAmount() != null) {
                totalAmount = totalAmount.add(line.getLineAmount());
            }
        }
        order.setTotalAmount(totalAmount);
        purchaseOrderMapper.updateById(order);

        // 5. 更新合同状态为 EXECUTING——CAS 条件更新（仅 APPROVED 可转为 EXECUTING），
        //    并发重复生成时第二个事务影响行数为 0 直接失败，杜绝同一合同生成多张采购订单
        LambdaUpdateWrapper<PuContract> wrapper = Wrappers.<PuContract>lambdaUpdate()
            .set(PuContract::getStatus, STATUS_EXECUTING)
            .eq(PuContract::getContractId, contractId)
            .eq(PuContract::getStatus, STATUS_APPROVED);
        int contractRows = baseMapper.update(null, wrapper);
        if (contractRows == 0) {
            throw new ServiceException("合同状态已变更(可能已生成采购订单), 请勿重复操作");
        }

        log.info("合同 {} 生成采购订单 {}: 金额 {}", contractId, order.getOrderNo(), totalAmount);
        return order.getOrderId();
    }

    /**
     * 构建采购合同分页/列表查询条件（按合同号、名称、供应商、类型、状态动态拼接，按创建时间倒序）。
     *
     * @param bo 查询参数
     * @return 查询条件构造器
     */
    private LambdaQueryWrapper<PuContract> buildQueryWrapper(PuContractBo bo) {
        LambdaQueryWrapper<PuContract> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getContractNo()), PuContract::getContractNo, bo.getContractNo());
        lqw.like(StringUtils.isNotBlank(bo.getContractName()), PuContract::getContractName, bo.getContractName());
        lqw.eq(bo.getVendorId() != null, PuContract::getVendorId, bo.getVendorId());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PuContract::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PuContract::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getContractType()), PuContract::getContractType, bo.getContractType());
        lqw.orderByDesc(PuContract::getCreateTime);
        return lqw;
    }
}
