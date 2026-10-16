package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.ai.engineering.config.EngineeringProperties;
import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.security.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** 工程任务有界创建、CAS 领取与租约状态机；事务内只访问元数据，不执行编辑器和对象存储 IO。 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringTaskService {
    private final EngineeringRepository repository;
    private final EngineeringDeviceService devices;
    private final EngineeringAccess access;
    private final EngineeringTemplateCatalog catalog;
    private final EngineeringProperties properties;
    private final Clock clock;
    private final AiSessionMapper sessions;
    private final EngineeringTaskNotifier notifier;
    private static final Set<String> PARAMS =
            Set.of("boardWidthMm", "boardHeightMm", "layers", "optionalModules");
    private static final Set<String> ERRORS =
            Set.of(
                    "DEVICE_OFFLINE",
                    "EDITOR_UNAVAILABLE",
                    "TEMPLATE_UNAVAILABLE",
                    "TEMPLATE_HASH_MISMATCH",
                    "CAPABILITY_UNSUPPORTED",
                    "EXECUTION_UNCERTAIN",
                    "EXPORT_FAILED",
                    "DRC_UNAVAILABLE");

    /**
     * 创建浏览器受控工程任务；幂等作用域固定为服务端确认的租户与用户。
     *
     * @param actor 服务端认证的当前操作者，方法会重新核对实时权限
     * @param command 只含模板引用和允许参数的动作请求，禁止任意路径及代码
     * @return 新建或同参数幂等复用的真实任务视图
     * @throws EngineeringException 权限或设备失效、模板不受支持、参数无效或幂等键内容冲突
     */
    public EngineeringTaskVo createDraft(EngineeringActor actor, EngineeringTaskCommand command) {
        return create(actor, command, null);
    }

    /**
     * 创建与会话关联的工程任务；请求标识不取自模型工具参数。
     *
     * @param actor 服务端认证操作者
     * @param command 有界受控请求，sessionId 必须属于当前 owner
     * @param requestId 由聊天入口捕获的稳定请求标识，浏览器直接创建可为空
     * @return 持久化后的真实任务视图
     * @throws EngineeringException 所有者或会话不匹配、参数无效或幂等冲突
     */
    public EngineeringTaskVo create(
            EngineeringActor actor, EngineeringTaskCommand command, String requestId) {
        access.requireActor(actor, "ai:engineering:task", true);
        require(
                command != null
                        && EngineeringDeviceService.PROTOCOL.equals(command.protocolVersion()),
                422,
                "PROTOCOL_UNSUPPORTED",
                "不支持该工程协议版本");
        require(
                command.deviceId() != null && command.action() != null,
                422,
                "NEEDS_INPUT",
                "请选择工程设备和受控动作");
        require(
                command.idempotencyKey() != null
                        && command.idempotencyKey().matches("[A-Za-z0-9:_-]{1,160}"),
                422,
                "INVALID_REQUEST",
                "幂等键格式无效");
        var params =
                command.action() == EngineeringAction.CREATE_PCB_DRAFT
                        ? catalog.parameters(command)
                        : (command.parameters() == null
                                ? Map.<String, Object>of()
                                : command.parameters());
        require(PARAMS.containsAll(params.keySet()), 422, "INVALID_REQUEST", "任务参数包含未开放字段");
        validateParameters(params);
        var normalized =
                new EngineeringTaskCommand(
                        command.protocolVersion(),
                        command.deviceId(),
                        command.action(),
                        command.idempotencyKey(),
                        command.sessionId(),
                        command.templateId(),
                        command.templateVersion(),
                        command.templateSha256(),
                        command.parentTaskId(),
                        params);
        String json = EngineeringJson.write(normalized);
        require(
                json.getBytes(StandardCharsets.UTF_8).length <= 16384,
                422,
                "INVALID_REQUEST",
                "任务请求超出限制");
        String hash = EngineeringSecrets.hash(json);
        var existing = findIdempotent(actor, command.idempotencyKey());
        if (existing != null) {
            require(
                    hash.equals(existing.getRequestHash()),
                    409,
                    "IDEMPOTENCY_CONFLICT",
                    "相同幂等键对应不同任务参数");
            return view(existing);
        }
        var device = repository.device(actor.tenantId(), actor.userId(), command.deviceId());
        require(
                device != null && "ACTIVE".equals(device.getStatus()),
                403,
                "DEVICE_REVOKED",
                "设备不可用或不属于当前用户");
        require(
                Objects.equals(
                        device.getCredentialFingerprint(),
                        access.owner(actor).credentialFingerprint()),
                403,
                "OWNER_REVOKED",
                "设备认证状态已变化，请重新配对");
        var allowed = EngineeringJson.tree(device.getAllowedCapabilities());
        boolean actionAllowed = false;
        for (var item : allowed)
            if (command.action().name().equals(item.asText())) actionAllowed = true;
        require(actionAllowed, 403, "CAPABILITY_UNSUPPORTED", "配对设备未被授权执行该动作");
        if (command.sessionId() != null) {
            var session =
                    sessions.selectOne(
                            Wrappers.<AiSession>lambdaQuery()
                                    .eq(AiSession::getId, command.sessionId())
                                    .eq(AiSession::getTenantId, actor.tenantId())
                                    .eq(AiSession::getUserId, actor.userId()));
            require(
                    session != null
                            && Objects.equals(session.getTenantId(), actor.tenantId())
                            && Objects.equals(session.getUserId(), actor.userId()),
                    403,
                    "SESSION_FORBIDDEN",
                    "会话不存在或不属于当前用户");
        }
        if (command.action() == EngineeringAction.CREATE_PCB_DRAFT) catalog.validate(normalized);
        else if (command.action() != EngineeringAction.CHECK_CONNECTION) {
            require(command.parentTaskId() != null, 422, "NEEDS_INPUT", "此动作必须选择已有工程任务");
            var parent = ownedTask(actor, command.parentTaskId());
            require(
                    Objects.equals(parent.getDeviceId(), command.deviceId()),
                    403,
                    "DEVICE_MISMATCH",
                    "原工程不属于当前设备");
        }
        if (command.parentTaskId() != null) ownedTask(actor, command.parentTaskId());
        long id = IdWorker.getId();
        var now = now();
        try {
            repository.update(
                    "insert into"
                        + " ai_engineering_task(task_id,tenant_id,user_id,device_id,session_id,request_id,parent_task_id,action,idempotency_key,request_hash,request_json,state,attempt,revision,waiting_expires_at,cancel_requested,step_journal,created_at,updated_at)"
                        + " values(?,?,?,?,?,?,?,?,?,?,?,?,0,0,?,false,'[]',?,?)",
                    id,
                    actor.tenantId(),
                    actor.userId(),
                    command.deviceId(),
                    command.sessionId(),
                    requestId,
                    command.parentTaskId(),
                    command.action().name(),
                    command.idempotencyKey(),
                    hash,
                    json,
                    "WAITING_DEVICE",
                    now.plus(properties.getWaitingTimeout()),
                    now,
                    now);
        } catch (DuplicateKeyException duplicate) {
            existing = findIdempotent(actor, command.idempotencyKey());
            require(
                    existing != null && hash.equals(existing.getRequestHash()),
                    409,
                    "IDEMPOTENCY_CONFLICT",
                    "相同幂等键对应不同任务参数");
            return view(existing);
        }
        return view(ownedTask(actor, id));
    }

    private void validateParameters(Map<String, Object> params) {
        for (String key : List.of("boardWidthMm", "boardHeightMm"))
            if (params.containsKey(key)) {
                Object raw = params.get(key);
                require(
                        raw instanceof String text && text.matches("[0-9]{1,4}(\\.[0-9]{1,4})?"),
                        422,
                        "INVALID_REQUEST",
                        "板尺寸必须使用有限精度十进制字符串");
                var number = new java.math.BigDecimal(raw.toString());
                require(
                        number.signum() > 0
                                && number.compareTo(new java.math.BigDecimal("1000")) <= 0,
                        422,
                        "INVALID_REQUEST",
                        "板尺寸超出允许范围");
            }
        if (params.containsKey("layers"))
            require(
                    params.get("layers") instanceof Number number
                            && Set.of(1, 2, 4).contains(number.intValue())
                            && number.doubleValue() == number.intValue(),
                    422,
                    "INVALID_REQUEST",
                    "板层必须是允许的整数");
        if (params.containsKey("optionalModules"))
            require(
                    params.get("optionalModules") instanceof List<?> modules
                            && modules.size() <= 32
                            && modules.stream()
                                    .allMatch(
                                            x ->
                                                    x instanceof String text
                                                            && text.matches("[A-Za-z0-9_-]{1,64}")),
                    422,
                    "INVALID_REQUEST",
                    "可选模块标识无效");
    }

    private AiEngineeringTask findIdempotent(EngineeringActor actor, String key) {
        return EngineeringRepository.one(
                repository.query(
                        AiEngineeringTask.class,
                        "select * from ai_engineering_task where tenant_id=? and user_id=? and"
                            + " idempotency_key=?",
                        actor.tenantId(),
                        actor.userId(),
                        key));
    }

    /**
     * 短事务领取本设备任务；先占设备忙碌槽，再以修订号 CAS 更新任务，两者一起提交或回滚。
     *
     * @param principal 设备过滤器认证并复核的当前设备，不接受正文身份
     * @return 本次尝试、随机租约和截止时间；没有任务或设备正忙返回 null
     * @throws EngineeringException 设备撤销、凭证过期、owner 权限失效或 CAS 竞争失败
     */
    public EngineeringTaskClaim claim(EngineeringDevicePrincipal principal) {
        devices.requireDevice(principal);
        return repository.transaction(
                () -> {
                    var device = lockDevice(principal);
                    if (device.getBusyTaskId() != null) return null;
                    var actor = principal.actor();
                    var task =
                            EngineeringRepository.one(
                                    repository.query(
                                            AiEngineeringTask.class,
                                            "select * from ai_engineering_task where tenant_id=?"
                                                + " and user_id=? and device_id=? and"
                                                + " state='WAITING_DEVICE' and waiting_expires_at>?"
                                                + " order by created_at,task_id limit 1",
                                            actor.tenantId(),
                                            actor.userId(),
                                            principal.deviceId(),
                                            now()));
                    if (task == null) return null;
                    if (device.getCapabilities() == null
                            || device.getLastHeartbeat() == null
                            || device.getLastHeartbeat().isBefore(now().minusSeconds(90)))
                        return null;
                    var heartbeat =
                            EngineeringJson.read(
                                    device.getCapabilities(),
                                    EngineeringDeviceService.Heartbeat.class);
                    if (heartbeat.capabilities() == null
                            || !heartbeat.capabilities().contains(task.getAction())) return null;
                    // 近期心跳与声明能力证明设备在线；创建和打开动作可自行启动尚未运行的编辑器。
                    if (!Set.of("CHECK_CONNECTION", "CREATE_PCB_DRAFT", "OPEN_PROJECT")
                                    .contains(task.getAction())
                            && !Boolean.TRUE.equals(heartbeat.editorAvailable())) return null;
                    int slot =
                            repository.update(
                                    "update ai_engineering_device set busy_task_id=? where"
                                        + " tenant_id=? and user_id=? and device_id=? and"
                                        + " status='ACTIVE' and generation=? and busy_task_id is"
                                        + " null",
                                    task.getTaskId(),
                                    actor.tenantId(),
                                    actor.userId(),
                                    principal.deviceId(),
                                    principal.generation());
                    require(slot == 1, 409, "DEVICE_BUSY", "设备正在执行其他工程任务");
                    String lease = EngineeringSecrets.token();
                    var now = now();
                    int changed =
                            repository.update(
                                    "update ai_engineering_task set"
                                        + " state='RUNNING',stage='CHECKING',attempt=attempt+1,revision=revision+1,lease_hash=?,lease_expires_at=?,deadline_at=?,updated_at=?"
                                        + " where task_id=? and tenant_id=? and user_id=? and"
                                        + " device_id=? and state='WAITING_DEVICE' and revision=?"
                                        + " and waiting_expires_at>?",
                                    EngineeringSecrets.hash(lease),
                                    now.plus(properties.getLeaseDuration()),
                                    now.plus(properties.getExecutionTimeout()),
                                    now,
                                    task.getTaskId(),
                                    actor.tenantId(),
                                    actor.userId(),
                                    principal.deviceId(),
                                    task.getRevision(),
                                    now);
                    require(changed == 1, 409, "STALE_ATTEMPT", "任务已被其他请求修改");
                    return claimView(ownedTask(actor, task.getTaskId()), lease);
                });
    }

    /**
     * 续约当前尝试；匹配 owner、设备、attempt、租约摘要和修订号，不延长运行总截止时间。
     *
     * @param principal 当前已认证设备
     * @param taskId 仅限本设备本人任务
     * @param lease 当前尝试、原始随机租约和预期修订号
     * @return 更新后的修订号、租约到期及取消状态
     * @throws EngineeringException 旧尝试、过期租约、旧修订号、终态或失效设备
     */
    public EngineeringTaskClaim renew(
            EngineeringDevicePrincipal principal, Long taskId, EngineeringLease lease) {
        return repository.transaction(
                () -> {
                    var task = lockActiveLease(principal, taskId, lease, true, true);
                    var expires = now().plus(properties.getLeaseDuration());
                    if (expires.isAfter(task.getDeadlineAt())) expires = task.getDeadlineAt();
                    int changed =
                            repository.update(
                                    "update ai_engineering_task set"
                                        + " lease_expires_at=?,revision=revision+1,updated_at=?"
                                        + " where task_id=? and tenant_id=? and user_id=? and"
                                        + " revision=? and state='RUNNING'",
                                    expires,
                                    now(),
                                    taskId,
                                    task.getTenantId(),
                                    task.getUserId(),
                                    lease.expectedRevision());
                    require(changed == 1, 409, "STALE_ATTEMPT", "任务修订号已变化");
                    return claimView(ownedTask(principal.actor(), taskId), lease.leaseToken());
                });
    }

    /**
     * 提交有界阶段事件；相同 sequence 和摘要重试返回原回执，不能提交任意终态。
     *
     * @param principal 当前已认证设备
     * @param taskId 本设备当前任务编号
     * @param event 连续序号、固定阶段及有界中文摘要，租约原文不会入账
     * @return 首次提交或幂等重试的同一回执
     * @throws EngineeringException 步骤内容冲突、阶段倒退、旧租约或未请求的取消确认
     */
    public EngineeringTaskReceipt event(
            EngineeringDevicePrincipal principal, Long taskId, EngineeringTaskEvent event) {
        require(
                event != null
                        && event.sequence() > 0
                        && event.sequence() <= 64
                        && event.stage() != null,
                422,
                "INVALID_EVENT",
                "步骤序号或阶段无效");
        require(
                event.message() == null
                        || event.message().getBytes(StandardCharsets.UTF_8).length <= 2048,
                422,
                "INVALID_EVENT",
                "阶段摘要超过两千字节");
        require(
                event.errorCode() == null || ERRORS.contains(event.errorCode()),
                422,
                "INVALID_EVENT",
                "未开放该错误类别");
        String digest =
                EngineeringSecrets.hash(
                        EngineeringJson.write(
                                Arrays.asList(
                                        event.attempt(),
                                        event.expectedRevision(),
                                        event.sequence(),
                                        event.stage().name(),
                                        event.message(),
                                        event.cancelAcknowledged(),
                                        event.errorCode())));
        return repository.transaction(
                () -> {
                    lockDevice(principal);
                    var task = lockedTask(principal.actor(), taskId);
                    verifyLease(task, principal, event.lease(), false);
                    var journal =
                            new ArrayList<>(
                                    Arrays.asList(
                                            EngineeringJson.read(
                                                    task.getStepJournal() == null
                                                            ? "[]"
                                                            : task.getStepJournal(),
                                                    Journal[].class)));
                    for (var entry : journal)
                        if (entry.sequence() == event.sequence()) {
                            require(
                                    entry.digest().equals(digest),
                                    409,
                                    "IDEMPOTENCY_CONFLICT",
                                    "该步骤序号已对应另一事件");
                            return entry.receipt();
                        }
                    require(
                            "RUNNING".equals(task.getState()),
                            409,
                            "STALE_ATTEMPT",
                            "任务已结束，不接受新阶段");
                    require(
                            task.getRevision() == event.expectedRevision(),
                            409,
                            "STALE_REVISION",
                            "任务修订号已变化");
                    require(
                            journal.size() < 64
                                    && event.sequence()
                                            == (journal.isEmpty()
                                                    ? 1
                                                    : journal.get(journal.size() - 1).sequence()
                                                            + 1),
                            409,
                            "EVENT_SEQUENCE_CONFLICT",
                            "步骤序号必须连续递增");
                    require(
                            task.getStage() == null
                                    || event.stage().ordinal()
                                            >= EngineeringStage.valueOf(task.getStage()).ordinal(),
                            422,
                            "INVALID_EVENT",
                            "执行阶段不能倒退");
                    require(
                            !event.cancelAcknowledged()
                                    || Boolean.TRUE.equals(task.getCancelRequested()),
                            409,
                            "CANCEL_NOT_REQUESTED",
                            "服务端尚未请求取消该任务");
                    String state = event.cancelAcknowledged() ? "CANCELED" : "RUNNING";
                    var receipt =
                            new EngineeringTaskReceipt(
                                    taskId.toString(),
                                    task.getAttempt(),
                                    task.getRevision() + 1,
                                    state,
                                    event.stage().name(),
                                    Boolean.TRUE.equals(task.getCancelRequested()));
                    journal.add(
                            new Journal(
                                    event.sequence(),
                                    digest,
                                    event.stage().name(),
                                    event.message(),
                                    receipt));
                    int changed =
                            repository.update(
                                    "update ai_engineering_task set"
                                        + " state=?,stage=?,step_journal=?,error_code=?,error_message=?,revision=revision+1,updated_at=?"
                                        + " where task_id=? and tenant_id=? and user_id=? and"
                                        + " device_id=? and attempt=? and revision=? and"
                                        + " lease_hash=? and state='RUNNING' and lease_expires_at>?"
                                        + " and deadline_at>?",
                                    state,
                                    event.stage().name(),
                                    EngineeringJson.write(journal),
                                    event.errorCode() == null
                                            ? task.getErrorCode()
                                            : event.errorCode(),
                                    event.errorCode() == null
                                            ? task.getErrorMessage()
                                            : "本地连接器报告执行异常，请查看任务阶段",
                                    now(),
                                    taskId,
                                    task.getTenantId(),
                                    task.getUserId(),
                                    principal.deviceId(),
                                    event.attempt(),
                                    event.expectedRevision(),
                                    EngineeringSecrets.hash(event.leaseToken()),
                                    now(),
                                    now());
                    require(changed == 1, 409, "STALE_ATTEMPT", "任务状态或租约已变化");
                    if (event.cancelAcknowledged()) clearBusy(task);
                    return receipt;
                });
    }

    /**
     * 取消本人任务；运行中只设置请求，由连接器在步骤边界确认，不自动重放编辑器写入。
     *
     * @param actor 当前服务端操作者
     * @param taskId 本人任务编号
     * @return 取消后真实状态，已终结任务保持原终态
     * @throws EngineeringException 任务不属于当前 owner 或权限失效
     */
    public EngineeringTaskVo cancel(EngineeringActor actor, Long taskId) {
        access.requireActor(actor, "ai:engineering:task", false);
        return repository.transaction(
                () -> {
                    var task = lockedTask(actor, taskId);
                    var state = EngineeringTaskState.valueOf(task.getState());
                    if (state.isTerminal() || Boolean.TRUE.equals(task.getCancelRequested()))
                        return view(task);
                    String target = state == EngineeringTaskState.RUNNING ? "RUNNING" : "CANCELED";
                    int changed =
                            repository.update(
                                    "update ai_engineering_task set"
                                        + " state=?,cancel_requested=true,revision=revision+1,updated_at=?"
                                        + " where task_id=? and tenant_id=? and user_id=? and"
                                        + " revision=? and state=?",
                                    target,
                                    now(),
                                    taskId,
                                    actor.tenantId(),
                                    actor.userId(),
                                    task.getRevision(),
                                    task.getState());
                    require(changed == 1, 409, "STALE_REVISION", "任务状态已变化");
                    return view(ownedTask(actor, taskId));
                });
    }

    /** 浏览器读取本人任务；失去查询权限后的历史链接立即失效。 */
    public EngineeringTaskVo detail(EngineeringActor actor, Long taskId) {
        access.requireActor(actor, "ai:engineering:query", false);
        return view(ownedTask(actor, taskId));
    }

    /** 设备读取本设备任务，供修订号冲突后恢复当前取消和阶段状态。 */
    public EngineeringTaskVo deviceDetail(EngineeringDevicePrincipal principal, Long taskId) {
        devices.requireDevice(principal);
        var task = ownedTask(principal.actor(), taskId);
        require(
                Objects.equals(task.getDeviceId(), principal.deviceId()),
                403,
                "DEVICE_MISMATCH",
                "任务不属于当前设备");
        return view(task);
    }

    /** 当前用户分页任务，可按会话恢复真实工具回执，不从模型自由文本解析卡片。 */
    public TableDataInfo<EngineeringTaskVo> list(
            EngineeringActor actor, Long sessionId, int page, int pageSize) {
        access.requireActor(actor, "ai:engineering:query", false);
        require(page > 0 && pageSize > 0 && pageSize <= 100, 422, "INVALID_REQUEST", "分页参数无效");
        String where =
                " where tenant_id=? and user_id=?" + (sessionId == null ? "" : " and session_id=?");
        var args = new ArrayList<Object>(List.of(actor.tenantId(), actor.userId()));
        if (sessionId != null) args.add(sessionId);
        long count =
                repository.count(
                        "select count(*) from ai_engineering_task" + where, args.toArray());
        args.add(pageSize);
        args.add((long) (page - 1) * pageSize);
        var rows =
                repository.query(
                        AiEngineeringTask.class,
                        "select * from ai_engineering_task"
                                + where
                                + " order by created_at desc,task_id desc limit ? offset ?",
                        args.toArray());
        return new TableDataInfo<>(rows.stream().map(this::view).toList(), count);
    }

    /**
     * 申请票据前验证有效租约；此方法不持有数据库锁等待文件 IO。
     *
     * @param principal 当前设备身份
     * @param taskId 本人本设备任务
     * @param lease 当前尝试与随机租约
     * @param checkRevision 是否要求修订号完全匹配
     * @param allowCancel 是否允许读取已经请求取消的运行任务
     * @return 内部任务记录，不得直接作为 HTTP 出参
     * @throws EngineeringException 租约、尝试、owner、修订号或状态无效
     */
    public AiEngineeringTask activeLease(
            EngineeringDevicePrincipal principal,
            Long taskId,
            EngineeringLease lease,
            boolean checkRevision,
            boolean allowCancel) {
        devices.requireDevice(principal);
        var task = ownedTask(principal.actor(), taskId);
        verifyLease(task, principal, lease, checkRevision);
        require("RUNNING".equals(task.getState()), 409, "STALE_ATTEMPT", "任务已结束");
        require(
                allowCancel || !Boolean.TRUE.equals(task.getCancelRequested()),
                409,
                "CANCEL_REQUESTED",
                "任务正在取消，已停止新上传");
        return task;
    }

    /**
     * 在已有短事务内按设备、任务顺序加锁并校验租约；调用方禁止在锁内执行 OSS/编辑器 IO。
     *
     * @param principal 当前设备身份
     * @param taskId 本人本设备任务
     * @param lease 当前尝试与随机租约
     * @param checkRevision 是否要求修订号匹配
     * @param allowCancel 是否允许已请求取消的任务
     * @return 仅供事务内元数据操作的内部任务
     * @throws EngineeringException 旧租约、旧修订号、已终结任务或失效授权
     */
    public AiEngineeringTask lockActiveLease(
            EngineeringDevicePrincipal principal,
            Long taskId,
            EngineeringLease lease,
            boolean checkRevision,
            boolean allowCancel) {
        lockDevice(principal);
        var task = lockedTask(principal.actor(), taskId);
        verifyLease(task, principal, lease, checkRevision);
        require("RUNNING".equals(task.getState()), 409, "STALE_ATTEMPT", "任务已结束");
        require(
                allowCancel || !Boolean.TRUE.equals(task.getCancelRequested()),
                409,
                "CANCEL_REQUESTED",
                "任务正在取消，已停止新上传");
        return task;
    }

    /**
     * 验证只保存租约摘要的上传票据；上传期间允许续约推进修订号，仍禁止旧尝试和取消后确认。
     *
     * @param principal 当前认证设备
     * @param taskId 票据绑定任务
     * @param attempt 票据绑定尝试
     * @param leaseHash 服务端票据负载保存的租约摘要，不能取自设备正文
     * @param lock true 时要求已在仓储短事务内，并按固定顺序加锁
     * @return 通过校验的内部任务
     * @throws EngineeringException 绑定不符、租约过期、任务结束或取消请求已生效
     */
    public AiEngineeringTask uploadLease(
            EngineeringDevicePrincipal principal,
            Long taskId,
            int attempt,
            String leaseHash,
            boolean lock) {
        if (lock) lockDevice(principal);
        else devices.requireDevice(principal);
        var task =
                lock ? lockedTask(principal.actor(), taskId) : ownedTask(principal.actor(), taskId);
        require(
                Objects.equals(task.getDeviceId(), principal.deviceId()),
                403,
                "DEVICE_MISMATCH",
                "任务不属于当前设备");
        require(
                task.getAttempt() == attempt && Objects.equals(task.getLeaseHash(), leaseHash),
                409,
                "STALE_ATTEMPT",
                "上传票据所属尝试或租约已失效");
        require(
                "RUNNING".equals(task.getState())
                        && !Boolean.TRUE.equals(task.getCancelRequested()),
                409,
                "STALE_ATTEMPT",
                "任务已结束或正在取消");
        require(
                task.getLeaseExpiresAt() != null
                        && task.getLeaseExpiresAt().isAfter(now())
                        && task.getDeadlineAt() != null
                        && task.getDeadlineAt().isAfter(now()),
                409,
                "LEASE_EXPIRED",
                "上传时任务租约已失效");
        return task;
    }

    private AiEngineeringDevice lockDevice(EngineeringDevicePrincipal principal) {
        var actor = principal.actor();
        var device =
                EngineeringRepository.one(
                        repository.query(
                                AiEngineeringDevice.class,
                                "select * from ai_engineering_device where tenant_id=? and"
                                    + " user_id=? and device_id=? for update",
                                actor.tenantId(),
                                actor.userId(),
                                principal.deviceId()));
        require(device != null, 401, "DEVICE_REVOKED", "设备已失效");
        devices.requireDevice(principal);
        return device;
    }

    private void verifyLease(
            AiEngineeringTask task,
            EngineeringDevicePrincipal principal,
            EngineeringLease lease,
            boolean revision) {
        require(
                lease != null && Objects.equals(task.getDeviceId(), principal.deviceId()),
                403,
                "DEVICE_MISMATCH",
                "任务不属于当前设备");
        require(
                task.getAttempt() == lease.attempt()
                        && EngineeringSecrets.matches(task.getLeaseHash(), lease.leaseToken()),
                409,
                "STALE_ATTEMPT",
                "任务尝试或租约令牌已失效");
        require(
                task.getLeaseExpiresAt() != null
                        && task.getLeaseExpiresAt().isAfter(now())
                        && task.getDeadlineAt() != null
                        && task.getDeadlineAt().isAfter(now()),
                409,
                "LEASE_EXPIRED",
                "任务租约或运行截止时间已过期，禁止重放设计写入");
        if (revision)
            require(
                    task.getRevision() == lease.expectedRevision(),
                    409,
                    "STALE_REVISION",
                    "任务修订号已变化");
    }

    private AiEngineeringTask lockedTask(EngineeringActor actor, Long id) {
        var task =
                EngineeringRepository.one(
                        repository.query(
                                AiEngineeringTask.class,
                                "select * from ai_engineering_task where tenant_id=? and user_id=?"
                                    + " and task_id=? for update",
                                actor.tenantId(),
                                actor.userId(),
                                id));
        require(task != null, 404, "TASK_NOT_FOUND", "任务不存在或不属于当前用户");
        return task;
    }

    /** 内部按 owner 读取任务，不导出租约摘要给 HTTP 或模型。 */
    public AiEngineeringTask ownedTask(EngineeringActor actor, Long id) {
        var task = repository.task(actor.tenantId(), actor.userId(), id);
        require(task != null, 404, "TASK_NOT_FOUND", "任务不存在或不属于当前用户");
        return task;
    }

    /**
     * 由可信产物判定器计算终态；完成 CAS 与设备忙碌槽释放在同一事务提交。
     *
     * @param principal 当前设备身份
     * @param taskId 本设备任务
     * @param lease 当前尝试、随机租约与预期修订号
     * @param resolver 仅由服务端已验真产物服务传入的元数据判定器，不能从请求正文构造
     * @return 最终持久化任务视图
     * @throws EngineeringException 终态、取消、过期租约、旧修订号或非法服务端判定结果
     */
    public EngineeringTaskVo finish(
            EngineeringDevicePrincipal principal,
            Long taskId,
            EngineeringLease lease,
            Function<AiEngineeringTask, Completion> resolver) {
        var completed = repository.transaction(
                () -> {
                    var task = lockActiveLease(principal, taskId, lease, true, false);
                    var outcome = resolver.apply(task);
                    require(
                            outcome != null
                                    && Set.of(
                                                    EngineeringTaskState.SUCCEEDED,
                                                    EngineeringTaskState.PARTIAL_SUCCESS,
                                                    EngineeringTaskState.FAILED)
                                            .contains(outcome.state()),
                            422,
                            "INVALID_RESULT",
                            "服务端产物结果无效");
                    int changed =
                            repository.update(
                                    "update ai_engineering_task set"
                                        + " state=?,stage='FINALIZING',revision=revision+1,error_code=?,error_message=?,step_journal=?,updated_at=?"
                                        + " where task_id=? and tenant_id=? and user_id=? and"
                                        + " device_id=? and attempt=? and revision=? and"
                                        + " state='RUNNING' and cancel_requested=false",
                                    outcome.state().name(),
                                    outcome.errorCode(),
                                    outcome.message(),
                                    completionJournal(task, outcome),
                                    now(),
                                    taskId,
                                    task.getTenantId(),
                                    task.getUserId(),
                                    principal.deviceId(),
                                    lease.attempt(),
                                    lease.expectedRevision());
                    require(changed == 1, 409, "STALE_ATTEMPT", "任务已取消或被其他请求完成");
                    clearBusy(task);
                    return view(ownedTask(principal.actor(), taskId));
                });
        try {
            notifier.completed(principal.actor(), completed);
        } catch (RuntimeException unavailable) {
            log.warn("工程任务完成通知失败，任务 {} 可通过查询恢复", completed.taskId());
        }
        return completed;
    }

    private String completionJournal(AiEngineeringTask task, Completion result) {
        String saved = task.getStepJournal() == null ? "[]" : task.getStepJournal();
        if (result.connectionDiagnostic() == null) return saved;
        var entries = new ArrayList<>(Arrays.asList(EngineeringJson.read(saved, Journal[].class)));
        if (entries.isEmpty()) {
            entries.add(new Journal(
                    1, EngineeringSecrets.hash("complete:" + task.getTaskId() + ":" + task.getRevision()),
                    "FINALIZING", "连接检查结果已保存",
                    new EngineeringTaskReceipt(task.getTaskId().toString(), task.getAttempt(),
                            task.getRevision() + 1, result.state().name(), "FINALIZING", false),
                    result.connectionDiagnostic()));
        } else {
            var last = entries.get(entries.size() - 1);
            entries.set(entries.size() - 1, new Journal(last.sequence(), last.digest(), last.stage(),
                    last.message(), last.receipt(), result.connectionDiagnostic()));
        }
        return EngineeringJson.write(entries);
    }

    /** 有界扫描等待超期及运行不确定任务，CAS 防止多实例重复终结；绝不自动重排。 */
    public int maintain() {
        var candidates =
                repository.query(
                        AiEngineeringTask.class,
                        "select * from ai_engineering_task where (state='WAITING_DEVICE' and"
                            + " waiting_expires_at<=?) or (state='RUNNING' and (lease_expires_at<=?"
                            + " or deadline_at<=?)) order by task_id limit 100",
                        now(),
                        now(),
                        now());
        int changed = 0;
        for (var candidate : candidates)
            changed +=
                    com.nexus.common.tenant.helper.TenantHelper.dynamic(
                            candidate.getTenantId(), () -> expire(candidate));
        return changed;
    }

    private int expire(AiEngineeringTask candidate) {
        return repository.transaction(
                () -> {
                    repository.query(
                            AiEngineeringDevice.class,
                            "select * from ai_engineering_device where tenant_id=? and user_id=?"
                                + " and device_id=? for update",
                            candidate.getTenantId(),
                            candidate.getUserId(),
                            candidate.getDeviceId());
                    boolean running = "RUNNING".equals(candidate.getState());
                    int changed =
                            repository.update(
                                    "update ai_engineering_task set"
                                        + " state=?,error_code=?,error_message=?,revision=revision+1,updated_at=?"
                                        + " where tenant_id=? and user_id=? and task_id=? and"
                                        + " revision=? and state=? and "
                                            + (running
                                                    ? "(lease_expires_at<=? or deadline_at<=?)"
                                                    : "waiting_expires_at<=?"),
                                    expireArgs(candidate, running));
                    if (changed == 1 && running) clearBusy(candidate);
                    return changed;
                });
    }

    private Object[] expireArgs(AiEngineeringTask task, boolean running) {
        var values =
                new ArrayList<Object>(
                        Arrays.asList(
                                running ? "FAILED" : "EXPIRED",
                                running ? "EXECUTION_UNCERTAIN" : "DEVICE_OFFLINE",
                                running ? "执行租约失效，工程可能已修改，请人工核对" : "等待设备超时",
                                now(),
                                task.getTenantId(),
                                task.getUserId(),
                                task.getTaskId(),
                                task.getRevision(),
                                task.getState(),
                                now()));
        if (running) values.add(now());
        return values.toArray();
    }

    private void clearBusy(AiEngineeringTask task) {
        repository.update(
                "update ai_engineering_device set busy_task_id=null where tenant_id=? and user_id=?"
                    + " and device_id=? and busy_task_id=?",
                task.getTenantId(),
                task.getUserId(),
                task.getDeviceId(),
                task.getTaskId());
    }

    private EngineeringTaskVo view(AiEngineeringTask t) {
        var journal =
                EngineeringJson.read(
                        t.getStepJournal() == null ? "[]" : t.getStepJournal(), Journal[].class);
        var events =
                Arrays.stream(journal)
                        .map(
                                e ->
                                        new EventView(
                                                e.sequence(),
                                                e.stage(),
                                                e.message(),
                                                e.receipt().revision()))
                        .toList();
        var request = EngineeringJson.read(t.getRequestJson(), EngineeringTaskCommand.class);
        return new EngineeringTaskVo(
                t.getTaskId().toString(),
                t.getDeviceId().toString(),
                text(t.getSessionId()),
                t.getRequestId(),
                text(t.getParentTaskId()),
                t.getAction(),
                t.getState(),
                t.getStage(),
                t.getAttempt(),
                t.getRevision(),
                Boolean.TRUE.equals(t.getCancelRequested()),
                t.getErrorCode(),
                t.getErrorMessage(),
                time(t.getCreatedAt()),
                time(t.getUpdatedAt()),
                "/ai/engineering/task/" + t.getTaskId(),
                events,
                EngineeringDeviceService.PROTOCOL,
                request.templateId(),
                request.templateVersion(),
                request.templateSha256(),
                request.parameters(),
                time(t.getLeaseExpiresAt()),
                time(t.getDeadlineAt()),
                journal.length == 0 ? null : journal[journal.length - 1].connectionDiagnostic());
    }

    private EngineeringTaskClaim claimView(AiEngineeringTask t, String token) {
        var request = EngineeringJson.read(t.getRequestJson(), EngineeringTaskCommand.class);
        return new EngineeringTaskClaim(
                EngineeringDeviceService.PROTOCOL,
                t.getTaskId().toString(),
                t.getAction(),
                t.getAttempt(),
                t.getRevision(),
                token,
                time(t.getLeaseExpiresAt()),
                time(t.getDeadlineAt()),
                request.templateId(),
                request.templateVersion(),
                request.templateSha256(),
                text(t.getParentTaskId()),
                request.parameters(),
                Boolean.TRUE.equals(t.getCancelRequested()));
    }

    private static String text(Long value) {
        return value == null ? null : value.toString();
    }

    private static String time(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * 内部步骤账本，不包含令牌，最多六十四项。
     *
     * @param sequence 连续递增的步骤序号，最多六十四项
     * @param digest 内部步骤内容摘要，不含令牌原文
     * @param stage 协议定义的固定执行阶段
     * @param message 供用户阅读的有界摘要
     * @param receipt 不含令牌的原始幂等回执
     */
    public record Journal(
            int sequence,
            String digest,
            String stage,
            String message,
            EngineeringTaskReceipt receipt,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            EngineeringConnectionDiagnostic connectionDiagnostic) {
        public Journal(int sequence, String digest, String stage, String message, EngineeringTaskReceipt receipt) {
            this(sequence, digest, stage, message, receipt, null);
        }
    }

    /**
     * 面向用户的有界步骤摘要。
     *
     * @param sequence 连续递增的步骤序号，最多六十四项
     * @param stage 协议定义的固定执行阶段
     * @param message 供用户阅读的有界摘要
     * @param revision 服务端修订号，与步骤序号分开
     */
    public record EventView(int sequence, String stage, String message, long revision) {}

    /**
     * 仅由可信产物判定服务构造的完成结果。
     *
     * @param state 服务端实际任务状态，不能自报任意终态
     * @param errorCode 稳定协议错误类别，不包含原始异常信息
     * @param message 供用户阅读的有界摘要
     */
    public record Completion(EngineeringTaskState state, String errorCode, String message,
            EngineeringConnectionDiagnostic connectionDiagnostic) {
        public Completion(EngineeringTaskState state, String errorCode, String message) {
            this(state, errorCode, message, null);
        }
    }
}
