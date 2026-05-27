package org.dromara.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.flowable.core.domain.ActStatus;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.pu.domain.PuPurchaseOrder;
import org.dromara.erp.pu.domain.PuPurchaseOrderLine;
import org.dromara.erp.pu.domain.PuPurchaseRequisition;
import org.dromara.erp.pu.domain.PuPurchaseRequisitionLine;
import org.dromara.erp.bd.domain.BdVendor;
import org.dromara.erp.bd.mapper.BdVendorMapper;
import org.dromara.erp.pu.domain.bo.PuPurchaseOrderBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseOrderVo;
import org.dromara.erp.pu.mapper.PuPurchaseOrderLineMapper;
import org.dromara.erp.pu.mapper.PuPurchaseOrderMapper;
import org.dromara.erp.pu.mapper.PuPurchaseRequisitionLineMapper;
import org.dromara.erp.pu.mapper.PuPurchaseRequisitionMapper;
import org.dromara.erp.pu.service.IPuPurchaseOrderService;
import org.dromara.erp.pu.service.PuPurchaseReceivingService;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 采购订单服务实现类
 * <p>
 * 实现采购订单的核心业务逻辑，包括：
 * <ul>
 *   <li>CRUD 操作：分页查询、新增（自动编号/默认状态）、修改（状态校验）、删除</li>
 *   <li>状态流转：提交（草稿→已提交）、审批（已提交→已批准）、关闭、作废</li>
 *   <li>联动操作：生成收货单据（到货通知+物料收货）、从采购申请转单生成</li>
 * </ul>
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service("puPurchaseOrderService")
public class PuPurchaseOrderServiceImpl implements IPuPurchaseOrderService {

