package com.nexus.erp.wm;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.SnItem;
import com.nexus.erp.wm.controller.WmSerialSelectionController;
import com.nexus.erp.wm.domain.WmSerialPostingRequest;
import com.nexus.erp.wm.domain.bo.WmSerialSelectionBo;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.trace.ErpTraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WmHandoverStrictContractTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void serialSelectionUsesOriginalHandoverPermission(boolean write) {
        var selections = mock(WmSerialSelectionService.class);
        var controller = new WmSerialSelectionController(selections);
        try (var permissions = mockStatic(StpUtil.class)) {
            assertDoesNotThrow(() -> {
                if (write) { var body = new WmSerialSelectionBo(); body.setSourceDocType("WM_MATERIAL_HANDOVER"); controller.freeze(body); }
                else controller.version("WM_MATERIAL_HANDOVER", 10L, 11L);
            });
            permissions.verify(() -> StpUtil.checkPermission("wm:materialHandover:" + (write ? "edit" : "query")));
        }
    }

    @Test void freezeRejectsSerialTargetDifferentFromActualHandoverLine() {
        var fixture = fixture();
        var items = List.of(new SnItem(1L, 101L, 71L, null, 50L));
        ServiceException error = assertThrows(ServiceException.class,
            () -> fixture.service.freeze(fixture.request, "handover-command", 1L, 7L, items));
        assertTrue(error.getMessage().contains("目标库位"));
        verify(fixture.store, never()).prepare(any(), anyString(), anyLong(), anyString(), anyString(), anyList());
    }

    @Test void postingAlsoRejectsOldFrozenSelectionWhoseTargetDisagrees() {
        var fixture = fixture();
        fixture.frozen(List.of(new SnItem(1L, 101L, 71L, null, 50L)));
        ServiceException error = assertThrows(ServiceException.class, () -> fixture.service.post(fixture.request));
        assertTrue(error.getMessage().contains("目标库位"));
        verify(fixture.store, never()).identity(anyLong());
        verify(fixture.store, never()).insertTx(any());
    }

    @Test void handoverPostedProofRetainsTwoSidedTransferDirection() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ErpTraceContext context = mock(ErpTraceContext.class); when(context.tenant()).thenReturn("000000");
        when(jdbc.queryForList(anyString(), eq("000000"), eq("WM_MATERIAL_HANDOVER"), eq(10L), eq(11L), eq("HANDOVER")))
            .thenReturn(List.of(Map.of("material_id", 20L, "quantity", BigDecimal.ONE)));
        var repository = new WmSerialInventoryRepository(jdbc, mock(WmMaterialStockMapper.class), mock(WmMaterialStockTxMapper.class), context, new ObjectMapper());
        assertEquals(0, repository.proofRequest("WM_MATERIAL_HANDOVER", 10L, 11L, "HANDOVER").direction(), "交接证明须按每件一出一入核验");
    }

    private WmSerialInventoryServiceTest fixture() {
        var fixture = new WmSerialInventoryServiceTest(); fixture.setup();
        fixture.request = WmSerialPostingRequest.out("WM_MATERIAL_HANDOVER", 10L, 11L, "HANDOVER", 20L, BigDecimal.ONE, 30L, 40L, 50L).transfer(30L, 8L, 70L);
        return fixture;
    }
}
