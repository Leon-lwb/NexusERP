package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.service.IBdMaterialService;
import com.nexus.erp.bd.service.IBdMaterialService;
import com.nexus.erp.mq.message.StockInMessage;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.wm.domain.WmArrivalNotice;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IWmBarcodeService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
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

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料入库单 Service 实现
 * <p>
 * 核心业务流程：
 * 1. 创建/编辑入库单 → 2. 保存时选审批流程 → 3. 提交审批(submit) → 4. 审批回调(afterFlowHandle)
 *    → 5. 状态变为 APPROVED → 6. 执行入库(executeReceive) → 7. 状态变为 FINISHED
 * <p>
 * 库存转移模型：入库 = 从虚拟库位(areaId=0,locationId=0)转移到目标库位
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service("wmMaterialReceiveService")
public class WmMaterialReceiveServiceImpl implements IWmMaterialReceiveService {

    private static final String ORDER_STATUS_APPROVED = "APPROVED";
    private static final String ORDER_STATUS_FINISHED = "FINISHED";
    private static final Long VIRTUAL_AREA_ID = 0L;
    private static final Long VIRTUAL_LOCATION_ID = 0L;

    private final WmMaterialReceiveMapper baseMapper;
    private final WmMaterialReceiveLineMapper receiveLineMapper;
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    private final WmMaterialStockMapper materialStockMapper;
    private final WmMaterialStockTxMapper materialStockTxMapper;
    private final WmArrivalNoticeMapper arrivalNoticeMapper;
    private final IBdMaterialService bdMaterialService;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final IWmBarcodeService wmBarcodeService;
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final ReliableMessageProducer messageProducer;

    // ==================== 1. 基础 CRUD ====================

    @Override
    public TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMaterialReceive> lqw = buildQueryWrapper(bo);
        Page<WmMaterialReceiveVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMaterialReceiveVo selectById(Long id) {
        WmMaterialReceiveVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            // 加载入库单行信息
            List<WmMaterialReceiveLine> lineList = receiveLineMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveLine>()
                    .eq(WmMaterialReceiveLine::getReceiveId, id));
            List<WmMaterialReceiveLineVo> lineVoList = MapstructUtils.convert(lineList, WmMaterialReceiveLineVo.class);

            // 加载每行的明细信息
            for (WmMaterialReceiveLineVo lineVo : lineVoList) {
                List<WmMaterialReceiveDetail> detailList = receiveDetailMapper.selectList(
                    new LambdaQueryWrapper<WmMaterialReceiveDetail>()
                        .eq(WmMaterialReceiveDetail::getLineId, lineVo.getLineId()));
                lineVo.setWmMaterialReceiveLineDetailList(
                    MapstructUtils.convert(detailList, WmMaterialReceiveDetailVo.class));
            }

