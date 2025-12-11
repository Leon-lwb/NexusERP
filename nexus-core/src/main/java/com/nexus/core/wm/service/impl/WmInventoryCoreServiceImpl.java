package com.nexus.core.wm.service.impl;

import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.constant.NexusConstants;
import com.nexus.common.utils.StringUtils;
import com.nexus.core.wm.domain.WmMaterialReceive;
import com.nexus.core.wm.domain.WmMaterialReceiveLine;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.nexus.core.wm.domain.WmMaterialStock;
import com.nexus.core.wm.domain.WmArrivalNotice;
import com.nexus.core.wm.service.IWmMaterialReceiveService;
import com.nexus.core.wm.service.IWmMaterialStockService;
import com.nexus.core.wm.service.IWmArrivalNoticeService;
import com.nexus.core.wm.service.IWmInventoryCoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存核心Service业务层处理
 *
 * @author nexus
 * @date 2025-12-11
 */
@Service
public class WmInventoryCoreServiceImpl implements IWmInventoryCoreService {

    @Autowired
    private IWmMaterialReceiveService wmMaterialReceiveService;

    @Autowired
    private IWmMaterialStockService wmMaterialStockService;

    @Autowired
    private IWmArrivalNoticeService wmArrivalNoticeService;

    /**
     * 执行物料入库
     *
     * @param receiveId 入库单ID
     * @return 结果
     */
    @Override
    @Transactional
    public AjaxResult executeReceive(Long receiveId) {
        try {
            // 1. 查询入库单信息
            WmMaterialReceive recpt = wmMaterialReceiveService.selectWmMaterialReceiveByReceiveId(receiveId);
            if (recpt == null) {
                return AjaxResult.error("入库单不存在");
            }

            // 检查单据状态
            if (!NexusConstants.ORDER_STATUS_APPROVED.equals(recpt.getStatus())) {
                return AjaxResult.error("只有已审核的入库单才能执行入库操作");
            }

            // 2. 构造库存事务数据并执行库存更新
            List<WmMaterialReceiveLine> lines = recpt.getWmMaterialReceiveLineList();
            if (lines != null && !lines.isEmpty()) {
                for (WmMaterialReceiveLine line : lines) {
                    // 获取行项目的明细列表
                    List<WmMaterialReceiveDetail> details = line.getWmMaterialReceiveLineDetailList();
                    if (details != null && !details.isEmpty()) {
                        for (WmMaterialReceiveDetail detail : details) {
                            // 检查必要字段是否为空
                            if (detail.getMaterialId() == null) {
                                throw new RuntimeException("物料ID不能为空");
                            }
                            if (detail.getWarehouseId() == null) {
                                throw new RuntimeException("仓库ID不能为空");
                            }

                            // 构造库存对象，从明细中获取所有必要字段
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
                            stock.setStockQuantity(detail.getQuantity());
                            stock.setRecptDate(new Date());

                            // 处理库存事务
                            processTransaction(stock, 1); // 1表示入库增加库存
                        }
                    } else {
                        // 如果没有明细，则从行项目本身获取信息（向后兼容）
                        // 检查必要字段是否为空
                        if (line.getMaterialId() == null) {
                            throw new RuntimeException("物料ID不能为空");
                        }
                        if (line.getWarehouseId() == null) {
                            throw new RuntimeException("仓库ID不能为空");
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
                        stock.setStockQuantity(line.getQuantityReceived());
                        stock.setRecptDate(new Date());

                        // 处理库存事务
                        processTransaction(stock, 1); // 1表示入库增加库存
                    }
                }
            }

            // 3. 更新入库单状态
            recpt.setStatus(NexusConstants.ORDER_STATUS_FINISHED);
            recpt.setUpdateTime(new Date());
            wmMaterialReceiveService.updateWmMaterialReceive(recpt);

            // 4. 更新关联的到货通知单状态
            WmArrivalNotice notice = wmArrivalNoticeService.selectWmArrivalNoticeByNoticeId(recpt.getNoticeId());
            if (StringUtils.isNotNull(notice)) {
                notice.setStatus(NexusConstants.ORDER_STATUS_FINISHED);
                notice.setUpdateTime(new Date());
                wmArrivalNoticeService.updateWmArrivalNotice(notice);
            }

            return AjaxResult.success("物料入库执行成功");
        } catch (Exception e) {
            e.printStackTrace(); // 打印异常堆栈信息以便调试
            return AjaxResult.error("物料入库执行失败：" + e.getMessage());
        }
    }

    /**
     * 处理库存事务
     *
     * @param stock            库存对象
     * @param transactionFlag 事务标记：1表示增加库存，-1表示减少库存
     */
    private void processTransaction(WmMaterialStock stock, int transactionFlag) {
        // 检查必要字段是否为空
        if (stock.getMaterialId() == null) {
            throw new RuntimeException("物料ID不能为空");
        }
        if (stock.getWarehouseId() == null) {
            throw new RuntimeException("仓库ID不能为空，物料ID: " + stock.getMaterialId() + ", 物料编码: " + stock.getMaterialCode());
        }

        // 查询现有库存
        WmMaterialStock condition = new WmMaterialStock();
        condition.setMaterialId(stock.getMaterialId());
        condition.setWarehouseId(stock.getWarehouseId());
        condition.setAreaId(stock.getAreaId());
        condition.setLocationId(stock.getLocationId());
        condition.setBatchId(stock.getBatchId());

        List<WmMaterialStock> existingStocks = wmMaterialStockService.selectWmMaterialStockList(condition);

        if (existingStocks != null && !existingStocks.isEmpty()) {
            // 如果库存记录已存在，更新数量
            WmMaterialStock existingStock = existingStocks.get(0);
            BigDecimal transactionQuantity = stock.getStockQuantity().multiply(new BigDecimal(transactionFlag));
            BigDecimal resultQuantity = existingStock.getStockQuantity().add(transactionQuantity);

            // 检查库存数量不能为负数
            if (resultQuantity.compareTo(new BigDecimal(0)) < 0) {
                throw new RuntimeException("库存数量不足！");
            }

            existingStock.setStockQuantity(resultQuantity);
            existingStock.setUpdateTime(new Date());
            wmMaterialStockService.updateWmMaterialStock(existingStock);
        } else {
            // 如果库存记录不存在，新增记录
            if (transactionFlag == 1) {
                // 只有增加库存时才允许新增记录
                stock.setCreateTime(new Date());
                stock.setUpdateTime(new Date());
                wmMaterialStockService.insertWmMaterialStock(stock);
            } else {
                throw new RuntimeException("库存记录不存在，无法执行出库操作！");
            }
        }
    }
}