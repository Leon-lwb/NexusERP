package com.nexus.erp.pu;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

class PuCertifiedPurchaseReceiptBatchPresenceTest {
 @Test void batchEntryIsASeparatePublicOwnedRepeatableReadService() throws Exception {
  Class<?> type=Class.forName("com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService");
        assertNotNull(type.getAnnotation(Service.class));
  assertNull(type.getMethod("consumeReceipt",String.class,Long.class).getAnnotation(Transactional.class));
 }
 @Test void receiptRoutingUsesExplicitNativeTenantIdentitiesNotGenericEntityTenantOmission() throws Exception {
  Class<?> type=Class.forName("com.nexus.erp.pu.mapper.PuCertifiedPurchaseReceiptBatchMapper");
        assertTrue(type.isInterface());
        assertNotNull(type.getAnnotation(Mapper.class));
  assertNotNull(type.getMethod("lockCommandsForOrder",String.class,Long.class));
  assertNotNull(type.getMethod("lockAllocationsForOrder",String.class,Long.class));
 }
}