            vo.setWmMaterialReceiveLineList(lineVoList);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMaterialReceiveBo bo) {
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        int rows = baseMapper.insert(entity);
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMaterialReceiveBo bo) {
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        // 删除现有的行和明细
        deleteLinesAndDetailsByReceiveId(entity.getReceiveId());
        // 重新插入行和明细数据
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long receiveId : ids) {
            deleteLinesAndDetailsByReceiveId(receiveId);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    // ==================== 2. 带校验的编辑保存（前端编辑页保存时调用） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWithValidation(WmMaterialReceiveBo bo) {
        // 校验入库单编号不能为空
        if (StringUtils.isBlank(bo.getReceiveCode())) {
            throw new ServiceException("编号不能为空！");
        }

        // 校验入库单编号唯一性
        LambdaQueryWrapper<WmMaterialReceive> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WmMaterialReceive::getReceiveCode, bo.getReceiveCode());
        List<WmMaterialReceive> existingReceives = baseMapper.selectList(queryWrapper);

        if (!existingReceives.isEmpty()) {
            WmMaterialReceive existingReceive = existingReceives.get(0);
            Long receiveId = bo.getReceiveId() == null ? -1L : bo.getReceiveId();
            if (!existingReceive.getReceiveId().equals(receiveId)) {
                throw new ServiceException("编号已存在！");
            }
        }

        // 如果状态为审批中，校验入库单行不能为空
        if ("APPROVING".equals(bo.getStatus())) {
            if (bo.getWmMaterialReceiveLineList() == null || bo.getWmMaterialReceiveLineList().isEmpty()) {
                throw new ServiceException("入库单行不能为空！");
            }
        }

        // 如果状态为已审核，校验每行的明细数据
        if ("APPROVED".equals(bo.getStatus())) {
            validateDetailsForApproval(bo);
        }

        // 更新实体
        WmMaterialReceive entity = MapstructUtils.convert(bo, WmMaterialReceive.class);
        deleteLinesAndDetailsByReceiveId(entity.getReceiveId());
        insertLinesAndDetails(entity.getReceiveId(), bo.getWmMaterialReceiveLineList());
        return baseMapper.updateById(entity) > 0;
    }

    // ==================== 3. 核心业务：执行入库（审批通过后，将物料从虚拟库位转移到目标库位） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean executeReceive(Long receiveId) {
        WmMaterialReceiveVo receiveVo = selectById(receiveId);
        if (receiveVo == null) {
            throw new ServiceException("入库单不存在");
        }
        if (!ORDER_STATUS_APPROVED.equals(receiveVo.getStatus())) {
            throw new ServiceException("只有已审核的入库单才能执行入库操作");
        }

        List<WmMaterialReceiveLineVo> lines = receiveVo.getWmMaterialReceiveLineList();
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("入库单行不能为空");
        }

        Map<String, WmMaterialStock> stockMap = new LinkedHashMap<>();
        for (WmMaterialReceiveLineVo line : lines) {
            List<WmMaterialReceiveDetailVo> details = line.getWmMaterialReceiveLineDetailList();
            if (details != null && !details.isEmpty()) {
                for (WmMaterialReceiveDetailVo detail : details) {
                    WmMaterialStock stock = buildStockFromDetail(receiveVo, detail);
                    putUniqueStock(stockMap, stock, "物料入库明细");
                }
            } else {
                WmMaterialStock stock = buildStockFromLine(receiveVo, line);
                putUniqueStock(stockMap, stock, "物料入库行");
            }
        }

        for (WmMaterialStock stock : stockMap.values()) {
            processStockTransferTransaction(receiveVo, stock, nvl(stock.getStockQuantity()));
        }

        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        receive.setStatus(ORDER_STATUS_FINISHED);
        receive.setUpdateTime(new Date());
        baseMapper.updateById(receive);

        List<WmMaterialReceiveLine> dbLines = receiveLineMapper.selectList(
            new LambdaQueryWrapper<WmMaterialReceiveLine>().eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        for (WmMaterialReceiveLine dbLine : dbLines) {
            List<WmMaterialReceiveDetail> dbDetails = receiveDetailMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveDetail>().eq(WmMaterialReceiveDetail::getLineId, dbLine.getLineId()));
            if (dbDetails != null && !dbDetails.isEmpty()) {
                for (WmMaterialReceiveDetail dbDetail : dbDetails) {
                    dbDetail.setUpdateTime(new Date());
                    receiveDetailMapper.updateById(dbDetail);
                }
            }
            dbLine.setUpdateTime(new Date());
            receiveLineMapper.updateById(dbLine);
        }

        if (receive.getNoticeId() != null) {
            WmArrivalNotice notice = arrivalNoticeMapper.selectById(receive.getNoticeId());
            if (notice != null) {
                notice.setStatus(ORDER_STATUS_FINISHED);
                notice.setUpdateTime(new Date());
                arrivalNoticeMapper.updateById(notice);
            }
        }

        // 入库完成后为每条库存生成条码，方便后续扫码追踪、拣货和盘点。
        // 这里先优先尝试默认配置；如果默认配置不存在，条码服务内部会自动回退到其它可用启用配置。
        for (WmMaterialStock stock : stockMap.values()) {
            wmBarcodeService.generateAndSaveByCode(
                "WM_RECEIVE_DEFAULT",
                "RECEIVE_IN",
                receiveId,
                null,
                stock.getMaterialId(),
                stock.getBatchId(),
                stock.getStockId(),
                new Date()
            );
        }

        return true;
    }

    /**
     * 回写采购订单行已收货数量
     * <p>
     * 根据入库单行的 materialId 和收货数量，匹配采购订单行并累加 receivedQuantity。
     * 全部到货后自动更新采购订单状态为 RECEIVED。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeBackPurchaseOrderReceivedQty(Long receiveId) {
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null || receive.getPurId() == null) {
            return;
        }
        Long orderId = receive.getPurId();
        List<WmMaterialReceiveLine> receiveLines = receiveLineMapper.selectList(
            new LambdaQueryWrapper<WmMaterialReceiveLine>().eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        if (receiveLines == null || receiveLines.isEmpty()) {
            return;
        }

        List<PuPurchaseOrderLine> orderLines = purchaseOrderLineMapper.selectList(
            new LambdaQueryWrapper<PuPurchaseOrderLine>().eq(PuPurchaseOrderLine::getOrderId, orderId));
        if (orderLines == null || orderLines.isEmpty()) {
            return;
        }

        Map<Long, java.util.List<PuPurchaseOrderLine>> materialLineMap = new LinkedHashMap<>();
        for (PuPurchaseOrderLine orderLine : orderLines) {
            materialLineMap.computeIfAbsent(orderLine.getMaterialId(), k -> new java.util.ArrayList<>()).add(orderLine);
        }

        for (WmMaterialReceiveLine receiveLine : receiveLines) {
            java.util.List<PuPurchaseOrderLine> candidates = materialLineMap.get(receiveLine.getMaterialId());
            PuPurchaseOrderLine orderLine = null;
            if (candidates != null && !candidates.isEmpty()) {
                orderLine = candidates.remove(0);
            }
            if (orderLine != null) {
                BigDecimal receivedQty = orderLine.getReceivedQuantity() != null ? orderLine.getReceivedQuantity() : BigDecimal.ZERO;
                BigDecimal newReceivedQty = receivedQty.add(receiveLine.getQuantityReceived() != null ? receiveLine.getQuantityReceived() : BigDecimal.ZERO);
                orderLine.setReceivedQuantity(newReceivedQty);

                BigDecimal orderQty = orderLine.getOrderQuantity() != null ? orderLine.getOrderQuantity() : BigDecimal.ZERO;
                int cmp = newReceivedQty.compareTo(orderQty);
                if (cmp == 0) {
                    orderLine.setReceiveStatus("FULL_RECEIVED");
                } else if (cmp > 0) {
                    orderLine.setReceiveStatus("OVER_RECEIVED");
                } else {
                    orderLine.setReceiveStatus("PARTIAL_RECEIVED");
                }

                purchaseOrderLineMapper.updateById(orderLine);
            }
        }

        updatePurchaseOrderStatus(orderId, orderLines);
    }

    /**
     * 根据收货进度更新采购订单状态
     */
    private void updatePurchaseOrderStatus(Long orderId, List<PuPurchaseOrderLine> orderLines) {
        boolean allReceived = true;
        boolean anyReceived = false;
        for (PuPurchaseOrderLine line : orderLines) {
            BigDecimal orderQty = line.getOrderQuantity() != null ? line.getOrderQuantity() : BigDecimal.ZERO;
            BigDecimal receivedQty = line.getReceivedQuantity() != null ? line.getReceivedQuantity() : BigDecimal.ZERO;
            if (receivedQty.compareTo(BigDecimal.ZERO) > 0) {
                anyReceived = true;
            }
            if (receivedQty.compareTo(orderQty) < 0) {
                allReceived = false;
            }
        }

        PuPurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        if (allReceived) {
            order.setStatus("RECEIVED");
        } else if (anyReceived) {
            order.setStatus("PARTIALLY_RECEIVED");
        }
        purchaseOrderMapper.updateById(order);
    }

    // ==================== 4. 工作流集成（提交审批 → Flowable流程引擎 → 审批回调同步状态） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(String receiveIdStr, String selectFlowId) {
        Long receiveId = Long.parseLong(receiveIdStr);
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null) {
            throw new ServiceException("入库单不存在");
        }

        // 如果前端没传 selectFlowId，从数据库已保存记录中读取
        if (StringUtils.isBlank(selectFlowId)) {
            if (receive.getSelectFlowId() != null) {
                selectFlowId = String.valueOf(receive.getSelectFlowId());
            } else {
                throw new ServiceException("请先在编辑页选择审批流程并保存");
            }
        }

        // selectFlowId 已在编辑时存入数据库，此处不重复 update 以免持有行锁
        // 导致 afterFlowHandle 回调死锁（submit TX on erp vs afterFlowHandle REQUIRES_NEW on erp）

        // 发起 Flowable 流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("dataId", receiveIdStr);
        variables.put("requireTaskOnStart", true);
        variables.put("receiveId", receive.getReceiveId());
        variables.put("receiveCode", receive.getReceiveCode());
        variables.put("receiveName", receive.getReceiveName());
        variables.put("vendorId", receive.getVendorId());
        variables.put("vendorName", receive.getVendorName());
        variables.put("receiveDate", receive.getReceiveDate());
    R<Void> result = processService.startProcessByDataId(receiveIdStr, selectFlowId, "wmMaterialReceiveService", variables);
        if (R.isSuccess(result)) {
            return true;
        }
        throw new ServiceException(result.getMsg());
    }

    @Override
    public String getProcInsId(String receiveIdStr) {
        // 第一级：查 wf_my_business 表
        try {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(receiveIdStr);
            if (business != null && StringUtils.isNotBlank(business.getProcessInstanceId())) {
                return business.getProcessInstanceId();
            }
        } catch (Exception ignored) {
        }

        // 第二级：查运行中的流程实例
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(receiveIdStr).singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        // 第三级：查历史流程实例
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(receiveIdStr).singleResult();
        return hist != null ? hist.getId() : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void afterFlowHandle(WfMyBusiness business) {
        Long receiveId = Long.parseLong(business.getDataId());
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        if (receive == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            receive.setStatus("APPROVING");
        } else if (ActStatus.pass.equals(actStatus)) {
            receive.setStatus("APPROVED");
            baseMapper.updateById(receive);
            StockInMessage stockInMsg = new StockInMessage();
            stockInMsg.setReceiveId(receiveId);
            stockInMsg.setSourceType("PURCHASE");
            stockInMsg.setSourceModule("wm");
            messageProducer.send(ExchangeConstants.DIRECT_EXCHANGE,
                RoutingKeyConstants.STOCK_IN_RECEIVE, stockInMsg);
            return;
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)) {
            receive.setStatus("REJECTED");
        } else if (ActStatus.revoke.equals(actStatus) || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus)) {
            receive.setStatus("PREPARE");
        }
        baseMapper.updateById(receive);
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

    // ==================== 5. 构建库存对象（从入库单行/明细转为库存实体） ====================

    private WmMaterialStock buildStockFromDetail(WmMaterialReceiveVo receive, WmMaterialReceiveDetailVo detail) {
        if (detail.getMaterialId() == null) {
            throw new ServiceException("物料ID不能为空");
        }
        if (detail.getWarehouseId() == null) {
            throw new ServiceException("仓库ID不能为空");
        }
        if (detail.getQuantity() == null) {
            throw new ServiceException("入库数量不能为空");
        }

        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(detail.getMaterialId());
        stock.setMaterialCode(detail.getMaterialCode());
        stock.setMaterialName(detail.getMaterialName());
        stock.setMaterialSpec(detail.getMaterialSpec());
        stock.setBaseUomId(detail.getBaseUomId());
        stock.setBaseUomName(detail.getBaseUomName());
        stock.setBatchId(detail.getBatchId());
        stock.setBatchCode(detail.getBatchCode());
        stock.setWarehouseId(detail.getWarehouseId());
        stock.setWarehouseCode(detail.getWarehouseCode());
        stock.setWarehouseName(detail.getWarehouseName());
        stock.setAreaId(detail.getAreaId());
        stock.setAreaCode(detail.getAreaCode());
        stock.setAreaName(detail.getAreaName());
        stock.setLocationId(detail.getLocationId());
        stock.setLocationCode(detail.getLocationCode());
        stock.setLocationName(detail.getLocationName());
        stock.setRemark(detail.getRemark());
        fillStockCommonFields(stock, receive, detail.getMaterialId());
        stock.setStockQuantity(detail.getQuantity());
        stock.setRecptDate(new Date());
        return stock;
    }

    private WmMaterialStock buildStockFromLine(WmMaterialReceiveVo receive, WmMaterialReceiveLineVo line) {
        if (line.getMaterialId() == null) {
            throw new ServiceException("物料ID不能为空");
        }
        if (line.getWarehouseId() == null) {
            throw new ServiceException("仓库ID不能为空，物料ID: " + line.getMaterialId() + ", 物料编码: " + line.getMaterialCode());
        }
        if (line.getQuantityReceived() == null) {
            throw new ServiceException("入库数量不能为空");
        }

        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialId(line.getMaterialId());
        stock.setMaterialCode(line.getMaterialCode());
        stock.setMaterialName(line.getMaterialName());
        stock.setMaterialSpec(line.getMaterialSpec());
        stock.setBaseUomId(line.getBaseUomId());
        stock.setBaseUomName(line.getBaseUomName());
        stock.setBatchId(line.getBatchId());
        stock.setBatchCode(line.getBatchCode());
        stock.setWarehouseId(line.getWarehouseId());
        stock.setWarehouseCode(line.getWarehouseCode());
        stock.setWarehouseName(line.getWarehouseName());
        stock.setAreaId(line.getAreaId());
        stock.setAreaCode(line.getAreaCode());
        stock.setAreaName(line.getAreaName());
        stock.setLocationId(line.getLocationId());
        stock.setLocationCode(line.getLocationCode());
        stock.setLocationName(line.getLocationName());
        stock.setRemark(line.getRemark());
        fillStockCommonFields(stock, receive, line.getMaterialId());
        stock.setStockQuantity(line.getQuantityReceived());
        stock.setRecptDate(new Date());
        return stock;
    }

    private void fillStockCommonFields(WmMaterialStock stock, WmMaterialReceiveVo receive, Long materialId) {
        BdMaterialVo material = bdMaterialService.selectById(materialId);
        if (material != null && material.getCategoryId() != null) {
            stock.setCategoryId(material.getCategoryId());
        }
        stock.setVendorId(receive.getVendorId());
        stock.setVendorCode(receive.getVendorCode());
        stock.setVendorName(receive.getVendorName());
        stock.setVendorNick(receive.getVendorNick());
    }

    private void putUniqueStock(Map<String, WmMaterialStock> stockMap, WmMaterialStock stock, String sourceType) {
        String key = buildStockKey(stock);
        if (stockMap.containsKey(key)) {
            throw new ServiceException(sourceType + "存在重复库存维度，无法执行入库：" + key);
        }
        stockMap.put(key, stock);
    }

    private String buildStockKey(WmMaterialStock stock) {
        return String.join("|",
            String.valueOf(stock.getMaterialId()),
            stock.getBatchId() != null ? stock.getBatchId().toString() : "",
            stock.getWarehouseId() != null ? stock.getWarehouseId().toString() : "",
            stock.getAreaId() != null ? stock.getAreaId().toString() : "",
            stock.getLocationId() != null ? stock.getLocationId().toString() : "",
            stock.getVendorId() != null ? stock.getVendorId().toString() : "",
            stock.getWoId() != null ? stock.getWoId().toString() : "",
            stock.getProductionDate() != null ? stock.getProductionDate().toString() : ""
        );
    }

    // ==================== 6. 库存转移核心逻辑（虚拟库位 → 目标库位） ====================

    private void processStockTransferTransaction(WmMaterialReceiveVo receive, WmMaterialStock stock, BigDecimal transactionQuantity) {
        if (transactionQuantity == null || transactionQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("入库数量必须大于0");
        }

        WmMaterialStock virtualCondition = copyStockCondition(stock);
        virtualCondition.setAreaId(VIRTUAL_AREA_ID);
        virtualCondition.setLocationId(VIRTUAL_LOCATION_ID);
        WmMaterialStock virtualStock = selectOneStock(virtualCondition);

        WmMaterialStock targetCondition = copyStockCondition(stock);
        WmMaterialStock targetStock = selectOneStock(targetCondition);

        processStockTransfer(receive, stock, virtualStock, targetStock, transactionQuantity);
    }

    private void processStockTransfer(WmMaterialReceiveVo receive, WmMaterialStock stock, WmMaterialStock virtualStock,
                                      WmMaterialStock targetStock, BigDecimal transactionQuantity) {
        Date now = new Date();

        if (virtualStock == null) {
            virtualStock = createVirtualFallbackStock(stock, transactionQuantity, now);
        }
        BigDecimal virtualBeforeQuantity = nvl(virtualStock.getStockQuantity());
        BigDecimal virtualAfterQuantity = virtualBeforeQuantity.subtract(transactionQuantity);
        if (virtualAfterQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("虚拟库位库存不足！");
        }
        virtualStock.setStockQuantity(virtualAfterQuantity);
        virtualStock.setUpdateTime(now);
        materialStockMapper.updateById(virtualStock);
        recordStockTransaction(receive, stock, -1, transactionQuantity, virtualBeforeQuantity,
            virtualAfterQuantity, virtualStock.getStockId(), "VIRTUAL_OUT");

        if (targetStock != null) {
            BigDecimal targetBeforeQuantity = nvl(targetStock.getStockQuantity());
            BigDecimal targetAfterQuantity = targetBeforeQuantity.add(transactionQuantity);
            targetStock.setStockQuantity(targetAfterQuantity);
            targetStock.setUpdateTime(now);
            materialStockMapper.updateById(targetStock);
            recordStockTransaction(receive, stock, 1, transactionQuantity, targetBeforeQuantity,
                targetAfterQuantity, targetStock.getStockId(), "TARGET_IN");
        } else {
            WmMaterialStock newStock = copyStock(stock);
            newStock.setStockId(null);
            newStock.setStockQuantity(transactionQuantity);
            newStock.setCreateTime(now);
            newStock.setUpdateTime(now);
            materialStockMapper.insert(newStock);
            recordStockTransaction(receive, stock, 1, transactionQuantity, BigDecimal.ZERO,
                transactionQuantity, newStock.getStockId(), "TARGET_IN");
        }
    }

    private WmMaterialStock createVirtualFallbackStock(WmMaterialStock stock, BigDecimal transactionQuantity, Date now) {
        WmMaterialStock virtualStock = copyStock(stock);
        virtualStock.setStockId(null);
        virtualStock.setAreaId(VIRTUAL_AREA_ID);
        virtualStock.setLocationId(VIRTUAL_LOCATION_ID);
        virtualStock.setLocationCode("VIRTUAL");
        virtualStock.setLocationName("虚拟库位");
        virtualStock.setStockQuantity(transactionQuantity);
        virtualStock.setRecptDate(now);
        virtualStock.setCreateTime(now);
        virtualStock.setUpdateTime(now);
        materialStockMapper.insert(virtualStock);
        return virtualStock;
    }

    // ==================== 7. 库存对象复制与查询 ====================

    private WmMaterialStock copyStockCondition(WmMaterialStock stock) {
        WmMaterialStock condition = copyStock(stock);
        condition.setStockId(null);
        condition.setStockQuantity(null);
        condition.setRecptDate(null);
        condition.setCreateTime(null);
        condition.setUpdateTime(null);
        return condition;
    }

    private WmMaterialStock copyStock(WmMaterialStock source) {
        WmMaterialStock target = new WmMaterialStock();
        target.setStockId(source.getStockId());
        target.setCategoryId(source.getCategoryId());
        target.setMaterialId(source.getMaterialId());
        target.setMaterialCode(source.getMaterialCode());
        target.setMaterialName(source.getMaterialName());
        target.setMaterialSpec(source.getMaterialSpec());
        target.setBaseUomId(source.getBaseUomId());
        target.setBaseUomName(source.getBaseUomName());
        target.setBatchId(source.getBatchId());
        target.setBatchCode(source.getBatchCode());
        target.setWarehouseId(source.getWarehouseId());
        target.setWarehouseCode(source.getWarehouseCode());
        target.setWarehouseName(source.getWarehouseName());
        target.setAreaId(source.getAreaId());
        target.setAreaCode(source.getAreaCode());
        target.setAreaName(source.getAreaName());
        target.setLocationId(source.getLocationId());
        target.setLocationCode(source.getLocationCode());
        target.setLocationName(source.getLocationName());
        target.setVendorId(source.getVendorId());
        target.setVendorCode(source.getVendorCode());
        target.setVendorName(source.getVendorName());
        target.setVendorNick(source.getVendorNick());
        target.setStockQuantity(source.getStockQuantity());
        target.setWoId(source.getWoId());
        target.setWoNo(source.getWoNo());
        target.setProductionDate(source.getProductionDate());
        target.setRecptDate(source.getRecptDate());
        target.setExpireDate(source.getExpireDate());
        target.setFrozenFlag(source.getFrozenFlag());
        target.setAttr1(source.getAttr1());
        target.setAttr2(source.getAttr2());
        target.setAttr3(source.getAttr3());
        target.setAttr4(source.getAttr4());
        target.setRemark(source.getRemark());
        return target;
    }

    private WmMaterialStock selectOneStock(WmMaterialStock condition) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(condition.getCategoryId() != null, WmMaterialStock::getCategoryId, condition.getCategoryId());
        lqw.eq(WmMaterialStock::getMaterialId, condition.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(condition.getMaterialCode()), WmMaterialStock::getMaterialCode, condition.getMaterialCode());
        lqw.eq(StringUtils.isNotBlank(condition.getMaterialSpec()), WmMaterialStock::getMaterialSpec, condition.getMaterialSpec());
        lqw.eq(StringUtils.isNotBlank(condition.getBaseUomId()), WmMaterialStock::getBaseUomId, condition.getBaseUomId());
        lqw.eq(condition.getBatchId() != null, WmMaterialStock::getBatchId, condition.getBatchId());
        lqw.eq(StringUtils.isNotBlank(condition.getBatchCode()), WmMaterialStock::getBatchCode, condition.getBatchCode());
        lqw.eq(WmMaterialStock::getWarehouseId, condition.getWarehouseId());
        lqw.eq(condition.getAreaId() != null, WmMaterialStock::getAreaId, condition.getAreaId());
        lqw.eq(condition.getLocationId() != null, WmMaterialStock::getLocationId, condition.getLocationId());
        lqw.eq(condition.getVendorId() != null, WmMaterialStock::getVendorId, condition.getVendorId());
        lqw.last("limit 1");
        return materialStockMapper.selectOne(lqw);
    }

    // ==================== 8. 库存流水记录 ====================

    private void recordStockTransaction(WmMaterialReceiveVo receive, WmMaterialStock stock, int transactionFlag, BigDecimal transactionQuantity,
                                        BigDecimal beforeQuantity, BigDecimal afterQuantity, Long stockId, String txType) {
        WmMaterialStockTx stockTx = new WmMaterialStockTx();
        stockTx.setTxId(null);
        stockTx.setTxType(txType);
        stockTx.setMaterialId(stock.getMaterialId());
        stockTx.setMaterialCode(stock.getMaterialCode());
        stockTx.setMaterialName(stock.getMaterialName());
        stockTx.setMaterialSpec(stock.getMaterialSpec());
        stockTx.setBaseUomId(stock.getBaseUomId());
        stockTx.setBaseUomName(stock.getBaseUomName());
        stockTx.setBatchId(stock.getBatchId());
        stockTx.setBatchCode(stock.getBatchCode());
        stockTx.setWarehouseId(stock.getWarehouseId());
        stockTx.setWarehouseCode(stock.getWarehouseCode());
        stockTx.setWarehouseName(stock.getWarehouseName());
        stockTx.setAreaId(stock.getAreaId());
        stockTx.setAreaCode(stock.getAreaCode());
        stockTx.setAreaName(stock.getAreaName());
        stockTx.setLocationId(stock.getLocationId());
        stockTx.setLocationCode(stock.getLocationCode());
        stockTx.setLocationName(stock.getLocationName());
        stockTx.setPackageId(null);
        stockTx.setPackageCode(null);
        stockTx.setSourceDocType("WM_MATERIAL_RECEIVE");
        stockTx.setSourceDocId(receive.getReceiveId());
        stockTx.setSourceDocCode(receive.getReceiveCode());
        stockTx.setSourceDocLineId(null);
        stockTx.setMaterialStockId(stockId);
        stockTx.setTxFlag(transactionFlag);
        stockTx.setTxQuantity(transactionQuantity);
        stockTx.setBeforeQuantity(beforeQuantity);
        stockTx.setAfterQuantity(afterQuantity);
        Date now = new Date();
        stockTx.setTxDate(now);
        stockTx.setErpDate(now);
        stockTx.setRecptDate(stock.getRecptDate());
        stockTx.setRemark(stock.getRemark());
        stockTx.setCreateTime(now);
        stockTx.setUpdateTime(now);
        materialStockTxMapper.insert(stockTx);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ==================== 9. 审批校验与数据持久化辅助 ====================

    /**
     * 审批状态下的明细校验：行不可为空，每条行的明细数量合计须等于行入库数量
     */
    private void validateDetailsForApproval(WmMaterialReceiveBo bo) {
        StringBuilder errorMsg = new StringBuilder();
        boolean isValid = true;

        if (bo.getWmMaterialReceiveLineList() == null || bo.getWmMaterialReceiveLineList().isEmpty()) {
            throw new ServiceException("入库单行不能为空！");
        }

        for (WmMaterialReceiveLineBo line : bo.getWmMaterialReceiveLineList()) {
            if (line.getWmMaterialReceiveLineDetailList() == null
                || line.getWmMaterialReceiveLineDetailList().isEmpty()) {
                isValid = false;
                errorMsg.append("物料[").append(line.getMaterialName())
                    .append("]未分配对应的明细行！");
            } else {
                for (WmMaterialReceiveDetailBo detail : line.getWmMaterialReceiveLineDetailList()) {
                    if (detail.getQuantity().compareTo(line.getQuantityReceived()) != 0) {
                        isValid = false;
                        errorMsg.append("物料[").append(line.getMaterialName())
                            .append("]明细数量与入库数量不一致！");
                    }
                }
            }
        }

        if (!isValid) {
            throw new ServiceException(errorMsg.toString());
        }
    }

    /**
     * 插入入库单行及其明细数据
     */
    private void insertLinesAndDetails(Long receiveId, List<WmMaterialReceiveLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<WmMaterialReceiveLine> lineList = MapstructUtils.convert(lineBoList, WmMaterialReceiveLine.class);
            for (WmMaterialReceiveLine line : lineList) {
                line.setLineId(null);
                line.setReceiveId(receiveId);
            }
            receiveLineMapper.insertBatch(lineList);

            // 插入明细数据
            for (int i = 0; i < lineBoList.size(); i++) {
                WmMaterialReceiveLineBo lineBo = lineBoList.get(i);
                if (lineBo.getWmMaterialReceiveLineDetailList() != null
                    && !lineBo.getWmMaterialReceiveLineDetailList().isEmpty()) {
                    List<WmMaterialReceiveDetail> detailList = MapstructUtils.convert(
                        lineBo.getWmMaterialReceiveLineDetailList(), WmMaterialReceiveDetail.class);
                    for (WmMaterialReceiveDetail detail : detailList) {
                        detail.setDetailId(null);
                        detail.setReceiveId(receiveId);
                        detail.setLineId(lineList.get(i).getLineId());
                    }
                    receiveDetailMapper.insertBatch(detailList);
                }
            }
        }
    }

    /**
     * 根据收货ID删除行和明细数据
     */
    private void deleteLinesAndDetailsByReceiveId(Long receiveId) {
        // 删除该入库单下所有行的明细数据
        List<WmMaterialReceiveLine> lines = receiveLineMapper.selectList(
            new LambdaQueryWrapper<WmMaterialReceiveLine>()
                .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
        for (WmMaterialReceiveLine line : lines) {
            receiveDetailMapper.delete(new LambdaQueryWrapper<WmMaterialReceiveDetail>()
                .eq(WmMaterialReceiveDetail::getLineId, line.getLineId()));
        }
        // 删除入库单行（明细已在上方循环中删除）
        receiveLineMapper.delete(new LambdaQueryWrapper<WmMaterialReceiveLine>()
            .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
    }

    private LambdaQueryWrapper<WmMaterialReceive> buildQueryWrapper(WmMaterialReceiveBo bo) {
        LambdaQueryWrapper<WmMaterialReceive> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReceiveCode()), WmMaterialReceive::getReceiveCode, bo.getReceiveCode());
        lqw.like(StringUtils.isNotBlank(bo.getReceiveName()), WmMaterialReceive::getReceiveName, bo.getReceiveName());
        lqw.eq(bo.getIqcId() != null, WmMaterialReceive::getIqcId, bo.getIqcId());
        lqw.eq(StringUtils.isNotBlank(bo.getIqcCode()), WmMaterialReceive::getIqcCode, bo.getIqcCode());
        lqw.eq(bo.getNoticeId() != null, WmMaterialReceive::getNoticeId, bo.getNoticeId());
        lqw.eq(StringUtils.isNotBlank(bo.getNoticeCode()), WmMaterialReceive::getNoticeCode, bo.getNoticeCode());
        lqw.eq(bo.getPurId() != null, WmMaterialReceive::getPurId, bo.getPurId());
        lqw.eq(StringUtils.isNotBlank(bo.getPurCode()), WmMaterialReceive::getPurCode, bo.getPurCode());
        lqw.eq(bo.getVendorId() != null, WmMaterialReceive::getVendorId, bo.getVendorId());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorCode()), WmMaterialReceive::getVendorCode, bo.getVendorCode());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmMaterialReceive::getVendorName, bo.getVendorName());
        lqw.eq(StringUtils.isNotBlank(bo.getVendorNick()), WmMaterialReceive::getVendorNick, bo.getVendorNick());
        lqw.eq(bo.getWarehouseId() != null, WmMaterialReceive::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmMaterialReceive::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmMaterialReceive::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getAreaId() != null, WmMaterialReceive::getAreaId, bo.getAreaId());
        lqw.eq(StringUtils.isNotBlank(bo.getAreaCode()), WmMaterialReceive::getAreaCode, bo.getAreaCode());
        lqw.like(StringUtils.isNotBlank(bo.getAreaName()), WmMaterialReceive::getAreaName, bo.getAreaName());
        lqw.eq(bo.getLocationId() != null, WmMaterialReceive::getLocationId, bo.getLocationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmMaterialReceive::getLocationCode, bo.getLocationCode());
        lqw.like(StringUtils.isNotBlank(bo.getLocationName()), WmMaterialReceive::getLocationName, bo.getLocationName());
        lqw.eq(bo.getReceiveDate() != null, WmMaterialReceive::getReceiveDate, bo.getReceiveDate());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmMaterialReceive::getStatus, bo.getStatus());
        return lqw;
    }
}