    private final PuPurchaseOrderMapper baseMapper;
    private final PuPurchaseOrderLineMapper lineMapper;
    private final PuPurchaseRequisitionMapper requisitionMapper;
    private final PuPurchaseRequisitionLineMapper requisitionLineMapper;
    private final PuPurchaseReceivingService purchaseReceivingService;
    private final BdVendorMapper vendorMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 分页查询采购订单列表
     */
    @Override
    public TableDataInfo<PuPurchaseOrderVo> selectPageList(PuPurchaseOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PuPurchaseOrder> lqw = buildQueryWrapper(bo);
        Page<PuPurchaseOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询采购订单列表（不分页）
     */
    @Override
    public List<PuPurchaseOrderVo> selectList(PuPurchaseOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询采购订单，附带明细行列表
     * <p>
     * 先查询主表信息，再根据 orderId 查询所有明细行并按排序号升序排列。
     */
    @Override
    public PuPurchaseOrderVo selectById(Long id) {
        PuPurchaseOrderVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getOrderId() != null) {
            LambdaQueryWrapper<PuPurchaseOrderLine> lqw = new LambdaQueryWrapper<>();
            lqw.eq(PuPurchaseOrderLine::getOrderId, vo.getOrderId());
            lqw.orderByAsc(PuPurchaseOrderLine::getSortOrder);
            vo.setLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增采购订单
     * <p>
     * 自动生成订单号（如为空）、默认设为草稿状态、默认订单日期为当天，
     * 同时保存明细行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PuPurchaseOrderBo bo) {
        PuPurchaseOrder entity = MapstructUtils.convert(bo, PuPurchaseOrder.class);
        validateVendor(entity.getVendorId(), entity.getExpectedArrivalDate());
        if (StringUtils.isBlank(entity.getOrderNo())) {
            entity.setOrderNo(autoCodeUtil.genSerialCode("PU_ORDER_CODE", null));
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getOrderDate() == null) {
            entity.setOrderDate(new Date());
        }
        int rows = baseMapper.insert(entity);
        saveLines(entity.getOrderId(), bo.getLineList());
        return rows;
    }

    /**
     * 修改采购订单
     * <p>
     * 仅草稿或已提交状态的订单允许编辑。修改时先删除全部旧明细行，再插入新明细行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PuPurchaseOrderBo bo) {
        PuPurchaseOrder entity = MapstructUtils.convert(bo, PuPurchaseOrder.class);
        validateVendor(entity.getVendorId(), entity.getExpectedArrivalDate());
        PuPurchaseOrder current = baseMapper.selectById(entity.getOrderId());
        if (current == null) {
            throw new ServiceException("采购订单不存在");
        }
        if (!STATUS_DRAFT.equals(current.getStatus()) && !STATUS_SUBMITTED.equals(current.getStatus())) {
            throw new ServiceException("当前状态不允许编辑采购订单");
        }
        deleteLines(entity.getOrderId());
        saveLines(entity.getOrderId(), bo.getLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除采购订单
     * <p>
     * 先删除各订单的明细行，再删除主表记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLines(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 提交采购订单：仅草稿状态允许提交，将状态改为已提交
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long orderId) {
        PuPurchaseOrder order = ensureOrder(orderId);
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new ServiceException("只有草稿状态的采购订单才能提交");
        }
        order.setStatus(STATUS_SUBMITTED);
        baseMapper.updateById(order);
    }

    // ==================== Flowable 工作流集成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String orderIdStr, String selectFlowId) {
        Long orderId = Long.parseLong(orderIdStr);
        PuPurchaseOrder order = baseMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException("采购订单不存在");
        }

        if (StringUtils.isBlank(selectFlowId)) {
            if (order.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(order.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", orderIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("orderId", order.getOrderId());
        variables.put("orderNo", order.getOrderNo());
        variables.put("vendorName", order.getVendorName());
        variables.put("orderDate", order.getOrderDate());
        R<Void> result = processService.startProcessByDataId(orderIdStr, selectFlowId, "puPurchaseOrderService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    @Override
    public String getProcInsId(String orderIdStr) {
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(orderIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(orderIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(orderIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long orderId = Long.parseLong(business.getDataId());
        PuPurchaseOrder order = baseMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            order.setStatus(STATUS_APPROVING);
        } else if (ActStatus.pass.equals(actStatus)) {
            order.setStatus(STATUS_APPROVED);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            order.setStatus(STATUS_REJECTED);
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            order.setStatus(STATUS_DRAFT);
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

    // ==================== 原有审批流（保留兼容） ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long orderId) {
        PuPurchaseOrder order = ensureOrder(orderId);
        if (!STATUS_SUBMITTED.equals(order.getStatus()) && !STATUS_APPROVING.equals(order.getStatus())) {
            throw new ServiceException("当前状态不允许审批采购订单");
        }
        order.setStatus(STATUS_APPROVED);
        baseMapper.updateById(order);
    }

    /**
     * 关闭采购订单：已关闭或已作废状态不允许重复关闭
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long orderId) {
        PuPurchaseOrder order = ensureOrder(orderId);
        if (STATUS_CLOSED.equals(order.getStatus()) || STATUS_CANCELLED.equals(order.getStatus())) {
            return;
        }
        order.setStatus(STATUS_CLOSED);
        baseMapper.updateById(order);
    }

    /**
     * 作废采购订单：已全部到货的订单不允许作废
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        PuPurchaseOrder order = ensureOrder(orderId);
        if (STATUS_RECEIVED.equals(order.getStatus())) {
            throw new ServiceException("已全部到货的采购订单不能作废");
        }
        order.setStatus(STATUS_CANCELLED);
        baseMapper.updateById(order);
    }

    /**
     * 生成收货联动单据
     * <p>
     * 仅已批准或部分到货状态的订单允许生成。调用 {@link PuPurchaseReceivingService}
     * 创建到货通知单和物料收货单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateReceivingLink(Long orderId) {
        PuPurchaseOrder order = ensureOrder(orderId);
        if (!STATUS_APPROVED.equals(order.getStatus()) && !STATUS_PARTIALLY_RECEIVED.equals(order.getStatus())) {
            throw new ServiceException("只有已批准或部分到货的采购订单才能生成收货联动单据");
        }
        return purchaseReceivingService.createArrivalNoticeAndReceive(orderId);
    }

    /**
     * 从采购申请生成采购订单草稿
     * <p>
     * 根据采购申请ID查询申请主表和明细行，将申请物料信息转换为采购订单明细行，
     * 并设置默认币种（CNY）、汇率（1）、备注等，返回未持久化的订单业务对象供前端确认后保存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PuPurchaseOrderBo generateFromRequisition(Long requisitionId) {
        PuPurchaseRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null) {
            throw new ServiceException("采购申请不存在");
        }
        List<PuPurchaseRequisitionLine> reqLines = requisitionLineMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseRequisitionLine>().eq(PuPurchaseRequisitionLine::getRequisitionId, requisitionId)
        );
        if (reqLines == null || reqLines.isEmpty()) {
            throw new ServiceException("采购申请没有明细，无法生成采购订单");
        }

        PuPurchaseOrderBo bo = new PuPurchaseOrderBo();
        bo.setRequisitionId(requisition.getRequisitionId());
        bo.setRequisitionNo(requisition.getRequisitionNo());
        bo.setOrderDate(new Date());
        bo.setStatus(STATUS_DRAFT);
        bo.setCurrency("CNY");
        bo.setExchangeRate(BigDecimal.ONE);
        bo.setRemark("由采购申请【" + requisition.getRequisitionNo() + "】生成");
        bo.setLineList(buildLinesFromRequisition(reqLines));
        return bo;
    }

    /**
     * 将采购申请明细行转换为采购订单明细行
     * <p>
     * 遍历申请明细，拷贝物料信息，设置订购数量为申请数量、已收数量为零，
     * 并按照顺序填充排序号。
     *
     * @param reqLines 采购申请明细行列表
     * @return 采购订单明细行列表
     */
    private List<PuPurchaseOrderLine> buildLinesFromRequisition(List<PuPurchaseRequisitionLine> reqLines) {
        List<PuPurchaseOrderLine> list = new ArrayList<>();
        int sort = 1;
        for (PuPurchaseRequisitionLine reqLine : reqLines) {
            PuPurchaseOrderLine orderLine = new PuPurchaseOrderLine();
            orderLine.setRequisitionLineId(reqLine.getLineId());
            orderLine.setMaterialId(reqLine.getMaterialId());
            orderLine.setMaterialCode(reqLine.getMaterialCode());
            orderLine.setMaterialName(reqLine.getMaterialName());
            orderLine.setMaterialSpec(reqLine.getMaterialSpec());
            orderLine.setUnit(reqLine.getUnit());
            orderLine.setOrderQuantity(reqLine.getApplyQuantity());
            orderLine.setReceivedQuantity(BigDecimal.ZERO);
            orderLine.setUnitPrice(BigDecimal.ZERO);
            orderLine.setTaxRate(BigDecimal.ZERO);
            orderLine.setLineAmount(BigDecimal.ZERO);
            orderLine.setSortOrder(sort++);
            orderLine.setRemark(reqLine.getRemark());
            list.add(orderLine);
        }
        return list;
    }

    /**
     * 批量保存采购订单明细行
     *
     * @param orderId 采购订单ID
     * @param lines   明细行列表
     */
    private void saveLines(Long orderId, List<PuPurchaseOrderLine> lines) {
        if (orderId == null || lines == null || lines.isEmpty()) {
            return;
        }
        for (PuPurchaseOrderLine line : lines) {
            line.setLineId(null);
            line.setOrderId(orderId);
            lineMapper.insert(line);
        }
    }

    /**
     * 删除指定采购订单的所有明细行
     *
     * @param orderId 采购订单ID
     */
    private void deleteLines(Long orderId) {
        if (orderId == null) {
            return;
        }
        LambdaQueryWrapper<PuPurchaseOrderLine> lqw = new LambdaQueryWrapper<>();
        lqw.eq(PuPurchaseOrderLine::getOrderId, orderId);
        lineMapper.delete(lqw);
    }

    /**
     * 确保采购订单存在，否则抛出异常
     *
     * @param orderId 采购订单ID
     * @return 采购订单实体
     */
    private PuPurchaseOrder ensureOrder(Long orderId) {
        PuPurchaseOrder order = baseMapper.selectById(orderId);
        if (order == null) {
            throw new ServiceException("采购订单不存在");
        }
        return order;
    }

    /**
     * 校验供应商合法性
     * <p>
     * 检查项：
     * <ol>
     *   <li>供应商存在性 — 根据 vendorId 查询 bd_vendor 表</li>
     *   <li>供应商状态 — 必须为 ACTIVE，若为 FROZEN 视为黑名单拒绝</li>
     *   <li>交期合理性 — 预计到货日期不能早于当前日期</li>
     * </ol>
     *
     * @param vendorId           供应商ID
     * @param expectedArrivalDate 预计到货日期（可空）
     */
    @Override
    public void validateVendor(Long vendorId, Date expectedArrivalDate) {
        if (vendorId == null) {
            throw new ServiceException("供应商ID不能为空");
        }
        BdVendor vendor = vendorMapper.selectById(vendorId);
        if (vendor == null) {
            throw new ServiceException("供应商不存在(ID={})", vendorId);
        }
        if ("FROZEN".equals(vendor.getStatus())) {
            throw new ServiceException("供应商【{}】已被冻结（黑名单），不可下单", vendor.getVendorName());
        }
        if (!"ACTIVE".equals(vendor.getStatus())) {
            throw new ServiceException("供应商【{}】状态异常（{}），不可下单", vendor.getVendorName(), vendor.getStatus());
        }
        if (expectedArrivalDate != null && expectedArrivalDate.before(new Date())) {
            throw new ServiceException("预计到货日期不能早于当前日期");
        }
    }

    /**
     * 构建查询条件 Wrapper
     * <p>
     * 支持按采购订单号精确匹配、按状态精确匹配、按供应商名称模糊查询，默认按创建时间降序排列。
     *
     * @param bo 查询条件
     * @return 查询条件 Wrapper
     */
    private LambdaQueryWrapper<PuPurchaseOrder> buildQueryWrapper(PuPurchaseOrderBo bo) {
        LambdaQueryWrapper<PuPurchaseOrder> lqw = new LambdaQueryWrapper<>();
        lqw.eq(StringUtils.isNotBlank(bo.getOrderNo()), PuPurchaseOrder::getOrderNo, bo.getOrderNo());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), PuPurchaseOrder::getStatus, bo.getStatus());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), PuPurchaseOrder::getVendorName, bo.getVendorName());
        lqw.orderByDesc(PuPurchaseOrder::getCreateTime);
        return lqw;
    }
}
