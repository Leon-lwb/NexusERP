package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmBarcode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** A carrier has one immutable subject; stock is only a checked binding-time reference. */
@Service @DS("erp") @RequiredArgsConstructor
public class WmBarcodeIdentityGuard {
    private final JdbcTemplate jdbc;
    private final ErpTraceContext context;
    public void prepare(WmBarcode value,boolean legacyGenerateAdapter){
        String tenant=context.tenant();context.requireWrite(tenant);value.setTenantId(tenant);
        String code=value.getBarcodeCode();
        need(code!=null && !code.isBlank() && code.length()<=128 && code.equals(code.trim())
            && code.chars().noneMatch(c->c<32 && c!=29 || c==127),"新条码须有完整原文，长度不超过128且没有首尾空白或扫描结束符");
        lockBinding(value,legacyGenerateAdapter);
        finishRegistration(value);
    }
    /** Locks the subject before the generator takes its database sequence row lock. */
    public void lockBinding(WmBarcode value,boolean legacyGenerateAdapter){
        String tenant=context.tenant();context.requireWrite(tenant);value.setTenantId(tenant);
        String primary=value.getPrimaryObjectType();
        if(primary==null && legacyGenerateAdapter){
            primary=value.getSnId()!=null?"SN":value.getBatchId()!=null?"BATCH":value.getMaterialId()!=null?"MATERIAL":value.getBizId()!=null?"DOCUMENT":null;
            value.setPrimaryObjectType(primary);
        }
        need(primary!=null && Set.of("SN","BATCH","MATERIAL","DOCUMENT").contains(primary),"新条码必须明确唯一主对象，库存ID不能作为正式主体");
        if("DOCUMENT".equals(primary)){
            need(value.getMaterialId()==null && value.getBatchId()==null && value.getBatchCode()==null && value.getSnId()==null && value.getStockId()==null && value.getBizLineId()==null,"单据载体请使用实际单据主体；物料身份请另建对应载体，不能混入未核实上下文");
            var spec=ErpDocumentCodeResolver.binding(value.getBizType());need(spec!=null && positive(value.getBizId()),"业务单据类型或真实ID未受支持");
            one("SELECT "+spec.id()+" FROM "+spec.table()+" WHERE tenant_id=? AND "+spec.id()+"=?"+(spec.logicalDelete()?" AND del_flag='0'":"")+" FOR UPDATE",tenant,value.getBizId());
            value.setBizType(spec.type());
        }else{
            need(!"SN".equals(primary) || positive(value.getSnId()),"SN主对象缺少正式identityId");
            need(!"BATCH".equals(primary) || positive(value.getBatchId()),"批次主对象缺少ERP批次ID");
            need(!"MATERIAL".equals(primary) || positive(value.getMaterialId()),"物料主对象缺少真实物料ID");
            // 与库存执行器共享物料→正式身份→批次→库存顺序；预读只用于确定应锁物料，锁后再次核验。
            Long lockedMaterial=value.getMaterialId();
            if(value.getSnId()!=null){var observed=one("SELECT material_id FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0'",tenant,value.getSnId());assertId(lockedMaterial,id(observed,"material_id"),"SN与物料断言不一致");lockedMaterial=id(observed,"material_id");}
            if(value.getBatchId()!=null){var observed=one("SELECT material_id FROM wm_batch WHERE tenant_id=? AND batch_id=? AND del_flag='0'",tenant,value.getBatchId());assertId(lockedMaterial,id(observed,"material_id"),"批次与物料断言不一致");lockedMaterial=id(observed,"material_id");}
            need(positive(lockedMaterial),"载体缺少真实物料身份");one("SELECT material_id FROM bd_material WHERE tenant_id=? AND material_id=? AND del_flag='0' FOR UPDATE",tenant,lockedMaterial);
            Map<String,Object> sn=null;
            if(value.getSnId()!=null){
                need(positive(value.getSnId()),"正式身份无效");sn=one("SELECT * FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR UPDATE",tenant,value.getSnId());
                need(positive(id(sn,"material_id")),"正式SN缺少物料实证");assertId(value.getMaterialId(),id(sn,"material_id"),"SN与物料断言不一致");value.setMaterialId(id(sn,"material_id"));
                assertId(value.getBatchId(),id(sn,"batch_id"),"SN与批次ID断言不一致");value.setBatchId(id(sn,"batch_id"));
                assertText(value.getBatchCode(),text(sn,"batch_code"),"SN与批次编码断言不一致");value.setBatchCode(text(sn,"batch_code"));
            }
            if(value.getBatchId()!=null){
                need(positive(value.getBatchId()),"批次身份无效");var batch=one("SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND del_flag='0' FOR UPDATE",tenant,value.getBatchId());
                need(positive(id(batch,"material_id")),"批次缺少物料实证");assertId(value.getMaterialId(),id(batch,"material_id"),"批次与物料断言不一致");value.setMaterialId(id(batch,"material_id"));
                assertText(value.getBatchCode(),text(batch,"batch_code"),"批次编码与ID不一致");value.setBatchCode(text(batch,"batch_code"));
            }else need(value.getBatchCode()==null,"批次编码不能替代真实批次ID");
            need(Objects.equals(lockedMaterial,value.getMaterialId()),"主体的物料在锁定前后发生变化，请重新核实");
            if(value.getStockId()!=null){
                need(positive(value.getStockId()),"库存引用无效");var stock=one("SELECT * FROM wm_material_stock WHERE tenant_id=? AND stock_id=? AND del_flag='0' FOR UPDATE",tenant,value.getStockId());
                assertId(value.getMaterialId(),id(stock,"material_id"),"库存引用的物料不一致");assertId(value.getBatchId(),id(stock,"batch_id"),"库存引用的批次不一致");assertId(value.getSnId(),id(stock,"sn_id"),"库存引用的正式SN不一致");
                need(stock.get("stock_quantity") instanceof BigDecimal quantity && quantity.signum()>0,"库存引用不是当前正数量库存");
                if(sn!=null)need(Objects.equals(value.getStockId(),id(sn,"stock_id")) && new BigDecimal(stock.get("stock_quantity").toString()).compareTo(BigDecimal.ONE)==0,"SN不是所指的一件当前在库实物");
            }
        }
    }
    /** Called in the same ERP transaction after lockBinding; no foreign effects occur between them. */
    public void finishRegistration(WmBarcode value){
        String tenant=context.tenant();context.requireWrite(tenant);String code=value.getBarcodeCode();
        need(code!=null && !code.isBlank() && code.length()<=128 && code.equals(code.trim())
            && code.chars().noneMatch(c->c<32 && c!=29 || c==127),"新条码原文无效或超过128位");
        need(jdbc.queryForList("SELECT barcode_id FROM wm_barcode WHERE tenant_id=? AND barcode_code=? LIMIT 1 FOR UPDATE",tenant,code).isEmpty(),"原条码已经登记，停用或历史删除的原码也不能复用");
        if(value.getStatus()==null)value.setStatus("ACTIVE");status(value.getStatus());
        Date generated=new Date();value.setGenerateTime(generated);value.setBindTime(generated);
        need(!"ACTIVE".equals(value.getStatus()) || value.getExpireTime()==null || value.getExpireTime().after(generated),"新可用标签的有效期已结束");
    }
    public WmBarcode lock(Long barcodeId){
        String tenant=context.tenant();context.requireWrite(tenant);need(positive(barcodeId),"缺少原条码ID");
        var rows=jdbc.query("SELECT * FROM wm_barcode WHERE tenant_id=? AND barcode_id=? AND del_flag='0' FOR UPDATE",BeanPropertyRowMapper.newInstance(WmBarcode.class),tenant,barcodeId);
        need(rows.size()==1,"原条码不存在或跨租户");return rows.get(0);
    }
    public void assertImmutable(WmBarcode old,WmBarcode requested){
        need(old!=null && Objects.equals(old.getBarcodeId(),requested.getBarcodeId()),"条码身份已改变");
        same(requested.getBarcodeCode(),old.getBarcodeCode());same(requested.getPrimaryObjectType(),old.getPrimaryObjectType());
        same(requested.getConfigId(),old.getConfigId());same(requested.getConfigCode(),old.getConfigCode());same(requested.getBizType(),old.getBizType());same(requested.getBizId(),old.getBizId());same(requested.getBizLineId(),old.getBizLineId());
        same(requested.getMaterialId(),old.getMaterialId());same(requested.getBatchId(),old.getBatchId());same(requested.getBatchCode(),old.getBatchCode());same(requested.getSnId(),old.getSnId());same(requested.getStockId(),old.getStockId());same(requested.getGenerateTime(),old.getGenerateTime());same(requested.getBindTime(),old.getBindTime());
        if(requested.getStatus()!=null)status(requested.getStatus());
    }
    private Map<String,Object> one(String sql,Object... args){var rows=jdbc.queryForList(sql,args);need(rows.size()==1,"条码主体或上下文不存在、已删除或不属于当前租户");return rows.get(0);}
    private static void same(Object provided,Object original){need(provided==null || Objects.equals(provided,original),"原条码及主对象映射不可重绑；请停用并新建有来源的标签");}
    private static void assertId(Long provided,Long actual,String message){need(provided==null || provided.equals(actual),message);}
    private static void assertText(String provided,String actual,String message){need(provided==null || provided.equals(actual),message);}
    private static void status(String value){need(Set.of("ACTIVE","INACTIVE","USED","EXPIRED").contains(value),"条码状态无效");}
    private static Long id(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:((Number)value).longValue();}
    private static String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}
    private static boolean positive(Long value){return value!=null && value>0;}
    private static void need(boolean condition,String message){if(!condition)throw new ServiceException(message);}
}
