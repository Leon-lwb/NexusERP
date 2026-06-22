package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdBom;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdBomMapper;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoSalesOrderHeaderBo;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ICreditCheckService;
import com.nexus.erp.so.service.ISoSalesOrderHeaderService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单Service业务层处理
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soSalesOrderHeaderService")
public class SoSalesOrderHeaderServiceImpl implements ISoSalesOrderHeaderService {

    private final SoSalesOrderHeaderMapper baseMapper;
    private final SoSalesOrderLineMapper soSalesOrderLineMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final BdBomMapper bdBomMapper;
    private final PrdProductionPlanMapper prdProductionPlanMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final ICreditCheckService creditCheckService;

    /**
     * 分页查询销售订单列表
     */
    @Override
    public TableDataInfo<SoSalesOrderHeaderVo> selectPageList(SoSalesOrderHeaderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = buildQueryWrapper(bo);
        Page<SoSalesOrderHeaderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售订单列表
     */
    @Override
    public List<SoSalesOrderHeaderVo> selectList(SoSalesOrderHeaderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售订单详情（包含行明细及关联生产计划）
     */
    @Override
    public SoSalesOrderHeaderVo selectById(Long id) {
        SoSalesOrderHeaderVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<SoSalesOrderLine> lineList = soSalesOrderLineMapper.selectList(
                new LambdaQueryWrapper<SoSalesOrderLine>()
                    .eq(SoSalesOrderLine::getSoId, id));
            List<SoSalesOrderLineVo> lineVoList = MapstructUtils.convert(lineList, SoSalesOrderLineVo.class);
            // Populate linked production plan number for each line
            for (SoSalesOrderLineVo lineVo : lineVoList) {
                if (lineVo.getLineId() != null) {
                    LambdaQueryWrapper<PrdProductionPlan> planQuery = Wrappers.lambdaQuery();
                    planQuery.eq(PrdProductionPlan::getOrderLineId, lineVo.getLineId());
                    planQuery.last("LIMIT 1");
                    PrdProductionPlan plan = prdProductionPlanMapper.selectOne(planQuery);
                    if (plan != null) {
                        lineVo.setLinkedPlanNo(plan.getPlanNo());
                        lineVo.setLinkedPlanId(plan.getPlanId());
                    }
                }
            }
            vo.setSoSalesOrderLineList(lineVoList);
        }
        return vo;
    }

    /**
     * 新增销售订单（默认草稿状态并保存行明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        if (StringUtils.isBlank(entity.getOrderStatus())) {
            entity.setOrderStatus(STATUS_DRAFT);
        }
        if (StringUtils.isBlank(entity.getSoNo())) {
            entity.setSoNo(autoCodeUtil.genSerialCode("SO_ORDER_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return rows;
    }

    /**
     * 修改销售订单（先删除原行明细再重新插入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .eq(SoSalesOrderLine::getSoId, entity.getSoId()));
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售订单（同时删除行明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .in(SoSalesOrderLine::getSoId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据销售订单编号查询行明细
     */
    @Override
    public List<SoSalesOrderLineVo> selectLineListBySoNo(String soNo) {
        LambdaQueryWrapper<SoSalesOrderHeader> headerWrapper = Wrappers.lambdaQuery();
        headerWrapper.eq(SoSalesOrderHeader::getSoNo, soNo);
        SoSalesOrderHeader header = baseMapper.selectOne(headerWrapper);
        if (header == null) {
            return List.of();
        }
        LambdaQueryWrapper<SoSalesOrderLine> lineWrapper = Wrappers.lambdaQuery();
        lineWrapper.eq(SoSalesOrderLine::getSoId, header.getSoId());
        lineWrapper.orderByDesc(SoSalesOrderLine::getSortOrder);
        List<SoSalesOrderLine> lineList = soSalesOrderLineMapper.selectList(lineWrapper);
        return MapstructUtils.convert(lineList, SoSalesOrderLineVo.class);
    }

    /**
     * 确认销售订单（包含信用检查，通过后自动创建生产计划）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long soId) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_DRAFT.equals(header.getOrderStatus()) && !STATUS_APPROVED.equals(header.getOrderStatus())) {
            throw new RuntimeException("当前状态不允许确认");
        }

        // 信用检查与冻结
        creditCheckService.checkAndFreeze(header.getClientId(), soId,
            header.getTotalAmount() != null ? header.getTotalAmount() : BigDecimal.ZERO);
        header.setCreditStatus("PASSED");
        header.setCreditCheckTime(new Date());

        header.setOrderStatus(STATUS_CONFIRMED);
        header.setConfirmedTime(new Date());
        baseMapper.updateById(header);
        log.info("销售订单确认成功, soId={}, soNo={}, creditStatus=PASSED", soId, header.getSoNo());

        // 自动创建生产计划（针对自制物料）
        createProductionPlansFromSalesOrder(soId, header.getSoNo());
    }

    /**
     * 履行销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fulfill(Long soId) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_CONFIRMED.equals(header.getOrderStatus())) {
            throw new RuntimeException("只有已确认状态的订单才能执行");
        }
        header.setOrderStatus(STATUS_FULFILLED);
        header.setFulfilledTime(new Date());
        baseMapper.updateById(header);
    }

    /**
     * 关闭销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long soId) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (STATUS_CANCELLED.equals(header.getOrderStatus())) {
            throw new RuntimeException("已取消的订单不能关闭");
        }
        header.setOrderStatus(STATUS_CLOSED);
        header.setClosedTime(new Date());
        baseMapper.updateById(header);
        creditCheckService.release(soId);
    }

    /**
     * 取消销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long soId, String reason) {
        SoSalesOrderHeader header = baseMapper.selectById(soId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (STATUS_CLOSED.equals(header.getOrderStatus())) {
            throw new RuntimeException("已关闭的订单不能取消");
        }
        header.setOrderStatus(STATUS_CANCELLED);
        header.setCancelledTime(new Date());
        header.setCancelledReason(reason);
        baseMapper.updateById(header);
        creditCheckService.release(soId);
    }

    // ==================== Flowable 工作流集成 ====================

    /**
     * 提交审批流程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String soIdStr, String selectFlowId) {
        Long soId = Long.parseLong(soIdStr);
        SoSalesOrderHeader order = baseMapper.selectById(soId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (!STATUS_DRAFT.equals(order.getOrderStatus())) {
            throw new RuntimeException("只有草稿状态的销售订单才能提交审批");
        }
        if (StringUtils.isBlank(selectFlowId)) {
            if (order.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(order.getSelectFlowId());
            } else {
                throw new RuntimeException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", soIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("soId", order.getSoId());
        variables.put("soNo", order.getSoNo());
        variables.put("clientName", order.getClientName());
        variables.put("orderDate", order.getOrderDate());
        R<Void> result = processService.startProcessByDataId(soIdStr, selectFlowId, "soSalesOrderHeaderService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new RuntimeException(result.getMsg());
    }

    /**
     * 获取流程实例ID
     */
    @Override
    public String getProcInsId(String soIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(soIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(soIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(soIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    /**
     * 流程审批后处理（更新销售订单状态）
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long soId = Long.parseLong(business.getDataId());
        SoSalesOrderHeader order = baseMapper.selectById(soId);
        if (order == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            order.setOrderStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            order.setOrderStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            order.setOrderStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            order.setOrderStatus(STATUS_DRAFT);
        }
        baseMapper.updateById(order);
    }

    /**
     * 根据数据ID获取业务数据
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    /**
     * 获取流程任务变量
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 获取流程任务候选用户
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * Batch insert or update sales order lines
     */
    private void insertOrUpdateLines(Long soId, List<SoSalesOrderLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<SoSalesOrderLine> lineList = MapstructUtils.convert(lineBoList, SoSalesOrderLine.class);
            for (SoSalesOrderLine line : lineList) {
                line.setLineId(null);
                line.setSoId(soId);
            }
            soSalesOrderLineMapper.insertBatch(lineList);
        }
    }

    /**
     * 销售订单确认后，对自制物料自动创建生产计划
     */
    private void createProductionPlansFromSalesOrder(Long soId, String soNo) {
        List<SoSalesOrderLine> lines = soSalesOrderLineMapper.selectList(
            new LambdaQueryWrapper<SoSalesOrderLine>()
                .eq(SoSalesOrderLine::getSoId, soId));
        for (SoSalesOrderLine line : lines) {
            if (line.getMaterialId() == null) {
                continue;
            }
            BdMaterial material = bdMaterialMapper.selectById(line.getMaterialId());
            if (material != null && Long.valueOf(1L).equals(material.getSourceType())) {
                createProductionPlan(line, soNo, material);
            }
        }
    }

    private void createProductionPlan(SoSalesOrderLine line, String soNo, BdMaterial material) {
        PrdProductionPlan plan = new PrdProductionPlan();
        plan.setPlanNo(autoCodeUtil.genSerialCode("PRD_PLAN_CODE", null));
        plan.setPlanName("销售订单-" + soNo + "-" + material.getMaterialName());
        plan.setPlanType("MPS");
        plan.setPlanDate(new Date());
        plan.setStatus(STATUS_DRAFT);
        plan.setReferenceNo(soNo);
        plan.setOrderLineId(line.getLineId());
        plan.setOrderLineNo(line.getLineNo());
        plan.setTotalQuantity(line.getOrderedQty());

        // 查找物料当前有效BOM
        LambdaQueryWrapper<BdBom> bomQuery = Wrappers.lambdaQuery();
        bomQuery.eq(BdBom::getParentMaterialId, material.getMaterialId());
        bomQuery.eq(BdBom::getIsCurrent, 1L);
        bomQuery.last("LIMIT 1");
        BdBom bom = bdBomMapper.selectOne(bomQuery);
        if (bom != null) {
            plan.setBomId(bom.getBomId());
            plan.setBomName(bom.getBomName());
            plan.setBomVersion(bom.getBomVersion());
        } else {
            log.warn("物料未配置BOM，生产计划将不关联BOM, materialId={}, materialCode={}",
                material.getMaterialId(), material.getMaterialCode());
        }

        prdProductionPlanMapper.insert(plan);
        log.info("销售订单确认后自动创建生产计划, soNo={}, materialCode={}, planNo={}",
            soNo, material.getMaterialCode(), plan.getPlanNo());
    }

    private LambdaQueryWrapper<SoSalesOrderHeader> buildQueryWrapper(SoSalesOrderHeaderBo bo) {
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = Wrappers.<SoSalesOrderHeader>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getSoNo()), SoSalesOrderHeader::getSoNo, bo.getSoNo())
            .eq(bo.getCoId() != null, SoSalesOrderHeader::getCoId, bo.getCoId())
            .eq(bo.getClientId() != null, SoSalesOrderHeader::getClientId, bo.getClientId())
            .like(StringUtils.isNotBlank(bo.getClientName()), SoSalesOrderHeader::getClientName, bo.getClientName())
            .eq(bo.getSalesRepId() != null, SoSalesOrderHeader::getSalesRepId, bo.getSalesRepId())
            .like(StringUtils.isNotBlank(bo.getSalesRepName()), SoSalesOrderHeader::getSalesRepName, bo.getSalesRepName())
            .eq(bo.getOrderDate() != null, SoSalesOrderHeader::getOrderDate, bo.getOrderDate())
            .eq(bo.getPromiseDate() != null, SoSalesOrderHeader::getPromiseDate, bo.getPromiseDate())
            .eq(bo.getRequestedDate() != null, SoSalesOrderHeader::getRequestedDate, bo.getRequestedDate())
            .eq(StringUtils.isNotBlank(bo.getCreditStatus()), SoSalesOrderHeader::getCreditStatus, bo.getCreditStatus())
            .eq(bo.getCreditBlockedBy() != null, SoSalesOrderHeader::getCreditBlockedBy, bo.getCreditBlockedBy())
            .eq(StringUtils.isNotBlank(bo.getOrderStatus()), SoSalesOrderHeader::getOrderStatus, bo.getOrderStatus())
            .eq(bo.getProjectId() != null, SoSalesOrderHeader::getProjectId, bo.getProjectId());
        return lqw;
    }
}
