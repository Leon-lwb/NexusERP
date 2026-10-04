package com.nexus.erp.ai.adapter;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 出生创建实际服务接线反例；不访问四个本地测试库。 */
class ErpCertifiedSourceBirthServiceTest {
    static final String T="000000";
    static final long NOW=1791086400000L;
    final ErpCertifiedSourceBirthMapper mapper=mock(ErpCertifiedSourceBirthMapper.class);
    final PuRequirementErpTransactionExecutor transactions=mock(PuRequirementErpTransactionExecutor.class);
    final PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
    final ErpCertifiedSourceBirthReader reader=mock(ErpCertifiedSourceBirthReader.class);
    ErpCertifiedSourceBirthService service;
    MockedStatic<StpUtil> auth;
    MockedStatic<LoginHelper> users;
    MockedStatic<TenantHelper> tenants;
    @BeforeEach void setup(){
        auth=mockStatic(StpUtil.class);users=mockStatic(LoginHelper.class);tenants=mockStatic(TenantHelper.class);
        users.when(LoginHelper::getUserId).thenReturn(7L);users.when(LoginHelper::getTenantId).thenReturn(T);
        tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(T);
        when(transactions.executeRepeatableRead(eq(T),any())).thenAnswer(c->((Supplier<?>)c.getArgument(1)).get());
        when(mapper.clock()).thenReturn(new Clock(BigDecimal.valueOf(NOW),"+08:00"));
        when(mapper.lockUnit(301L)).thenReturn(new Unit(T,301L,301L,1L,"数量",BigDecimal.ONE,4,"u-new",NOW-1000,1L,"0"));
        when(mapper.lockCategory(501L)).thenReturn(new Category(T,501L,1L,"0"));
        when(mapper.lockWarehouse(801L)).thenReturn(new Warehouse(T,801L,1L,"0"));
        when(mapper.lockArea(802L)).thenReturn(new Area(T,802L,801L,1L,0L,1L,"0"));
        when(mapper.lockLocation(803L)).thenReturn(new Location(T,803L,801L,802L,1L,0L,"0"));
        when(mapper.lockMaterialConflicts(eq(T),anyLong(),eq("TEST-NEW-001"))).thenReturn(List.of());
        when(mapper.lockAllStocks(anyLong())).thenReturn(List.of());when(mapper.lockAllTransactions(anyLong())).thenReturn(List.of());
        when(mapper.insertNewGuard(anyString(),anyLong())).thenReturn(1);when(mapper.insertMaterial(any())).thenReturn(1);when(mapper.insertBirth(any())).thenReturn(1);
        service=new ErpCertifiedSourceBirthService(mapper,transactions,qualification,reader);
    }
    @AfterEach void cleanup(){auth.close();users.close();tenants.close();}
    @Test void createActuallyInsertsNewMaterialAndBirthInsideOwnedErpCallback(){
        var result=service.create(request());
        assertNotNull(result);assertTrue(result.materialId()>0);assertTrue(result.birthId()>0);
        var order=inOrder(qualification,reader,mapper);
        order.verify(qualification).requireSourceChainTransaction();order.verify(reader).requireRegistrySchemaLocked();
        order.verify(mapper).insertMaterial(any());order.verify(mapper).insertBirth(any());
        verify(transactions).executeRepeatableRead(eq(T),any());
        assertEquals("SUPPLIER_TERMS_PENDING",result.supplierTermsState());
    }
    @Test void firstGuardMustBeCreatedInBirthTransactionNotInReadPreview(){
        var created=service.create(request());verify(mapper).insertNewGuard(T,created.materialId());
    }
    @Test void recentExistingMaterialCodeCannotBeRegisteredAsBorn(){
        when(mapper.lockMaterialConflicts(eq(T),anyLong(),anyString())).thenReturn(List.of(55L));
        assertThrows(ServiceException.class,()->service.create(request()));
        verify(mapper,never()).insertMaterial(any());verify(mapper,never()).insertBirth(any());
    }
    @Test void unitMissingVerifiedVersionCannotBeInferredFromBaseFlag(){
        when(mapper.lockUnit(301L)).thenReturn(new Unit(T,301L,null,1L,"数量",BigDecimal.ONE,null,null,null,1L,"0"));
        assertThrows(ServiceException.class,()->service.create(request()));verify(mapper,never()).insertMaterial(any());
    }
    @Test void newWarehouseDoesNotHideMaterialHistoryInAnotherWarehouse(){
        when(mapper.lockAllTransactions(anyLong())).thenReturn(List.of(900L));
        assertThrows(ServiceException.class,()->service.create(request()));verify(mapper,never()).insertBirth(any());
    }
    @Test void crossTenantWarehouseParentMustRejectBeforeAnyInsert(){
        when(mapper.lockArea(802L)).thenReturn(new Area("000001",802L,801L,1L,0L,1L,"0"));
        assertThrows(ServiceException.class,()->service.create(request()));verify(mapper,never()).insertMaterial(any());
    }
    @Test void birthInsertFailureIsNotReturnedAsCreatedMaterial(){
        when(mapper.insertBirth(any())).thenReturn(0);
        assertThrows(ServiceException.class,()->service.create(request()));verify(mapper).insertMaterial(any());
    }
    static ErpCertifiedSourceBirthService.CreateMaterial request(){
        return new ErpCertifiedSourceBirthService.CreateMaterial("TEST-NEW-001","模拟测试物料",501L,301L,801L,802L,803L,"本地采购新范围模拟，不是现场实绩");
    }
}
