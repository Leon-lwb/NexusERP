package com.nexus.erp.ai.adapter;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.util.*;
import static com.nexus.erp.ai.adapter.ErpCertifiedSourceBirthSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 持久出生必须原样独立读取，近期日期或无来源空集合均不授予初始空库存认证。 */
class ErpCertifiedSourceBirthReaderTest {
    static final String T="000000";static final long AT=1791086400000L;
    final ErpCertifiedSourceBirthMapper mapper=mock(ErpCertifiedSourceBirthMapper.class);
    final PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
    final ErpCertifiedSourceBirthReader reader=new ErpCertifiedSourceBirthReader(mapper,qualification);
    final Unit unit=new Unit(T,301L,301L,1L,"数量",BigDecimal.ONE,4,"u-new",AT-1000,1L,"0");
    final Warehouse warehouse=new Warehouse(T,801L,1L,"0");
    final Area area=new Area(T,802L,801L,1L,0L,1L,"0");
    final Location location=new Location(T,803L,801L,802L,1L,0L,"0");
    StoredBirth birth;MockedStatic<TenantHelper> tenants;
    @BeforeEach void setup(){
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);tenants.when(TenantHelper::getTenantId).thenReturn(T);
        var raw=new StoredBirth(901L,T,1001L,"TEST-NEW-001",501L,301L,4,"u-new",AT-1000,hash(unit),801L,802L,803L,hash(warehouse,area,location),AT,7L,"本地模拟",INITIAL_EMPTY,null);
        birth=new StoredBirth(raw.birthId(),raw.tenantId(),raw.materialId(),raw.materialCode(),raw.categoryId(),raw.baseUomId(),raw.quantityScale(),raw.conversionVersion(),raw.unitVerifiedAt(),raw.unitFingerprint(),raw.warehouseId(),raw.areaId(),raw.locationId(),raw.storageFingerprint(),raw.bornAt(),raw.actorId(),raw.scenario(),raw.initialState(),birthHash(raw));
        when(mapper.sourceEngines()).thenReturn(List.of("erp_certified_source_birth","bd_material","bd_unit_of_measure","bd_material_category","wm_warehouse","wm_storage_area","wm_storage_location","wm_material_stock","wm_material_stock_tx","pu_requirement_material_guard").stream().map(t->new Engine(t,"InnoDB")).toList());
        when(mapper.registryEngine()).thenReturn("InnoDB");when(mapper.pinRegistry()).thenReturn(List.of());when(mapper.registryColumns()).thenReturn(columns());
        when(mapper.registryIndexes()).thenReturn(List.of(new Index("PRIMARY",0,"birth_id",1),new Index("uk_source_birth_material",0,"tenant_id",1),new Index("uk_source_birth_material",0,"material_id",2)));
        when(mapper.clock()).thenReturn(new Clock(BigDecimal.valueOf(AT+86400000),"+08:00"));
        when(mapper.lockBirth(T,1001L)).thenReturn(birth);when(mapper.lockMaterial(1001L)).thenReturn(new Material(T,1001L,"TEST-NEW-001",501L,301L,1L,"0",7L,AT));
        when(mapper.lockUnit(301L)).thenReturn(unit);when(mapper.lockWarehouse(801L)).thenReturn(warehouse);when(mapper.lockArea(802L)).thenReturn(area);when(mapper.lockLocation(803L)).thenReturn(location);
        when(mapper.lockAllStocks(1001L)).thenReturn(List.of());when(mapper.lockAllTransactions(1001L)).thenReturn(List.of());
    }
    @AfterEach void clean(){tenants.close();}
    @Test void persistentBirthRemainsValidWithoutAnyRecentCreationHeuristic(){
        assertEquals(birth,reader.verifyInitialEmptyLocked(T,1001L));
        verify(mapper).lockAllStocks(1001L);verify(mapper).lockAllTransactions(1001L);
    }
    @Test void missingPersistentBirthNeverMeansReliableZero(){when(mapper.lockBirth(T,1001L)).thenReturn(null);assertThrows(ServiceException.class,()->reader.verifyInitialEmptyLocked(T,1001L));}
    @Test void anyOldStockIncludingZeroDeletedOrOtherWarehouseRefusesEmptyPath(){when(mapper.lockAllStocks(1001L)).thenReturn(List.of(55L));assertThrows(ServiceException.class,()->reader.verifyInitialEmptyLocked(T,1001L));}
    @Test void orphanHistoryWithNoStockCannotBeCertifiedAsEmpty(){when(mapper.lockAllTransactions(1001L)).thenReturn(List.of(999L));assertThrows(ServiceException.class,()->reader.verifyInitialEmptyLocked(T,1001L));}
    @Test void originalMaterialCreationAndPersistentBirthMustAgree(){when(mapper.lockMaterial(1001L)).thenReturn(new Material(T,1001L,"TEST-NEW-001",501L,301L,1L,"0",1L,AT-1000));assertThrows(ServiceException.class,()->reader.verifyInitialEmptyLocked(T,1001L));}
    @Test void currentUnitVersionCannotReplaceFrozenVersion(){when(mapper.lockUnit(301L)).thenReturn(new Unit(T,301L,301L,1L,"数量",BigDecimal.ONE,4,"v-changed",AT-1000,1L,"0"));assertThrows(ServiceException.class,()->reader.verifyInitialEmptyLocked(T,1001L));}
    @Test void storageParentChangeMustRefuseTheFrozenBirth(){when(mapper.lockLocation(803L)).thenReturn(new Location(T,803L,801L,899L,1L,0L,"0"));assertThrows(ServiceException.class,()->reader.verifyInitialEmptyLocked(T,1001L));}
    @Test void registryWithoutPermanentMaterialUniqueKeyCannotAuthenticate(){when(mapper.registryIndexes()).thenReturn(List.of(new Index("PRIMARY",0,"birth_id",1)));assertThrows(ServiceException.class,()->reader.verifyInitialEmptyLocked(T,1001L));}
    static List<Column> columns(){
        var cols=new ArrayList<Column>();for(String name:List.of("birth_id","material_id","category_id","base_uom_id","warehouse_id","area_id","location_id","actor_id"))cols.add(new Column(name,"bigint","NO",null));
        cols.add(new Column("quantity_scale","tinyint","NO",null));
        for(String name:List.of("unit_verified_at","born_at"))cols.add(new Column(name,"datetime","NO",null));
        cols.add(new Column("tenant_id","varchar","NO",20L));cols.add(new Column("material_code","varchar","NO",64L));cols.add(new Column("conversion_version","varchar","NO",64L));
        cols.add(new Column("initial_state","varchar","NO",32L));cols.add(new Column("scenario","varchar","NO",500L));
        for(String name:List.of("unit_fingerprint","storage_fingerprint","birth_fingerprint"))cols.add(new Column(name,"char","NO",64L));return cols;
    }
}
