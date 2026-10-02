package com.nexus.erp.pu.service;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
/** Master numbering is outside the ERP callback; unused reserved numbers may leave gaps. */
@Component
@DS("master")
public class PuCertifiedPurchaseWorkflowNumberReservation {
    private final AutoCodeUtil codes;
    public PuCertifiedPurchaseWorkflowNumberReservation(AutoCodeUtil codes) {this.codes=codes;}
    public String reserveOrderNumber() {
        if(TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isSynchronizationActive()
            ||!TransactionSynchronizationManager.getResourceMap().isEmpty()||TransactionContext.getXID()!=null)
            throw new ServiceException("订单编号必须在ERP专属事务之外保留");
        String number=codes.genSerialCode("PU_ORDER_CODE",null);
        if(number==null||!number.matches("[A-Za-z0-9_-]{1,64}")) throw new ServiceException("采购订单编号规则未返回有效编号");
        return number;
    }
}
