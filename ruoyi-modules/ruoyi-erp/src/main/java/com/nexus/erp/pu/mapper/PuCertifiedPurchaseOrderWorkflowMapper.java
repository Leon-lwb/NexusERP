package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.*;

/** 显式指定租户的当前读与 CAS 写入；不可变确认记录不提供通用 CRUD。 */
@Mapper
@InterceptorIgnore(tenantLine="true", dataPermission="true")
public interface PuCertifiedPurchaseOrderWorkflowMapper {
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT c.material_id FROM pu_purchase_order o JOIN pu_purchase_requisition r ON r.tenant_id=o.tenant_id AND r.requisition_id=o.requisition_id "
        +"JOIN pu_certified_purchase_command c ON c.tenant_id=r.tenant_id AND c.request_key=r.decision_idempotency_key AND c.requisition_id=r.requisition_id "
        +"WHERE o.tenant_id=#{tenantId} AND o.order_id=#{id} AND c.command_status='COMMITTED'")
    Long findMaterial(@Param("tenantId") String tenantId,@Param("id") Long orderId);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,order_id,order_no,requisition_id,vendor_id,status,IFNULL(certified_version,0) AS version,certified_terms_id AS quote_id,"
        +"DATE_FORMAT(expected_arrival_date,'%Y-%m-%d') AS expected_arrival_date,del_flag,select_flow_id,currency,exchange_rate,total_amount,"
        +"amount_excluding_tax,tax_amount,discount_amount,freight_amount,discount_rate,payment_terms,delivery_terms "
        +"FROM pu_purchase_order WHERE tenant_id=#{tenantId} AND order_id=#{id} FOR UPDATE")
    Head lockHead(@Param("tenantId") String tenantId,@Param("id") Long id);

    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT tenant_id,line_id,order_id,unit_price,unit_price_excluding_tax,tax_rate,line_amount,line_amount_excluding_tax,line_tax_amount,invoiced_qty "
        +"FROM pu_purchase_order_line WHERE tenant_id=#{tenantId} AND order_id=#{id} ORDER BY line_id FOR UPDATE")
    List<LineMoney> lockMoneyLines(@Param("tenantId") String tenantId,@Param("id") Long id);

    String QUOTE_COLUMNS="quote_id,tenant_id,order_id,quote_version,request_key,request_hash,entered_by,entered_at,vendor_id,material_id,base_uom_id,"
        +"source_request_key,source_fingerprint,catalog_fingerprint,order_fingerprint,quantity,unit_price_excluding_tax,tax_rate,currency,exchange_rate,"
        +"DATE_FORMAT(promised_arrival_date,'%Y-%m-%d') AS promised_arrival_date,DATE_FORMAT(valid_from,'%Y-%m-%d') AS valid_from,"
        +"DATE_FORMAT(valid_until,'%Y-%m-%d') AS valid_until,supplier_quote_reference,supplier_contact,evidence_reference,evidence_sha256,"
        +"payment_terms,delivery_terms,rounding_policy,quote_hash";
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+QUOTE_COLUMNS+" FROM pu_certified_purchase_order_terms WHERE tenant_id=#{tenantId} AND quote_id=#{id} FOR UPDATE")
    Quote lockQuote(@Param("tenantId") String tenantId,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+QUOTE_COLUMNS+" FROM pu_certified_purchase_order_terms WHERE tenant_id=#{tenantId} AND request_key=#{key} FOR UPDATE")
    Quote lockQuoteRequest(@Param("tenantId") String tenantId,@Param("key") String key);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT quote_version FROM pu_certified_purchase_order_terms WHERE tenant_id=#{tenantId} AND order_id=#{id} ORDER BY quote_version FOR UPDATE")
    List<Integer> lockQuoteVersions(@Param("tenantId") String tenantId,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT DATE_FORMAT(CURRENT_DATE(),'%Y-%m-%d')")
    String databaseDate();

    @Insert("INSERT INTO pu_certified_purchase_order_terms (quote_id,tenant_id,order_id,quote_version,request_key,request_hash,entered_by,entered_at,"
        +"vendor_id,material_id,base_uom_id,source_request_key,source_fingerprint,catalog_fingerprint,order_fingerprint,quantity,"
        +"unit_price_excluding_tax,tax_rate,currency,exchange_rate,promised_arrival_date,valid_from,valid_until,supplier_quote_reference,"
        +"supplier_contact,evidence_reference,evidence_sha256,payment_terms,delivery_terms,rounding_policy,quote_hash) VALUES ("
        +"#{q.quoteId},#{q.tenantId},#{q.orderId},#{q.quoteVersion},#{q.requestKey},#{q.requestHash},#{q.enteredBy},#{q.enteredAt},"
        +"#{q.vendorId},#{q.materialId},#{q.baseUomId},#{q.sourceRequestKey},#{q.sourceFingerprint},#{q.catalogFingerprint},#{q.orderFingerprint},"
        +"#{q.quantity},#{q.unitPriceExcludingTax},#{q.taxRate},#{q.currency},#{q.exchangeRate},#{q.promisedArrivalDate},#{q.validFrom},#{q.validUntil},"
        +"#{q.supplierQuoteReference},#{q.supplierContact},#{q.evidenceReference},#{q.evidenceSha256},#{q.paymentTerms},#{q.deliveryTerms},#{q.roundingPolicy},#{q.quoteHash})")
    int insertQuote(@Param("q") Quote quote);

    String CONFIRMATION_COLUMNS="confirmation_id,tenant_id,quote_id,quote_hash,request_key,request_hash,actor_id,permission_code,confirmed_at,review_note";
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+CONFIRMATION_COLUMNS+" FROM pu_certified_purchase_order_terms_confirmation WHERE tenant_id=#{tenantId} AND quote_id=#{id} FOR UPDATE")
    Confirmation lockConfirmation(@Param("tenantId") String tenantId,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+CONFIRMATION_COLUMNS+" FROM pu_certified_purchase_order_terms_confirmation WHERE tenant_id=#{tenantId} AND request_key=#{key} FOR UPDATE")
    Confirmation lockConfirmationRequest(@Param("tenantId") String tenantId,@Param("key") String key);
    @Insert("INSERT INTO pu_certified_purchase_order_terms_confirmation ("+CONFIRMATION_COLUMNS+") VALUES ("
        +"#{c.confirmationId},#{c.tenantId},#{c.quoteId},#{c.quoteHash},#{c.requestKey},#{c.requestHash},#{c.actorId},#{c.permissionCode},#{c.confirmedAt},#{c.reviewNote})")
    int insertConfirmation(@Param("c") Confirmation confirmation);

    /** 对真实订单头的状态/版本/供应商执行 CAS；现有 Flowable/配置化/旧路径均不得绕过门禁。 */
    @Update("UPDATE pu_purchase_order SET status='APPROVED',certified_version=IFNULL(certified_version,0)+1,certified_terms_id=#{q.quoteId},"
        +"expected_arrival_date=#{q.promisedArrivalDate},currency=#{q.currency},exchange_rate=#{q.exchangeRate},"
        +"total_amount=#{total.gross},amount_excluding_tax=#{total.net},tax_amount=#{total.tax},discount_amount=0,freight_amount=0,discount_rate=0,"
        +"payment_terms=#{q.paymentTerms},delivery_terms=#{q.deliveryTerms},update_by=#{actorId},update_time=NOW() "
        +"WHERE tenant_id=#{tenantId} AND order_id=#{old.orderId} AND requisition_id=#{old.requisitionId} AND vendor_id=#{old.vendorId} "
        +"AND status='DRAFT' AND IFNULL(certified_version,0)=#{old.version} AND certified_terms_id IS NULL AND del_flag='0' AND select_flow_id IS NULL")
    int approveHead(@Param("tenantId") String tenantId,@Param("old") Head old,@Param("q") Quote quote,@Param("total") Totals total,@Param("actorId") Long actor);
    @Update("UPDATE pu_purchase_order_line SET unit_price=#{price.grossUnit},unit_price_excluding_tax=#{price.netUnit},tax_rate=#{price.rate},"
        +"line_amount=#{price.gross},line_amount_excluding_tax=#{price.net},line_tax_amount=#{price.tax},update_by=#{actorId},update_time=NOW() "
        +"WHERE tenant_id=#{tenantId} AND order_id=#{old.orderId} AND line_id=#{old.lineId} AND requisition_line_id=#{old.requisitionLineId} "
        +"AND material_id=#{old.materialId} AND unit=#{old.unit} AND order_quantity=#{old.orderQuantity} "
        +"AND received_quantity=0 AND returned_quantity=0 AND receive_status='PENDING' AND IFNULL(invoiced_qty,0)=0 AND del_flag='0'")
    int priceLine(@Param("tenantId") String tenantId,@Param("old") PuCertifiedPurchaseLifecycleMapper.OrderLine old,
        @Param("price") LinePrice price,@Param("actorId") Long actor);
    @Update("UPDATE pu_purchase_order SET status=#{next},certified_version=IFNULL(certified_version,0)+1,update_by=#{actorId},update_time=NOW() "
        +"WHERE tenant_id=#{tenantId} AND order_id=#{old.orderId} AND requisition_id=#{old.requisitionId} AND vendor_id=#{old.vendorId} "
        +"AND status=#{old.status} AND IFNULL(certified_version,0)=#{old.version} AND del_flag='0' AND select_flow_id IS NULL")
    int closeHead(@Param("tenantId") String tenantId,@Param("old") Head old,@Param("next") String next,@Param("actorId") Long actor);

    String OPERATION_COLUMNS="command_id,tenant_id,order_id,request_key,request_hash,actor_id,operation,expected_version,result_version,result_status,"
        +"quote_id,quote_hash,DATE_FORMAT(expected_arrival_date,'%Y-%m-%d') AS expected_arrival_date,total_amount,amount_excluding_tax,tax_amount,result_fingerprint,occurred_at";
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+OPERATION_COLUMNS+" FROM pu_certified_purchase_order_operation WHERE tenant_id=#{tenantId} AND request_key=#{key} FOR UPDATE")
    OperationRecord lockOperation(@Param("tenantId") String tenantId,@Param("key") String key);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+OPERATION_COLUMNS+" FROM pu_certified_purchase_order_operation WHERE tenant_id=#{tenantId} AND order_id=#{id} AND operation='APPROVE_ORDER' FOR UPDATE")
    OperationRecord lockApprovalOperation(@Param("tenantId") String tenantId,@Param("id") Long id);
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    @Select("SELECT "+OPERATION_COLUMNS+" FROM pu_certified_purchase_order_operation WHERE tenant_id=#{tenantId} AND order_id=#{id} AND operation IN ('CLOSE_ORDER','CANCEL_ORDER') FOR UPDATE")
    OperationRecord lockTerminalOperation(@Param("tenantId") String tenantId,@Param("id") Long id);
    @Insert("INSERT INTO pu_certified_purchase_order_operation (command_id,tenant_id,order_id,request_key,request_hash,actor_id,operation,expected_version,result_version,"
        +"result_status,quote_id,quote_hash,expected_arrival_date,total_amount,amount_excluding_tax,tax_amount,result_fingerprint,occurred_at) VALUES ("
        +"#{r.commandId},#{r.tenantId},#{r.orderId},#{r.requestKey},#{r.requestHash},#{r.actorId},#{r.operation},#{r.expectedVersion},#{r.resultVersion},"
        +"#{r.resultStatus},#{r.quoteId},#{r.quoteHash},#{r.expectedArrivalDate},#{r.totalAmount},#{r.amountExcludingTax},#{r.taxAmount},#{r.resultFingerprint},#{r.occurredAt})")
    int insertOperation(@Param("r") OperationRecord result);

    record Head(String tenantId,Long orderId,String orderNo,Long requisitionId,Long vendorId,String status,Integer version,Long quoteId,
        String expectedArrivalDate,String delFlag,Long selectFlowId,String currency,BigDecimal exchangeRate,BigDecimal totalAmount,
        BigDecimal amountExcludingTax,BigDecimal taxAmount,BigDecimal discountAmount,BigDecimal freightAmount,BigDecimal discountRate,String paymentTerms,String deliveryTerms) { }
    record LineMoney(String tenantId,Long lineId,Long orderId,BigDecimal unitPrice,BigDecimal unitPriceExcludingTax,BigDecimal taxRate,
        BigDecimal lineAmount,BigDecimal lineAmountExcludingTax,BigDecimal lineTaxAmount,BigDecimal invoicedQty) { }
    record Totals(BigDecimal net,BigDecimal tax,BigDecimal gross) { }
    record LinePrice(BigDecimal netUnit,BigDecimal grossUnit,BigDecimal rate,BigDecimal net,BigDecimal tax,BigDecimal gross) { }
    record Quote(Long quoteId,String tenantId,Long orderId,Integer quoteVersion,String requestKey,String requestHash,Long enteredBy,Date enteredAt,
        Long vendorId,Long materialId,Long baseUomId,String sourceRequestKey,String sourceFingerprint,String catalogFingerprint,String orderFingerprint,
        BigDecimal quantity,BigDecimal unitPriceExcludingTax,BigDecimal taxRate,String currency,BigDecimal exchangeRate,String promisedArrivalDate,
        String validFrom,String validUntil,String supplierQuoteReference,String supplierContact,String evidenceReference,String evidenceSha256,
        String paymentTerms,String deliveryTerms,String roundingPolicy,String quoteHash) {
        public Quote {enteredAt=enteredAt==null?null:new Date(enteredAt.getTime());}
        @Override public Date enteredAt(){return enteredAt==null?null:new Date(enteredAt.getTime());}
    }
    record Confirmation(Long confirmationId,String tenantId,Long quoteId,String quoteHash,String requestKey,String requestHash,Long actorId,
        String permissionCode,Date confirmedAt,String reviewNote) {
        public Confirmation {confirmedAt=confirmedAt==null?null:new Date(confirmedAt.getTime());}
        @Override public Date confirmedAt(){return confirmedAt==null?null:new Date(confirmedAt.getTime());}
    }
    record OperationRecord(Long commandId,String tenantId,Long orderId,String requestKey,String requestHash,Long actorId,String operation,Integer expectedVersion,
        Integer resultVersion,String resultStatus,Long quoteId,String quoteHash,String expectedArrivalDate,BigDecimal totalAmount,BigDecimal amountExcludingTax,
        BigDecimal taxAmount,String resultFingerprint,Date occurredAt) {
        public OperationRecord {occurredAt=occurredAt==null?null:new Date(occurredAt.getTime());}
        @Override public Date occurredAt(){return occurredAt==null?null:new Date(occurredAt.getTime());}
    }
}
