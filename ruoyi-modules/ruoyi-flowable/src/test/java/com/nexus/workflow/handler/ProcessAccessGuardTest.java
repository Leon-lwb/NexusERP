package com.nexus.workflow.handler;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.utils.TaskUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.domain.WfCopy;
import com.nexus.workflow.mapper.WfCopyMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程详情访问权限校验单元测试。
 * <p>守护历史回归：taskCandidateUser 不解析用户所属候选组，本系统大量任务按
 * ROLE{id}/DEPT{id} 候选组分配，缺候选组查询会把合法办理人误拦。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessAccessGuardTest {

    private static final String PROC_INS_ID = "proc-001";
    private static final String TENANT_ID = "000000";
    private static final String USERNAME = "zhangsan";
    private static final Long USER_ID = 100L;

    @Mock
    private HistoryService historyService;
    @Mock
    private WfCopyMapper wfCopyMapper;
    @InjectMocks
    private ProcessAccessGuard guard;

    private HistoricProcessInstance instance;

    @BeforeEach
    void setUp() {
        instance = mock(HistoricProcessInstance.class);
        when(instance.getStartUserId()).thenReturn("other-user");
    }

    /** 打桩引擎查询链：历史实例可查、任务/抄送默认全部未命中 */
    private void stubEngineQueries() {
        HistoricProcessInstanceQuery hisQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hisQuery);
        when(hisQuery.processInstanceId(anyString())).thenReturn(hisQuery);
        // TenantHelper.getTenantId() 未打桩时真实返回 null，anyString() 不匹配 null 参数
        when(hisQuery.processInstanceTenantId(any())).thenReturn(hisQuery);
        when(hisQuery.singleResult()).thenReturn(instance);

        HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskCandidateUser(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskCandidateGroupIn(anyList())).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);

        when(wfCopyMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
    }

    private MockedStatic<LoginHelper> loginHelper(boolean superAdmin, boolean tenantAdmin) {
        MockedStatic<LoginHelper> login = Mockito.mockStatic(LoginHelper.class);
        login.when(LoginHelper::isSuperAdmin).thenReturn(superAdmin);
        login.when(LoginHelper::isTenantAdmin).thenReturn(tenantAdmin);
        login.when(LoginHelper::getUserId).thenReturn(USER_ID);
        return login;
    }

    private MockedStatic<TaskUtils> taskUtils(String username, List<String> groups) {
        MockedStatic<TaskUtils> utils = Mockito.mockStatic(TaskUtils.class);
        utils.when(TaskUtils::getUserName).thenReturn(username);
        utils.when(TaskUtils::getCandidateGroup).thenReturn(groups);
        return utils;
    }

    @Test
    @DisplayName("procInsId 为空直接放行")
    void blankIdPasses() {
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false)) {
            assertDoesNotThrow(() -> guard.checkCanView(null));
            assertDoesNotThrow(() -> guard.checkCanView("  "));
        }
    }

    @Test
    @DisplayName("超级管理员/租户管理员放行且不查引擎")
    void adminPassesWithoutEngineQueries() {
        try (MockedStatic<LoginHelper> ignored = loginHelper(true, false)) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
            Mockito.verifyNoInteractions(historyService);
        }
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, true)) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
            Mockito.verifyNoInteractions(historyService);
        }
    }

    @Test
    @DisplayName("发起人放行")
    void starterPasses() {
        stubEngineQueries();
        when(instance.getStartUserId()).thenReturn(USERNAME);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false);
             MockedStatic<TaskUtils> ignored2 = taskUtils(USERNAME, List.of())) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
        }
    }

    @Test
    @DisplayName("办理人放行")
    void assigneePasses() {
        stubEngineQueries();
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();
        when(taskQuery.taskAssignee(USERNAME)).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(USERNAME)).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false);
             MockedStatic<TaskUtils> ignored2 = taskUtils(USERNAME, List.of())) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
        }
    }

    @Test
    @DisplayName("候选人放行")
    void candidateUserPasses() {
        stubEngineQueries();
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();
        when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
        // assignee 未命中
        when(taskQuery.taskAssignee(anyString())).thenAnswer(inv -> taskQuery);
        when(taskQuery.count()).thenReturn(0L, 1L); // 第一次 count(assignee)=0，第二次 count(candidate)=1
        when(taskQuery.taskCandidateUser(USERNAME)).thenReturn(taskQuery);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false);
             MockedStatic<TaskUtils> ignored2 = taskUtils(USERNAME, List.of())) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
        }
    }

    @Test
    @DisplayName("候选组成员放行（回归：taskCandidateUser 不解析用户所属组）")
    void candidateGroupMemberPasses() {
        stubEngineQueries();
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();
        when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenAnswer(inv -> taskQuery);
        when(taskQuery.taskCandidateUser(anyString())).thenAnswer(inv -> taskQuery);
        // assignee=0, candidateUser=0, candidateGroupIn=1
        when(taskQuery.count()).thenReturn(0L, 0L, 1L);
        when(taskQuery.taskCandidateGroupIn(List.of("ROLE1", "DEPT2"))).thenReturn(taskQuery);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false);
             MockedStatic<TaskUtils> ignored2 = taskUtils(USERNAME, List.of("ROLE1", "DEPT2"))) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
        }
    }

    @Test
    @DisplayName("被抄送人放行")
    void copyReceiverPasses() {
        stubEngineQueries();
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();
        when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenAnswer(inv -> taskQuery);
        when(taskQuery.taskCandidateUser(anyString())).thenAnswer(inv -> taskQuery);
        when(taskQuery.taskCandidateGroupIn(anyList())).thenAnswer(inv -> taskQuery);
        when(taskQuery.count()).thenReturn(0L);
        when(wfCopyMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false);
             MockedStatic<TaskUtils> ignored2 = taskUtils(USERNAME, List.of())) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
        }
    }

    @Test
    @DisplayName("全部身份未命中时拒绝")
    void outsiderRejected() {
        stubEngineQueries();
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();
        when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskAssignee(anyString())).thenAnswer(inv -> taskQuery);
        when(taskQuery.taskCandidateUser(anyString())).thenAnswer(inv -> taskQuery);
        when(taskQuery.taskCandidateGroupIn(anyList())).thenAnswer(inv -> taskQuery);
        when(taskQuery.count()).thenReturn(0L);
        when(wfCopyMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false);
             MockedStatic<TaskUtils> ignored2 = taskUtils(USERNAME, List.of())) {
            ServiceException ex = assertThrows(ServiceException.class,
                () -> guard.checkCanView(PROC_INS_ID));
            assertTrue(ex.getMessage().contains("无权查看"));
        }
    }

    @Test
    @DisplayName("实例不存在时不拦截（由后续业务逻辑抛出各自异常）")
    void missingInstanceNotBlockedHere() {
        HistoricProcessInstanceQuery hisQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hisQuery);
        when(hisQuery.processInstanceId(anyString())).thenReturn(hisQuery);
        when(hisQuery.processInstanceTenantId(any())).thenReturn(hisQuery);
        when(hisQuery.singleResult()).thenReturn(null);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false)) {
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
        }
    }

    @Test
    @DisplayName("租户过滤生效：非本租户实例视为不存在而不拦截")
    void tenantFilterApplied() {
        HistoricProcessInstanceQuery hisQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hisQuery);
        when(hisQuery.processInstanceId(anyString())).thenReturn(hisQuery);
        when(hisQuery.processInstanceTenantId(TENANT_ID)).thenReturn(hisQuery);
        when(hisQuery.singleResult()).thenReturn(null);
        try (MockedStatic<LoginHelper> ignored = loginHelper(false, false);
             MockedStatic<TenantHelper> ignored2 = Mockito.mockStatic(TenantHelper.class);
             MockedStatic<TaskUtils> ignored3 = taskUtils(USERNAME, List.of())) {
            ignored2.when(TenantHelper::getTenantId).thenReturn(TENANT_ID);
            assertDoesNotThrow(() -> guard.checkCanView(PROC_INS_ID));
            // 校验租户条件确实被拼上
            Mockito.verify(hisQuery).processInstanceTenantId(TENANT_ID);
        }
    }
}
