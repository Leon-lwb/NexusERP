package com.nexus.erp.ai.adapter;

import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.service.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 预览仅锁既有竞争池，不能将查询偷偷变成初始化。 */
class ErpCertifiedSourcePreviewPoolTest {
    @Test void readExistingPoolNeverCallsProvisioningLockOrBusinessMutation(){
        var mapper=mock(PuRequirementCompetitionPoolMapper.class);var locks=mock(PuRequirementMaterialLockService.class);
        var qualification=mock(PuRequirementErpTransactionQualification.class);
        when(locks.lockExisting("000000",List.of(1001L))).thenReturn(Map.of(1001L,0));
        try(var tenant=mockStatic(TenantHelper.class)){
            tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            var reader=new ErpPurchaseCompetitionSourceReader(mapper,locks,qualification);
            var result=reader.readLocked("000000",1001L,LocalDate.of(2026,10,16));
            assertTrue(result.stocks().isEmpty());verify(locks).lockExisting("000000",List.of(1001L));
            verify(locks,never()).lock(anyString(),anyCollection());
            verify(qualification).requireSourceChainTransaction();
        }
    }
}
