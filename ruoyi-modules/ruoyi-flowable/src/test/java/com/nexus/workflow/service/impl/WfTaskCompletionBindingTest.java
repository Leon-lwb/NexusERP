package com.nexus.workflow.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.UserService;
import com.nexus.common.core.utils.SpringContextHolder;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.utils.TaskUtils;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.online.service.IFormService;
import com.nexus.system.service.CommonService;
import com.nexus.system.service.ISysUserService;
import com.nexus.workflow.domain.WfDeployForm;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.domain.bo.WfTaskBo;
import com.nexus.workflow.mapper.FlowTaskMapper;
import com.nexus.workflow.mapper.WfDeployFormMapper;
import com.nexus.workflow.service.IWfCopyService;
import com.nexus.workflow.service.WfCallBackServiceI;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.NativeProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.NativeTaskQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 直接调用真实任务完成入口，验证父绑定校验早于变量剥离和全部写入。
 * 所有引擎、表单、签章及业务边界均为替身，不访问数据库或运行中服务。
 */
class WfTaskCompletionBindingTest {

    private static final String TASK_ID = "task-A";
    private static final String INSTANCE_ID = "process-A";
    private static final String DEFINITION_ID = "definition-A:1";
    private static final String DATA_ID = "10001";
    private static final String TENANT_ID = "000001";
    private static final String USERNAME = "approver-A";

    private TaskService tasks;
    private RuntimeService runtime;
    private RepositoryService repository;
    private ManagementService management;
    private WfMyBusinessServiceImpl businesses;
    private WfDeployFormMapper forms;
    private FlowTaskMapper flowTasks;
    private IWfCopyService copies;
    private ESignatureService signatures;
    private IFormService onlineForm;
    private WfCallBackServiceI callback;
    private WfTaskServiceImpl service;
    private TaskQuery taskQuery;
    private NativeTaskQuery lockedTaskQuery;
    private NativeProcessInstanceQuery lockedInstanceQuery;
    private TaskEntityImpl task;
    private ProcessInstance instance;
    private WfMyBusiness business;
    private WfTaskBo request;
    private MockedStatic<TenantHelper> tenants;
    private MockedStatic<TaskUtils> taskUsers;
    private MockedStatic<SpringContextHolder> beans;

    @BeforeEach
    void setUp() {
        tasks = mock(TaskService.class);
        runtime = mock(RuntimeService.class);
        repository = mock(RepositoryService.class);
        management = mock(ManagementService.class);
        businesses = mock(WfMyBusinessServiceImpl.class);
        forms = mock(WfDeployFormMapper.class);
        flowTasks = mock(FlowTaskMapper.class);
        copies = mock(IWfCopyService.class);
        signatures = mock(ESignatureService.class);
        onlineForm = mock(IFormService.class);
        callback = mock(WfCallBackServiceI.class);
        service = spy(new WfTaskServiceImpl(repository, runtime, tasks, mock(HistoryService.class),
            mock(UserService.class), copies, mock(CommonService.class), mock(ISysUserService.class),
            businesses, forms, flowTasks, signatures));
        ReflectionTestUtils.setField(service, "managementService", management);
        doReturn(null).when(service).getNextFlowNode(anyString(), any());

        task = task(1);
        taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        when(tasks.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of());
        lockedTaskQuery = mock(NativeTaskQuery.class, RETURNS_SELF);
        when(tasks.createNativeTaskQuery()).thenReturn(lockedTaskQuery);
        when(lockedTaskQuery.singleResult()).thenReturn(task);
        lockedInstanceQuery = mock(NativeProcessInstanceQuery.class, RETURNS_SELF);
        when(runtime.createNativeProcessInstanceQuery()).thenReturn(lockedInstanceQuery);
        instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn(INSTANCE_ID);
        when(instance.getProcessInstanceId()).thenReturn(INSTANCE_ID);
        when(instance.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
        when(instance.getBusinessKey()).thenReturn(DATA_ID);
        when(instance.getTenantId()).thenReturn(TENANT_ID);
        when(lockedInstanceQuery.singleResult()).thenReturn(instance);
        when(management.getTableName(ProcessInstance.class)).thenReturn("ACT_RU_EXECUTION");
        when(management.getTableName(Task.class)).thenReturn("ACT_RU_TASK");

        business = business(DATA_ID, INSTANCE_ID);
        when(businesses.list(any(Wrapper.class))).thenReturn(List.of(business));
        when(businesses.getByDataId(anyString())).thenAnswer(invocation -> {
            String dataId = invocation.getArgument(0);
            return DATA_ID.equals(dataId) ? business : business(dataId, "process-B");
        });
        when(businesses.updateById(any(WfMyBusiness.class))).thenReturn(true);
        when(copies.makeCopy(any())).thenReturn(true);

        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn(DEFINITION_ID);
        when(definition.getDeploymentId()).thenReturn("deployment-A");
        ProcessDefinitionQuery definitions = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
        when(repository.createProcessDefinitionQuery()).thenReturn(definitions);
        when(definitions.singleResult()).thenReturn(definition);
        WfDeployForm form = new WfDeployForm();
        form.setTableName("online_approval");
        when(forms.selectOne(any(Wrapper.class))).thenReturn(form);
        when(onlineForm.addOrUpdateOnlineDataByName(anyString(), anyMap())).thenReturn(1);
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("approval");
        UserTask node = new UserTask();
        node.setId("approve");
        process.addFlowElement(node);
        model.addProcess(process);
        when(repository.getBpmnModel(DEFINITION_ID)).thenReturn(model);

        tenants = mockStatic(TenantHelper.class);
        tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT_ID);
        taskUsers = mockStatic(TaskUtils.class);
        taskUsers.when(TaskUtils::getUserName).thenReturn(USERNAME);
        taskUsers.when(TaskUtils::getCandidateGroup).thenReturn(List.of("ROLE1"));
        beans = mockStatic(SpringContextHolder.class);
        beans.when(() -> SpringContextHolder.getBean("wfCallback")).thenReturn(callback);
        beans.when(() -> SpringContextHolder.getBean(IFormService.class)).thenReturn(onlineForm);
        // 只模拟事务线程标志，所有持久化组件仍为替身，不建立真实数据库连接。
        TransactionSynchronizationManager.setActualTransactionActive(true);
        request = request(DATA_ID, INSTANCE_ID);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        beans.close();
        taskUsers.close();
        tenants.close();
    }

    private TaskEntityImpl task(int revision) {
        TaskEntityImpl result = new TaskEntityImpl();
        result.setId(TASK_ID);
        result.setProcessInstanceId(INSTANCE_ID);
        result.setProcessDefinitionId(DEFINITION_ID);
        result.setTaskDefinitionKey("approve");
        result.setExecutionId("execution-A");
        result.setAssignee(USERNAME);
        result.setTenantId(TENANT_ID);
        result.setName("采购审批");
        result.setRevision(revision);
        result.setSuspensionState(1);
        return result;
    }

