package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.mapper.PrdSubcontractMapper;
import com.nexus.erp.prd.service.IPrdSubcontractClosureService;
import com.nexus.erp.prd.service.IPrdSubcontractService;
import com.nexus.erp.wm.domain.WmSubcontractReceive;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.WmSubcontractReceiveBo;
import com.nexus.erp.wm.domain.vo.WmSubcontractReceiveVo;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmSubcontractReceiveMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IWmSubcontractReceiveService;
import com.nexus.erp.wm.strategy.StockOperationStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 委外收货单 Service 实现 (PCBA 闭环)
 * <p>
 * 创建(默认半成品仓路由) -> 过账(订单维度分布式锁 + CAS 认领 + 策略入库[自动建批次/精确库存行] +
 * actualQty 原子累加 + 达计划量自动完工)。
 * <p>数据源: 收货单与库存均在 ry-erp 库, {@code @DS("erp")}。</p>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmSubcontractReceiveServiceImpl implements IWmSubcontractReceiveService {

    private final WmSubcontractReceiveMapper receiveMapper;
    private final PrdSubcontractMapper subcontractMapper;
    private final WmWarehouseMapper warehouseMapper;
    private final StockOperationStrategyFactory strategyFactory;
    private final IPrdSubcontractClosureService closureService;
    private final LockTemplate lockTemplate;

    @Override
    public List<WmSubcontractReceiveVo> listBySubcontract(Long subcontractId) {
        return receiveMapper.selectVoList(Wrappers.<WmSubcontractReceive>lambdaQuery()
            .eq(WmSubcontractReceive::getSubcontractId, subcontractId)
            .orderByDesc(WmSubcontractReceive::getCreateTime));
    }

    @Override
    public WmSubcontractReceiveVo selectById(Long receiveId) {
        WmSubcontractReceiveVo vo = receiveMapper.selectVoById(receiveId);
        if (vo == null) {
            throw new ServiceException("收货单不存在: " + receiveId);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReceive(WmSubcontractReceiveBo bo) {
        PrdSubcontract subcontract = subcontractMapper.selectById(bo.getSubcontractId());
        if (subcontract == null) {
            throw new ServiceException("委外订单不存在");
        }
        String status = subcontract.getStatus();
        if (!IPrdSubcontractService.STATUS_RELEASED.equals(status)
            && !IPrdSubcontractService.STATUS_IN_PROGRESS.equals(status)) {
            throw new ServiceException("仅已下达/进行中状态可收货, 当前状态: " + status);
        }
        if (bo.getReceivedQty() == null || bo.getReceivedQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("收货数量必须大于0");
        }
        // 累计收货超发校验(与计划量比较, 允许尾差结案由关闭操作处理)
        BigDecimal already = subcontract.getActualQty() == null ? BigDecimal.ZERO : subcontract.getActualQty();
        BigDecimal planned = subcontract.getPlannedQty() == null ? BigDecimal.ZERO : subcontract.getPlannedQty();
        if (already.add(bo.getReceivedQty()).compareTo(planned) > 0) {
            throw new ServiceException("累计收货将超过计划量: 已收"
                + already.stripTrailingZeros().toPlainString() + ", 计划"
                + planned.stripTrailingZeros().toPlainString());
        }

        WmSubcontractReceive receive = MapstructUtils.convert(bo, WmSubcontractReceive.class);
        receive.setReceiveId(null);
        receive.setReceiveNo("WSCR-" + subcontract.getSubcontractNo() + "-"
            + (receiveMapper.selectCount(Wrappers.<WmSubcontractReceive>lambdaQuery()
                .eq(WmSubcontractReceive::getSubcontractId, bo.getSubcontractId())).intValue() + 1));
        receive.setStatus("DRAFT");
        receive.setSubcontractNo(subcontract.getSubcontractNo());
        // 冗余物料/供应商维度(以订单为准, 防前端少传)
        fillOrderDimensions(receive, subcontract);
        // 目标仓默认路由: 显式指定 > 订单配置 > 半成品命名约定
        if (receive.getTargetWarehouseId() == null) {
            receive.setTargetWarehouseId(subcontract.getTargetWarehouseId());
        }
        receiveMapper.insert(receive);
        log.info("委外收货单创建: {} (订单{})", receive.getReceiveNo(), subcontract.getSubcontractNo());
        return receive.getReceiveId();
    }

    private void fillOrderDimensions(WmSubcontractReceive receive, PrdSubcontract subcontract) {
        if (receive.getMaterialId() == null) {
            receive.setMaterialId(subcontract.getMaterialId());
        }
        if (receive.getMaterialCode() == null) {
            receive.setMaterialCode(subcontract.getMaterialCode());
        }
        if (receive.getMaterialName() == null) {
            receive.setMaterialName(subcontract.getMaterialName());
        }
        if (receive.getBaseUomName() == null) {
            receive.setBaseUomName(subcontract.getBaseUomName());
        }
        if (receive.getVendorId() == null) {
            receive.setVendorId(subcontract.getVendorId());
            receive.setVendorCode(subcontract.getVendorCode());
            receive.setVendorName(subcontract.getVendorName());
        }
        // 目标仓名称按仓库主数据回填
        if (receive.getTargetWarehouseId() != null && receive.getTargetWarehouseName() == null) {
            WmWarehouse wh = warehouseMapper.selectById(receive.getTargetWarehouseId());
            if (wh != null) {
                receive.setTargetWarehouseCode(wh.getWarehouseCode());
                receive.setTargetWarehouseName(wh.getWarehouseName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteReceive(Long receiveId) {
        WmSubcontractReceive receive = receiveMapper.selectById(receiveId);
        if (receive == null) {
            return 0;
        }
        if (!"DRAFT".equals(receive.getStatus())) {
            throw new ServiceException("仅草稿状态可删除收货单");
        }
        return receiveMapper.deleteById(receiveId);
    }

    @Override
    public void postReceive(Long receiveId) {
        WmSubcontractReceive receive = receiveMapper.selectById(receiveId);
        if (receive == null) {
            throw new ServiceException("收货单不存在: " + receiveId);
        }
        LockInfo lock = lockTemplate.lock("wm:sc:receive:" + receive.getSubcontractId(), 30000L, 5000L);
        if (lock == null) {
            throw new ServiceException("委外订单收货操作并发冲突, 请稍后重试");
        }
        try {
            SpringUtils.getAopProxy(this).doPostReceive(receiveId);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 过账事务体: CAS 认领 -> 策略入库(自动建批次) -> actualQty 累加 + 自动完工
     */
    @Transactional(rollbackFor = Exception.class)
    public void doPostReceive(Long receiveId) {
        WmSubcontractReceive receive = receiveMapper.selectById(receiveId);
        if (receive == null) {
            throw new ServiceException("收货单不存在");
        }
        // CAS 认领: DRAFT -> POSTED
        int claimed = receiveMapper.update(null, Wrappers.<WmSubcontractReceive>lambdaUpdate()
            .set(WmSubcontractReceive::getStatus, "POSTED")
            .set(WmSubcontractReceive::getReceiveDate, new Date())
            .eq(WmSubcontractReceive::getReceiveId, receiveId)
            .eq(WmSubcontractReceive::getStatus, "DRAFT"));
        if (claimed == 0) {
            log.warn("收货单[{}]已被认领过账, 跳过", receive.getReceiveNo());
            return;
        }

        Date now = new Date();
        // 策略入库: 自动建批次 + findOrCreateStock + 原子加库存 + 流水
        strategyFactory.getStrategy(StockOperationTypeEnum.SUBCONTRACT_RECEIVE).execute(
            receive.getReceiveId(), receive.getReceiveNo(),
            receive.getSubcontractId(), receive.getSubcontractNo(),
            receive.getMaterialId(), receive.getMaterialCode(), receive.getMaterialName(),
            receive.getMaterialSpec(), receive.getBaseUomName(),
            receive.getVendorId(), receive.getVendorCode(), receive.getVendorName(),
            receive.getTargetWarehouseId(), receive.getTargetLocationId(),
            receive.getVendorBatchCode(), receive.getReceivedQty(), now);

        // actualQty 原子累加 + 达量自动完工(同事务)
        closureService.accumulateActualQty(receive.getSubcontractId(), receive.getReceivedQty());
        log.info("委外收货单[{}]过账完成", receive.getReceiveNo());
    }
}
