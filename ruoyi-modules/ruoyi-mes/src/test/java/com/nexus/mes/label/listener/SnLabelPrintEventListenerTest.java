package com.nexus.mes.label.listener;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.SerialIdentity;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 同步事件只保存任务，存储异常必须传播以回滚造码事务。 */
class SnLabelPrintEventListenerTest {
    final MesLabelTemplateMapper templates=mock(MesLabelTemplateMapper.class);
    final ProSnMapper sns=mock(ProSnMapper.class);
    final LabelPrintService printing=mock(LabelPrintService.class);
    final SerialIdentityPort identities=mock(SerialIdentityPort.class);
    MockedStatic<TenantHelper> tenant;
    final SnLabelPrintEventListener listener=new SnLabelPrintEventListener(templates,sns,new LabelRenderService(identities),printing);
    @BeforeAll static void metadata(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),MesLabelTemplate.class);}
    @BeforeEach void tenantContext(){tenant=mockStatic(TenantHelper.class);tenant.when(TenantHelper::getTenantId).thenReturn("000001");}
    @AfterEach void clearTenantContext(){tenant.close();}
    void enabled(String content){
        var template=new MesLabelTemplate();template.setTemplateId(5L);template.setTemplateRevision(7L);template.setLabelType("SN");template.setContent(content);
        when(templates.selectList(any())).thenReturn(List.of(template));var sn=new ProSn();sn.setTenantId("000001");sn.setSnId(1L);sn.setIdentityId(1001L);sn.setMaterialId(100L);sn.setSnCode("SN-1");when(sns.selectById(1L)).thenReturn(sn);
        when(identities.find("000001",1001L)).thenReturn(new SerialIdentity(1001L,"SN-1",100L,null,null));
        when(printing.newLog(any(),any(),any(),any())).thenReturn(MesLabelPrintLog.of(1L,"SN-1",5L,"TPL","synthetic",9100,"AUTO"));
    }
    @Test void noTemplatesDoNotCreateTasks(){when(templates.selectList(any())).thenReturn(List.of());listener.onSnCreated(new SnCreatedEvent(1L,"SN-1",null,1L));verifyNoInteractions(printing);}
    @Test void validEventOnlySubmitsFrozenTask(){enabled("{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\"}]}");listener.onSnCreated(new SnCreatedEvent(1L,"SN-1",null,1L));verify(printing).freezeFields(any(),any());verify(printing).submit(any(),contains("SN-1"),any());verify(printing,never()).send(any(),anyString(),any());}
    @Test void failedPersistenceEscapesInsteadOfBeingSwallowed(){enabled("{\"items\":[{\"type\":\"qrcode\",\"field\":\"snCode\"}]}");doThrow(new IllegalStateException("unavailable")).when(printing).submit(any(),anyString(),any());assertThrows(IllegalStateException.class,()->listener.onSnCreated(new SnCreatedEvent(1L,"SN-1",null,1L)));verify(printing,never()).recordFailure(any(),any());}
    @Test void badTemplateCreatesFailureTaskWithoutNetwork(){enabled("{not-json");listener.onSnCreated(new SnCreatedEvent(1L,"SN-1",null,1L));verify(printing).recordFailure(any(),contains("JSON"));verify(printing,never()).submit(any(),anyString(),any());}
    @Test void listenerHasSynchronousEventAnnotationAndNoAfterCommitFallback() throws Exception {
        var method=SnLabelPrintEventListener.class.getMethod("onSnCreated",SnCreatedEvent.class);
        assertNotNull(method.getAnnotation(org.springframework.context.event.EventListener.class));
        assertNull(method.getAnnotation(org.springframework.transaction.event.TransactionalEventListener.class));
    }
}