    private WfMyBusiness business(String dataId, String instanceId) {
        WfMyBusiness result = new WfMyBusiness();
        result.setId(101L);
        result.setDataId(dataId);
        result.setProcessInstanceId(instanceId);
        result.setProcessDefinitionId(DEFINITION_ID);
        result.setServiceImplName("wfCallback");
        result.setActStatus(ActStatus.doing);
        return result;
    }

    private WfTaskBo request(String dataId, String instanceId) {
        WfTaskBo result = new WfTaskBo();
        result.setTaskId(TASK_ID);
        result.setDataId(dataId);
        result.setProcInsId(instanceId);
        result.setVariables(new HashMap<>(Map.of("approved", true, "needSign", false,
            "signCredential", "仅用于离线测试的凭证")));
        return result;
    }

    private void assertRejected(String message) {
        when(runtime.getVariable("execution-A", "needSign")).thenReturn(true);
        Map<String, Object> variablesBefore = new HashMap<>(request.getVariables());
        String instanceBefore = request.getProcInsId();
        ServiceException error = assertThrows(ServiceException.class, () -> service.complete(request),
            "非法父绑定或过期状态必须以业务异常失败关闭");
        assertTrue(error.getMessage().contains(message), "异常必须具体说明拒绝原因");
        assertEquals(variablesBefore, request.getVariables(), "拒绝前不得剥离或改写请求变量");
        assertEquals(instanceBefore, request.getProcInsId(), "拒绝前不得改写请求实例");
        assertNoSideEffects();
    }

    private void assertNoSideEffects() {
        verify(onlineForm, never()).addOrUpdateOnlineDataByName(anyString(), anyMap());
        verify(tasks, never()).addComment(any(), any(), any(), any());
        verify(tasks, never()).setAssignee(any(), any());
        verify(tasks, never()).claim(any(), any());
        verify(tasks, never()).resolveTask(any());
        verify(tasks, never()).complete(anyString());
        verify(tasks, never()).complete(anyString(), anyMap(), anyBoolean());
        verify(signatures, never()).sign(any());
        verify(businesses, never()).updateById(any(WfMyBusiness.class));
        verify(callback, never()).afterFlowHandle(any());
        verify(copies, never()).makeCopy(any());
        verify(runtime, never()).setVariable(anyString(), anyString(), any());
        verify(runtime, never()).setVariables(anyString(), anyMap());
        verify(tasks, never()).setVariable(anyString(), anyString(), any());
        verify(tasks, never()).setVariables(anyString(), anyMap());
        verifyNoInteractions(forms, flowTasks);
        verify(repository, never()).getBpmnModel(any());
    }

    @Test
    @DisplayName("任务来自甲而请求业务来自乙时，在全部副作用之前拒绝")
    void wrongBusinessIsRejectedBeforeAllWrites() {
        request.setDataId("10002");
        assertRejected("任务与流程业务关联不一致");
    }

    @Test
    @DisplayName("请求流程实例不同于原任务实例时早拒绝")
    void wrongRequestedInstanceIsRejected() {
        request.setProcInsId("process-B");
        assertRejected("请求流程实例与任务不一致");
    }

    @ParameterizedTest(name = "业务关联任务缺少数据编号：{index}")
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("业务关联任务漏传数据编号不能跳过校验或丢弃回调")
    void missingBusinessDataIsRejected(String dataId) {
        request.setDataId(dataId);
        assertRejected("业务关联任务必须提供数据编号");
    }

    @Test
    @DisplayName("同名办理人不能办理另一租户的任务")
    void foreignTenantTaskIsRejected() {
        task.setTenantId("000002");
        assertRejected("任务不属于当前租户");
    }

    @Test
    @DisplayName("开启多租户但上下文缺失时拒绝")
    void missingTenantContextIsRejected() {
        tenants.when(TenantHelper::getTenantId).thenReturn(null);
        assertRejected("当前租户上下文缺失");
    }

    @Test
    @DisplayName("任务与锁定实例租户不一致时拒绝")
    void foreignTenantInstanceIsRejected() {
        when(instance.getTenantId()).thenReturn("000002");
        assertRejected("任务与流程实例不一致");
    }

    @Test
    @DisplayName("任务已不存在的重复请求不产生副作用")
    void missingTaskIsRejected() {
        when(taskQuery.singleResult()).thenReturn(null);
        assertRejected("任务不存在");
    }

    @Test
    @DisplayName("挂起任务不保存在线表单且不签署")
    void suspendedTaskIsRejected() {
        task.setSuspensionState(2);
        assertRejected("任务已挂起");
    }

    @Test
    @DisplayName("等待实例锁期间流程结束时早拒绝")
    void endedInstanceAfterLockIsRejected() {
        when(lockedInstanceQuery.singleResult()).thenReturn(null);
        assertRejected("流程实例不存在或已结束");
    }

    @Test
    @DisplayName("锁定实例挂起时早拒绝")
    void suspendedInstanceIsRejected() {
        when(instance.isSuspended()).thenReturn(true);
        assertRejected("流程实例已挂起");
    }

    @Test
    @DisplayName("并发请求等待锁后任务消失时不产生任何写入")
    void taskCompletedWhileWaitingIsRejected() {
        when(lockedTaskQuery.singleResult()).thenReturn(null);
        assertRejected("任务已被办理或状态已变化");
    }

    @Test
    @DisplayName("并发修改任务版本后旧请求不能继续签署或办结")
    void taskRevisionChangedWhileWaitingIsRejected() {
        when(lockedTaskQuery.singleResult()).thenReturn(task(2));
        assertRejected("任务已被办理或状态已变化");
    }

    @Test
    @DisplayName("锁后办理人发生变化时再次验证既有权限")
    void lockedTaskPermissionIsRechecked() {
        TaskEntityImpl changed = task(1);
        changed.setAssignee("approver-B");
        when(lockedTaskQuery.singleResult()).thenReturn(changed);
        when(tasks.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of());
        assertRejected("当前用户不是该任务的办理人");
    }

    @Test
    @DisplayName("同一实例出现两条业务关联时不任选一条回调")
    void duplicateBusinessAssociationsAreRejected() {
        when(businesses.list(any(Wrapper.class))).thenReturn(List.of(business, business("10002", INSTANCE_ID)));
        assertRejected("流程实例存在重复业务关联");
    }

    @Test
    @DisplayName("实际业务关联实例不同于任务时早拒绝")
    void inconsistentBusinessInstanceIsRejected() {
        business.setProcessInstanceId("process-B");
        assertRejected("任务与流程业务关联不一致");
    }

    @Test
    @DisplayName("实际业务关联定义不同于原任务时早拒绝")
    void inconsistentBusinessDefinitionIsRejected() {
        business.setProcessDefinitionId("definition-B:1");
        assertRejected("任务与流程业务关联不一致");
    }

    @Test
    @DisplayName("引擎业务键与实际业务数据不同不能回调")
    void inconsistentEngineBusinessKeyIsRejected() {
        when(instance.getBusinessKey()).thenReturn("10002");
        assertRejected("任务与流程业务关联不一致");
    }

