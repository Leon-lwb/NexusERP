package org.dromara.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.flowable.core.domain.ActStatus;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.BdClientCredit;
import org.dromara.erp.bd.domain.BdMaterial;
import org.dromara.erp.bd.domain.BdBom;
import org.dromara.erp.bd.mapper.BdClientCreditMapper;
import org.dromara.erp.bd.mapper.BdMaterialMapper;
import org.dromara.erp.bd.mapper.BdBomMapper;
import org.dromara.erp.prd.domain.PrdProductionPlan;
import org.dromara.erp.prd.mapper.PrdProductionPlanMapper;
import org.dromara.erp.so.domain.SoSalesOrderHeader;
import org.dromara.erp.so.domain.SoSalesOrderLine;
import org.dromara.erp.so.domain.bo.SoSalesOrderHeaderBo;
import org.dromara.erp.so.domain.bo.SoSalesOrderLineBo;
import org.dromara.erp.so.domain.vo.SoSalesOrderHeaderVo;
import org.dromara.erp.so.domain.vo.SoSalesOrderLineVo;
import org.dromara.erp.so.mapper.SoSalesOrderHeaderMapper;
import org.dromara.erp.so.mapper.SoSalesOrderLineMapper;
import org.dromara.erp.so.service.ISoSalesOrderHeaderService;
import org.dromara.system.strategy.AutoCodeUtil;
import org.dromara.workflow.domain.WfMyBusiness;
import org.dromara.workflow.service.IWfProcessService;
import org.dromara.workflow.service.impl.WfMyBusinessServiceImpl;
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
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("soSalesOrderHeaderService")
public class SoSalesOrderHeaderServiceImpl implements ISoSalesOrderHeaderService {

    private final SoSalesOrderHeaderMapper baseMapper;
    private final SoSalesOrderLineMapper soSalesOrderLineMapper;
    private final BdClientCreditMapper bdClientCreditMapper;
    private final BdMaterialMapper bdMaterialMapper;
    private final BdBomMapper bdBomMapper;
    private final PrdProductionPlanMapper prdProductionPlanMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    @Override
    public TableDataInfo<SoSalesOrderHeaderVo> selectPageList(SoSalesOrderHeaderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = buildQueryWrapper(bo);
        Page<SoSalesOrderHeaderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SoSalesOrderHeaderVo> selectList(SoSalesOrderHeaderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        if (StringUtils.isBlank(entity.getOrderStatus())) {
            entity.setOrderStatus(STATUS_DRAFT);
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .eq(SoSalesOrderLine::getSoId, entity.getSoId()));
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .in(SoSalesOrderLine::getSoId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

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

        // 信用检查
        String creditCheckResult = checkCredit(header);
        if (!"OK".equals(creditCheckResult)) {
            header.setCreditStatus("BLOCKED");
            header.setCreditCheckTime(new Date());
            header.setCreditBlockedBy(-1L);
            baseMapper.updateById(header);
            throw new RuntimeException("信用检查未通过：" + creditCheckResult);
        }
        header.setCreditStatus("PASSED");
        header.setCreditCheckTime(new Date());

        header.setOrderStatus(STATUS_CONFIRMED);
        header.setConfirmedTime(new Date());
        baseMapper.updateById(header);
        log.info("销售订单确认成功, soId={}, soNo={}, creditStatus=PASSED", soId, header.getSoNo());

        // 自动创建生产计划（针对自制物料）
        createProductionPlansFromSalesOrder(soId, header.getSoNo());
    }

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
    }

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
    }

