package com.nexus.core.wm.service.impl;

import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.constant.NexusConstants;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.utils.SecurityUtils;
import com.nexus.common.utils.bean.BeanUtils;
import com.nexus.core.wm.domain.WmMaterialReceive;
import com.nexus.core.wm.domain.WmMaterialReceiveLine;
import com.nexus.core.wm.domain.WmMaterialReceiveDetail;
import com.nexus.core.wm.domain.WmMaterialStock;
import com.nexus.core.wm.domain.WmArrivalNotice;
import com.nexus.core.wm.domain.WmMaterialStockTx;
import com.nexus.core.bd.domain.BdMaterial;
import com.nexus.core.wm.service.IWmMaterialReceiveService;
import com.nexus.core.wm.service.IWmMaterialStockService;
import com.nexus.core.wm.service.IWmArrivalNoticeService;
import com.nexus.core.wm.service.IWmInventoryCoreService;
import com.nexus.core.wm.service.IWmMaterialStockTxService;
import com.nexus.core.bd.service.IBdMaterialService;
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

    @Autowired
    private IWmMaterialStockTxService wmMaterialStockTxService;

    @Autowired
    private IBdMaterialService bdMaterialService;

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
                            
                            // 使用BeanUtils复制相同属性
                            BeanUtils.copyBeanProp(stock, detail);
                            
                            // 通过materialId查询物料信息获取categoryId
                            BdMaterial material = bdMaterialService.selectBdMaterialByMaterialId(detail.getMaterialId());
                            if (material != null && material.getCategoryId() != null) {
                                stock.setCategoryId(material.getCategoryId());
                            }
                            
                            // 设置其他特殊字段
                            stock.setStockQuantity(detail.getQuantity());
                            stock.setRecptDate(new Date());
                            
                            // 从主表获取供应商信息
                            stock.setVendorId(recpt.getVendorId());
                            stock.setVendorCode(recpt.getVendorCode());
                            stock.setVendorName(recpt.getVendorName());
                            stock.setVendorNick(recpt.getVendorNick());

                            // 处理库存转移事务
                            processStockTransferTransaction(stock, detail.getQuantity());
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
                        
                        // 使用BeanUtils复制相同属性
                        BeanUtils.copyBeanProp(stock, line);
                        
                        // 通过materialId查询物料信息获取categoryId
                        BdMaterial material = bdMaterialService.selectBdMaterialByMaterialId(line.getMaterialId());
                        if (material != null && material.getCategoryId() != null) {
                            stock.setCategoryId(material.getCategoryId());
                        }
                        
                        // 设置其他特殊字段
                        stock.setStockQuantity(line.getQuantityReceived());
                        stock.setRecptDate(new Date());
                        
                        // 从主表获取供应商信息
                        stock.setVendorId(recpt.getVendorId());
                        stock.setVendorCode(recpt.getVendorCode());
                        stock.setVendorName(recpt.getVendorName());
                        stock.setVendorNick(recpt.getVendorNick());

                        // 处理库存转移事务
                        processStockTransferTransaction(stock, line.getQuantityReceived());
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
        
        // 使用BeanUtils复制查询条件
        BeanUtils.copyBeanProp(condition, stock);
        
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
            
            // 记录库存事务
            recordStockTransaction(stock, transactionFlag, stock.getStockQuantity(), 
                               existingStock.getStockQuantity().subtract(transactionQuantity), resultQuantity, 
                               existingStock.getStockId());
        } else {
            // 如果库存记录不存在，新增记录
            if (transactionFlag == 1) {
                // 只有增加库存时才允许新增记录
                stock.setCreateTime(new Date());
                stock.setUpdateTime(new Date());
                wmMaterialStockService.insertWmMaterialStock(stock);
                
                // 记录库存事务
                recordStockTransaction(stock, transactionFlag, stock.getStockQuantity(), 
                                   BigDecimal.ZERO, stock.getStockQuantity(), 
                                   stock.getStockId());
            } else {
                throw new RuntimeException("库存记录不存在，无法执行出库操作！");
            }
        }
    }

    /**
     * 处理库存转移事务（从虚拟库位到目标库位）
     * 
     * @param stock 库存对象
     * @param transactionQuantity 事务数量
     */
    private void processStockTransferTransaction(WmMaterialStock stock, BigDecimal transactionQuantity) {
        Date now = new Date();
        
        // 1. 查询虚拟库位库存（假设虚拟库位ID为0或其他特定值）
        WmMaterialStock virtualCondition = new WmMaterialStock();
        
        // 使用BeanUtils复制查询条件
        BeanUtils.copyBeanProp(virtualCondition, stock);
        
        // 设置虚拟库位的特殊条件
        virtualCondition.setAreaId(0L); // 虚拟库区ID
        virtualCondition.setLocationId(0L); // 虚拟库位ID
        
        List<WmMaterialStock> virtualStocks = wmMaterialStockService.selectWmMaterialStockList(virtualCondition);
        WmMaterialStock virtualStock = (virtualStocks != null && !virtualStocks.isEmpty()) ? virtualStocks.get(0) : null;
        
        // 2. 查询目标库位库存
        WmMaterialStock targetCondition = new WmMaterialStock();
        
        // 使用BeanUtils复制查询条件
        BeanUtils.copyBeanProp(targetCondition, stock);
        
        List<WmMaterialStock> targetStocks = wmMaterialStockService.selectWmMaterialStockList(targetCondition);
        WmMaterialStock targetStock = (targetStocks != null && !targetStocks.isEmpty()) ? targetStocks.get(0) : null;
        
        // 3. 处理库存转移
        processStockTransfer(stock, virtualStock, targetStock, transactionQuantity);
    }

    /**
     * 处理库存转移事务（从虚拟库位到目标库位）
     * 
     * @param stock 库存对象
     * @param virtualLocationStock 虚拟库位库存对象
     * @param targetLocationStock 目标库位库存对象
     * @param transactionQuantity 事务数量
     */
    private void processStockTransfer(WmMaterialStock stock, WmMaterialStock virtualLocationStock, 
                                   WmMaterialStock targetLocationStock, BigDecimal transactionQuantity) {
        Date now = new Date();
        
        // 1. 处理虚拟库位出库事务
        if (virtualLocationStock != null) {
            // 减少虚拟库位库存数量
            BigDecimal virtualBeforeQuantity = virtualLocationStock.getStockQuantity();
            BigDecimal virtualAfterQuantity = virtualBeforeQuantity.subtract(transactionQuantity);
            
            // 检查虚拟库位库存是否足够
            if (virtualAfterQuantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("虚拟库位库存不足！");
            }
            
            // 更新虚拟库位库存
            virtualLocationStock.setStockQuantity(virtualAfterQuantity);
            virtualLocationStock.setUpdateTime(now);
            wmMaterialStockService.updateWmMaterialStock(virtualLocationStock);
            
            // 记录虚拟库位出库事务
            recordStockTransaction(stock, -1, transactionQuantity, virtualBeforeQuantity, 
                                virtualAfterQuantity, virtualLocationStock.getStockId(), "VIRTUAL_OUT");
        }
        
        // 2. 处理目标库位入库事务
        if (targetLocationStock != null) {
            // 增加目标库位库存数量
            BigDecimal targetBeforeQuantity = targetLocationStock.getStockQuantity();
            BigDecimal targetAfterQuantity = targetBeforeQuantity.add(transactionQuantity);
            
            // 更新目标库位库存
            targetLocationStock.setStockQuantity(targetAfterQuantity);
            targetLocationStock.setUpdateTime(now);
            wmMaterialStockService.updateWmMaterialStock(targetLocationStock);
            
            // 记录目标库位入库事务
            recordStockTransaction(stock, 1, transactionQuantity, targetBeforeQuantity, 
                                targetAfterQuantity, targetLocationStock.getStockId(), "TARGET_IN");
        } else {
            // 如果目标库位没有库存记录，则创建新记录
            WmMaterialStock newStock = new WmMaterialStock();
            
            // 使用BeanUtils复制相同属性
            BeanUtils.copyBeanProp(newStock, stock);
            
            newStock.setStockQuantity(transactionQuantity);
            newStock.setRecptDate(stock.getRecptDate());
            newStock.setCreateTime(now);
            newStock.setUpdateTime(now);
            
            wmMaterialStockService.insertWmMaterialStock(newStock);
            
            // 记录目标库位入库事务
            recordStockTransaction(stock, 1, transactionQuantity, BigDecimal.ZERO, 
                                transactionQuantity, newStock.getStockId(), "TARGET_IN");
        }
    }

    /**
     * 记录库存事务
     * 
     * @param stock 库存对象
     * @param transactionFlag 事务标记：1表示增加库存，-1表示减少库存
     * @param transactionQuantity 事务数量
     * @param beforeQuantity 变动前数量
     * @param afterQuantity 变动后数量
     * @param stockId 库存记录ID
     * @param txType 事务类型
     */
    private void recordStockTransaction(WmMaterialStock stock, int transactionFlag, BigDecimal transactionQuantity, 
                                 BigDecimal beforeQuantity, BigDecimal afterQuantity, Long stockId, String txType) {
        WmMaterialStockTx stockTx = new WmMaterialStockTx();
        
        // 使用BeanUtils复制相同属性
        BeanUtils.copyBeanProp(stockTx, stock);
        
        // 基本信息
        stockTx.setTxType(txType); // 事务类型
        
        // 事务信息
        stockTx.setMaterialStockId(stockId);
        stockTx.setTxFlag(transactionFlag);
        stockTx.setTxQuantity(transactionQuantity);
        stockTx.setTxDate(new Date());
        stockTx.setRecptDate(stock.getRecptDate());
        
        // 数量信息
        stockTx.setBeforeQuantity(beforeQuantity);
        stockTx.setAfterQuantity(afterQuantity);
        
        // 操作信息
        stockTx.setCreateBy(getUsername());
        stockTx.setCreateTime(new Date());
        
        // 保存事务记录
        wmMaterialStockTxService.insertWmMaterialStockTx(stockTx);
    }

    /**
     * 记录库存事务
     * 
     * @param stock 库存对象
     * @param transactionFlag 事务标记：1表示增加库存，-1表示减少库存
     * @param transactionQuantity 事务数量
     * @param beforeQuantity 变动前数量
     * @param afterQuantity 变动后数量
     * @param stockId 库存记录ID
     */
    private void recordStockTransaction(WmMaterialStock stock, int transactionFlag, BigDecimal transactionQuantity, 
                                 BigDecimal beforeQuantity, BigDecimal afterQuantity, Long stockId) {
        recordStockTransaction(stock, transactionFlag, transactionQuantity, beforeQuantity, afterQuantity, stockId, "INVENTORY_UPDATE");
    }

    /**
     * 获取当前登录用户名
     * 
     * @return 用户名
     */
    private String getUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "admin"; // 默认用户名
        }
    }
}