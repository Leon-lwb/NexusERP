package com.nexus.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.domain.bo.WfTaskBo;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.NativeProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.NativeTaskQuery;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务完成入口的父绑定守卫，仅负责事务内读取和验证，不执行审批或业务写入。
 * 实例、任务和业务关联依次锁定读取，防止等待并发办结后仍使用旧快照签署。
 * 原生锁定语句适用于现行 MySQL 数据源，锁保持到调用入口的事务结束。
 */
final class TaskCompletionBindingGuard {

    private static final Set<String> CLOSED_STATUSES = Set.of(ActStatus.pass, ActStatus.stop,
        ActStatus.revoke, ActStatus.err);

    /** 客户端数值编号只接受安全整数；完整雪花编号须按既有字符串传输。 */
    private static final BigInteger MAX_SAFE_CLIENT_ID = BigInteger.valueOf(9007199254740991L);

    /** 只列已核对通知/收货提交及回调的同单据父字段，不猜测普通表单字段。 */
    private static final Map<String, String> SOURCE_ID_ALIASES = Map.of(
        "wmArrivalNoticeService", "noticeId",
        "wmCertifiedArrivalNoticeService", "noticeId",
        "wmMaterialReceiveCrudService", "receiveId",
        "wmMaterialReceiveService", "receiveId");

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ManagementService managementService;
    private final WfMyBusinessServiceImpl businessService;
    private final boolean tenantEnabled;
    private final String tenantId;

    TaskCompletionBindingGuard(TaskService taskService, RuntimeService runtimeService,
                               ManagementService managementService, WfMyBusinessServiceImpl businessService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.managementService = managementService;
        this.businessService = businessService;
        this.tenantEnabled = TenantHelper.isEnable();
        this.tenantId = tenantEnabled ? TenantHelper.getTenantId() : null;
    }

    /**
     * 在办理权限查询和锁定读取之前核对请求实例及当前任务的租户边界。
     *
     * @param request 原请求，实例编号可省略
     * @param task    按请求任务编号取得的任务快照
     */
    void checkRequest(WfTaskBo request, TaskEntity task) {
        if (tenantEnabled && StringUtils.isBlank(tenantId)) {
            throw new ServiceException("当前租户上下文缺失");
        }
        if (tenantEnabled && !StringUtils.equals(tenantId, task.getTenantId())) {
            throw new ServiceException("任务不属于当前租户");
        }
        if (StringUtils.isBlank(task.getProcessInstanceId())) {
            throw new ServiceException("任务缺少流程实例，不能办理");
        }
        if (StringUtils.isNotBlank(request.getProcInsId())
            && !StringUtils.equals(request.getProcInsId(), task.getProcessInstanceId())) {
            throw new ServiceException("请求流程实例与任务不一致");
        }
        if (task.isSuspended()) {
            throw new ServiceException("任务已挂起，不能办理");
        }
    }

