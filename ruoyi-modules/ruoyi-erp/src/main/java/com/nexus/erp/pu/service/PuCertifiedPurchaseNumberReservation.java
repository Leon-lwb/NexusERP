package com.nexus.erp.pu.service;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** master编号保留不属于ERP ACID；失败允许编号空洞，不能在ERP回调内跨库调用。 */
@Component
public class PuCertifiedPurchaseNumberReservation {
    private final AutoCodeUtil codes;
    public PuCertifiedPurchaseNumberReservation(AutoCodeUtil codes) { this.codes = codes; }
    public String reserveRequisitionNumber() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.getResourceMap().isEmpty())
            throw new ServiceException("采购编号必须在ERP专属事务之外保留");
        String number = codes.genSerialCode("PU_REQUISITION_CODE", null);
        if (number == null || !number.matches("[A-Za-z0-9_-]{1,64}"))
            throw new ServiceException("采购编号规则未返回有效编号");
        return number;
    }
}
