package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.common.constant.ProcessConstants;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 只读契约：用真实引擎的元数据/状态替身反驳业务关联被错配写 pass，不连接服务/库。 */
class WmCertifiedArrivalWorkflowReaderTest {
    static final String T="000000", P="own-process";
    RuntimeService runtime=mock(RuntimeService.class,RETURNS_DEEP_STUBS);
    HistoryService history=mock(HistoryService.class,RETURNS_DEEP_STUBS);
    TaskService tasks=mock(TaskService.class);
    TaskQuery taskQuery=mock(TaskQuery.class);
    WfMyBusinessServiceImpl business=mock(WfMyBusinessServiceImpl.class);
    HistoricProcessInstance historic=mock(HistoricProcessInstance.class);
    ProcessInstance active=mock(ProcessInstance.class);
    MockedStatic<TenantHelper> tenant;
    Object reader;
    Object read(){try{return reader.getClass().getMethod("read",String.class,Long.class).invoke(reader,T,81L);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    Object field(Object x,String name){try{return x.getClass().getMethod(name).invoke(x);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @BeforeEach void setup() throws Exception {
        Class<?> type;try{type=Class.forName("com.nexus.erp.wm.service.impl.WmCertifiedArrivalWorkflowReader");}catch(ClassNotFoundException missing){fail("缺少实际流程关联/状态读取契约");return;}
        reader=type.getConstructors()[0].newInstance(business,runtime,history,tasks);
        tenant=mockStatic(TenantHelper.class);tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn(T);
        var b=new WfMyBusiness();b.setDataId("81");b.setServiceImplName("wmCertifiedArrivalNoticeService");b.setProcessInstanceId(P);b.setProcessDefinitionId("own-definition");b.setActStatus("pass");
        when(business.getByDataId("81")).thenReturn(b);
        when(history.createHistoricProcessInstanceQuery().processInstanceId(P).singleResult()).thenReturn(historic);
        when(historic.getId()).thenReturn(P);when(historic.getProcessDefinitionId()).thenReturn("own-definition");when(historic.getTenantId()).thenReturn(T);when(historic.getBusinessKey()).thenReturn("81");when(historic.getEndTime()).thenReturn(new Date(1));
        when(runtime.createProcessInstanceQuery().processInstanceId(P).singleResult()).thenReturn(null);
        when(tasks.createTaskQuery()).thenReturn(taskQuery);when(taskQuery.processInstanceId(P)).thenReturn(taskQuery);when(taskQuery.count()).thenReturn(0L);
        historicVariable("dataId","81");historicVariable("noticeId",81L);historicVariable("noticeCode","AN001");historicVariable(ProcessConstants.PROCESS_STATUS_KEY,"completed");
    }
    void historicVariable(String name,Object v){when(history.createHistoricVariableInstanceQuery().processInstanceId(P).variableName(name).singleResult().getValue()).thenReturn(v);}
    @AfterEach void close(){if(tenant!=null)tenant.close();}
    @Test void actualCompletedOwnProcessProvesApproval(){Object proof=read();assertEquals("APPROVED",field(proof,"nextStatus"));assertEquals("AN001",field(proof,"businessCode"));}
    @Test void pollutedAssociationPassDoesNotHideRunningOwnInstance(){
        when(runtime.createProcessInstanceQuery().processInstanceId(P).singleResult()).thenReturn(active);when(historic.getEndTime()).thenReturn(null);
        when(active.getId()).thenReturn(P);when(active.getProcessDefinitionId()).thenReturn("own-definition");when(active.getTenantId()).thenReturn(T);when(active.getBusinessKey()).thenReturn("81");
        when(runtime.getVariable(P,"dataId")).thenReturn("81");when(runtime.getVariable(P,"noticeId")).thenReturn(81L);when(runtime.getVariable(P,"noticeCode")).thenReturn("AN001");when(runtime.getVariable(P,ProcessConstants.PROCESS_STATUS_KEY)).thenReturn("running");
        assertEquals("APPROVING",field(read(),"nextStatus"));
    }
    @Test void anotherCallbackServiceAssociationCannotProveApproval(){business.getByDataId("81").setServiceImplName("wmArrivalNoticeServiceImpl");assertThrows(ServiceException.class,this::read);}
    @Test void anotherProcessDefinitionCannotProveApproval(){when(historic.getProcessDefinitionId()).thenReturn("foreign-definition");assertThrows(ServiceException.class,this::read);}
    @Test void foreignTenantInstanceCannotProveApproval(){when(historic.getTenantId()).thenReturn("000001");assertThrows(ServiceException.class,this::read);}
    @Test void foreignImmutableBusinessKeyCannotBeRepairedByClientVariable(){when(historic.getBusinessKey()).thenReturn("999");assertThrows(ServiceException.class,this::read);}
    @Test void wrongSourceDataIdCannotProveApproval(){historicVariable("dataId","999");assertThrows(ServiceException.class,this::read);}
    @Test void missingBusinessCodeCannotProveApproval(){historicVariable("noticeCode",null);assertThrows(ServiceException.class,this::read);}
    @Test void activeTaskCannotBePaperedOverByCompletedVariable(){when(tasks.createTaskQuery().processInstanceId(P).count()).thenReturn(1L);assertThrows(ServiceException.class,this::read);}
    @Test void terminatedProcessIsNotApproved(){historicVariable(ProcessConstants.PROCESS_STATUS_KEY,"terminated");assertEquals("REJECTED",field(read(),"nextStatus"));}
    @Test void deletedHistoryWithSpoofedCompletedVariableIsNotApproved(){when(historic.getDeleteReason()).thenReturn("删除流程");assertThrows(ServiceException.class,this::read);}
}
