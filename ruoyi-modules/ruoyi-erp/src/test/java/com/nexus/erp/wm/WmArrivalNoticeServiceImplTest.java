package com.nexus.erp.wm;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseDocumentGuard;
import com.nexus.erp.pu.service.PuPurchaseReceivingService;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeBo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.impl.WmArrivalNoticeServiceImpl;
import com.nexus.erp.wm.service.impl.WmServiceHelper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class WmArrivalNoticeServiceImplTest {
    @Mock WmArrivalNoticeMapper baseMapper;
    @Mock WmArrivalNoticeLineMapper noticeLineMapper;
    @Mock PuPurchaseOrderMapper purchaseOrderMapper;
    @Mock PuPurchaseReceivingService purchaseReceivingService;
    @Mock PuCertifiedPurchaseDocumentGuard certifiedDocumentGuard;
    @Mock AutoCodeUtil autoCodeUtil;
    @Mock IWfProcessService processService;
    @Mock WfMyBusinessServiceImpl businesses;
    @Mock FlowableProcInsIdHelper procInsIds;
    @Mock WmServiceHelper wm;
    @InjectMocks WmArrivalNoticeServiceImpl service;
    static MockedStatic<SpringUtil> spring;
    MockedStatic<TenantHelper> tenant;

    @BeforeAll static void tables() {
        spring=mockStatic(SpringUtil.class);
        Converter converter=new Converter() {
            public <S,T> T convert(S source,Class<T> type) {return BeanUtil.copyProperties(source,type);}
        };
        spring.when(()->SpringUtil.getBean(Converter.class)).thenReturn(converter);
        var assistant=new MapperBuilderAssistant(new MybatisConfiguration(),"");
        TableInfoHelper.initTableInfo(assistant,WmArrivalNotice.class);
        TableInfoHelper.initTableInfo(assistant,WmArrivalNoticeLine.class);
        TableInfoHelper.initTableInfo(assistant,PuPurchaseOrder.class);
    }
    @AfterAll static void closeSpring() {spring.close();}
    @BeforeEach void dependencies() {
        ReflectionTestUtils.setField(service,"baseMapper",baseMapper);
        ReflectionTestUtils.setField(service,"wm",new WmServiceHelper(autoCodeUtil,processService,businesses,procInsIds));
        tenant=mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
    }
    @AfterEach void closeTenant() {tenant.close();}

    @Test void unmarkedArrivalOrderWithLedgerIsRecognizedByTheRealGuardBeforeReceiptCreation() {
        var evidence=mock(com.nexus.erp.pu.mapper.PuCertifiedPurchaseDocumentGuardMapper.class);
        var realGuard=new PuCertifiedPurchaseDocumentGuard(evidence);
        ReflectionTestUtils.setField(service,"certifiedDocumentGuard",realGuard);
        WmArrivalNotice notice=notice(1L,"PO-A");notice.setStatus("APPROVING");
        when(baseMapper.selectById(1L)).thenReturn(notice);
        when(purchaseOrderMapper.selectOne(any())).thenReturn(order(20L,"PO-A"));
        when(evidence.countOrderAllocations("000000",20L)).thenReturn(1L);
        ServiceException error=assertThrows(ServiceException.class,()->service.afterFlowHandle(flow(ActStatus.pass)));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"));
        verify(evidence).countOrderAllocations("000000",20L);
        verifyNoInteractions(purchaseReceivingService,noticeLineMapper);
        verify(baseMapper,never()).updateById(any(WmArrivalNotice.class));
        assertEquals("APPROVING",notice.getStatus());
    }
    @Test void draftReceiptCreationDoesNotPretendThePurchaseOrderWasReceived() {
        WmArrivalNotice notice=notice(1L,"PO-A"); notice.setStatus("APPROVING");
        when(baseMapper.selectById(1L)).thenReturn(notice);
        lenient().when(purchaseOrderMapper.selectOne(any())).thenReturn(order(20L,"PO-A"));
        when(purchaseReceivingService.createMaterialReceiveFromNotice(1L)).thenReturn(40L);
        service.afterFlowHandle(flow(ActStatus.pass));
        assertEquals("APPROVED",notice.getStatus());
        verify(baseMapper).updateById(notice);
        verify(purchaseOrderMapper,never()).updateById(any(PuPurchaseOrder.class));
        verify(purchaseOrderMapper,never()).update(any(),any());
    }
    @Test void certifiedArrivalApprovalRefusesBeforeDraftReceiptOrAnyStatusWrite() {
        WmArrivalNotice notice=notice(1L,"PO-A"); notice.setStatus("APPROVING");
        when(baseMapper.selectById(1L)).thenReturn(notice);
        lenient().when(purchaseOrderMapper.selectOne(any())).thenReturn(order(20L,"PO-A"));
        ServiceException error=new ServiceException("protected-document");
        lenient().doThrow(error).when(certifiedDocumentGuard).requireLegacyOrderMutation(20L);
        assertSame(error,assertThrows(ServiceException.class,()->service.afterFlowHandle(flow(ActStatus.pass))));
        assertEquals("APPROVING",notice.getStatus());
        verifyNoInteractions(purchaseReceivingService,noticeLineMapper);
        verify(baseMapper,never()).updateById(any(WmArrivalNotice.class));
        verify(purchaseOrderMapper,never()).updateById(any(PuPurchaseOrder.class));
    }
    @Test void certifiedArrivalMutatorsCheckExistingOrderBeforeAnyWritesOrWorkflow() {
        lenient().when(baseMapper.selectById(1L)).thenReturn(notice(1L,"PO-A"));
        lenient().when(purchaseOrderMapper.selectOne(any())).thenReturn(order(20L,"PO-A"));
        ServiceException error=new ServiceException("protected-document");
        lenient().doThrow(error).when(certifiedDocumentGuard).requireLegacyOrderMutation(20L);
        WmArrivalNoticeBo bo=new WmArrivalNoticeBo();bo.setNoticeId(1L);bo.setPurOrderCode("PO-B");
        for(Runnable call:List.<Runnable>of(()->service.updateByBo(bo),()->service.deleteByIds(new Long[]{1L}),
            ()->service.submit("1","flow"),()->service.afterFlowHandle(flow(ActStatus.doing)),()->service.afterFlowHandle(flow(ActStatus.reject)))) {
            assertSame(error,assertThrows(ServiceException.class,call::run));
        }
        verifyNoInteractions(noticeLineMapper,purchaseReceivingService,processService,autoCodeUtil);
        verify(baseMapper,never()).updateById(any(WmArrivalNotice.class));
        verify(baseMapper,never()).deleteBatchIds(anyCollection());
    }
    @Test void newArrivalForCertifiedOrderRefusesBeforeNumberGenerationOrInsert() {
        lenient().when(purchaseOrderMapper.selectOne(any())).thenReturn(order(20L,"PO-A"));
        ServiceException error=new ServiceException("protected-document");
        lenient().doThrow(error).when(certifiedDocumentGuard).requireLegacyOrderMutation(20L);
        WmArrivalNoticeBo bo=new WmArrivalNoticeBo();bo.setPurOrderCode("PO-A");bo.setNoticeCode("NOTICE");
        assertSame(error,assertThrows(ServiceException.class,()->service.insertByBo(bo)));
        verifyNoInteractions(baseMapper,noticeLineMapper,purchaseReceivingService,autoCodeUtil);
    }
    @Test void historicalArrivalRemainsReadableWithoutChangingPurchaseOrder() {
        lenient().when(baseMapper.selectVoById(1L)).thenReturn(null);
        assertNull(service.selectById(1L));
        verifyNoInteractions(purchaseOrderMapper,certifiedDocumentGuard,purchaseReceivingService);
    }
    private WfMyBusiness flow(String status) {var value=new WfMyBusiness();value.setDataId("1");value.setActStatus(status);return value;}
    private WmArrivalNotice notice(Long id,String code) {var value=new WmArrivalNotice();value.setNoticeId(id);value.setPurOrderCode(code);return value;}
    private PuPurchaseOrder order(Long id,String code) {var value=new PuPurchaseOrder();value.setOrderId(id);value.setOrderNo(code);value.setStatus("APPROVED");return value;}
}
