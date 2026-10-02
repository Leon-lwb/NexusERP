package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.ErpPurchaseSourceSchemaMapper;
import com.nexus.erp.pu.mapper.ErpPurchaseSourceSchemaMapper.TableEngine;
import com.nexus.erp.pu.mapper.ErpPurchaseSourceSchemaMapper.NativeQuantityColumn;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 未联网的元数据反例；不将建库脚本的InnoDB声明当成当前实际表已核验。 */
class ErpPurchaseSourceSchemaVerifierTest {
    private final ErpPurchaseSourceSchemaMapper mapper = mock(ErpPurchaseSourceSchemaMapper.class);
    private final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
    private final ErpPurchaseSourceSchemaVerifier verifier = new ErpPurchaseSourceSchemaVerifier(mapper,qualification);

    private void known(String version) {
        when(mapper.pinTableDefinitions()).thenReturn(List.of());
        when(mapper.selectNativeQuantityColumns()).thenReturn(knownColumns());
        when(mapper.selectTableEngines()).thenReturn(ErpPurchaseSourceSchemaVerifier.TABLES.stream()
            .map(name -> new TableEngine(name,"InnoDB")).toList());
        when(mapper.selectServerVersion()).thenReturn(version);
        when(mapper.selectSessionIsolation()).thenReturn("REPEATABLE-READ");
        when(mapper.selectUnsafeGapLockFlag57()).thenReturn(BigDecimal.ZERO);
    }
    private static List<NativeQuantityColumn> knownColumns() {
        Map<String,List<String>> columns=Map.of(
            "bd_material",List.of("safety_stock"),"bd_unit_of_measure",List.of("conversion_rate"),
            "bd_material_vendor",List.of("verified_moq_qty","order_multiple","units_per_pack"),
            "wm_material_stock",List.of("stock_quantity","reserved_quantity"),
            "prd_material_requirement_detail",List.of("required_quantity","issued_quantity"),
            "pu_purchase_requisition_line",List.of("apply_quantity","approved_quantity"),
            "pu_purchase_order_line",List.of("order_quantity","received_quantity","returned_quantity"),
            "pu_requirement_allocation_ledger",List.of("covered_demand_base_qty","occupied_base_qty","fulfilled_base_qty","released_base_qty"),
            "pu_requirement_allocation_event",List.of("occupied_base_before","occupied_base_after"));
        var result=new ArrayList<NativeQuantityColumn>();columns.forEach((table,names)->names.forEach(name->
            result.add(new NativeQuantityColumn(table,name,"decimal",18,"conversion_rate".equals(name)?6:4))));return result;
    }
    @Test void everyMappedQuantityRequiresNativePrecisionEvenOutsideInventory() {
        known("5.7.44");when(mapper.selectNativeQuantityColumns()).thenReturn(knownColumns());
        verifier.requireTransactionalSources();verify(mapper).selectNativeQuantityColumns();
    }
    @Test void floatingOrWrongPrecisionInAnySourceCannotBePromotedByBigDecimalMapping() {
        for (var original:knownColumns()) {
            known("5.7.44");var rows=new ArrayList<>(knownColumns());
            int index=rows.indexOf(original);
            for(var invalid:List.of(
                new NativeQuantityColumn(original.tableName(),original.columnName(),"double",18,original.numericScale()),
                new NativeQuantityColumn(original.tableName(),original.columnName(),"decimal",12,original.numericScale()),
                new NativeQuantityColumn(original.tableName(),original.columnName(),"decimal",18,2))) {
                rows.set(index,invalid);when(mapper.selectNativeQuantityColumns()).thenReturn(rows);
                assertThrows(ServiceException.class,verifier::requireTransactionalSources,original.tableName()+"."+original.columnName());
            }
        }
    }
    @Test void missingDuplicateUnknownQuantityMetadataIsNotAnEmptyCompleteSource() {
        known("5.7.44");var rows=new ArrayList<>(knownColumns());rows.remove(0);
        when(mapper.selectNativeQuantityColumns()).thenReturn(rows);assertThrows(ServiceException.class,verifier::requireTransactionalSources);
        rows=new ArrayList<>(knownColumns());rows.add(rows.get(0));when(mapper.selectNativeQuantityColumns()).thenReturn(rows);
        assertThrows(ServiceException.class,verifier::requireTransactionalSources);
        when(mapper.selectNativeQuantityColumns()).thenReturn(null);assertThrows(ServiceException.class,verifier::requireTransactionalSources);
    }

    @Test void tableDefinitionReadMustPrecedeEngineCertification() {
        known("5.7.44"); when(mapper.pinTableDefinitions()).thenReturn(List.of());
        verifier.requireTransactionalSources();
        var order = inOrder(qualification,mapper);
        order.verify(qualification).requireSourceChainTransaction();
        order.verify(mapper).pinTableDefinitions();
        order.verify(mapper).selectTableEngines();
    }

