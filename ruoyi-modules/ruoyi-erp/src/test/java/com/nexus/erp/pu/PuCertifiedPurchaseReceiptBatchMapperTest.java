package com.nexus.erp.pu;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseReceiptBatchMapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
class PuCertifiedPurchaseReceiptBatchMapperTest {
 @Test void everyRoutingReadIsExplicitlyTenantBoundAndUncached() {
  for(var method:PuCertifiedPurchaseReceiptBatchMapper.class.getDeclaredMethods()) {
   Select select=method.getAnnotation(Select.class);assertNotNull(select,method.getName());
   String sql=String.join(" ",select.value()).toLowerCase(Locale.ROOT);
   assertTrue(sql.contains("tenant_id"));assertTrue(sql.contains("#{tenantid}"));assertFalse(sql.contains("${"));
   assertFalse(sql.contains("cast("));assertFalse(sql.contains("trim("));assertFalse(sql.contains("coalesce("));
   Options options=method.getAnnotation(Options.class);assertNotNull(options);assertFalse(options.useCache());
   assertEquals(Options.FlushCachePolicy.TRUE,options.flushCache());
   assertEquals(method.getName().startsWith("lock"),sql.contains("for update"),method.getName());
  }
 }
 @Test void permanentRouteEvidenceIsNotFilteredByMarkerStatusOrDeletion() throws Exception {
  for(String name:new String[]{"lockCommandsForOrder","lockAllocationsForOrder"}) {
   String sql=String.join(" ",PuCertifiedPurchaseReceiptBatchMapper.class.getMethod(name,String.class,Long.class).getAnnotation(Select.class).value());
   assertFalse(sql.contains("del_flag='0'"));assertFalse(sql.contains("command_status='COMMITTED'"));
   assertTrue(sql.contains("requisition_id"));assertTrue(sql.contains("order_line_id"));assertTrue(sql.contains("request_key")||sql.contains("decision_idempotency_key"));
  }
 }
 @Test void routingReadsNativeIdentityAndNeverReliesOnPayloadQuantity() throws Exception {
  String sql=String.join(" ",PuCertifiedPurchaseReceiptBatchMapper.class.getMethod("lockReceiptLines",String.class,Long.class).getAnnotation(Select.class).value());
  assertTrue(sql.contains("line_id"));assertTrue(sql.contains("purchase_line_id"));assertTrue(sql.contains("base_uom_id"));assertTrue(sql.contains("quantity_received"));
 }
}
