package com.nexus.erp.ai.adapter;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/** 出生的内部固定类型、父链及摘要规则；不对外签发独立核验器或客户端资格旗号。 */
final class ErpCertifiedSourceBirthSupport {
    static final String INITIAL_EMPTY="EMPTY_AT_BIRTH";
    private ErpCertifiedSourceBirthSupport(){ }
    static Actor actor(String permission){
        StpUtil.checkLogin();StpUtil.checkPermission(permission);
        String tenant=TenantHelper.getTenantId();Long user=LoginHelper.getUserId();
        need(TenantHelper.isEnable()&&tenant!=null&&tenant.matches("[0-9]{6,20}")
            &&tenant.equals(LoginHelper.getTenantId())&&positive(user),"当前租户或操作人未知");
        return new Actor(tenant,user,permission);
    }
    static void recheck(Actor expected){need(expected.equals(actor(expected.permission())),"创建过程中身份或租户变化");}
    static long clock(Clock value){
        need(value!=null&&"+08:00".equals(value.timeZone())&&value.epochMillis()!=null,"数据库时钟或时区未知");
        try{long now=value.epochMillis().longValueExact();need(now>0&&now%1000==0,"数据库时钟精度未知");return now;}
        catch(ArithmeticException invalid){throw rejected("数据库时间戳不能精确读取");}
    }
    static void unit(Unit u,String tenant,Long id,long at){
        need(u!=null&&tenant.equals(u.tenantId())&&id.equals(u.unitId())&&id.equals(u.primaryId())
            &&Long.valueOf(1).equals(u.isBase())&&Long.valueOf(1).equals(u.status())&&"0".equals(u.delFlag())
            &&text(u.category(),64)&&u.conversionRate()!=null&&u.conversionRate().compareTo(BigDecimal.ONE)==0
            &&u.quantityScale()!=null&&u.quantityScale()>=0&&u.quantityScale()<=4
            &&text(u.conversionVersion(),64)&&u.verifiedAt()!=null&&u.verifiedAt()>0&&u.verifiedAt()<=at,
            "根单位须先正式确认恒等自映射、精度、版本和核验时间；不能补认旧单位");
    }
    static String storage(Warehouse w,Area a,Location l,String tenant,Long wid,Long aid,Long lid){
        need(w!=null&&a!=null&&l!=null&&tenant.equals(w.tenantId())&&tenant.equals(a.tenantId())&&tenant.equals(l.tenantId())
            &&wid.equals(w.warehouseId())&&aid.equals(a.areaId())&&lid.equals(l.locationId())
            &&wid.equals(a.warehouseId())&&wid.equals(l.warehouseId())&&aid.equals(l.areaId())
            &&Long.valueOf(1).equals(w.status())&&"0".equals(w.delFlag())
            &&Long.valueOf(1).equals(a.enabled())&&Long.valueOf(0).equals(a.frozen())&&Long.valueOf(1).equals(a.locationFlag())&&"0".equals(a.delFlag())
            &&Long.valueOf(1).equals(l.enabled())&&Long.valueOf(0).equals(l.frozen())&&"0".equals(l.delFlag()),
            "仓库、库区、储位的完整租户父链缺失、停用或冻结");
        return hash(w,a,l);
    }
    static String birthHash(StoredBirth b){
        return hash("ERP_NEW_MATERIAL_BIRTH_V1",b.birthId(),b.tenantId(),b.materialId(),b.materialCode(),b.categoryId(),b.baseUomId(),
            b.quantityScale(),b.conversionVersion(),b.unitVerifiedAt(),b.unitFingerprint(),b.warehouseId(),b.areaId(),b.locationId(),
            b.storageFingerprint(),b.bornAt(),b.actorId(),b.scenario(),b.initialState());
    }
    static String hash(Object... values){
        try{var digest=MessageDigest.getInstance("SHA-256");for(Object value:values){byte[] bytes=canonical(value).getBytes(StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw rejected("来源摘要算法不可用");}
    }
    private static String canonical(Object value){
        if(value==null)return "N;";
        if(value instanceof BigDecimal n)return "D:"+n.stripTrailingZeros().toPlainString()+";";
        if(value instanceof CharSequence s){StringBuilder r=new StringBuilder("S:").append(s.length()).append(':');
            for(int i=0;i<s.length();i++)r.append(String.format(java.util.Locale.ROOT,"%04x",(int)s.charAt(i)));return r.append(';').toString();}
        if(value.getClass().isRecord()){
            StringBuilder r=new StringBuilder("R:").append(value.getClass().getName()).append(':');
            try{for(var c:value.getClass().getRecordComponents())r.append(canonical(c.getName())).append(canonical(c.getAccessor().invoke(value)));}
            catch(ReflectiveOperationException invalid){throw rejected("出生来源字段不能复核");}return r.append(';').toString();
        }
        need(value instanceof Number||value instanceof Boolean,"出生摘要包含未知类型");
        return value.getClass().getName()+":"+Objects.toString(value)+";";
    }
    static boolean positive(Long value){return value!=null&&value>0;}
    static boolean text(String value,int size){return value!=null&&!value.isBlank()&&value.equals(value.strip())&&value.length()<=size;}
    static void need(boolean yes,String reason){if(!yes)throw rejected(reason);}
    static ServiceException rejected(String reason){return new ServiceException("可信新范围来源拒绝："+reason);}
    record Actor(String tenantId,Long userId,String permission){ }
}
