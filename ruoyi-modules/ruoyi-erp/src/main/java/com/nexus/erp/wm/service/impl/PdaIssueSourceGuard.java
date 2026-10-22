package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.mapper.PdaIssueSourceMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

// 同名类型说明：本文件同时使用 java.util.Date 和 java.sql.Date，后者保留全限定名以区分。

/** PDA 领料的只读来源门禁；所有核验完成前不修改领料行或调用扣库。 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class PdaIssueSourceGuard {
    private final PdaIssueSourceMapper mapper;
    private final DataSource dataSource;

    /** 在调用方事务中锁定唯一真实来源，已有标识或编码只能核验，不能被扫码改换。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class,
        noRollbackFor = SourceRejectedException.class)
    public Source resolve(String tenantId, WmProductionIssue head, WmProductionIssueLine line, PdaPickBo request) {
        requireOriginalRepeatableRead();
        java.sql.Date databaseToday = mapper.currentDatabaseDate();
        require(databaseToday != null, "数据库当前日期未知，不能确认来源效期");
        Long warehouseId = head.getWarehouseId();
        require(positive(warehouseId) && (line.getWarehouseId() == null || warehouseId.equals(line.getWarehouseId())),
            "领料行与有效头仓库不一致");
        WmWarehouse warehouse = mapper.lockWarehouse(tenantId, warehouseId);
        require(warehouse != null && tenantId.equals(warehouse.getTenantId()) && "0".equals(warehouse.getDelFlag())
            && warehouseId.equals(warehouse.getWarehouseId()) && Long.valueOf(1L).equals(warehouse.getStatus())
            && StringUtils.isNotBlank(warehouse.getWarehouseCode())
            && matchesIfPresent(head.getWarehouseCode(), warehouse.getWarehouseCode())
            && matchesIfPresent(line.getWarehouseCode(), warehouse.getWarehouseCode()), "领料仓库不存在、未启用或身份不符");

        require(positive(line.getMaterialId()), "领料物料未知");
        BdMaterial material = mapper.lockMaterial(tenantId, line.getMaterialId());
        require(material != null && tenantId.equals(material.getTenantId()) && "0".equals(material.getDelFlag())
            && line.getMaterialId().equals(material.getMaterialId()) && Long.valueOf(1L).equals(material.getStatus())
            && (Long.valueOf(0L).equals(material.getIsBatchManage()) || Long.valueOf(1L).equals(material.getIsBatchManage())),
            "物料不存在、未启用或批次管理属性未知");

        String locationCode = code(request.getLocationCode());
        require(locationCode == null || matchesIfPresent(line.getLocationCode(), locationCode), "扫码库位与已分配库位不一致");
        if (locationCode == null) {
            locationCode = code(line.getLocationCode());
        }
        require(locationCode != null || positive(line.getLocationId()), "未分配库位时必须扫码真实库位");
        List<WmStorageLocation> locations = mapper.lockLocations(tenantId, warehouseId, locationCode, line.getLocationId());
        require(locations != null && locations.size() == 1, "库位不存在或编码不唯一");
        WmStorageLocation location = locations.get(0);
        require(location != null && positive(location.getLocationId()) && tenantId.equals(location.getTenantId())
            && warehouseId.equals(location.getWarehouseId()) && "0".equals(location.getDelFlag())
            && Long.valueOf(1L).equals(location.getEnableFlag()) && Long.valueOf(0L).equals(location.getFrozenFlag())
            && StringUtils.isNotBlank(location.getLocationCode())
            && (line.getLocationId() == null || line.getLocationId().equals(location.getLocationId()))
            && (locationCode == null || locationCode.equals(location.getLocationCode()))
            && matchesIfPresent(line.getLocationCode(), location.getLocationCode()), "库位失效、未启用、冻结、跨仓库或与已分配身份不符");

        require(positive(location.getAreaId()), "库位缺少真实父库区");
        WmStorageArea area = mapper.lockArea(tenantId, location.getAreaId());
        require(area != null && tenantId.equals(area.getTenantId()) && "0".equals(area.getDelFlag())
            && location.getAreaId().equals(area.getAreaId()) && warehouseId.equals(area.getWarehouseId())
            && Long.valueOf(1L).equals(area.getEnableFlag()) && Long.valueOf(0L).equals(area.getFrozenFlag()),
            "父库区不存在、未启用、冻结或租户/仓库父链不符");

        WmBatch batch = resolveBatch(tenantId, warehouseId, line, request, material, databaseToday);
        Long batchId = batch != null ? batch.getBatchId() : null;
        List<WmMaterialStock> stocks = mapper.lockStocks(tenantId, line.getMaterialId(), warehouseId, location.getLocationId(), batchId);
        require(stocks != null && stocks.size() == 1, "真实库存不存在或执行范围内来源不唯一");
        WmMaterialStock stock = stocks.get(0);
        require(stock != null && positive(stock.getStockId()) && tenantId.equals(stock.getTenantId())
            && line.getMaterialId().equals(stock.getMaterialId()) && warehouseId.equals(stock.getWarehouseId())
            && location.getLocationId().equals(stock.getLocationId()) && area.getAreaId().equals(stock.getAreaId())
            && "0".equals(stock.getDelFlag())
            && matchesIfPresent(stock.getAreaCode(), area.getAreaCode())
            && matchesIfPresent(stock.getWarehouseCode(), warehouse.getWarehouseCode())
            && matchesIfPresent(stock.getLocationCode(), location.getLocationCode()), "真实库存租户、物料、仓储或有效性不符");
        if (batch != null) {
            require(batch.getBatchId().equals(stock.getBatchId()) && batch.getBatchCode().equals(stock.getBatchCode()),
                "真实库存批次标识与编码不一致");
        } else {
            require(stock.getBatchId() == null && StringUtils.isBlank(stock.getBatchCode()),
                "非批次来源范围内存在真实批次，不能按空码放行");
        }
        require(Long.valueOf(0L).equals(stock.getFrozenFlag()), "真实库存已冻结或冻结状态未知");
        require(!expired(stock.getExpireDate(), databaseToday), "真实库存已过期");
        BigDecimal quantity = stock.getStockQuantity();
        BigDecimal reserved = stock.getReservedQuantity();
        require(quantity != null && reserved != null && quantity.signum() >= 0 && reserved.signum() >= 0,
            "库存或预留数量未知、无效");
        require(quantity.subtract(reserved).compareTo(request.getQty()) >= 0, "真实库存可用量不足或已全预留");
        return new Source(warehouse, location, batch, stock);
    }

    /**
     * 批次和非批次均依赖原连接的范围锁，不能只凭 Spring 隔离元数据或另借连接判断。
     * DEFAULT 元数据为空时仍核验已绑定物理连接；较弱、未知或无原事务连接均拒绝。
     */
    private void requireOriginalRepeatableRead() {
        require(TransactionSynchronizationManager.isActualTransactionActive(), "来源核验需要原 ERP 事务");
        Object resource = TransactionSynchronizationManager.getResource(dataSource);
        require(resource instanceof ConnectionHolder, "原 ERP 事务连接未知，不能核验来源范围");
        try {
            Connection connection = ((ConnectionHolder) resource).getConnection();
            require(!connection.getAutoCommit()
                && connection.getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ,
                "来源核验需要原可重复读事务，实际连接隔离条件未知或不符");
        } catch (SQLException | IllegalStateException unavailable) {
            throw new SourceRejectedException("无法确认原 ERP 事务连接的实际隔离条件");
        }
    }

    private WmBatch resolveBatch(String tenantId, Long warehouseId, WmProductionIssueLine line,
                                 PdaPickBo request, BdMaterial material, java.sql.Date databaseToday) {
        String batchCode = code(request.getBatchCode());
        require(batchCode == null || matchesIfPresent(line.getBatchCode(), batchCode), "扫码批次与已分配批次不一致");
        if (batchCode == null) {
            batchCode = code(line.getBatchCode());
        }
        require(line.getBatchId() == null || positive(line.getBatchId()), "已分配批次标识无效，零标识不能冒充非批次来源");
        if (batchCode == null && positive(line.getBatchId())) {
            WmBatch allocated = mapper.lockBatchById(tenantId, line.getBatchId());
            require(allocated != null && line.getBatchId().equals(allocated.getBatchId())
                && tenantId.equals(allocated.getTenantId()) && StringUtils.isNotBlank(allocated.getBatchCode()), "已分配批次不存在或身份未知");
            batchCode = allocated.getBatchCode();
        }
        if (batchCode == null) {
            require(Long.valueOf(0L).equals(material.getIsBatchManage()), "批次管理物料缺少明确批次来源");
            return null;
        }
        List<WmBatch> batches = mapper.lockBatchesByCode(tenantId, batchCode);
        require(batches != null && batches.size() == 1, "批次不存在或编码不唯一");
        WmBatch batch = batches.get(0);
        require(batch != null && positive(batch.getBatchId()) && tenantId.equals(batch.getTenantId())
            && line.getMaterialId().equals(batch.getMaterialId()) && warehouseId.equals(batch.getWarehouseId())
            && "0".equals(batch.getDelFlag()) && "ACTIVE".equals(batch.getStatus())
            && batchCode.equals(batch.getBatchCode()) && (line.getBatchId() == null || line.getBatchId().equals(batch.getBatchId())),
            "批次失效、租户/物料/仓库不符或与已分配身份不一致");
        require(!expired(batch.getExpiryDate(), databaseToday), "批次已过期");
        return batch;
    }

    private static String code(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }
    private static boolean matchesIfPresent(String allocated, String actual) {
        return StringUtils.isBlank(allocated) || Objects.equals(allocated, actual);
    }
    private static boolean positive(Long id) {
        return id != null && id > 0;
    }
    private static boolean expired(Date date, java.sql.Date databaseToday) {
        // JDBC 日期均来自同一 ERP 连接；以其 CURDATE 午夜为边界，不读取系统当日或自行改时区。
        return date != null && date.before(databaseToday);
    }
    private static void require(boolean allowed, String reason) {
        if (!allowed) {
            throw new SourceRejectedException(reason);
        }
    }

    /** 仅用于未写入阶段的业务拒绝；持久化故障不能使用此类型或被吞掉。 */
    public static final class SourceRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SourceRejectedException(String reason) {
            super(reason);
        }
    }

    /** 核验后的锁内来源；编码取真实主数据，不使用扫码文本拼造身份。 */
    public record Source(WmWarehouse warehouse, WmStorageLocation location, WmBatch batch, WmMaterialStock stock) {
        public void bindTo(WmProductionIssueLine line) {
            line.setWarehouseId(warehouse.getWarehouseId());
            line.setWarehouseCode(warehouse.getWarehouseCode());
            line.setWarehouseName(warehouse.getWarehouseName());
            line.setLocationId(location.getLocationId());
            line.setLocationCode(location.getLocationCode());
            line.setLocationName(location.getLocationName());
            line.setBatchId(batch != null ? batch.getBatchId() : stock.getBatchId());
            line.setBatchCode(batch != null ? batch.getBatchCode() : null);
        }
    }
}
