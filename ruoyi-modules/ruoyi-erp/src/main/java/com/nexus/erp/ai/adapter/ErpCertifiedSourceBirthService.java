package com.nexus.erp.ai.adapter;

import static com.nexus.erp.ai.adapter.ErpCertifiedSourceBirthSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/** 只允许服务端生成身份的全新物料与持久出生同事务写入，绝不补登记旧物料。 */
@Service
public class ErpCertifiedSourceBirthService {
    private final ErpCertifiedSourceBirthMapper mapper;
    private final PuRequirementErpTransactionExecutor transactions;
    private final PuRequirementErpTransactionQualification qualification;
    private final ErpCertifiedSourceBirthReader reader;
    public ErpCertifiedSourceBirthService(ErpCertifiedSourceBirthMapper mapper,PuRequirementErpTransactionExecutor transactions,
            PuRequirementErpTransactionQualification qualification,ErpCertifiedSourceBirthReader reader){
        this.mapper=mapper;this.transactions=transactions;this.qualification=qualification;this.reader=reader;
    }
    public CreatedMaterial create(CreateMaterial request){
        var actor=actor("bd:material:add");
        need(request!=null&&text(request.materialCode(),64)&&text(request.materialName(),200)&&positive(request.categoryId())
            &&positive(request.baseUomId())&&positive(request.warehouseId())&&positive(request.areaId())&&positive(request.locationId())
            &&text(request.scenario(),500),"只接受完整新物料及明确测试场景请求");
        return transactions.executeRepeatableRead(actor.tenantId(),()->{
            recheck(actor);qualification.requireSourceChainTransaction();reader.requireRegistrySchemaLocked();
            long at=clock(io(mapper::clock));Long id=IdWorker.getId();Long birthId=IdWorker.getId();
            var conflicts=io(()->mapper.lockMaterialConflicts(actor.tenantId(),id,request.materialCode()));
            need(conflicts!=null&&conflicts.isEmpty(),"物料身份或编码已经存在，不能补登记出生");
            var category=io(()->mapper.lockCategory(request.categoryId()));
            need(category!=null&&actor.tenantId().equals(category.tenantId())&&request.categoryId().equals(category.categoryId())
                &&Long.valueOf(1).equals(category.status())&&"0".equals(category.delFlag()),"物料分类身份或状态不符");
            var unit=io(()->mapper.lockUnit(request.baseUomId()));unit(unit,actor.tenantId(),request.baseUomId(),at);
            var warehouse=io(()->mapper.lockWarehouse(request.warehouseId()));var area=io(()->mapper.lockArea(request.areaId()));
            var location=io(()->mapper.lockLocation(request.locationId()));
            String storage=storage(warehouse,area,location,actor.tenantId(),request.warehouseId(),request.areaId(),request.locationId());
            empty(io(()->mapper.lockAllStocks(id)),"新物料身份已有库存，禁止出生补认");
            empty(io(()->mapper.lockAllTransactions(id)),"新物料身份已有历史或孤立流水，禁止出生补认");
            var raw=new StoredBirth(birthId,actor.tenantId(),id,request.materialCode(),request.categoryId(),request.baseUomId(),
                unit.quantityScale(),unit.conversionVersion(),unit.verifiedAt(),hash(unit),request.warehouseId(),request.areaId(),
                request.locationId(),storage,at,actor.userId(),request.scenario(),INITIAL_EMPTY,null);
            var born=new StoredBirth(raw.birthId(),raw.tenantId(),raw.materialId(),raw.materialCode(),raw.categoryId(),raw.baseUomId(),
                raw.quantityScale(),raw.conversionVersion(),raw.unitVerifiedAt(),raw.unitFingerprint(),raw.warehouseId(),raw.areaId(),
                raw.locationId(),raw.storageFingerprint(),raw.bornAt(),raw.actorId(),raw.scenario(),raw.initialState(),birthHash(raw));
            recheck(actor);qualification.requireSourceChainTransaction();
            need(io(()->mapper.insertMaterial(new NewMaterial(id,actor.tenantId(),request.materialCode(),request.materialName(),
                request.categoryId(),request.baseUomId(),at,actor.userId(),request.scenario())))==1,"新物料插入失败");
            need(io(()->mapper.insertNewGuard(actor.tenantId(),id))==1,"新物料竞争池出生失败，必须整体回滚");
            need(io(()->mapper.insertBirth(born))==1,"出生事实插入失败，必须整体回滚物料");
            qualification.requireSourceChainTransaction();recheck(actor);
            return new CreatedMaterial(id,birthId,born.birthFingerprint(),"SUPPLIER_TERMS_PENDING");
        });
    }

    private static void empty(List<Long> rows, String reason) {
        need(rows != null && rows.isEmpty(), reason);
    }

    private static <T> T io(Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException invalid) {
            throw rejected("出生来源读写失败");
        }
    }

    /** 请求中没有物料ID、出生ID、租户、操作人、核验时间或来源摘要。 */
    public record CreateMaterial(String materialCode,String materialName,Long categoryId,Long baseUomId,
                                 Long warehouseId,Long areaId,Long locationId,String scenario) { }
    /** 出生事实不是供应条款认证或采购授权。 */
    public record CreatedMaterial(Long materialId,Long birthId,String birthFingerprint,String supplierTermsState) { }
}
