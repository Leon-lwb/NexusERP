package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 新建永久草稿必须冻结承诺，不封堵正常审批状态与实际仓储明细维护。 */
class WmCertifiedDraftMutationTest extends WmCertifiedReceiptMutationGuardTest {
    Object call(String name,Class<?>[] types,Object... args){try{return guard.getClass().getMethod(name,types).invoke(guard,args);}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError("缺少永久草稿原生修改保护",e);}}
    void marker(){doAnswer(i->"CERTIFIED_DRAFT_V1").when(mapper).lockCreationKind("000000",101L);}
    WmMaterialReceiveLineBo line(){var bo=new WmMaterialReceiveLineBo();bo.setLineId(701L);bo.setReceiveId(101L);bo.setMaterialId(201L);bo.setBaseUomId("26");bo.setQuantityReceived(new BigDecimal("7.0000"));return bo;}
    @Test void permanentDraftCannotBeDeletedOrItsLineEditedThroughOrdinaryCrud(){marker();assertThrows(ServiceException.class,()->guard.requireMutableLine(701L,101L));assertThrows(ServiceException.class,()->guard.requireMutableLineParent(101L));assertThrows(ServiceException.class,()->call("requireDeletable",new Class<?>[]{Long.class},101L));}
    @Test void permanentDraftStillAllowsWorkflowAndStorageDetailMutation(){marker();assertDoesNotThrow(()->guard.requireUnposted(101L));assertDoesNotThrow(()->guard.requireMutableDetailParent(101L,701L));}
    @Test void headCrudCannotIncreaseCommittedQuantityOrRemoveLine(){marker();when(mapper.readFrozenDraftLines("000000",101L)).thenReturn(List.of(new com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.FrozenDraftLine(701L,201L,"26",new BigDecimal("7.0000"))));var bo=line();bo.setQuantityReceived(BigDecimal.TEN);assertThrows(ServiceException.class,()->call("requireFrozenDraftLines",new Class<?>[]{Long.class,List.class},101L,List.of(bo)));assertThrows(ServiceException.class,()->call("requireFrozenDraftLines",new Class<?>[]{Long.class,List.class},101L,List.of()));}
    @Test void unchangedNativeQuantityCanKeepNotesAndStorageDetails(){marker();when(mapper.readFrozenDraftLines("000000",101L)).thenReturn(List.of(new com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.FrozenDraftLine(701L,201L,"26",new BigDecimal("7.0000"))));assertDoesNotThrow(()->call("requireFrozenDraftLines",new Class<?>[]{Long.class,List.class},101L,List.of(line())));}
    @Test void immutableDraftFactsNeverAcquireCommandLockAfterHead() {
        var query = Arrays.stream(com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.class.getMethods())
            .filter(m -> m.isAnnotationPresent(org.apache.ibatis.annotations.Select.class))
            .filter(m -> String.join(" ", m.getAnnotation(org.apache.ibatis.annotations.Select.class).value())
                .contains("FROM wm_certified_receive_command"))
            .findFirst().orElseThrow();
        var sql = String.join(" ", query.getAnnotation(org.apache.ibatis.annotations.Select.class).value());
        assertFalse(sql.toUpperCase(Locale.ROOT).contains("FOR UPDATE"),
            "整单编辑已持收货头锁，不可再反向锁永久请求而与原键重放形成死锁");
        assertFalse(query.getAnnotation(org.apache.ibatis.annotations.Options.class).useCache());
    }
}
