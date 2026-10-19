package com.nexus.erp.wm.domain;

import java.math.BigDecimal;

/**
 * 由真实单据入口构造；仓储、数量和物料须再次在锁内核对，不能作为客户端事实。
 *
 * @param sourceType 由服务端限定的原业务来源类型。
 * @param sourceId 由来源类型限定的原业务记录标识。
 * @param sourceLineId 原业务单据行标识。
 * @param materialId 当前业务行实际物料标识。
 * @param quantity 实际业务数量，不能以物料清单估算替代实际身份或用量。
 * @param warehouseId 业务所属仓库标识。
 * @param locationId 实际作业储位标识。
 * @param batchId 库存批次身份；与制造子批次标识分别保存。
 * @param unitCost 原计价策略确定的单位成本。
 * @param targetWarehouseId 目标仓库标识。
 * @param targetAreaId 目标库区标识。
 * @param targetLocationId 目标储位标识。
 * @param reserveOrderId 原业务预留单标识。
 * @param reserveLineId 原业务预留行标识。
 * @param consumeReserved 实际消费的原业务预留量。
 */
public record WmSerialPostingRequest(
        String sourceType,
        Long sourceId,
        Long sourceLineId,
        String txType,
        Long materialId,
        BigDecimal quantity,
        int direction,
        Long warehouseId,
        Long locationId,
        Long batchId,
        BigDecimal unitCost,
        Long targetWarehouseId,
        Long targetAreaId,
        Long targetLocationId,
        Long reserveOrderId,
        Long reserveLineId,
        BigDecimal consumeReserved) {
    public static WmSerialPostingRequest out(
            String type,
            Long id,
            Long line,
            String tx,
            Long material,
            BigDecimal qty,
            Long warehouse,
            Long location,
            Long batch) {
        return new WmSerialPostingRequest(
                type,
                id,
                line,
                tx,
                material,
                qty,
                -1,
                warehouse,
                location,
                batch,
                null,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO);
    }

    public static WmSerialPostingRequest in(
            String type,
            Long id,
            Long line,
            String tx,
            Long material,
            BigDecimal qty,
            Long warehouse,
            Long location,
            Long batch,
            BigDecimal cost) {
        return new WmSerialPostingRequest(
                type,
                id,
                line,
                tx,
                material,
                qty,
                1,
                warehouse,
                location,
                batch,
                cost,
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO);
    }

    public WmSerialPostingRequest transfer(Long warehouse, Long area, Long location) {
        return new WmSerialPostingRequest(
                sourceType,
                sourceId,
                sourceLineId,
                txType,
                materialId,
                quantity,
                0,
                warehouseId,
                locationId,
                batchId,
                unitCost,
                warehouse,
                area,
                location,
                null,
                null,
                BigDecimal.ZERO);
    }

    public WmSerialPostingRequest reservation(Long order, Long line, BigDecimal qty) {
        return new WmSerialPostingRequest(
                sourceType,
                sourceId,
                sourceLineId,
                txType,
                materialId,
                quantity,
                direction,
                warehouseId,
                locationId,
                batchId,
                unitCost,
                targetWarehouseId,
                targetAreaId,
                targetLocationId,
                order,
                line,
                qty);
    }

    public long lineKey() {
        return sourceLineId == null ? 0 : sourceLineId;
    }
}
