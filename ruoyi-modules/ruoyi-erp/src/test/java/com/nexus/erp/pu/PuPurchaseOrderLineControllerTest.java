package com.nexus.erp.pu;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.controller.PuPurchaseOrderLineController;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class PuPurchaseOrderLineControllerTest {
    @Mock PuPurchaseOrderLineMapper orderLineMapper;
    @Mock PuCertifiedPurchaseDocumentGuard certifiedDocumentGuard;
    @InjectMocks PuPurchaseOrderLineController controller;
    @BeforeAll static void table() { TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),PuPurchaseOrderLine.class); }
    @Test void certifiedReceiptDifferencesCannotBeClosedWithoutLifecycleEvent() {
        ServiceException error=new ServiceException("protected-document");
        lenient().doThrow(error).when(certifiedDocumentGuard).requireLegacyOrderLineMutation(11L);
        lenient().when(orderLineMapper.update(isNull(),any())).thenReturn(1);
        assertSame(error,assertThrows(ServiceException.class,()->controller.confirmOverReceive(11L)));
        assertSame(error,assertThrows(ServiceException.class,()->controller.closeShortReceive(11L)));
        verifyNoInteractions(orderLineMapper);
    }
    @Test void legacyReceiptDifferencesRetainExistingStatusTransitions() {
        when(orderLineMapper.update(isNull(),any())).thenReturn(1);
        assertEquals(200,controller.confirmOverReceive(11L).getCode());
        assertEquals(200,controller.closeShortReceive(11L).getCode());
        verify(certifiedDocumentGuard,times(2)).requireLegacyOrderLineMutation(11L);
    }
}