    /**
     * 锁定原任务实例及任务行，解析唯一业务关联，所有校验均早于变量剥离和副作用。
     *
     * @param request 原请求，业务关联任务必须提供匹配的数据编号
     * @param snapshot 已通过请求与办理权限校验的任务快照
     * @return 锁后任务和已验证的业务对象；通用流程的业务对象为空
     */
    Binding lockAndResolve(WfTaskBo request, TaskEntity snapshot) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ServiceException("任务办结必须在事务中执行");
        }
        String instanceId = snapshot.getProcessInstanceId();
        int revision = snapshot.getRevision();
        NativeProcessInstanceQuery instanceQuery = runtimeService.createNativeProcessInstanceQuery()
            .sql("SELECT * FROM " + managementService.getTableName(ProcessInstance.class)
                + " WHERE ID_ = #{processInstanceId} AND PROC_INST_ID_ = #{processInstanceId}"
                + (tenantEnabled ? " AND TENANT_ID_ = #{tenantId}" : "") + " FOR UPDATE")
            .parameter("processInstanceId", instanceId);
        if (tenantEnabled) {
            instanceQuery.parameter("tenantId", tenantId);
        }
        ProcessInstance instance = instanceQuery.singleResult();
        if (instance == null || instance.isEnded()) {
            throw new ServiceException("流程实例不存在或已结束");
        }
        if (!StringUtils.equals(instanceId, instance.getId())
            || !StringUtils.equals(instanceId, instance.getProcessInstanceId())
            || !StringUtils.equals(snapshot.getProcessDefinitionId(), instance.getProcessDefinitionId())
            || (tenantEnabled && !StringUtils.equals(tenantId, instance.getTenantId()))) {
            throw new ServiceException("任务与流程实例不一致");
        }
        if (instance.isSuspended()) {
            throw new ServiceException("流程实例已挂起，不能办理");
        }

        // 锁定读取而不是普通任务复查，避免可重复读事务继续返回办结前快照。
        NativeTaskQuery taskQuery = taskService.createNativeTaskQuery()
            .sql("SELECT * FROM " + managementService.getTableName(Task.class)
                + " WHERE ID_ = #{taskId} AND PROC_INST_ID_ = #{processInstanceId}"
                + (tenantEnabled ? " AND TENANT_ID_ = #{tenantId}" : "") + " FOR UPDATE")
            .parameter("taskId", snapshot.getId()).parameter("processInstanceId", instanceId);
        if (tenantEnabled) {
            taskQuery.parameter("tenantId", tenantId);
        }
        TaskEntity task = (TaskEntity) taskQuery.singleResult();
        if (task == null || task.getRevision() != revision || task.isSuspended()
            || !StringUtils.equals(snapshot.getId(), task.getId())
            || !StringUtils.equals(instanceId, task.getProcessInstanceId())
            || !StringUtils.equals(snapshot.getProcessDefinitionId(), task.getProcessDefinitionId())
            || (tenantEnabled && !StringUtils.equals(tenantId, task.getTenantId()))) {
            throw new ServiceException("任务已被办理或状态已变化");
        }

        // 实例方向为空不能证明无业务；反向凭据只取引擎原业务键，不能由客户端补证。
        List<WfMyBusiness> byInstance = readAssociations("process_instance_id", instanceId);
        String businessKey = instance.getBusinessKey();
        List<WfMyBusiness> byKey = businessKey == null ? List.of() : readAssociations("data_id", businessKey);
        WfMyBusiness forward = byInstance.isEmpty() ? null : byInstance.get(0);
        WfMyBusiness reverse = byKey.isEmpty() ? null : byKey.get(0);
        if (forward == null && reverse == null) {
            if (StringUtils.isNotBlank(request.getDataId())) {
                throw new ServiceException("任务没有对应的流程业务关联");
            }
            checkSourceVariables(request, instanceId, businessKey, null);
            return new Binding(task, null);
        }
        if (forward != null) {
            checkBusinessOrigin(forward, task, instance);
        }
        if (reverse != null) {
            checkBusinessOrigin(reverse, task, instance);
        }
        if (forward == null || reverse == null || !forward.getId().equals(reverse.getId())
            || !StringUtils.equals(forward.getServiceImplName(), reverse.getServiceImplName())
            || !StringUtils.equals(forward.getActStatus(), reverse.getActStatus())) {
            throw new ServiceException("流程业务关联正反向不一致");
        }
        if (StringUtils.isBlank(request.getDataId())) {
            throw new ServiceException("业务关联任务必须提供数据编号");
        }
        if (!StringUtils.equals(request.getDataId(), forward.getDataId())) {
            throw new ServiceException("任务与流程业务关联不一致");
        }
        checkSourceVariables(request, instanceId, forward.getDataId(), forward.getServiceImplName());
        return new Binding(task, forward);
    }

    /**
     * 按一个既有身份列进行有界锁定读取，正反向都显式限定当前租户。
     *
     * @param column 固定的实例或业务编号列，由本类调用点决定
     * @param value  原任务实例或引擎业务键，不使用请求变量
     * @return 空列表代表该方向确无关联，重复或失败均抛业务异常
     */
    private List<WfMyBusiness> readAssociations(String column, String value) {
        // 实体没有租户属性，使用表结构已有的租户列；不关闭现有租户拦截器。
        QueryWrapper<WfMyBusiness> query = new QueryWrapper<WfMyBusiness>()
            .eq(column, value).eq(tenantEnabled, "tenant_id", tenantId).last("LIMIT 2 FOR UPDATE");
        List<WfMyBusiness> associations = businessService.list(query);
        if (associations == null || (associations.size() == 1 && associations.get(0) == null)) {
            throw new ServiceException("流程业务关联查询失败");
        }
        if (associations.size() > 1) {
            throw new ServiceException("流程实例存在重复业务关联");
        }
        return associations;
    }

    /**
     * 每个方向命中的真实记录都必须属于原任务实例、定义和原引擎业务键。
     *
     * @param business 锁定取得的真实业务关联
     * @param task     锁后原任务
     * @param instance 锁后原实例
     */
    private void checkBusinessOrigin(WfMyBusiness business, Task task, ProcessInstance instance) {
        if (business.getId() == null || StringUtils.isBlank(business.getDataId())
            || !StringUtils.equals(task.getProcessInstanceId(), business.getProcessInstanceId())
            || !StringUtils.equals(task.getProcessDefinitionId(), business.getProcessDefinitionId())
            || !StringUtils.equals(business.getDataId(), instance.getBusinessKey())) {
            throw new ServiceException("任务与流程业务关联不一致");
        }
        if (StringUtils.isBlank(business.getActStatus()) || CLOSED_STATUSES.contains(business.getActStatus())) {
            throw new ServiceException("流程业务已结束或异常，不能办理");
        }
        if (StringUtils.isBlank(business.getServiceImplName())) {
            throw new ServiceException("流程业务关联缺少回调配置");
        }
    }

    /**
     * 只读核对真正用于业务来源的提交变量及已有实例变量，不剥离、补造或纠正请求。
     * 数据编号沿用字符串契约；通知/收货别名仅在已核实的回调契约下校验。
     *
     * @param request         原请求
     * @param instanceId      原实例编号
     * @param authoritativeId 已验证业务编号；通用流程使用其原独立业务键
     * @param serviceName     已验证回调名；通用流程为空
     */
    private void checkSourceVariables(WfTaskBo request, String instanceId, String authoritativeId, String serviceName) {
        Map<String, Object> submitted = request.getVariables();
        Object currentDataId = runtimeService.getVariable(instanceId, "dataId");
        if ((currentDataId != null && !matchesStringId(currentDataId, authoritativeId))
            || (submitted != null && submitted.containsKey("dataId")
                && !matchesStringId(submitted.get("dataId"), authoritativeId))) {
            throw new ServiceException("流程父来源变量与权威关联不一致");
        }
        String alias = serviceName == null ? null : SOURCE_ID_ALIASES.get(serviceName);
        if (alias == null) {
            return;
        }
        Object currentAlias = runtimeService.getVariable(instanceId, alias);
        // 认证通知提交后 reader 必须读取原 noticeId；不能等签署办结后才缺证，也不能由客户端补证。
        if ("wmCertifiedArrivalNoticeService".equals(serviceName) && currentAlias == null) {
            throw new ServiceException("认证通知缺少原实例 noticeId 来源");
        }
        if ((currentAlias != null && !matchesAliasId(currentAlias, authoritativeId, false))
            || (submitted != null && submitted.containsKey(alias)
                && !matchesAliasId(submitted.get(alias), authoritativeId, true))) {
            throw new ServiceException("流程父来源变量与权威关联不一致");
        }
    }

    /**
     * 字符串父编号只按原始表示精确比较，不接受数值转型、空白修剪或科学计数。
     *
     * @param value    来源字段值
     * @param expected 服务端原身份；通用空业务键允许同为空的来源字段
     * @return 是否保持既有字符串身份
     */
    private boolean matchesStringId(Object value, String expected) {
        return expected == null ? value == null : value instanceof String && expected.equals(value);
    }

    /**
     * 已证实的整型父别名可以精确匹配；客户端超安全整数、浮点和其他类型不认可。
     * 服务器原整型雪花值不经过浮点转换，也不按客户端传输限制损失其完整精度。
     *
     * @param value         来源字段值
     * @param expected      原身份字符串
     * @param fromRequest   是否来自客户端提交变量
     * @return 是否为精确匹配且适合该来源的表示
     */
    private boolean matchesAliasId(Object value, String expected, boolean fromRequest) {
        if (value instanceof String) {
            return expected != null && expected.equals(value);
        }
        BigInteger integer;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            integer = BigInteger.valueOf(((Number) value).longValue());
        } else if (value != null && value.getClass() == BigInteger.class) {
            integer = (BigInteger) value;
        } else {
            return false;
        }
        return expected != null && (!fromRequest || integer.abs().compareTo(MAX_SAFE_CLIENT_ID) <= 0)
            && expected.equals(integer.toString());
    }

    /**
     * 通过完整父绑定校验的只读结果，入口复用同一业务对象进行回写和回调。
     *
     * @param task     锁定后仍可办理的原任务
     * @param business 唯一业务关联；无关联的通用流程为空
     */
    record Binding(TaskEntity task, WfMyBusiness business) {
    }
}