    @Test
    @DisplayName("业务回调配置缺失也必须在表单写入前拒绝")
    void missingCallbackIsRejectedBeforeFormWrite() {
        business.setServiceImplName("  ");
        assertRejected("流程业务关联缺少回调配置");
    }

    @Test
    @DisplayName("无实际关联不能由客户端数据编号指定其他业务回调")
    void suppliedDataWithoutAssociationIsRejected() {
        when(businesses.list(any(Wrapper.class))).thenReturn(List.of());
        assertRejected("任务没有对应的流程业务关联");
    }

    @Test
    @DisplayName("无事务上下文不能使用立即释放的锁继续办结")
    void missingTransactionIsRejected() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertRejected("任务办结必须在事务中执行");
    }

    @Test
    @DisplayName("无业务关联的通用任务兼容办结且不执行业务回调")
    void genericTaskWithoutAssociationStillCompletes() {
        when(businesses.list(any(Wrapper.class))).thenReturn(List.of());
        request.setDataId(null);
        request.setProcInsId(null);
        assertDoesNotThrow(() -> service.complete(request), "无关联通用任务必须保持兼容");
        verify(tasks).complete(eq(TASK_ID), anyMap(), eq(false));
        verify(tasks).addComment(eq(TASK_ID), eq(INSTANCE_ID), anyString(), isNull());
        verify(businesses, never()).updateById(any(WfMyBusiness.class));
        verifyNoInteractions(callback);
        assertEquals(INSTANCE_ID, request.getProcInsId(), "抄送必须使用任务的权威实例");
    }

    @Test
    @DisplayName("正常业务任务按真实剩余任务回调原业务且不再次按客户端编号查找")
    void validBusinessCompletesAndCallsOriginalCallback() {
        request.setProcInsId(null);
        assertDoesNotThrow(() -> service.complete(request), "一致的业务父绑定必须允许正常办结");
        verify(businesses).updateById(same(business));
        verify(callback).afterFlowHandle(same(business));
        verify(businesses, never()).getByDataId(anyString());
        assertEquals(ActStatus.pass, business.getActStatus(), "无剩余任务才可回写通过");
        verify(tasks).addComment(eq(TASK_ID), eq(INSTANCE_ID), anyString(), isNull());
    }

    @Test
    @DisplayName("并行任务展示编号不同不误拒绝真实任务，剩余任务维持审批中")
    void parallelDisplayTaskDoesNotReplaceActualTask() {
        business.setTaskId("another-parallel-task");
        TaskEntityImpl remaining = task(1);
        remaining.setId("remaining-task");
        when(taskQuery.list()).thenReturn(List.of(remaining));
        assertDoesNotThrow(() -> service.complete(request), "业务展示任务编号不能取代引擎任务关系");
        assertEquals(ActStatus.doing, business.getActStatus(), "仍有真实活动任务必须保持审批中");
        assertEquals("remaining-task", business.getTaskId(), "展示任务从真实活动任务回写");
        verify(callback).afterFlowHandle(same(business));
    }

    @Test
    @DisplayName("服务端需签章不能被请求否定，表单过滤不能破坏签章快照")
    void signatureSnapshotAndServerGateArePreserved() {
        when(runtime.getVariable("execution-A", "needSign")).thenReturn(true);
        when(onlineForm.addOrUpdateOnlineDataByName(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> variables = invocation.getArgument(1);
            assertFalse(variables.containsKey("signCredential"), "表单不得接收签章凭证");
            assertFalse(variables.containsKey("needSign"), "表单不得接收客户端签章开关");
            variables.clear();
            return 1;
        });
        assertDoesNotThrow(() -> service.complete(request), "表单过滤后仍须用原始签章快照通过关卡");
        ArgumentCaptor<SignCommand> command = ArgumentCaptor.forClass(SignCommand.class);
        verify(signatures).sign(command.capture());
        assertEquals("仅用于离线测试的凭证", command.getValue().credential(), "签署必须保留原凭证快照");
        verify(tasks).complete(TASK_ID);
        assertTrue(request.getVariables().isEmpty(), "写引擎的变量不得残留签章凭证或控制字段");
    }

    @Test
    @DisplayName("签署失败不推进引擎或业务回调")
    void failedSignatureStillStopsEngineCompletion() {
        request.setVariables(new HashMap<>(Map.of("signCredential", "仅用于离线测试的凭证")));
        when(runtime.getVariable("execution-A", "needSign")).thenReturn(true);
        doThrow(new ServiceException("测试签署拒绝")).when(signatures).sign(any());
        ServiceException error = assertThrows(ServiceException.class, () -> service.complete(request),
            "签署拒绝必须向上抛出而不是吞掉");
        assertEquals("测试签署拒绝", error.getMessage(), "必须保留签章失败原因");
        verify(tasks, never()).complete(anyString());
        verify(tasks, never()).complete(anyString(), anyMap(), anyBoolean());
        verify(tasks, never()).addComment(any(), any(), any(), any());
        verify(tasks, never()).setAssignee(any(), any());
        verify(businesses, never()).updateById(any(WfMyBusiness.class));
        verifyNoInteractions(callback, copies, onlineForm);
    }

    @Test
    @DisplayName("委派待处理分支仍先验证绑定，再使用原实例评论和解决任务")
    void delegationUsesVerifiedBinding() {
        task.setDelegationState(DelegationState.PENDING);
        request.setProcInsId(null);
        when(taskQuery.list()).thenReturn(List.of(task));
        assertDoesNotThrow(() -> service.complete(request), "合法委派办理不能被父绑定修复破坏");
        verify(tasks).resolveTask(TASK_ID);
        verify(tasks).addComment(eq(TASK_ID), eq(INSTANCE_ID), anyString(), isNull());
        verify(tasks, never()).complete(anyString());
        verify(tasks, never()).complete(anyString(), anyMap(), anyBoolean());
        assertEquals(ActStatus.doing, business.getActStatus(), "委派解决后仍在途不能误判通过");
    }

    @Test
    @DisplayName("现有候选人办理权限保持不变")
    void candidatePermissionIsPreserved() {
        task.setAssignee("another-user");
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn(IdentityLinkType.CANDIDATE);
        when(link.getUserId()).thenReturn(USERNAME);
        when(tasks.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of(link));
        assertDoesNotThrow(() -> service.complete(request), "真实候选人仍应可以办理");
        verify(callback).afterFlowHandle(same(business));
    }

    @Test
    @DisplayName("现有候选组办理权限保持不变")
    void candidateGroupPermissionIsPreserved() {
        task.setAssignee("another-user");
        IdentityLink link = mock(IdentityLink.class);
        when(link.getType()).thenReturn(IdentityLinkType.CANDIDATE);
        when(link.getGroupId()).thenReturn("ROLE1");
        when(tasks.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of(link));
        assertDoesNotThrow(() -> service.complete(request), "真实候选组成员仍应可以办理");
        verify(callback).afterFlowHandle(same(business));
    }

    @Test
    @DisplayName("非办理人且非候选人仍早拒绝，不扩大已有权限边界")
    void unrelatedUserIsRejected() {
        task.setAssignee("another-user");
        when(tasks.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of());
        assertRejected("当前用户不是该任务的办理人");
    }

    @ParameterizedTest(name = "任务编号缺失：{index}")
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("任务编号缺失时不能把查询条件退化为任意任务")
    void missingTaskIdIsRejected(String taskId) {
        request.setTaskId(taskId);
        assertRejected("任务编号不能为空");
    }

    @ParameterizedTest(name = "业务已经终态：{0}")
    @ValueSource(strings = {"审批通过", "取消", "撤回", "审批异常"})
    @DisplayName("业务已处于终态而引擎仍有任务时失败关闭")
    void terminalBusinessIsRejected(String status) {
        business.setActStatus(status);
        assertRejected("流程业务已结束或异常");
    }

    @Test
    @DisplayName("锁后任务不能被替换为另一实例的任务")
    void changedLockedTaskInstanceIsRejected() {
        TaskEntityImpl changed = task(1);
        changed.setProcessInstanceId("process-B");
        when(lockedTaskQuery.singleResult()).thenReturn(changed);
        assertRejected("任务已被办理或状态已变化");
    }

    @Test
    @DisplayName("业务任务缺失引擎业务键不能当作关联已核实")
    void missingEngineBusinessKeyIsRejected() {
        when(instance.getBusinessKey()).thenReturn(null);
        assertRejected("任务与流程业务关联不一致");
    }

    @Test
    @DisplayName("锁定查询使用真实表名、参数占位和当前租户，业务关联读取有界")
    void lockingReadsAreParameterizedTenantScopedAndBounded() {
        assertDoesNotThrow(() -> service.complete(request), "合法父绑定应通过事务内校验");
        ArgumentCaptor<String> instanceSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> taskSql = ArgumentCaptor.forClass(String.class);
        verify(lockedInstanceQuery).sql(instanceSql.capture());
        verify(lockedTaskQuery).sql(taskSql.capture());
        assertTrue(instanceSql.getValue().contains("ACT_RU_EXECUTION"), "实例表名必须来自引擎管理接口");
        assertTrue(taskSql.getValue().contains("ACT_RU_TASK"), "任务表名必须来自引擎管理接口");
        assertTrue(instanceSql.getValue().endsWith("FOR UPDATE"), "实例锁必须保持到事务结束");
        assertTrue(taskSql.getValue().endsWith("FOR UPDATE"), "任务必须用锁定读取避免旧快照");
        assertFalse(instanceSql.getValue().contains(INSTANCE_ID), "实例编号不能拼进原生语句");
        assertFalse(taskSql.getValue().contains(TASK_ID), "任务编号不能拼进原生语句");
        verify(lockedInstanceQuery).parameter("processInstanceId", INSTANCE_ID);
        verify(lockedInstanceQuery).parameter("tenantId", TENANT_ID);
        verify(lockedTaskQuery).parameter("taskId", TASK_ID);
        verify(lockedTaskQuery).parameter("processInstanceId", INSTANCE_ID);
        verify(lockedTaskQuery).parameter("tenantId", TENANT_ID);
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(businesses, times(2)).list(query.capture());
        assertTrue(query.getAllValues().get(0).getSqlSegment().contains("process_instance_id"), "正向关联必须按原任务实例查询");
        assertTrue(query.getAllValues().get(1).getSqlSegment().contains("data_id"), "反向关联必须按引擎原业务键查询");
        for (Wrapper<?> wrapper : query.getAllValues()) {
            assertTrue(wrapper.getSqlSegment().contains("tenant_id"), "正反向查询都必须限定实际租户列");
            assertTrue(wrapper.getSqlSegment().endsWith("LIMIT 2 FOR UPDATE"), "每个方向最多读取两条即可识别重复关联");
            assertTrue(((QueryWrapper<?>) wrapper).getParamNameValuePairs().containsValue(TENANT_ID),
                "租户条件必须使用当前上下文而不是客户端变量");
        }
        assertTrue(((QueryWrapper<?>) query.getAllValues().get(1)).getParamNameValuePairs().containsValue(DATA_ID),
            "反向查询必须使用原实例业务键，不凭请求数据编号补证");
        InOrder order = inOrder(runtime, tasks, businesses, onlineForm);
        order.verify(runtime).createNativeProcessInstanceQuery();
        order.verify(tasks).createNativeTaskQuery();
        order.verify(businesses, times(2)).list(any(Wrapper.class));
        order.verify(onlineForm).addOrUpdateOnlineDataByName(anyString(), anyMap());
    }

    @Test
    @DisplayName("显式关闭多租户的通用任务仍保持兼容，不强造租户字段")
    void genericTaskWithTenantDisabledStillCompletes() {
        tenants.when(TenantHelper::isEnable).thenReturn(false);
        tenants.when(TenantHelper::getTenantId).thenReturn(null);
        when(businesses.list(any(Wrapper.class))).thenReturn(List.of());
        request.setDataId(null);
        assertDoesNotThrow(() -> service.complete(request), "显式单租户模式必须沿用既有行为");
        verify(tasks).complete(eq(TASK_ID), anyMap(), eq(false));
        verify(lockedInstanceQuery, never()).parameter(eq("tenantId"), any());
        verify(lockedTaskQuery, never()).parameter(eq("tenantId"), any());
    }

    @Test
    @DisplayName("两个并发请求只有锁后仍存活的请求写入，失效请求变量保持原样")
    void concurrentLoserHasNoSideEffectsAfterWaiting() throws Exception {
        when(runtime.getVariable("execution-A", "needSign")).thenReturn(true);
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch winnerHandled = new CountDownLatch(1);
        AtomicInteger instanceLocks = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean();
        when(taskQuery.singleResult()).thenAnswer(invocation -> {
            bothRead.countDown();
            return task(1);
        });
        // 替身只模拟等待锁后的读取结果，不把此用例作为真实数据库行锁验收。
        when(lockedInstanceQuery.singleResult()).thenAnswer(invocation -> {
            if (instanceLocks.incrementAndGet() == 1) {
                assertTrue(bothRead.await(10, TimeUnit.SECONDS), "两个请求必须均取得办结前快照");
            } else {
                assertTrue(winnerHandled.await(10, TimeUnit.SECONDS), "后来的请求须在首请求办结后读取任务");
            }
            return instance;
        });
        when(lockedTaskQuery.singleResult()).thenAnswer(invocation -> completed.get() ? null : task(1));
        doAnswer(invocation -> { completed.set(true); return null; })
            .when(tasks).complete(anyString(), anyMap(), anyBoolean());
        doAnswer(invocation -> { winnerHandled.countDown(); return null; })
            .when(callback).afterFlowHandle(any());
        WfTaskBo firstRequest = request(DATA_ID, INSTANCE_ID);
        WfTaskBo secondRequest = request(DATA_ID, INSTANCE_ID);
        Map<String, Object> originalVariables = new HashMap<>(firstRequest.getVariables());
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<ServiceException> first = workers.submit(() -> completeInIsolatedThread(firstRequest));
            Future<ServiceException> second = workers.submit(() -> completeInIsolatedThread(secondRequest));
            ServiceException firstError = first.get(15, TimeUnit.SECONDS);
            ServiceException secondError = second.get(15, TimeUnit.SECONDS);
            assertEquals(1, (firstError == null ? 0 : 1) + (secondError == null ? 0 : 1),
                "同任务的两个并发请求必须恰好一个失败关闭");
            ServiceException error = firstError == null ? secondError : firstError;
            WfTaskBo loser = firstError == null ? secondRequest : firstRequest;
            assertTrue(error.getMessage().contains("任务已被办理或状态已变化"), "落败请求必须因锁后失效而拒绝");
            assertEquals(originalVariables, loser.getVariables(), "落败请求不得发生变量剥离");
            verify(onlineForm, times(1)).addOrUpdateOnlineDataByName(anyString(), anyMap());
            verify(tasks, times(1)).addComment(any(), any(), any(), any());
            verify(tasks, times(1)).setAssignee(any(), any());
            verify(tasks, times(1)).complete(anyString(), anyMap(), anyBoolean());
            verify(businesses, times(1)).updateById(any(WfMyBusiness.class));
            verify(callback, times(1)).afterFlowHandle(any());
            verify(copies, times(1)).makeCopy(any());
            verify(signatures, times(1)).sign(any());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS), "离线测试线程必须全部退出");
        }
    }

    private ServiceException completeInIsolatedThread(WfTaskBo threadRequest) {
        try (MockedStatic<TenantHelper> threadTenants = mockStatic(TenantHelper.class);
             MockedStatic<TaskUtils> threadUsers = mockStatic(TaskUtils.class);
             MockedStatic<SpringContextHolder> threadBeans = mockStatic(SpringContextHolder.class)) {
            threadTenants.when(TenantHelper::isEnable).thenReturn(true);
            threadTenants.when(TenantHelper::getTenantId).thenReturn(TENANT_ID);
            threadUsers.when(TaskUtils::getUserName).thenReturn(USERNAME);
            threadUsers.when(TaskUtils::getCandidateGroup).thenReturn(List.of("ROLE1"));
            threadBeans.when(() -> SpringContextHolder.getBean("wfCallback")).thenReturn(callback);
            threadBeans.when(() -> SpringContextHolder.getBean(IFormService.class)).thenReturn(onlineForm);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                service.complete(threadRequest);
                return null;
            } catch (ServiceException error) {
                return error;
            } finally {
                TransactionSynchronizationManager.clear();
            }
        }
    }

    @Test
    @DisplayName("回调名称实际解析不到合法服务时，不能先写表单或剥离凭证")
    void invalidCallbackBeanIsRejectedBeforeAllWrites() {
        beans.when(() -> SpringContextHolder.getBean("wfCallback")).thenReturn(new Object());
        assertRejected("流程业务回调配置不可用");
    }

    @Test
    @DisplayName("回调服务不存在时转换为具体业务拒绝，且没有副作用")
    void missingCallbackBeanIsRejectedBeforeAllWrites() {
        beans.when(() -> SpringContextHolder.getBean("wfCallback"))
                .thenThrow(new NoSuchBeanDefinitionException("wfCallback"));
        assertRejected("流程业务回调配置不可用");
    }

    @Test
    @DisplayName("空请求在读取任务和任何副作用之前拒绝")
    void nullRequestIsRejectedBeforeTaskQuery() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.complete(null),
            "空请求必须是明确业务拒绝而不是空指针异常");
        assertTrue(error.getMessage().contains("任务编号不能为空"), "缺少任务请求必须说明参数错误");
        verifyNoInteractions(tasks, runtime, businesses);
        assertNoSideEffects();
    }

    @ParameterizedTest(name = "业务状态缺失：{index}")
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("业务状态缺失不能按正常在途状态继续写入")
    void missingBusinessStatusIsRejected(String status) {
        business.setActStatus(status);
        assertRejected("流程业务已结束或异常");
    }

    @Test
    @DisplayName("关联已重绑定另一实例时，旧任务省略数据编号也必须在所有副作用前拒绝")
    void reverseReboundBusinessCannotBecomeGenericByOmission() {
        business.setProcessInstanceId("process-B");
        when(businesses.list(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().contains("process_instance_id") ? List.of() : List.of(business);
        });
        request.setDataId(null);
        request.setProcInsId(null);
        assertRejected("任务与流程业务关联不一致");
        verify(businesses, never()).getByDataId(anyString());
    }

    @Test
    @DisplayName("顶层父链正确但变量数据编号指向另一单据时，变量原样且全部副作用禁止")
    void correctTopLevelCannotPersistAnotherBusinessDataVariable() {
        request.getVariables().put("dataId", "10002");
        when(forms.selectOne(any(Wrapper.class))).thenReturn(null);
        assertRejected("流程父来源变量与权威关联不一致");
    }

    /** 按查询方向提供持久化替身结果，不伪造实体租户字段，也不调用数据库。 */
    private void associations(List<WfMyBusiness> byInstance, List<WfMyBusiness> byKey) {
        when(businesses.list(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().contains("process_instance_id") ? byInstance : byKey;
        });
    }

    private void businessCallback(String serviceName) {
        business.setServiceImplName(serviceName);
        beans.when(() -> SpringContextHolder.getBean(serviceName)).thenReturn(callback);
    }

    private void authoritativeData(String dataId) {
        business.setDataId(dataId);
        request.setDataId(dataId);
        when(instance.getBusinessKey()).thenReturn(dataId);
    }

    private void assertValidParentCompletion(String dataId) {
        when(runtime.getVariable("execution-A", "needSign")).thenReturn(true);
        request.getVariables().put("dataId", dataId);
        assertDoesNotThrow(() -> service.complete(request), "匹配原父链的变量必须允许正常签署与办结");
        ArgumentCaptor<Map> variables = ArgumentCaptor.forClass(Map.class);
        verify(tasks).complete(eq(TASK_ID), variables.capture(), eq(false));
        assertEquals(dataId, variables.getValue().get("dataId"), "写入引擎的父来源必须保持原权威字符串编号");
        assertFalse(variables.getValue().containsKey("signCredential"), "引擎变量不得保留二级认证凭证");
        ArgumentCaptor<SignCommand> command = ArgumentCaptor.forClass(SignCommand.class);
        verify(signatures).sign(command.capture());
        assertEquals(Long.valueOf(dataId), command.getValue().bizId(), "签署单据必须仍是原业务编号");
        verify(onlineForm).addOrUpdateOnlineDataByName(anyString(), anyMap());
        verify(tasks).addComment(eq(TASK_ID), eq(INSTANCE_ID), anyString(), isNull());
        verify(tasks).setAssignee(TASK_ID, USERNAME);
        verify(businesses).updateById(same(business));
        verify(callback).afterFlowHandle(same(business));
        verify(copies).makeCopy(same(request));
        verify(businesses, never()).getByDataId(anyString());
    }

    @ParameterizedTest(name = "逆向已重绑定但数据编号缺失：{index}")
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("旧实例逆向关联错父时，空或空白顶层编号均不能降格为通用")
    void reverseReboundRejectsEveryOmittedDataShape(String dataId) {
        business.setProcessInstanceId("process-B");
        associations(List.of(), List.of(business));
        request.setDataId(dataId);
        assertRejected("任务与流程业务关联不一致");
    }

    @Test
    @DisplayName("反向实际业务定义错配，即使请求省略编号也早拒绝")
    void reverseWrongDefinitionIsRejected() {
        business.setProcessDefinitionId("definition-B:1");
        associations(List.of(), List.of(business));
        request.setDataId(null);
        assertRejected("任务与流程业务关联不一致");
    }

    @Test
    @DisplayName("原业务键反向命中重复记录时不能任选一条或当作通用")
    void reverseDuplicateAssociationIsRejected() {
        associations(List.of(), List.of(business, business(DATA_ID, "process-B")));
        request.setDataId(null);
        assertRejected("流程实例存在重复业务关联");
    }

    @ParameterizedTest(name = "反向业务终态：{0}")
    @ValueSource(strings = {"审批通过", "取消", "撤回", "审批异常"})
    @DisplayName("反向实际业务已终态仍不能通过漏编号办结旧任务")
    void reverseTerminalBusinessIsRejected(String status) {
        business.setActStatus(status);
        associations(List.of(), List.of(business));
        request.setDataId(null);
        assertRejected("流程业务已结束或异常");
    }

    @Test
    @DisplayName("正反向各一条但关联行主键不同，不能把它们拼成可信父链")
    void differentForwardAndReverseRowsAreRejected() {
        WfMyBusiness reverse = business(DATA_ID, INSTANCE_ID);
        reverse.setId(202L);
        associations(List.of(business), List.of(reverse));
        assertRejected("流程业务关联正反向不一致");
    }

    @Test
    @DisplayName("正向有业务而反向查不到原键时失败关闭")
    void forwardOnlyAssociationIsRejected() {
        associations(List.of(business), List.of());
        assertRejected("流程业务关联正反向不一致");
    }

    @Test
    @DisplayName("反向存在原业务但正向查询丢失时不能靠客户端编号修补")
    void reverseOnlyAssociationIsRejected() {
        associations(List.of(), List.of(business));
        assertRejected("流程业务关联正反向不一致");
    }

    @Test
    @DisplayName("反向查询失败不能按无记录处理为通用")
    void failedReverseQueryIsRejected() {
        associations(List.of(), null);
        request.setDataId(null);
        assertRejected("流程业务关联查询失败");
    }

    @Test
    @DisplayName("真正无关联的独立业务键可办结并保留匹配的父来源变量")
    void genericIndependentKeyAndMatchingVariableRemainCompatible() {
        associations(List.of(), List.of());
        when(instance.getBusinessKey()).thenReturn("generic-independent-key");
        request.setDataId(null);
        request.setProcInsId(null);
        request.getVariables().put("dataId", "generic-independent-key");
        assertDoesNotThrow(() -> service.complete(request), "不能把所有非空业务键都认作业务关联");
        ArgumentCaptor<Map> variables = ArgumentCaptor.forClass(Map.class);
        verify(tasks).complete(eq(TASK_ID), variables.capture(), eq(false));
        assertEquals("generic-independent-key", variables.getValue().get("dataId"), "通用任务匹配独立原键的变量仍须兼容");
        verify(businesses, times(2)).list(any(Wrapper.class));
        verify(businesses, never()).getByDataId(anyString());
        verify(businesses, never()).updateById(any(WfMyBusiness.class));
        verifyNoInteractions(callback);
        assertEquals(INSTANCE_ID, request.getProcInsId(), "通用任务抄送也必须使用原任务实例");
    }

    @Test
    @DisplayName("无关联通用任务也不能通过变量把详情数据来源指向别单")
    void genericIndependentKeyCannotPersistForeignDataVariable() {
        associations(List.of(), List.of());
        when(instance.getBusinessKey()).thenReturn("generic-independent-key");
        request.setDataId(null);
        request.getVariables().put("dataId", "10002");
        assertRejected("流程父来源变量与权威关联不一致");
    }

    @Test
    @DisplayName("反向资料只命中其他租户时，不误取其记录作为当前通用任务回调")
    void reverseQueryRemainsCurrentTenantScoped() {
        WfMyBusiness foreign = business(DATA_ID, "process-B");
        // 仅模拟租户列条件的查询结果；没有给业务实体增加不存在的租户属性。
        when(businesses.list(any(Wrapper.class))).thenAnswer(invocation -> {
            QueryWrapper<?> query = invocation.getArgument(0);
            if (query.getSqlSegment().contains("tenant_id")
                && query.getParamNameValuePairs().containsValue(TENANT_ID)) {
                return List.of();
            }
            return List.of(foreign);
        });
        request.setDataId(null);
        assertDoesNotThrow(() -> service.complete(request), "其他租户的同键记录不能绑定当前通用任务");
        verify(businesses, times(2)).list(any(Wrapper.class));
        verify(businesses, never()).getByDataId(anyString());
        verify(businesses, never()).updateById(any(WfMyBusiness.class));
        verifyNoInteractions(callback);
    }

    static Stream<Arguments> invalidDataVariableValues() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(10001), Arguments.of(10001L),
            Arguments.of(10001.0), Arguments.of(10001.0f), Arguments.of(new BigInteger(DATA_ID)),
            Arguments.of(new BigDecimal(DATA_ID)), Arguments.of(true), Arguments.of(List.of(DATA_ID)),
            Arguments.of(Map.of("id", DATA_ID)), Arguments.of((Object) new String[]{DATA_ID}),
            Arguments.of("010001"), Arguments.of("10001 "), Arguments.of("1.0001E4"));
    }

    @ParameterizedTest(name = "错误的父来源编号类型或表示：{index}")
    @MethodSource("invalidDataVariableValues")
    @DisplayName("父来源数据编号必须按已有字符串契约精确匹配，不能宽松转型")
    void invalidDataVariableTypeOrRepresentationIsRejected(Object value) {
        request.getVariables().put("dataId", value);
        assertRejected("流程父来源变量与权威关联不一致");
    }

    static Stream<Arguments> unsafeSnowflakeDataValues() {
        return Stream.of(Arguments.of(9007199254740992L), Arguments.of(9007199254740992d),
            Arguments.of(new BigInteger("9007199254740992")), Arguments.of(new BigDecimal("9007199254740992.0")),
            Arguments.of("9.007199254740992E15"));
    }

    @ParameterizedTest(name = "不安全雪花编号转换：{index}")
    @MethodSource("unsafeSnowflakeDataValues")
    @DisplayName("超安全整数的数值或科学计数不能因长整型转换相等而获认可")
    void unsafeSnowflakeDataConversionIsRejected(Object value) {
        authoritativeData("9007199254740992");
        request.getVariables().put("dataId", value);
        assertRejected("流程父来源变量与权威关联不一致");
    }

    @Test
    @DisplayName("完整雪花编号字符串与原父链一致时明文允许正常审批")
    void exactSnowflakeStringRemainsCompatible() {
        authoritativeData("9007199254740993");
        assertValidParentCompletion("9007199254740993");
    }

    @Test
    @DisplayName("匹配的字符串父变量与普通可编辑字段均保留，不猜测表单主键归属")
    void matchingParentAndEditableFieldsRemainCompatible() {
        request.getVariables().put("id", "editable-online-row");
        request.getVariables().put("vendorId", "editable-vendor");
        request.getVariables().put("comment", "正常审批意见");
        assertValidParentCompletion(DATA_ID);
        assertEquals("editable-online-row", request.getVariables().get("id"), "无可信契约的表单主键不能一律等同业务编号");
        assertEquals("editable-vendor", request.getVariables().get("vendorId"), "正常审批可编辑字段不得被批量剥离或禁止");
        assertEquals("正常审批意见", request.getVariables().get("comment"), "普通审批意见必须保留");
    }

    @Test
    @DisplayName("已有运行变量的数据来源被污染时，也必须先拒绝而非继续签署")
    void alreadyPollutedRuntimeDataVariableIsRejected() {
        when(runtime.getVariable(INSTANCE_ID, "dataId")).thenReturn("10002");
        assertRejected("流程父来源变量与权威关联不一致");
    }

    static Stream<Arguments> sourceAliasContracts() {
        return Stream.of(Arguments.of("wmArrivalNoticeService", "noticeId"),
            Arguments.of("wmCertifiedArrivalNoticeService", "noticeId"),
            Arguments.of("wmMaterialReceiveCrudService", "receiveId"),
            Arguments.of("wmMaterialReceiveService", "receiveId"));
    }

    @ParameterizedTest(name = "可信父字段冲突：{0}/{1}")
    @MethodSource("sourceAliasContracts")
    @DisplayName("已核实通知或收货父字段冲突时，顶层正确也禁止全部副作用")
    void trustedSourceAliasCannotPointToAnotherDocument(String serviceName, String field) {
        businessCallback(serviceName);
        when(runtime.getVariable(INSTANCE_ID, field)).thenReturn(10001L);
        request.getVariables().put(field, "10002");
        assertRejected("流程父来源变量与权威关联不一致");
    }

    @ParameterizedTest(name = "可信父字段匹配：{0}/{1}")
    @MethodSource("sourceAliasContracts")
    @DisplayName("既有通知和收货整型父字段匹配时明文保持正常签署和回调")
    void matchingIntegralSourceAliasRemainsCompatible(String serviceName, String field) {
        businessCallback(serviceName);
        request.getVariables().put(field, 10001L);
        when(runtime.getVariable(INSTANCE_ID, field)).thenReturn(10001L);
        assertValidParentCompletion(DATA_ID);
        assertEquals(10001L, request.getVariables().get(field), "安全整数父字段不得被字符串化或改写");
    }

    @ParameterizedTest(name = "已有可信父字段污染：{0}/{1}")
    @MethodSource("sourceAliasContracts")
    @DisplayName("运行时既有通知或收货父字段错配也在副作用前拒绝")
    void pollutedRuntimeSourceAliasIsRejected(String serviceName, String field) {
        businessCallback(serviceName);
        when(runtime.getVariable(INSTANCE_ID, field)).thenReturn(10002L);
        assertRejected("流程父来源变量与权威关联不一致");
    }

    @Test
    @DisplayName("收货流程里的上游通知编号不误当收货父编号，保持有界契约")
    void upstreamNoticeInReceiveFlowIsNotInventedAsItsOwnParent() {
        businessCallback("wmMaterialReceiveCrudService");
        request.getVariables().put("receiveId", DATA_ID);
        request.getVariables().put("noticeId", "upstream-notice");
        assertValidParentCompletion(DATA_ID);
        assertEquals("upstream-notice", request.getVariables().get("noticeId"), "上游通知字段没有同单据父编号契约，不能猜测禁掉");
        assertEquals(DATA_ID, request.getVariables().get("receiveId"), "真正收货父编号必须保持原单据");
    }

    @Test
    @DisplayName("服务器原整型雪花父字段可保留，客户端完整字符串匹配才允许")
    void serverLongSnowflakeAndClientStringAliasRemainCompatible() {
        authoritativeData("9007199254740993");
        businessCallback("wmCertifiedArrivalNoticeService");
        when(runtime.getVariable(INSTANCE_ID, "noticeId")).thenReturn(9007199254740993L);
        request.getVariables().put("noticeId", "9007199254740993");
        assertValidParentCompletion("9007199254740993");
        assertEquals("9007199254740993", request.getVariables().get("noticeId"), "客户端原雪花字符串不得经过浮点转换");
    }

    @Test
    @DisplayName("客户端整型别名超过安全整数时，即使数值碰巧等于原编号也拒绝")
    void unsafeClientIntegralSourceAliasIsRejected() {
        authoritativeData("9007199254740992");
        businessCallback("wmCertifiedArrivalNoticeService");
        when(runtime.getVariable(INSTANCE_ID, "noticeId")).thenReturn(9007199254740992L);
        request.getVariables().put("noticeId", 9007199254740992L);
        assertRejected("流程父来源变量与权威关联不一致");
    }

    @Test
    @DisplayName("默认租户明确为零六位且原任务实例均匹配时，完整父链允许")
    void explicitDefaultTenantParentChainRemainsCompatible() {
        tenants.when(TenantHelper::getTenantId).thenReturn("000000");
        task.setTenantId("000000");
        when(instance.getTenantId()).thenReturn("000000");
        when(runtime.getVariable(INSTANCE_ID, "dataId")).thenReturn(DATA_ID);
        request.setProcInsId(null);
        assertValidParentCompletion(DATA_ID);
        verify(lockedInstanceQuery).parameter("tenantId", "000000");
        verify(lockedTaskQuery).parameter("tenantId", "000000");
        assertEquals(INSTANCE_ID, request.getProcInsId(), "默认租户也只能使用原任务实例作为抄送父链");
    }

    @ParameterizedTest(name = "开启租户下未知任务租户：{index}")
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("开启租户时，未知任务空租户不能猜测归入默认租户")
    void enabledDefaultTenantRejectsUnknownTaskTenant(String taskTenant) {
        tenants.when(TenantHelper::getTenantId).thenReturn("000000");
        task.setTenantId(taskTenant);
        when(instance.getTenantId()).thenReturn("000000");
        request.getVariables().put("dataId", DATA_ID);
        assertRejected("任务不属于当前租户");
    }

    @ParameterizedTest(name = "开启租户下未知实例租户：{index}")
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("开启租户时，原任务正确而实例空租户仍按严格边界拒绝")
    void enabledDefaultTenantRejectsUnknownInstanceTenant(String instanceTenant) {
        tenants.when(TenantHelper::getTenantId).thenReturn("000000");
        task.setTenantId("000000");
        when(instance.getTenantId()).thenReturn(instanceTenant);
        request.getVariables().put("dataId", DATA_ID);
        assertRejected("任务与流程实例不一致");
    }

    static Stream<Arguments> disabledEmptyTenantCases() {
        return Stream.of(Arguments.of(null, null), Arguments.of(null, ""),
            Arguments.of("", null), Arguments.of("", ""));
    }

    @ParameterizedTest(name = "关闭多租户的空租户矩阵：{index}")
    @MethodSource("disabledEmptyTenantCases")
    @DisplayName("明确关闭多租户时，通用任务空或空串租户依既有契约保持正常父链")
    void disabledTenantAllowsGenericNullAndEmptyTenantMatrix(String taskTenant, String instanceTenant) {
        tenants.when(TenantHelper::isEnable).thenReturn(false);
        tenants.when(TenantHelper::getTenantId).thenReturn(null);
        task.setTenantId(taskTenant);
        when(instance.getTenantId()).thenReturn(instanceTenant);
        when(instance.getBusinessKey()).thenReturn("generic-independent-key");
        associations(List.of(), List.of());
        request.setDataId(null);
        request.setProcInsId(null);
        request.getVariables().put("dataId", "generic-independent-key");
        when(runtime.getVariable("execution-A", "needSign")).thenReturn(true);
        assertDoesNotThrow(() -> service.complete(request), "显式关闭多租户不能擅自归一空值或阻断通用任务");
        verify(tasks).complete(eq(TASK_ID), anyMap(), eq(false));
        verify(tasks).addComment(eq(TASK_ID), eq(INSTANCE_ID), anyString(), isNull());
        verify(signatures).sign(any());
        verify(copies).makeCopy(same(request));
        assertEquals(INSTANCE_ID, request.getProcInsId(), "空租户通用任务的父实例仍须来自原任务");
        assertEquals("generic-independent-key", request.getVariables().get("dataId"), "匹配原独立键的父来源变量不得改写");
        verify(lockedInstanceQuery, never()).parameter(eq("tenantId"), any());
        verify(lockedTaskQuery, never()).parameter(eq("tenantId"), any());
        ArgumentCaptor<Wrapper> queries = ArgumentCaptor.forClass(Wrapper.class);
        verify(businesses, times(2)).list(queries.capture());
        for (Wrapper<?> query : queries.getAllValues()) {
            assertFalse(query.getSqlSegment().contains("tenant_id"), "显式单租户模式不应伪造默认租户条件");
        }
        verify(businesses, never()).updateById(any(WfMyBusiness.class));
        verifyNoInteractions(callback);
    }

    @ParameterizedTest(name = "通用原业务键无值：{index}")
    @NullAndEmptySource
    @DisplayName("真正无关联通用任务的空或空串原键与同值来源字段保持兼容")
    void genericNullAndEmptyKeyWithSameSourceValueRemainsCompatible(String key) {
        associations(List.of(), List.of());
        when(instance.getBusinessKey()).thenReturn(key);
        request.setDataId(null);
        request.setProcInsId(null);
        request.getVariables().put("dataId", key);
        assertDoesNotThrow(() -> service.complete(request), "没有业务关联且来源值不引向别单的通用任务应保持兼容");
        verify(tasks).complete(eq(TASK_ID), anyMap(), eq(false));
        assertEquals(key, request.getVariables().get("dataId"), "通用空业务键的同值字段不得被强行归一或补造");
        verify(businesses, times(key == null ? 1 : 2)).list(any(Wrapper.class));
        verify(businesses, never()).updateById(any(WfMyBusiness.class));
        verify(businesses, never()).getByDataId(anyString());
        verifyNoInteractions(callback);
        assertEquals(INSTANCE_ID, request.getProcInsId(), "没有业务键也只能使用原任务的实例父链");
    }

    @Test
    @DisplayName("认证通知运行来源缺失且客户端也不带时，不能先签署或办结再同步拒绝")
    void certifiedNoticeWithoutServerOrClientSourceIsRejectedBeforeWrites() {
        businessCallback("wmCertifiedArrivalNoticeService");
        when(runtime.getVariable(INSTANCE_ID, "dataId")).thenReturn(DATA_ID);
        when(runtime.getVariable(INSTANCE_ID, "noticeId")).thenReturn(null);
        request.setProcInsId(null);
        assertRejected("认证通知缺少原实例 noticeId 来源");
        verify(runtime).getVariable(INSTANCE_ID, "noticeId");
    }

    @Test
    @DisplayName("客户端正确通知编号也不能补认证通知缺少的原运行来源")
    void correctClientNoticeCannotSupplyMissingServerSource() {
        businessCallback("wmCertifiedArrivalNoticeService");
        when(runtime.getVariable(INSTANCE_ID, "dataId")).thenReturn(DATA_ID);
        when(runtime.getVariable(INSTANCE_ID, "noticeId")).thenReturn(null);
        request.getVariables().put("noticeId", DATA_ID);
        request.setProcInsId(null);
        assertRejected("认证通知缺少原实例 noticeId 来源");
        assertEquals(DATA_ID, request.getVariables().get("noticeId"), "客户端正确值必须原样保留，但不能成为服务器来源补证");
        verify(runtime).getVariable(INSTANCE_ID, "noticeId");
    }

    @Test
    @DisplayName("认证通知原服务器来源匹配时，客户端省略通知编号仍可完整办结")
    void certifiedOriginalServerNoticeDoesNotRequireClientResubmission() {
        businessCallback("wmCertifiedArrivalNoticeService");
        when(runtime.getVariable(INSTANCE_ID, "dataId")).thenReturn(DATA_ID);
        when(runtime.getVariable(INSTANCE_ID, "noticeId")).thenReturn(10001L);
        assertFalse(request.getVariables().containsKey("noticeId"), "本例必须仅由原服务器来源提供通知编号");
        assertValidParentCompletion(DATA_ID);
        assertFalse(request.getVariables().containsKey("noticeId"), "不得替客户端补造通知来源字段");
    }

    @ParameterizedTest(name = "可选原父别名不扩大必需性：{0}")
    @ValueSource(strings = {"wmArrivalNoticeService", "wmMaterialReceiveCrudService", "wmMaterialReceiveService"})
    @DisplayName("普通通知及原生收货无同等必需来源契约时，缺失可选别名仍保持正常父链")
    void nonCertifiedBusinessDoesNotGainUnprovenMandatoryAlias(String serviceName) {
        businessCallback(serviceName);
        when(runtime.getVariable(INSTANCE_ID, "dataId")).thenReturn(DATA_ID);
        assertFalse(request.getVariables().containsKey("noticeId"), "兼容用例不得暗中由客户端提供通知来源");
        assertFalse(request.getVariables().containsKey("receiveId"), "兼容用例不得暗中由客户端提供收货来源");
        assertValidParentCompletion(DATA_ID);
    }
}
