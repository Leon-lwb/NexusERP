package com.nexus.erp.pu;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
class PuCertifiedPurchaseReceiptBatchPresenceTest {
 @Test void batchEntryIsASeparatePublicOwnedRepeatableReadService() throws Exception {
  Class<?> type=Class.forName("com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService");
  assertNotNull(type.getAnnotation(org.springframework.stereotype.Service.class));
  assertNull(type.getMethod("consumeReceipt",String.class,Long.class).getAnnotation(Transactional.class));
 }
 @Test void receiptRoutingUsesExplicitNativeTenantIdentitiesNotGenericEntityTenantOmission() throws Exception {
  Class<?> type=Class.forName("com.nexus.erp.pu.mapper.PuCertifiedPurchaseReceiptBatchMapper");
  assertTrue(type.isInterface());assertNotNull(type.getAnnotation(org.apache.ibatis.annotations.Mapper.class));
  assertNotNull(type.getMethod("lockCommandsForOrder",String.class,Long.class));
  assertNotNull(type.getMethod("lockAllocationsForOrder",String.class,Long.class));
 }
}