    @Test void tableDefinitionFailureStopsMetadataBeforeAnyGuardCanWrite() {
        known("5.7.44");when(mapper.pinTableDefinitions()).thenThrow(new IllegalStateException("PRIVATE_MDL_SENTINEL"));
        var error=assertThrows(ServiceException.class,verifier::requireTransactionalSources);
        assertFalse(error.getMessage().contains("PRIVATE_MDL_SENTINEL"));verify(mapper,never()).selectTableEngines();
    }
    @Test void unexpectedDefinitionRowsCannotBeCertifiedAsPinned() {
        known("5.7.44");when(mapper.pinTableDefinitions()).thenReturn(List.of(1L));
        assertThrows(ServiceException.class,verifier::requireTransactionalSources);verify(mapper,never()).selectTableEngines();
    }
    @Test void actualDefinitionSqlReferencesEveryCertifiedTableWithoutBusinessRows() {
        var c=new org.apache.ibatis.session.Configuration();c.addMapper(ErpPurchaseSourceSchemaMapper.class);
        var ms=c.getMappedStatement(ErpPurchaseSourceSchemaMapper.class.getName()+".pinTableDefinitions");
        String sql=ms.getBoundSql(null).getSql().toLowerCase(Locale.ROOT);
        var found=new HashSet<String>();var matches=java.util.regex.Pattern.compile("from ([a-z_]+) where 1=0").matcher(sql);
        while(matches.find()) assertTrue(found.add(matches.group(1)));
        assertEquals(ErpPurchaseSourceSchemaVerifier.TABLES,found);assertTrue(ms.isFlushCacheRequired());assertFalse(ms.isUseCache());
        assertFalse(sql.contains("join")||sql.contains("select *")||sql.contains("#{")||sql.contains("insert")||sql.contains("update"));
    }

    @Test void definitionPinMustBypassServerCacheNotOnlyMyBatisCache() {
        var c=new org.apache.ibatis.session.Configuration();c.addMapper(ErpPurchaseSourceSchemaMapper.class);
        var ms=c.getMappedStatement(ErpPurchaseSourceSchemaMapper.class.getName()+".pinTableDefinitions");
        String sql=ms.getBoundSql(null).getSql().strip().toLowerCase(Locale.ROOT);
        // MySQL 5.7 只允许 UNION 首个 SELECT 指定 SQL_NO_CACHE；不改会话/全局缓存变量。
        assertTrue(sql.startsWith("select sql_no_cache "),"MyBatis flush 不能证明服务端缓存未代答表定义读");
        assertEquals(1,sql.split("sql_no_cache",-1).length-1);
        assertFalse(sql.substring(sql.indexOf("union all")).contains("sql_no_cache"));
    }

    @Test void allObservedTablesAnd57GapLocksAreRequired() {
        known("5.7.44"); assertDoesNotThrow(verifier::requireTransactionalSources);
        verify(qualification).requireSourceChainTransaction(); verify(mapper).selectUnsafeGapLockFlag57();
    }
    @Test void mysql8DoesNotQueryRemoved57Variable() {
        known("8.0.41"); assertDoesNotThrow(verifier::requireTransactionalSources);
        verify(mapper,never()).selectUnsafeGapLockFlag57();
    }
    @Test void missingOrDuplicateTableIsNotAnEmptyReliableSource() {
        known("5.7.44");
        when(mapper.selectTableEngines()).thenReturn(List.of(new TableEngine("bd_material","InnoDB")));
        assertThrows(ServiceException.class,verifier::requireTransactionalSources);
    }
    @Test void anyNonTransactionalOrUnknownEngineRejectsBeforeGuardCanWrite() {
        for (String engine : Arrays.asList(null,"MyISAM","MEMORY")) {
            known("5.7.44");
            var rows = new ArrayList<>(ErpPurchaseSourceSchemaVerifier.TABLES.stream().map(name -> new TableEngine(name,"InnoDB")).toList());
            rows.set(0,new TableEngine(rows.get(0).tableName(),engine)); when(mapper.selectTableEngines()).thenReturn(rows);
            assertThrows(ServiceException.class,verifier::requireTransactionalSources);
        }
    }
    @Test void noSafeGapFlagCannotGrantCompletePool() {
        for (BigDecimal flag : Arrays.asList(null,BigDecimal.ONE,new BigDecimal("2"))) {
            known("5.7.44"); when(mapper.selectUnsafeGapLockFlag57()).thenReturn(flag);
            assertThrows(ServiceException.class,verifier::requireTransactionalSources);
        }
    }
    @Test void actualSessionReadCommittedCannotBeUpgradedByJdbcMarker() {
        known("5.7.44"); when(mapper.selectSessionIsolation()).thenReturn("READ-COMMITTED");
        assertThrows(ServiceException.class,verifier::requireTransactionalSources);
    }
    @Test void unknownVersionOrMariaDbCannotInheritMysqlAssumptions() {
        for (String version : Arrays.asList(null,"5.6.51","5.7.19","5.7.44-MariaDB","unknown")) {
            known(version); assertThrows(ServiceException.class,verifier::requireTransactionalSources);
        }
    }
    @Test void queryFailureHasNoRawSqlConnectionOrCause() {
        known("5.7.44"); when(mapper.selectTableEngines()).thenThrow(new IllegalStateException("PRIVATE_SQL_SENTINEL"));
        var failure = assertThrows(ServiceException.class,verifier::requireTransactionalSources);
        assertFalse(failure.getMessage().contains("PRIVATE_SQL_SENTINEL")); assertNull(failure.getCause());
    }
}
