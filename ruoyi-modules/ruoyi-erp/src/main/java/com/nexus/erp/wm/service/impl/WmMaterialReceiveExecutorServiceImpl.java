package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.service.IBdMaterialService;
import com.nexus.erp.wm.domain.WmArrivalNotice;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
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
import com.nexus.erp.wm.service.IWmMaterialReceiveExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料入库执行Service实现类，负责库存过账、虚拟库位转目标库位、库存事务记录
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMaterialReceiveExecutorServiceImpl implements IWmMaterialReceiveExecutorService {

    /** 待入库状态（已上架） */
    private static final String ORDER_STATUS_SHELVED = "SHELVED";
    /** 已完成状态 */
    private static final String ORDER_STATUS_FINISHED = "FINISHED";
    /** 虚拟区域ID */
    private static final Long VIRTUAL_AREA_ID = 0L;
    /** 虚拟库位ID */
    private static final Long VIRTUAL_LOCATION_ID = 0L;
    /** 来源单据类型：物料入库单（用于防重校验） */
    private static final String SOURCE_DOC_TYPE_RECEIVE = "WM_MATERIAL_RECEIVE";

    /** 基础数据访问映射器（Mapper） */
    private final WmMaterialReceiveMapper baseMapper;
    /** 领用行数据访问映射器（Mapper） */
    private final WmMaterialReceiveLineMapper receiveLineMapper;
    /** 领用明细数据访问映射器（Mapper） */
    private final WmMaterialReceiveDetailMapper receiveDetailMapper;
    /** 物料库存数据访问映射器（Mapper） */
    private final WmMaterialStockMapper materialStockMapper;
    /** 物料库存流水数据访问映射器（Mapper） */
    private final WmMaterialStockTxMapper materialStockTxMapper;
    /** 到货通知数据访问映射器（Mapper） */
    private final WmArrivalNoticeMapper arrivalNoticeMapper;
    /** 物料领用执行器业务服务，提供物料领用执行器相关能力 */
    private final IBdMaterialService bdMaterialService;
    /** 条码业务服务，提供条码相关能力 */
    private final IWmBarcodeService wmBarcodeService;
    /** 仓储模块MQ消息发送服务，独立于@DS("erp")，确保消息记录写入ry-vue库 */
    private final WmMqSendService wmMqSendService;
    /** 库存计价服务(F3-a:采购入库按参考价/标准成本计价) */
    private final com.nexus.erp.wm.service.IInventoryValuationService valuationService;

    /**
     * 执行物料入库
     * <p>
     * 完整流程：防重校验 → 虚拟库位转储 → 更新单据状态 → 更新到货通知 → 生成条码 → 发送采购回写MQ消息
     * </p>
     *
     * @param receiveId 入库单ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean executeReceive(Long receiveId) {
        WmMaterialReceiveVo receiveVo = buildReceiveVo(receiveId);
        if (receiveVo == null) {
            throw new ServiceException("入库单不存在");
        }
        if (!ORDER_STATUS_SHELVED.equals(receiveVo.getStatus())) {
            throw new ServiceException("只有待入库状态的入库单才能执行入库操作");
        }

        // 并发防重：条件更新抢占（SHELVED → EXECUTING），影响行数为 0 说明已被并发执行，杜绝双击/MQ 同时到达的重复过账
        int claimed = baseMapper.update(null, Wrappers.<WmMaterialReceive>lambdaUpdate()
            .set(WmMaterialReceive::getStatus, "EXECUTING")
            .eq(WmMaterialReceive::getReceiveId, receiveId)
            .eq(WmMaterialReceive::getStatus, ORDER_STATUS_SHELVED));
        if (claimed == 0) {
            throw new ServiceException("入库单已执行过入库过账，请勿重复操作");
        }

        // 防重校验：检查是否已存在该入库单的库存事务记录，避免重复入库
        if (hasPosted(receiveId)) {
            throw new ServiceException("入库单已执行过入库过账，请勿重复操作");
        }

        List<WmMaterialReceiveLineVo> lines = receiveVo.getWmMaterialReceiveLineList();
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException("入库单行不能为空");
        }

        // 按明细/行构建库存对象，去重校验
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

        // 执行库存转储：虚拟库位扣减 + 目标库位增加
        for (WmMaterialStock stock : stockMap.values()) {
            processStockTransferTransaction(receiveVo, stock, nvl(stock.getStockQuantity()));
        }

        // 更新入库单状态为已完成
        WmMaterialReceive receive = baseMapper.selectById(receiveId);
        receive.setStatus(ORDER_STATUS_FINISHED);
        receive.setUpdateTime(new Date());
        baseMapper.updateById(receive);

        // 更新入库行和明细的时间戳
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

        // 更新关联的到货通知单状态为已完成
        if (receive.getNoticeId() != null) {
            WmArrivalNotice notice = arrivalNoticeMapper.selectById(receive.getNoticeId());
            if (notice != null) {
                notice.setStatus(ORDER_STATUS_FINISHED);
                notice.setUpdateTime(new Date());
                arrivalNoticeMapper.updateById(notice);
            }
        }

        // 为入库的库存生成条码
        for (WmMaterialStock stock : stockMap.values()) {
            wmBarcodeService.generateAndSaveByCode(
                "WM_RECEIVE_DEFAULT",
                "RECEIVE_IN",
                receiveId,
                null,
                stock.getMaterialId(),
                stock.getBatchId(),
                stock.getStockId(),
                stock.getSnId(),
                new Date()
            );
        }

        // 入库完成后，异步回写采购订单已收货数量
        // 通过MQ解耦，避免仓储模块直接依赖采购模块的写操作
        // 使用事务同步机制，在事务提交后再发送MQ消息：
        // 1. 确保入库数据已持久化，避免消息先于数据到达消费者
        // 2. 事务提交后通过独立的WmMqSendService发送，该Service无@DS注解，
        //    可正确切换到master数据源，将消息记录写入ry-vue库的mq_message_record表
        if (receive.getPurId() != null) {
            // 在请求线程中提前获取租户ID，afterCommit/异步线程中无法获取
            final String tenantId = TenantHelper.getTenantId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wmMqSendService.sendPurchaseReceiveWriteback(receiveId, tenantId);
                }
            });
        }

        return true;
    }

    /**
     * 防重校验：检查该入库单是否已执行过库存过账
     * <p>
     * 通过查询库存事务表（wm_material_stock_tx）中是否存在该来源单据的记录来判断。
     * 如果已存在记录，说明该入库单已经执行过入库过账，不允许重复操作。
     * </p>
     *
     * @param receiveId 入库单ID
     * @return true=已过账（不允许重复），false=未过账
     */
    private boolean hasPosted(Long receiveId) {
        Long count = materialStockTxMapper.selectCount(
            new LambdaQueryWrapper<WmMaterialStockTx>()
                .eq(WmMaterialStockTx::getSourceDocType, SOURCE_DOC_TYPE_RECEIVE)
                .eq(WmMaterialStockTx::getSourceDocId, receiveId));
        return count != null && count > 0;
    }

    /**
     * 构建入库单VO（含行和明细）
     *
     * @param receiveId 入库单ID
     * @return 入库单VO
     */
    private WmMaterialReceiveVo buildReceiveVo(Long receiveId) {
        WmMaterialReceiveVo vo = baseMapper.selectVoById(receiveId);
        if (vo != null) {
            List<WmMaterialReceiveLine> lineList = receiveLineMapper.selectList(
                new LambdaQueryWrapper<WmMaterialReceiveLine>()
                    .eq(WmMaterialReceiveLine::getReceiveId, receiveId));
            List<WmMaterialReceiveLineVo> lineVoList = MapstructUtils.convert(lineList, WmMaterialReceiveLineVo.class);

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

    /**
     * 根据入库明细构建库存对象
     *
     * @param receive 入库单VO
     * @param detail  入库明细VO
     * @return 库存对象
     */
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
        // 将入库明细的序列号信息写入库存，确保库存记录可追溯至具体SN
        stock.setSnId(detail.getSnId());
        stock.setSnCode(detail.getSnCode());
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

    /**
     * 根据入库行构建库存对象
     *
     * @param receive 入库单VO
     * @param line    入库行VO
     * @return 库存对象
     */
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

    /**
     * 填充库存公共字段（物料分类、供应商信息）
     *
     * @param stock      库存对象
     * @param receive    入库单VO
     * @param materialId 物料ID
     */
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

    /**
     * 将库存对象放入Map，若存在重复维度则抛出异常
     *
     * @param stockMap   库存Map
     * @param stock      库存对象
     * @param sourceType 来源类型
     */
    private void putUniqueStock(Map<String, WmMaterialStock> stockMap, WmMaterialStock stock, String sourceType) {
        String key = buildStockKey(stock);
        if (stockMap.containsKey(key)) {
            throw new ServiceException(sourceType + "存在重复库存维度，无法执行入库：" + key);
        }
        stockMap.put(key, stock);
    }

    /**
     * 构建库存唯一键
     *
     * @param stock 库存对象
     * @return 唯一键字符串
     */
    private String buildStockKey(WmMaterialStock stock) {
        return String.join("|",
            String.valueOf(stock.getMaterialId()),
            stock.getBatchId() != null ? stock.getBatchId().toString() : "",
            stock.getSnId() != null ? stock.getSnId().toString() : "",
            stock.getWarehouseId() != null ? stock.getWarehouseId().toString() : "",
            stock.getAreaId() != null ? stock.getAreaId().toString() : "",
            stock.getLocationId() != null ? stock.getLocationId().toString() : "",
            stock.getVendorId() != null ? stock.getVendorId().toString() : "",
            stock.getWoId() != null ? stock.getWoId().toString() : "",
            stock.getProductionDate() != null ? stock.getProductionDate().toString() : ""
        );
    }

    /**
     * 处理库存转储事务（虚拟库位转目标库位）
     *
     * @param receive            入库单VO
     * @param stock              库存对象
     * @param transactionQuantity 事务数量
     */
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

        Long actualStockId = processStockTransfer(receive, stock, virtualStock, targetStock, transactionQuantity);
        // F3-a:采购入库按参考价/标准成本计价并回写成本
        BigDecimal inUnitCost = resolveReceiveUnitCost(stock.getMaterialId());
        valuationService.updateStockCost(actualStockId, transactionQuantity, inUnitCost);
        WmMaterialStock actualStock = materialStockMapper.selectById(actualStockId);
        // 回填 TARGET_IN 流水成本(按实际库存成本)
        materialStockTxMapper.update(null, Wrappers.<WmMaterialStockTx>lambdaUpdate()
            .set(WmMaterialStockTx::getUnitCost, actualStock != null ? actualStock.getUnitCost() : inUnitCost)
            .set(WmMaterialStockTx::getTxAmount, actualStock != null ? actualStock.getUnitCost().multiply(transactionQuantity) : inUnitCost.multiply(transactionQuantity))
            .eq(WmMaterialStockTx::getSourceDocId, receive.getReceiveId())
            .eq(WmMaterialStockTx::getTxType, "TARGET_IN"));
        stock.setStockId(actualStockId); // 回写实际的库存ID
    }

    /** 入库计价:物料参考价,其次标准成本,无价格按 0 */
    private BigDecimal resolveReceiveUnitCost(Long materialId) {
        BdMaterialVo material = materialId != null ? bdMaterialService.selectById(materialId) : null;
        if (material == null) {
            return BigDecimal.ZERO;
        }
        if (material.getReferencePrice() != null) {
            return material.getReferencePrice();
        }
        return material.getStandardCost() != null ? material.getStandardCost() : BigDecimal.ZERO;
    }

    /**
     * 执行库存转储（虚拟库位扣减、目标库位增加）
     *
     * @param receive            入库单VO
     * @param stock              库存对象
     * @param virtualStock       虚拟库位库存
     * @param targetStock        目标库位库存
     * @param transactionQuantity 事务数量
     */
    private Long processStockTransfer(WmMaterialReceiveVo receive, WmMaterialStock stock, WmMaterialStock virtualStock,
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
            return targetStock.getStockId();
        } else {
            WmMaterialStock newStock = copyStock(stock);
            newStock.setStockId(null);
            newStock.setStockQuantity(transactionQuantity);
            // 新增库存记录默认未冻结
            newStock.setFrozenFlag(0L);
            newStock.setCreateTime(now);
            newStock.setUpdateTime(now);
            materialStockMapper.insert(newStock);
            recordStockTransaction(receive, stock, 1, transactionQuantity, BigDecimal.ZERO,
                transactionQuantity, newStock.getStockId(), "TARGET_IN");
            return newStock.getStockId();
        }
    }

    /**
     * 创建虚拟库位兜底库存（当虚拟库位库存不存在时）
     *
     * @param stock              库存对象
     * @param transactionQuantity 事务数量
     * @param now                当前时间
     * @return 虚拟库位库存
     */
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

    /**
     * 复制库存查询条件（清除ID、数量和时间字段）
     *
     * @param stock 库存对象
     * @return 查询条件对象
     */
    private WmMaterialStock copyStockCondition(WmMaterialStock stock) {
        WmMaterialStock condition = copyStock(stock);
        condition.setStockId(null);
        condition.setStockQuantity(null);
        condition.setRecptDate(null);
        condition.setCreateTime(null);
        condition.setUpdateTime(null);
        return condition;
    }

    /**
     * 深拷贝库存对象
     *
     * @param source 源库存对象
     * @return 拷贝后的库存对象
     */
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
        // 深拷贝时同步复制序列号信息，避免库存转储时丢失SN追溯
        target.setSnId(source.getSnId());
        target.setSnCode(source.getSnCode());
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
        target.setUnitCost(source.getUnitCost());
        target.setTotalCost(source.getTotalCost());
        target.setAttr1(source.getAttr1());
        target.setAttr2(source.getAttr2());
        target.setAttr3(source.getAttr3());
        target.setAttr4(source.getAttr4());
        target.setRemark(source.getRemark());
        return target;
    }

    /**
     * 根据条件查询单条库存记录
     *
     * @param condition 查询条件
     * @return 库存记录
     */
    private WmMaterialStock selectOneStock(WmMaterialStock condition) {
        LambdaQueryWrapper<WmMaterialStock> lqw = Wrappers.lambdaQuery();
        lqw.eq(condition.getCategoryId() != null, WmMaterialStock::getCategoryId, condition.getCategoryId());
        lqw.eq(WmMaterialStock::getMaterialId, condition.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(condition.getMaterialCode()), WmMaterialStock::getMaterialCode, condition.getMaterialCode());
        lqw.eq(StringUtils.isNotBlank(condition.getMaterialSpec()), WmMaterialStock::getMaterialSpec, condition.getMaterialSpec());
        lqw.eq(StringUtils.isNotBlank(condition.getBaseUomId()), WmMaterialStock::getBaseUomId, condition.getBaseUomId());
        lqw.eq(condition.getBatchId() != null, WmMaterialStock::getBatchId, condition.getBatchId());
        lqw.eq(condition.getSnId() != null, WmMaterialStock::getSnId, condition.getSnId());
        lqw.eq(StringUtils.isNotBlank(condition.getBatchCode()), WmMaterialStock::getBatchCode, condition.getBatchCode());
        lqw.eq(WmMaterialStock::getWarehouseId, condition.getWarehouseId());
        lqw.eq(condition.getAreaId() != null, WmMaterialStock::getAreaId, condition.getAreaId());
        lqw.eq(condition.getLocationId() != null, WmMaterialStock::getLocationId, condition.getLocationId());
        lqw.eq(condition.getVendorId() != null, WmMaterialStock::getVendorId, condition.getVendorId());
        lqw.last("limit 1");
        return materialStockMapper.selectOne(lqw);
    }

    /**
     * 记录库存事务
     *
     * @param receive            入库单VO
     * @param stock              库存对象
     * @param transactionFlag    事务标志（1入库，-1出库）
     * @param transactionQuantity 事务数量
     * @param beforeQuantity     变动前数量
     * @param afterQuantity      变动后数量
     * @param stockId            库存记录ID
     * @param txType             事务类型
     */
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
        stockTx.setSnId(stock.getSnId());
        stockTx.setSnCode(stock.getSnCode());
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
        // F3-a:业务日期取收货单日期(过账落正确期间),空则当前时间
        stockTx.setErpDate(receive.getReceiveDate() != null ? receive.getReceiveDate() : now);
        stockTx.setRecptDate(stock.getRecptDate());
        stockTx.setRemark(stock.getRemark());
        stockTx.setCreateTime(now);
        stockTx.setUpdateTime(now);
        materialStockTxMapper.insert(stockTx);
    }

    /**
     * BigDecimal空值转零
     *
     * @param value 数值
     * @return 非空数值
     */
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