    // ==================== Flowable 工作流集成 ====================

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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object getBusinessDataById(String dataId) {
        return selectById(Long.parseLong(dataId));
    }

    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 信用检查
     * 查询客户信用额度，计算已用额度（已确认未关闭订单合计），超额则阻止
     */
    private String checkCredit(SoSalesOrderHeader header) {
        if (header.getClientId() == null) {
            return "订单未关联客户";
        }
        try {
            LambdaQueryWrapper<BdClientCredit> creditLqw = Wrappers.lambdaQuery();
            creditLqw.eq(BdClientCredit::getClientId, header.getClientId());
            BdClientCredit credit = bdClientCreditMapper.selectOne(creditLqw);
            if (credit == null || credit.getClientCredit() == null) {
                log.info("客户未配置信用额度，跳过信用检查, clientId={}", header.getClientId());
                return "OK";
            }

            // 计算已用额度：已确认/已履行/部分发货的订单合计（排除已关闭/已取消）
            LambdaQueryWrapper<SoSalesOrderHeader> usedLqw = Wrappers.lambdaQuery();
            usedLqw.eq(SoSalesOrderHeader::getClientId, header.getClientId());
            usedLqw.ne(SoSalesOrderHeader::getOrderStatus, STATUS_CLOSED);
            usedLqw.ne(SoSalesOrderHeader::getOrderStatus, STATUS_CANCELLED);
            usedLqw.ne(SoSalesOrderHeader::getOrderStatus, STATUS_DRAFT);
            // 排除本订单（如果是修改后重新确认）
            if (header.getSoId() != null) {
                usedLqw.ne(SoSalesOrderHeader::getSoId, header.getSoId());
            }
            List<SoSalesOrderHeader> activeOrders = baseMapper.selectList(usedLqw);

            BigDecimal usedAmount = BigDecimal.ZERO;
            for (SoSalesOrderHeader order : activeOrders) {
                if (order.getTotalAmount() != null) {
                    usedAmount = usedAmount.add(order.getTotalAmount());
                }
            }

            BigDecimal orderAmount = header.getTotalAmount() != null ? header.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal totalAmount = usedAmount.add(orderAmount);
            BigDecimal creditLimit = BigDecimal.valueOf(credit.getClientCredit());

            if (totalAmount.compareTo(creditLimit) > 0) {
                log.warn("信用检查失败, clientId={}, 信用额度={}, 已用={}, 本次={}, 合计={}",
                    header.getClientId(), creditLimit, usedAmount, orderAmount, totalAmount);
                return "客户【" + credit.getClientName() + "】信用额度不足"
                    + "（额度:" + creditLimit + ", 已用:" + usedAmount + ", 本次:" + orderAmount + "）";
            }

            log.info("信用检查通过, clientId={}, 额度={}, 已用={}, 本次={}",
                header.getClientId(), creditLimit, usedAmount, orderAmount);
            return "OK";
        } catch (Exception e) {
            log.error("信用检查异常, clientId={}", header.getClientId(), e);
            return "信用检查系统异常：" + e.getMessage();
        }
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
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), SoSalesOrderHeader::getSoNo, bo.getSoNo());
        lqw.eq(bo.getCoId() != null, SoSalesOrderHeader::getCoId, bo.getCoId());
        lqw.eq(bo.getClientId() != null, SoSalesOrderHeader::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoSalesOrderHeader::getClientName, bo.getClientName());
        lqw.eq(bo.getSalesRepId() != null, SoSalesOrderHeader::getSalesRepId, bo.getSalesRepId());
        lqw.like(StringUtils.isNotBlank(bo.getSalesRepName()), SoSalesOrderHeader::getSalesRepName, bo.getSalesRepName());
        lqw.eq(bo.getOrderDate() != null, SoSalesOrderHeader::getOrderDate, bo.getOrderDate());
        lqw.eq(bo.getPromiseDate() != null, SoSalesOrderHeader::getPromiseDate, bo.getPromiseDate());
        lqw.eq(bo.getRequestedDate() != null, SoSalesOrderHeader::getRequestedDate, bo.getRequestedDate());
        lqw.eq(StringUtils.isNotBlank(bo.getCreditStatus()), SoSalesOrderHeader::getCreditStatus, bo.getCreditStatus());
        lqw.eq(bo.getCreditBlockedBy() != null, SoSalesOrderHeader::getCreditBlockedBy, bo.getCreditBlockedBy());
        lqw.eq(StringUtils.isNotBlank(bo.getOrderStatus()), SoSalesOrderHeader::getOrderStatus, bo.getOrderStatus());
        lqw.eq(bo.getProjectId() != null, SoSalesOrderHeader::getProjectId, bo.getProjectId());
        return lqw;
    }
}
