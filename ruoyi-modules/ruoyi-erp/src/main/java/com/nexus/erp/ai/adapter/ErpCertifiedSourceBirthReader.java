package com.nexus.erp.ai.adapter;

import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Supplier;
import static com.nexus.erp.ai.adapter.ErpCertifiedSourceBirthSupport.*;

/** 独立当前读持久出生与全物料库存/流水；没有基于近期创建时间的补认证入口。 */
@Service
public class ErpCertifiedSourceBirthReader {
    private static final Set<String> SOURCES=Set.of("erp_certified_source_birth","bd_material","bd_unit_of_measure","bd_material_category",
        "wm_warehouse","wm_storage_area","wm_storage_location","wm_material_stock","wm_material_stock_tx","pu_requirement_material_guard");
    private final ErpCertifiedSourceBirthMapper mapper;
    private final PuRequirementErpTransactionQualification qualification;
    public ErpCertifiedSourceBirthReader(ErpCertifiedSourceBirthMapper mapper,PuRequirementErpTransactionQualification qualification){
        this.mapper=Objects.requireNonNull(mapper);this.qualification=Objects.requireNonNull(qualification);
    }
    /** 写入或认证前固定所有实际表定义，核出生表强制字段及永久唯一键。 */
    public void requireRegistrySchemaLocked(){
        qualification.requireSourceChainTransaction();
        var pins=io(mapper::pinRegistry);need(pins!=null&&pins.isEmpty(),"出生表定义固定失败");
        var engines=io(mapper::sourceEngines);need(engines!=null,"出生来源表引擎未知");var names=new HashSet<String>();
        for(var e:engines)need(e!=null&&SOURCES.contains(e.tableName())&&names.add(e.tableName())&&"InnoDB".equalsIgnoreCase(e.engine()),"出生来源表缺失、重复或非事务引擎");
        need(names.equals(SOURCES),"出生所需实际来源表不完整");
        var expected=new HashMap<String,String>();
        for(String n:List.of("birth_id","material_id","category_id","base_uom_id","warehouse_id","area_id","location_id","actor_id"))expected.put(n,"bigint");
        expected.put("quantity_scale","tinyint");expected.put("unit_verified_at","datetime");expected.put("born_at","datetime");
        for(String n:List.of("tenant_id","material_code","conversion_version","scenario","initial_state"))expected.put(n,"varchar");
        for(String n:List.of("unit_fingerprint","storage_fingerprint","birth_fingerprint"))expected.put(n,"char");
        var columns=io(mapper::registryColumns);need(columns!=null,"出生字段定义未知");var observed=new HashSet<String>();
        var lengths=Map.of("tenant_id",20L,"material_code",64L,"conversion_version",64L,"scenario",500L,"initial_state",32L,
            "unit_fingerprint",64L,"storage_fingerprint",64L,"birth_fingerprint",64L);
        for(var c:columns)need(c!=null&&expected.containsKey(c.columnName())&&observed.add(c.columnName())
            &&expected.get(c.columnName()).equals(c.dataType())&&"NO".equals(c.nullable())
            &&(!lengths.containsKey(c.columnName())||lengths.get(c.columnName()).equals(c.textLength())),"出生字段类型、必填或长度不符");
        need(observed.equals(expected.keySet()),"出生字段定义不完整");
        var indexes=io(mapper::registryIndexes);need(indexes!=null,"出生永久唯一键未知");var keys=new TreeMap<String,TreeMap<Integer,String>>();
        for(var i:indexes){need(i!=null&&Integer.valueOf(0).equals(i.nonUnique())&&i.position()!=null&&i.position()>0
            &&keys.computeIfAbsent(i.indexName(),n->new TreeMap<>()).putIfAbsent(i.position(),i.columnName())==null,"出生唯一键定义未知");}
        need(keys.size()==2&&keys.containsKey("PRIMARY")&&keys.get("PRIMARY").equals(new TreeMap<>(Map.of(1,"birth_id")))
            &&keys.containsKey("uk_source_birth_material")&&keys.get("uk_source_birth_material").equals(new TreeMap<>(Map.of(1,"tenant_id",2,"material_id"))),"出生必须有主键及完整租户物料永久唯一键");
    }
    /** 非空来源只比较全物料身份覆盖，不要求出生登记存在。 */
    public void requireGlobalCoverageLocked(String tenant,Long material,Set<Long> stocks,Set<Long> transactions){
        need(TenantHelper.isEnable()&&tenant!=null&&tenant.matches("[0-9]{6,20}")&&tenant.equals(TenantHelper.getTenantId())
            &&positive(material)&&stocks!=null&&!stocks.isEmpty()&&transactions!=null&&!transactions.isEmpty()
            &&stocks.stream().allMatch(ErpCertifiedSourceBirthSupport::positive)
            &&transactions.stream().allMatch(ErpCertifiedSourceBirthSupport::positive),"非空全物料来源身份或租户未知");
        qualification.requireSourceChainTransaction();
        var expectedStocks=Set.copyOf(stocks);var expectedTransactions=Set.copyOf(transactions);
        // 仅读取未过滤的身份全集，不读取出生表，也不将额外租户/删除来源升级为可用供给。
        var allStocks=io(()->mapper.lockAllStocks(material));var allTransactions=io(()->mapper.lockAllTransactions(material));
        need(exactIdentities(allStocks).equals(expectedStocks)&&exactIdentities(allTransactions).equals(expectedTransactions),
            "同物料存在被租户过滤隐藏的库存、删除来源或孤立流水，整池拒绝");
        qualification.requireSourceChainTransaction();
    }
    /** 不接收外部库存、摘要或出生事实；每层独立复读原始持久记录和全量来源。 */
    public StoredBirth verifyInitialEmptyLocked(String tenant,Long material){
        need(TenantHelper.isEnable()&&tenant!=null&&tenant.matches("[0-9]{6,20}")&&tenant.equals(TenantHelper.getTenantId())&&positive(material),"出生请求租户或物料身份不符");
        requireRegistrySchemaLocked();
        var b=io(()->mapper.lockBirth(tenant,material));need(b!=null&&positive(b.birthId())&&tenant.equals(b.tenantId())&&material.equals(b.materialId())
            &&positive(b.categoryId())&&positive(b.baseUomId())&&positive(b.warehouseId())&&positive(b.areaId())&&positive(b.locationId())
            &&positive(b.actorId())&&b.bornAt()!=null&&b.bornAt()>0&&text(b.materialCode(),64)&&text(b.scenario(),500)
            &&INITIAL_EMPTY.equals(b.initialState())&&b.birthFingerprint()!=null&&b.birthFingerprint().matches("[0-9a-f]{64}")
            &&b.birthFingerprint().equals(birthHash(b)),"持久新物料出生事实缺失或被改变，不能将空集合认证为库存零");
        var m=io(()->mapper.lockMaterial(material));need(m!=null&&tenant.equals(m.tenantId())&&material.equals(m.materialId())
            &&b.materialCode().equals(m.materialCode())&&b.categoryId().equals(m.categoryId())&&b.baseUomId().equals(m.baseUomId())
            &&Long.valueOf(1).equals(m.status())&&"0".equals(m.delFlag())&&b.actorId().equals(m.createdBy())&&b.bornAt().equals(m.createdAt()),"原始物料创建事实与同事务出生记录不符");
        long now=clock(io(mapper::clock));need(now>=b.bornAt(),"出生时点晚于数据库观察");
        var u=io(()->mapper.lockUnit(b.baseUomId()));unit(u,tenant,b.baseUomId(),now);
        need(Objects.equals(b.quantityScale(),u.quantityScale())&&Objects.equals(b.conversionVersion(),u.conversionVersion())
            &&Objects.equals(b.unitVerifiedAt(),u.verifiedAt())&&u.verifiedAt()<=b.bornAt()&&hash(u).equals(b.unitFingerprint()),"出生单位版本与当前完整单位事实不符");
        var w=io(()->mapper.lockWarehouse(b.warehouseId()));var a=io(()->mapper.lockArea(b.areaId()));var l=io(()->mapper.lockLocation(b.locationId()));
        need(storage(w,a,l,tenant,b.warehouseId(),b.areaId(),b.locationId()).equals(b.storageFingerprint()),"出生完整仓储父链改变");
        var stocks=io(()->mapper.lockAllStocks(material));var txs=io(()->mapper.lockAllTransactions(material));
        need(stocks!=null&&txs!=null&&stocks.isEmpty()&&txs.isEmpty(),"已有库存或历史/孤立流水，必须返回原始流水链认证");
        qualification.requireSourceChainTransaction();return b;
    }
    private static Set<Long> exactIdentities(List<Long> rows){
        need(rows!=null,"全物料来源身份全集未知");var result=new HashSet<Long>();
        for(Long id:rows)need(positive(id)&&result.add(id),"全物料身份含未知、非正数或重复来源");
        return Set.copyOf(result);
    }
    private static <T>T io(Supplier<T> read){try{return read.get();}catch(RuntimeException failure){throw rejected("持久出生原始来源读取失败");}}
}
