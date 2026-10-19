package com.nexus.erp.wm.domain;

import java.math.BigDecimal;
/** 由真实单据入口构造；仓储、数量和物料须再次在锁内核对，不能作为客户端事实。 */
public record WmSerialPostingRequest(String sourceType, Long sourceId, Long sourceLineId, String txType,
    Long materialId, BigDecimal quantity, int direction, Long warehouseId, Long locationId, Long batchId,
    BigDecimal unitCost, Long targetWarehouseId, Long targetAreaId, Long targetLocationId,
    Long reserveOrderId, Long reserveLineId, BigDecimal consumeReserved) {
    public static WmSerialPostingRequest out(String type, Long id, Long line, String tx, Long material,
        BigDecimal qty, Long warehouse, Long location, Long batch) {
        return new WmSerialPostingRequest(type,id,line,tx,material,qty,-1,warehouse,location,batch,null,null,null,null,null,null,BigDecimal.ZERO);
    }
    public static WmSerialPostingRequest in(String type, Long id, Long line, String tx, Long material,
        BigDecimal qty, Long warehouse, Long location, Long batch, BigDecimal cost) {
        return new WmSerialPostingRequest(type,id,line,tx,material,qty,1,warehouse,location,batch,cost,null,null,null,null,null,BigDecimal.ZERO);
    }
    public WmSerialPostingRequest transfer(Long warehouse, Long area, Long location) {
        return new WmSerialPostingRequest(sourceType,sourceId,sourceLineId,txType,materialId,quantity,0,warehouseId,locationId,batchId,
            unitCost,warehouse,area,location,null,null,BigDecimal.ZERO);
    }
    public WmSerialPostingRequest reservation(Long order, Long line, BigDecimal qty) {
        return new WmSerialPostingRequest(sourceType,sourceId,sourceLineId,txType,materialId,quantity,direction,warehouseId,locationId,batchId,
            unitCost,targetWarehouseId,targetAreaId,targetLocationId,order,line,qty);
    }
    public long lineKey() { return sourceLineId==null?0:sourceLineId; }
}
