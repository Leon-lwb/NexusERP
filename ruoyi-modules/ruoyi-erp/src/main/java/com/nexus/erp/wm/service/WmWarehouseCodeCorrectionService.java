package com.nexus.erp.wm.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.WmWarehouseCodeCorrectionBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * ERP 仓库编码全量纠正服务。
 * <p>主数据使用当前编码 CAS 更新，成功后在同一 ERP 事务中同步当前库存和库存流水的仓库编码快照。
 * 本服务不改写库存数量、成本、业务状态、来源单据或事务时间。</p>
 *
 * @author Nexus
 */
@Slf4j
@Service
@DS("erp")
@RequiredArgsConstructor
public class WmWarehouseCodeCorrectionService {

    private static final String RULE_CODE = "WAREHOUSE_CODE";

    private final WmWarehouseMapper warehouseMapper;
    private final WmMaterialStockMapper materialStockMapper;
    private final WmMaterialStockTxMapper materialStockTxMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 重新生成并全量同步指定仓库的编码。
     *
     * @param warehouseId 仓库ID
     * @param bo          当前编码及纠正原因
     * @return 新生成的仓库编码
     */
    @Lock4j(keys = {"#warehouseId"})
    @Transactional(rollbackFor = Exception.class)
    public String correct(Long warehouseId, WmWarehouseCodeCorrectionBo bo) {
        validateRequest(warehouseId, bo);
        WmWarehouse current = warehouseMapper.selectById(warehouseId);
        if (current == null) {
            throw new ServiceException("仓库不存在: " + warehouseId);
        }
        if (!Objects.equals(current.getWarehouseCode(), bo.getExpectedCurrentCode())) {
            throw new ServiceException("仓库编码已变化，请刷新后重新核对");
        }

        String newCode = autoCodeUtil.genSerialCode(RULE_CODE, null);
        WmWarehouse master = new WmWarehouse();
        master.setWarehouseCode(newCode);
        int rows = warehouseMapper.update(master, Wrappers.<WmWarehouse>lambdaUpdate()
            .eq(WmWarehouse::getWarehouseId, warehouseId)
            .eq(WmWarehouse::getWarehouseCode, bo.getExpectedCurrentCode()));
        if (rows != 1) {
            throw new ServiceException("仓库编码已被其他操作修改，本次纠正未生效");
        }

        WmMaterialStock stock = new WmMaterialStock();
        stock.setWarehouseCode(newCode);
        materialStockMapper.update(stock, Wrappers.<WmMaterialStock>lambdaUpdate()
            .eq(WmMaterialStock::getWarehouseId, warehouseId));

        WmMaterialStockTx transaction = new WmMaterialStockTx();
        transaction.setWarehouseCode(newCode);
        materialStockTxMapper.update(transaction, Wrappers.<WmMaterialStockTx>lambdaUpdate()
            .eq(WmMaterialStockTx::getWarehouseId, warehouseId));

        log.info("仓库编码全量纠正完成: warehouseId={}, oldCode={}, newCode={}",
            warehouseId, current.getWarehouseCode(), newCode);
        return newCode;
    }

    private void validateRequest(Long warehouseId, WmWarehouseCodeCorrectionBo bo) {
        if (warehouseId == null) {
            throw new ServiceException("仓库ID不能为空");
        }
        if (bo == null || StringUtils.isBlank(bo.getExpectedCurrentCode())) {
            throw new ServiceException("当前仓库编码不能为空");
        }
        if (StringUtils.isBlank(bo.getReason())) {
            throw new ServiceException("编码纠正原因不能为空");
        }
    }
}
