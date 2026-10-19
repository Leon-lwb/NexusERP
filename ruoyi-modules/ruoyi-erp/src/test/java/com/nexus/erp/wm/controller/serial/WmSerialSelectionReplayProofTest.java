package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.SnItem;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.domain.bo.WmSerialSelectionBo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.trace.ErpTraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实selection与真实逐件凭据校验；历史命令不得退回当前源版本或库存写路径。 */
class WmSerialSelectionReplayProofTest {
    static class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);
        final ErpTraceContext context=mock(ErpTraceContext.class);
        final WmSerialInventoryRepository repository;
        final WmSerialInventoryService inventory;
        final WmSerialSelectionService service;
        final Map<String,Object> group=new HashMap<>();
        final List<WmMaterialStockTx> transactions=new ArrayList<>();
        List<SnItem> frozen=List.of(new SnItem(101L,null,40L,null,50L));
        final WmSerialSelectionBo bo=new WmSerialSelectionBo();
        WmSerialPostingRequest original=WmSerialPostingRequest.in("WM_MATERIAL_RECEIVE",10L,11L,"RECEIVE_IN",20L,BigDecimal.ONE,30L,40L,50L,new BigDecimal("12.3456"));
        @SuppressWarnings({"rawtypes","unchecked"}) Fixture() {
            when(context.tenant()).thenReturn("000001");
            repository=spy(new WmSerialInventoryRepository(jdbc,mock(WmMaterialStockMapper.class),mock(WmMaterialStockTxMapper.class),context,new ObjectMapper()));
            inventory=spy(new WmSerialInventoryService(repository));service=new WmSerialSelectionService(repository,inventory);
            doNothing().when(repository).requireWrite();
            doThrow(new ServiceException("不得读取当前来源")).when(repository).request(anyString(),anyLong(),anyLong());
            doThrow(new ServiceException("不得读取当前版本")).when(repository).sourceHash(any());
            doAnswer(call->frozen).when(repository).items(99L);
            when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
                String sql=call.getArgument(0);Object[] args=SerialAdapterFixture.params(call);
                if(sql.contains("wm_trace_posting_group"))return List.of(Arrays.asList(args).contains("original-command")&&"000001".equals(context.tenant())?new HashMap<>(group):new HashMap<String,Object>());
                if(sql.contains("CONSUME_RESERVE"))return transactions.stream().filter(tx->"CONSUME_RESERVE".equals(tx.getTxType())).map(tx->{Map<String,Object> row=new HashMap<>();row.put("source_doc_id",tx.getSourceDocId());row.put("source_doc_line_id",tx.getSourceDocLineId());return row;}).toList();
                throw new AssertionError("意外查询: "+sql);
            });
            when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->transactions);
            bo.setSourceDocType(original.sourceType());bo.setSourceDocId(10L);bo.setLineId(11L);bo.setCommandId("original-command");bo.setDetailVersion("opaque:original-freeze-v2");bo.setRevision(1);bo.setPolicyVersion(7);bo.setSnItems(frozen);
            group.put("posting_group_id",99L);group.put("tenant_id","000001");group.put("command_id",bo.getCommandId());group.put("source_doc_id",10L);group.put("source_doc_line_id",11L);group.put("detail_version",1L);group.put("policy_version",7L);group.put("source_hash",bo.getDetailVersion());group.put("material_id",20L);group.put("quantity",BigDecimal.ONE);group.put("amount",new BigDecimal("12.3456"));group.put("status","POSTED");refresh();
        }
        void refresh() {
            group.put("source_doc_type",original.sourceType());group.put("tx_type",original.txType());
            group.put("content_hash",inventory.selectionHash(frozen));group.put("fingerprint",inventory.fingerprint(original,7,inventory.selectionHash(frozen)));
            transactions.clear();var tx=new WmMaterialStockTx();tx.setTenantId("000001");tx.setTxId(501L);tx.setMaterialStockId(601L);tx.setSourceDocType(original.sourceType());tx.setSourceDocId(10L);tx.setSourceDocLineId(11L);tx.setMaterialId(20L);tx.setTxType(original.txType());tx.setTxFlag(original.direction());tx.setSnId(101L);tx.setBatchId(50L);tx.setTxQuantity(BigDecimal.ONE);tx.setBeforeQuantity(original.direction()>0?BigDecimal.ZERO:BigDecimal.ONE);tx.setAfterQuantity(original.direction()>0?BigDecimal.ONE:BigDecimal.ZERO);tx.setTxAmount(new BigDecimal("12.3456"));tx.setUnitCost(new BigDecimal("12.3456"));tx.setTrackingVersion(2L);tx.setPostingGroupId(99L);tx.setDelFlag("0");tx.setTxDate(new Date());transactions.add(tx);
        }
        void noEffects() {
            verify(repository,never()).request(anyString(),anyLong(),anyLong());verify(repository,never()).sourceHash(any());verify(repository,never()).stocks(anyLong());verify(repository,never()).group(any());
            verify(inventory,never()).freeze(any(),anyString(),anyLong(),anyLong(),anyList());verify(inventory,never()).post(any());verify(jdbc,never()).update(anyString(),any(Object[].class));
        }
    }
    @Test void originalPostedCommandUsesItsActualImmutableStockProof() {
        var f=new Fixture();assertEquals(99L,f.service.freeze(f.bo));verify(f.repository).verifyProof(any(),any(),eq(f.frozen));f.noEffects();
    }
    @ParameterizedTest @ValueSource(strings={"version","revision","policy","sourceType","sourceId","lineId","selection","fingerprint","persistedItems"})
    void sameCommandWithChangedContentOrDamagedSnapshotCannotReplay(String field) {
        var f=new Fixture();switch(field) {
            case "version"->f.bo.setDetailVersion("forged");case "revision"->f.bo.setRevision(2);case "policy"->f.bo.setPolicyVersion(8);
            case "sourceType"->f.bo.setSourceDocType("WM_MISC_RECPT");case "sourceId"->f.bo.setSourceDocId(12L);case "lineId"->f.bo.setLineId(13L);
            case "selection"->f.bo.setSnItems(List.of(new SnItem(101L,null,41L,null,50L)));
            case "fingerprint"->f.group.put("fingerprint","tampered");case "persistedItems"->f.frozen=List.of(new SnItem(102L,null,40L,null,50L));
        }
        assertThrows(ServiceException.class,()->f.service.freeze(f.bo));f.noEffects();
    }
    @ParameterizedTest @ValueSource(strings={"missing","quantity","amount","tenant"})
    void missingOrChangedOriginalLedgerIsNotAcknowledgedAndNeverReposted(String field) {
        var f=new Fixture();switch(field) {case "missing"->f.transactions.clear();case "quantity"->f.transactions.get(0).setTxQuantity(new BigDecimal("2"));case "amount"->f.transactions.get(0).setTxAmount(BigDecimal.ZERO);case "tenant"->f.transactions.get(0).setTenantId("000002");}
        assertThrows(ServiceException.class,()->f.service.freeze(f.bo));f.noEffects();
    }
    @Test void preparedReplayReturnsOnlyItsExistingFreezeWithoutPretendingItPosted() {
        var f=new Fixture();f.group.put("status","PREPARED");f.transactions.clear();assertEquals(99L,f.service.freeze(f.bo));verify(f.repository,never()).verifyProof(any(),any(),anyList());f.noEffects();
    }
    @Test void olderPartialIssueUsesItsOwnCommandEvenWhenCurrentQuantityAndLatestRevisionChanged() {
        var f=new Fixture();f.original=WmSerialPostingRequest.out("WM_PRODUCTION_ISSUE",10L,11L,"PRODUCTION_ISSUE",20L,BigDecimal.ONE,30L,40L,50L);f.bo.setSourceDocType(f.original.sourceType());f.refresh();assertEquals(99L,f.service.freeze(f.bo));f.noEffects();
    }
    @Test void salesReplayRestoresOriginalReservationSourceFromImmutableFacts() {
        var f=new Fixture();f.original=WmSerialPostingRequest.out("WM_SALES_ISSUE",10L,11L,"SALES_OUT",20L,BigDecimal.ONE,30L,40L,50L).reservation(701L,702L,BigDecimal.ONE);f.bo.setSourceDocType(f.original.sourceType());f.refresh();
        var reserve=new WmMaterialStockTx();reserve.setTxType("CONSUME_RESERVE");reserve.setTxFlag(0);reserve.setSourceDocType("SALES_ORDER");reserve.setSourceDocId(701L);reserve.setSourceDocLineId(702L);reserve.setSnId(101L);reserve.setTxQuantity(BigDecimal.ONE);f.transactions.add(reserve);
        assertEquals(99L,f.service.freeze(f.bo));f.noEffects();
    }
    @Test void unknownCommandStillRequiresCurrentOpaqueVersion() {
        var f=new Fixture();f.bo.setCommandId("new-command");doReturn(f.original).when(f.repository).request(anyString(),anyLong(),anyLong());doReturn("current-v3").when(f.repository).sourceHash(any());
        assertThrows(ServiceException.class,()->f.service.freeze(f.bo));verify(f.inventory,never()).freeze(any(),anyString(),anyLong(),anyLong(),anyList());
        f.bo.setDetailVersion("current-v3");doReturn(100L).when(f.inventory).freeze(any(),anyString(),anyLong(),anyLong(),anyList());assertEquals(100L,f.service.freeze(f.bo));verify(f.inventory).freeze(f.original,"new-command",1,7,f.frozen);
    }
}
