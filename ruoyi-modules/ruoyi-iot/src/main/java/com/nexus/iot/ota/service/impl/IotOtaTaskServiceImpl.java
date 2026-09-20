package com.nexus.iot.ota.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.command.domain.IotDeviceCommand;
import com.nexus.iot.command.domain.bo.IotDeviceCommandBo;
import com.nexus.iot.command.service.IIotDeviceCommandService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceStatus;
import com.nexus.iot.device.domain.IotProduct;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.group.service.IIotDeviceGroupService;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import com.nexus.iot.device.mapper.IotProductMapper;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.firmware.domain.IotFirmware;
import com.nexus.iot.firmware.mapper.IotFirmwareMapper;
import com.nexus.iot.ota.domain.IotOtaStatus;
import com.nexus.iot.ota.domain.IotOtaTask;
import com.nexus.iot.ota.domain.bo.IotOtaBatchTaskBo;
import com.nexus.iot.ota.domain.bo.IotOtaReportBo;
import com.nexus.iot.ota.domain.bo.IotOtaTaskBo;
import com.nexus.iot.ota.domain.vo.IotOtaBatchResultVo;
import com.nexus.iot.ota.domain.vo.IotOtaVersionCheckVo;
import com.nexus.iot.ota.mapper.IotOtaTaskMapper;
import com.nexus.iot.ota.service.IIotOtaTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IoT OTA 固件升级任务Service业务层处理（审计 §4「功能缺失清单」#1 OTA MVP）。
 * <p>
 * 关键设计：
 * <ol>
 *   <li><b>复用既有下行通道</b>：升级指令走 {@link IIotDeviceCommandService#sendServiceInvoke}
 *       （{@code event/set} Topic + {@code iot_device_command} 留痕 + 设备回执），
 *       本模块不新增 MQTT 发布路径；</li>
 *   <li><b>先留痕后下发</b>：任务先以 PENDING 落库拿到 taskId，再下发指令；发布失败时任务落
 *       FAILED 并写明"仅登记未下发"，**不伪造成功**（与 {@code property/set} 同口径）；</li>
 *   <li><b>设备可用性前置校验</b>：禁用/注销设备（{@link IotDeviceStatus#canConnect} 为 false）
 *       直接拒绝，与 MQTT 认证口径一致；</li>
 *   <li><b>业务幂等</b>：同设备 + 同固件不允许并存"进行中"任务；回执靠状态机 CAS 幂等，
 *       终态任务不接受后续回执；</li>
 *   <li><b>超时兜底</b>：{@code markTimeout} 由 {@code IotOtaTimeoutTask} 定时调用
 *       （SnailJob + {@code @Scheduled} 本地兜底 + Lock4j）。</li>
 * </ol>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotOtaTaskServiceImpl implements IIotOtaTaskService {

    /** 升级指令的服务名（下发到 {@code iot/{tenant}/{device}/event/set}）。 */
    public static final String SERVICE_OTA_UPGRADE = "otaUpgrade";
    /** 指令状态：已下发（与 {@code IotDeviceCommandServiceImpl.STATUS_SENT} 同值）。 */
    public static final String CMD_STATUS_SENT = "SENT";
    /** 设备事件码前缀：OTA 进度/结果事件（{@code OTA_PROGRESS}/{@code OTA_RESULT}）。 */
    public static final String EVENT_CODE_PREFIX = "OTA_";

    private final IotOtaTaskMapper baseMapper;
    private final IotFirmwareMapper firmwareMapper;
    private final IotDeviceMapper deviceMapper;
    private final IotProductMapper productMapper;
    private final IIotDeviceService deviceService;
    private final IIotDeviceGroupService deviceGroupService;
    private final IIotDeviceCommandService deviceCommandService;
    /**
     * 注入全局 Jackson ObjectMapper（静态 {@code JsonUtils} 初始化依赖 Spring 容器，单测无法打桩）。
     */
    private final ObjectMapper objectMapper;

    /**
     * 分页查询升级任务。
     *
     * @param deviceId   设备ID（可空）
     * @param firmwareId 固件ID（可空）
     * @param status     状态（可空）
     * @param batchNo    批次号（可空）
     * @param pageQuery  分页参数
     * @return 任务分页结果
     */
    @Override
    public TableDataInfo<IotOtaTask> selectPageList(Long deviceId, Long firmwareId, String status,
                                                    String batchNo, PageQuery pageQuery) {
        LambdaQueryWrapper<IotOtaTask> lqw = Wrappers.lambdaQuery();
        lqw.eq(deviceId != null, IotOtaTask::getDeviceId, deviceId);
        lqw.eq(firmwareId != null, IotOtaTask::getFirmwareId, firmwareId);
        lqw.eq(StringUtils.isNotBlank(status), IotOtaTask::getStatus, status);
        lqw.eq(StringUtils.isNotBlank(batchNo), IotOtaTask::getBatchNo, batchNo);
        lqw.orderByDesc(IotOtaTask::getTaskId);
        Page<IotOtaTask> page = baseMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询任务详情。
     *
     * @param taskId 任务ID
     * @return 任务实体，不存在返回 null
     */
    @Override
    public IotOtaTask selectById(Long taskId) {
        return taskId == null ? null : baseMapper.selectById(taskId);
    }

    /**
     * 单设备下发升级任务。
     *
     * @param bo 任务业务对象
     * @return 落库后的任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotOtaTask createTask(IotOtaTaskBo bo) {
        return doCreate(bo.getDeviceId(), bo.getFirmwareId(), null, null, bo.getRemark());
    }

    /**
     * 按分组批量下发升级任务（部分成功语义）。
     *
     * @param bo 批量任务业务对象
     * @return 批量结果
     */
    @Override
    public IotOtaBatchResultVo createBatchTask(IotOtaBatchTaskBo bo) {
        List<Long> deviceIds = deviceGroupService.selectDeviceIds(bo.getGroupId(), bo.getIncludeSubGroup());
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new ServiceException("分组下没有设备，未创建任何升级任务：groupId=" + bo.getGroupId());
        }
        // 固件先校验一次：未发布/不存在时整批快速失败，避免逐台设备重复报同一错误
        requireReleasedFirmware(bo.getFirmwareId());
        String batchNo = "OTA" + System.currentTimeMillis();
        IotOtaBatchResultVo result = new IotOtaBatchResultVo();
        result.setBatchNo(batchNo);
        result.setTotal(deviceIds.size());
        for (Long deviceId : deviceIds) {
            try {
                result.getTasks().add(doCreate(deviceId, bo.getFirmwareId(), bo.getGroupId(), batchNo, bo.getRemark()));
                result.setCreated(result.getCreated() + 1);
            } catch (Exception e) {
                // 单台失败（禁用/注销/重复任务/设备不存在）不影响其余设备，原因逐条回传
                result.setSkipped(result.getSkipped() + 1);
                result.getMessages().add("设备[" + deviceId + "]未创建：" + e.getMessage());
                log.warn("批量 OTA 任务跳过设备：deviceId={}, batchNo={}, err={}", deviceId, batchNo, e.getMessage());
            }
        }
        log.info("批量 OTA 任务下发完成：batchNo={}, groupId={}, total={}, created={}, skipped={}",
            batchNo, bo.getGroupId(), result.getTotal(), result.getCreated(), result.getSkipped());
        return result;
    }

    /**
     * 人工重试（仅 FAILED/TIMEOUT）。
     *
     * @param taskId 任务ID
     * @return 重试后的任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotOtaTask retry(Long taskId) {
        IotOtaTask task = requireTask(taskId);
        if (!IotOtaStatus.canRetry(task.getStatus())) {
            throw new ServiceException("任务当前状态为 " + task.getStatus()
                + "，仅 FAILED/TIMEOUT 可重试（进行中任务请等待或先取消，已成功任务无需重试）");
        }
        IotFirmware firmware = requireReleasedFirmware(task.getFirmwareId());
        IotDeviceVo device = requireUsableDevice(task.getDeviceId(), "重试升级");
        String fromStatus = task.getStatus();
        Date now = new Date();
        // CAS 领取：仅当状态仍为读取时的状态才允许重试（防并发重复重试）
        int rows = baseMapper.update(null, Wrappers.<IotOtaTask>lambdaUpdate()
            .eq(IotOtaTask::getTaskId, taskId)
            .eq(IotOtaTask::getStatus, fromStatus)
            .setSql("retry_count = retry_count + 1")
            .set(IotOtaTask::getStatus, IotOtaStatus.SENT)
            .set(IotOtaTask::getProgress, 0)
            .set(IotOtaTask::getSentTime, now)
            .set(IotOtaTask::getStartTime, null)
            .set(IotOtaTask::getFinishTime, null)
            .set(IotOtaTask::getFailReason, null)
            .set(IotOtaTask::getUpdateTime, now));
        if (rows == 0) {
            throw new ServiceException("任务重试失败：状态已被其他操作变更，请刷新后重试");
        }
        // 重试重新下发一条新指令（任务与指令一一对应，留痕清晰、便于区分每次尝试）
        dispatch(taskId, IotOtaStatus.SENT, firmware, device);
        return baseMapper.selectById(taskId);
    }

    /**
     * 人工取消（仅 PENDING/SENT/UPGRADING）。
     *
     * @param taskId 任务ID
     * @param reason 取消原因
     * @return 取消后的任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotOtaTask cancel(Long taskId, String reason) {
        IotOtaTask task = requireTask(taskId);
        if (!IotOtaStatus.canCancel(task.getStatus())) {
            throw new ServiceException("任务当前状态为 " + task.getStatus()
                + "，仅 PENDING/SENT/UPGRADING 可取消");
        }
        Date now = new Date();
        int rows = baseMapper.update(null, Wrappers.<IotOtaTask>lambdaUpdate()
            .eq(IotOtaTask::getTaskId, taskId)
            .eq(IotOtaTask::getStatus, task.getStatus())
            .set(IotOtaTask::getStatus, IotOtaStatus.CANCELLED)
            .set(IotOtaTask::getFinishTime, now)
            .set(IotOtaTask::getFailReason, StringUtils.isBlank(reason)
                ? "人工取消升级任务" : StringUtils.substring("人工取消：" + reason, 0, 500))
            .set(IotOtaTask::getUpdateTime, now));
        if (rows == 0) {
            throw new ServiceException("任务取消失败：状态已被其他操作变更，请刷新后重试");
        }
        log.info("OTA 任务已取消：taskId={}, reason={}", taskId, reason);
        return baseMapper.selectById(taskId);
    }

    /**
     * 处理设备上报的进度/结果（HTTP 回执端点）。
     *
     * @param bo 回执业务对象
     * @return 更新后的任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotOtaTask report(IotOtaReportBo bo) {
        IotDevice device = resolveDevice(bo);
        if (device == null) {
            throw new ServiceException("设备不存在或无权限访问：" + describeDevice(bo));
        }
        return doReport(device, bo);
    }

    /**
     * 消费端旁路入口：从 {@code OTA_*} 设备事件解析升级进度/结果。
     * <p>非 OTA 事件直接返回；任何异常只记 WARN，**不阻断事件流消费**（事件本身已落库）。
     *
     * @param device   设备记录
     * @param entry    事件流 entry
     * @param streamId Stream entry id
     */
    @Override
    public void handleOtaEvent(IotDevice device, Map<Object, Object> entry, String streamId) {
        if (device == null || entry == null) {
            return;
        }
        try {
            String eventCode = str(entry.get("event_code"));
            if (StringUtils.isBlank(eventCode) || !eventCode.toUpperCase().startsWith(EVENT_CODE_PREFIX)) {
                return;
            }
            Map<String, Object> payload = parsePayload(str(entry.get("payload")));
            String code = eventCode.toUpperCase();
            boolean resultEvent = code.contains("RESULT") || code.contains("FINISH") || code.contains("DONE");
            String target;
            if (resultEvent) {
                target = isSuccess(payload.get("success")) || isSuccess(payload.get("result"))
                    ? IotOtaStatus.SUCCESS : IotOtaStatus.FAILED;
            } else {
                target = IotOtaStatus.UPGRADING;
            }
            IotOtaReportBo bo = new IotOtaReportBo();
            bo.setDeviceId(device.getDeviceId());
            bo.setTaskId(parseLong(firstNonBlank(str(payload.get("otaTaskId")), str(payload.get("taskId")),
                str(payload.get("task_id")))));
            bo.setStatus(target);
            bo.setProgress(toInt(payload.get("progress")));
            bo.setVersion(str(payload.get("version")));
            bo.setFailReason(firstNonBlank(str(payload.get("failReason")), str(payload.get("message")),
                str(payload.get("reason"))));
            bo.setMsgId(streamId);
            TenantHelper.dynamic(device.getTenantId(), () -> doReport(device, bo));
        } catch (Exception e) {
            log.warn("OTA 事件旁路处理失败（不影响事件消费）：device={}, streamId={}, err={}",
                device.getDeviceCode(), streamId, e.getMessage());
        }
    }

    /**
     * 超时兜底：SENT/UPGRADING 且下发时间早于 deadline 的任务置 TIMEOUT。
     *
     * @param deadline 超时截止时间
     * @return 被置为 TIMEOUT 的任务数
     */
    @Override
    public int markTimeout(Date deadline) {
        if (deadline == null) {
            return 0;
        }
        Date now = new Date();
        return baseMapper.update(null, Wrappers.<IotOtaTask>lambdaUpdate()
            .in(IotOtaTask::getStatus, List.of(IotOtaStatus.SENT, IotOtaStatus.UPGRADING))
            .isNotNull(IotOtaTask::getSentTime)
            .lt(IotOtaTask::getSentTime, deadline)
            .set(IotOtaTask::getStatus, IotOtaStatus.TIMEOUT)
            .set(IotOtaTask::getFinishTime, now)
            .set(IotOtaTask::getFailReason, "升级超时：设备在超时窗口内未回报进度/结果")
            .set(IotOtaTask::getUpdateTime, now));
    }

    /**
     * 版本一致性核对。
     *
     * @param firmwareId 固件ID
     * @param onlyBehind 仅返回落后设备
     * @return 不一致设备清单
     */
    @Override
    public List<IotOtaVersionCheckVo> checkVersion(Long firmwareId, Boolean onlyBehind) {
        IotFirmware firmware = firmwareMapper.selectById(firmwareId);
        if (firmware == null) {
            throw new ServiceException("固件不存在：" + firmwareId);
        }
        List<IotDevice> devices = deviceMapper.selectList(Wrappers.<IotDevice>lambdaQuery()
            .eq(IotDevice::getStatus, IotDeviceStatus.ACTIVE)
            .and(w -> w.isNull(IotDevice::getFirmwareVersion)
                .or().ne(IotDevice::getFirmwareVersion, firmware.getVersion()))
            .orderByAsc(IotDevice::getDeviceCode));
        List<IotOtaVersionCheckVo> list = new ArrayList<>();
        if (devices == null) {
            return list;
        }
        for (IotDevice d : devices) {
            boolean behind = isBehind(d.getFirmwareVersion(), firmware.getVersion());
            if (Boolean.TRUE.equals(onlyBehind) && !behind) {
                continue;
            }
            IotOtaVersionCheckVo vo = new IotOtaVersionCheckVo();
            vo.setDeviceId(d.getDeviceId());
            vo.setDeviceCode(d.getDeviceCode());
            vo.setDeviceName(d.getDeviceName());
            vo.setCurrentVersion(d.getFirmwareVersion());
            vo.setTargetVersion(firmware.getVersion());
            vo.setMatch(false);
            vo.setBehind(behind);
            list.add(vo);
        }
        return list;
    }

    /**
     * 版本落后判定：分段数值比较（如 {@code 1.10.0 > 1.9.0}）。
     * <p>非数值段按 0 处理，仅作粗粒度提示（版本号规约建议 {@code MAJOR.MINOR.PATCH}）。
     *
     * @param current 当前版本（空视为落后）
     * @param target  目标版本
     * @return 落后返回 true
     */
    public static boolean isBehind(String current, String target) {
        if (StringUtils.isBlank(current)) {
            return true;
        }
        if (StringUtils.isBlank(target)) {
            return false;
        }
        String[] cur = current.trim().split("\\.");
        String[] tar = target.trim().split("\\.");
        int len = Math.max(cur.length, tar.length);
        for (int i = 0; i < len; i++) {
            long c = segment(cur, i);
            long t = segment(tar, i);
            if (c != t) {
                return c < t;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 建任务主体：校验 → 先留痕(PENDING) → 下发 → 回写 SENT/FAILED。
     * <p>包级可见以便单测直接覆盖，避免 {@code @Transactional} 自调用失效问题。
     */
    private IotOtaTask doCreate(Long deviceId, Long firmwareId, Long groupId, String batchNo, String remark) {
        if (deviceId == null) {
            throw new ServiceException("设备ID不能为空");
        }
        IotFirmware firmware = requireReleasedFirmware(firmwareId);
        IotDeviceVo device = requireUsableDevice(deviceId, "下发升级任务");
        requireProductMatch(firmware, device);
        // 业务幂等：同设备 + 同固件不允许并存"进行中"任务（重复点击/重复批量下发被拒）
        Long active = baseMapper.selectCount(Wrappers.<IotOtaTask>lambdaQuery()
            .eq(IotOtaTask::getDeviceId, deviceId)
            .eq(IotOtaTask::getFirmwareId, firmwareId)
            .in(IotOtaTask::getStatus, IotOtaStatus.ACTIVE));
        if (active != null && active > 0) {
            throw new ServiceException("设备[" + device.getDeviceCode()
                + "]已存在进行中的同固件升级任务，请等待完成或先取消");
        }
        IotOtaTask task = new IotOtaTask();
        task.setFirmwareId(firmware.getFirmwareId());
        task.setTargetVersion(firmware.getVersion());
        task.setCurrentVersion(device.getFirmwareVersion());
        task.setDeviceId(device.getDeviceId());
        task.setDeviceCode(device.getDeviceCode());
        task.setGroupId(groupId);
        task.setBatchNo(batchNo);
        task.setStatus(IotOtaStatus.PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        task.setOperatorId(LoginHelper.getUserId());
        task.setOperatorName(LoginHelper.getUsername());
        task.setRemark(remark);
        // 先留痕（PENDING）拿到 taskId，再下发：即使下发失败也有完整任务记录
        baseMapper.insert(task);
        dispatch(task.getTaskId(), IotOtaStatus.PENDING, firmware, device);
        return baseMapper.selectById(task.getTaskId());
    }

    /**
     * 下发升级指令并回写任务状态（expectedStatus 为 CAS 前置状态）。
     */
    private void dispatch(Long taskId, String expectedStatus, IotFirmware firmware, IotDeviceVo device) {
        IotDeviceCommandBo cmdBo = new IotDeviceCommandBo();
        cmdBo.setDeviceId(device.getDeviceId());
        cmdBo.setServiceName(SERVICE_OTA_UPGRADE);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("otaTaskId", String.valueOf(taskId));
        params.put("firmwareId", String.valueOf(firmware.getFirmwareId()));
        params.put("version", firmware.getVersion());
        params.put("fileOssId", firmware.getFileOssId());
        params.put("fileName", firmware.getFileName());
        params.put("fileSize", firmware.getFileSize());
        params.put("checksumType", firmware.getChecksumType());
        params.put("checksum", firmware.getChecksum());
        cmdBo.setParams(params);
        cmdBo.setRemark("OTA升级任务 " + taskId);
        IotDeviceCommand command = deviceCommandService.sendServiceInvoke(cmdBo);
        boolean sent = command != null && CMD_STATUS_SENT.equals(command.getStatus());
        Date now = new Date();
        baseMapper.update(null, Wrappers.<IotOtaTask>lambdaUpdate()
            .eq(IotOtaTask::getTaskId, taskId)
            .eq(IotOtaTask::getStatus, expectedStatus)
            .set(IotOtaTask::getCommandId, command == null ? null : command.getCommandId())
            .set(IotOtaTask::getStatus, sent ? IotOtaStatus.SENT : IotOtaStatus.FAILED)
            .set(sent, IotOtaTask::getSentTime, now)
            .set(!sent, IotOtaTask::getFinishTime, now)
            .set(IotOtaTask::getFailReason, sent ? null
                : "升级指令下发失败：MQTT Broker 未启用或发布异常，任务仅登记未下发")
            .set(IotOtaTask::getUpdateTime, now));
        if (sent) {
            log.info("OTA 升级指令已下发：taskId={}, device={}, version={}, commandId={}",
                taskId, device.getDeviceCode(), firmware.getVersion(),
                command == null ? null : command.getCommandId());
        } else {
            log.warn("OTA 升级指令下发失败（已留痕 FAILED）：taskId={}, device={}, version={}",
                taskId, device.getDeviceCode(), firmware.getVersion());
        }
    }

    /**
     * 回执处理主体：状态机校验 + CAS 更新 + 成功时回写设备固件版本。
     */
    private IotOtaTask doReport(IotDevice device, IotOtaReportBo bo) {
        String target = bo.getStatus() == null ? null : bo.getStatus().trim().toUpperCase();
        if (!IotOtaStatus.REPORT_TARGETS.contains(target)) {
            throw new ServiceException("非法的回执状态：" + bo.getStatus() + "（仅支持 UPGRADING/SUCCESS/FAILED）");
        }
        IotOtaTask task = resolveTask(device, bo.getTaskId());
        if (task == null) {
            throw new ServiceException("未找到匹配的升级任务：device=" + device.getDeviceCode()
                + ", taskId=" + bo.getTaskId());
        }
        if (!IotOtaStatus.canReport(task.getStatus(), target)) {
            throw new ServiceException("非法的升级任务状态流转：" + task.getStatus() + " → " + target
                + "（任务已处于终态或回执顺序不合法，重复回执被拒绝）");
        }
        Date now = new Date();
        boolean terminal = IotOtaStatus.isTerminal(target);
        int current = task.getProgress() == null ? 0 : task.getProgress();
        int progress = bo.getProgress() == null ? current : Math.max(0, Math.min(100, bo.getProgress()));
        // 进度单调不回退：乱序回执不把进度改小
        progress = Math.max(progress, current);
        if (IotOtaStatus.SUCCESS.equals(target)) {
            progress = 100;
        }
        int rows = baseMapper.update(null, Wrappers.<IotOtaTask>lambdaUpdate()
            .eq(IotOtaTask::getTaskId, task.getTaskId())
            .eq(IotOtaTask::getStatus, task.getStatus())
            .set(IotOtaTask::getStatus, target)
            .set(IotOtaTask::getProgress, progress)
            .set(IotOtaStatus.UPGRADING.equals(target) && task.getStartTime() == null,
                IotOtaTask::getStartTime, now)
            .set(terminal, IotOtaTask::getFinishTime, now)
            .set(IotOtaStatus.FAILED.equals(target), IotOtaTask::getFailReason,
                StringUtils.isBlank(bo.getFailReason()) ? "设备回执升级失败"
                    : StringUtils.substring(bo.getFailReason(), 0, 500))
            .set(StringUtils.isNotBlank(bo.getMsgId()), IotOtaTask::getReportMsgId, bo.getMsgId())
            .set(IotOtaTask::getUpdateTime, now));
        if (rows == 0) {
            throw new ServiceException("回执处理失败：任务状态已被并发变更，请刷新后重试");
        }
        if (IotOtaStatus.SUCCESS.equals(target) && StringUtils.isNotBlank(task.getTargetVersion())) {
            // 升级成功 → 回写设备当前固件版本（版本一致性核对的权威来源）
            deviceMapper.update(null, Wrappers.<IotDevice>lambdaUpdate()
                .eq(IotDevice::getDeviceId, device.getDeviceId())
                .set(IotDevice::getFirmwareVersion, task.getTargetVersion())
                .set(IotDevice::getUpdateTime, now));
            log.info("OTA 升级成功，设备固件版本已回写：device={}, {} → {}",
                device.getDeviceCode(), task.getCurrentVersion(), task.getTargetVersion());
        } else {
            log.info("OTA 回执已处理：taskId={}, device={}, {} → {}, progress={}",
                task.getTaskId(), device.getDeviceCode(), task.getStatus(), target, progress);
        }
        return baseMapper.selectById(task.getTaskId());
    }

    /**
     * 取已发布固件（不存在/未发布/已停用直接拒绝）。
     */
    private IotFirmware requireReleasedFirmware(Long firmwareId) {
        if (firmwareId == null) {
            throw new ServiceException("固件ID不能为空");
        }
        IotFirmware firmware = firmwareMapper.selectById(firmwareId);
        if (firmware == null) {
            throw new ServiceException("固件不存在：" + firmwareId);
        }
        if (!com.nexus.iot.firmware.domain.IotFirmwareStatus.isReleasable(firmware.getStatus())) {
            throw new ServiceException("固件[" + firmware.getFirmwareName() + " " + firmware.getVersion()
                + "]当前状态为 " + firmware.getStatus() + "，仅已发布(RELEASED)固件可下发升级任务");
        }
        return firmware;
    }

    /**
     * 取可用设备（存在 + 允许 MQTT 接入口径）。
     */
    private IotDeviceVo requireUsableDevice(Long deviceId, String action) {
        if (deviceId == null) {
            throw new ServiceException("设备ID不能为空");
        }
        IotDeviceVo device = deviceService.selectById(deviceId);
        if (device == null) {
            throw new ServiceException("设备不存在或无权限访问：" + deviceId);
        }
        if (!IotDeviceStatus.canConnect(device.getStatus())) {
            throw new ServiceException("设备[" + device.getDeviceCode() + "]当前状态为 " + device.getStatus()
                + "，已禁用/注销的设备不可" + action);
        }
        return device;
    }

    /**
     * 固件适用产品型号校验（{@code productKey} 为空表示不限）。
     */
    private void requireProductMatch(IotFirmware firmware, IotDeviceVo device) {
        String productKey = firmware.getProductKey();
        if (StringUtils.isBlank(productKey)) {
            return;
        }
        IotProduct product = device.getProductId() == null ? null : productMapper.selectById(device.getProductId());
        if (product == null || !productKey.equals(product.getProductCode())) {
            throw new ServiceException("固件[" + firmware.getFirmwareName() + " " + firmware.getVersion()
                + "]适用产品型号为 " + productKey + "，设备[" + device.getDeviceCode() + "]不匹配");
        }
    }

    private IotOtaTask requireTask(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        IotOtaTask task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("升级任务不存在：" + taskId);
        }
        return task;
    }

    /**
     * 回执设备解析：优先 deviceId（租户过滤生效），其次 deviceCode。
     */
    private IotDevice resolveDevice(IotOtaReportBo bo) {
        if (bo.getDeviceId() != null) {
            return deviceMapper.selectById(bo.getDeviceId());
        }
        if (StringUtils.isBlank(bo.getDeviceCode())) {
            throw new ServiceException("必须提供 deviceId 或 deviceCode");
        }
        return deviceMapper.selectOne(Wrappers.<IotDevice>lambdaQuery()
            .eq(IotDevice::getDeviceCode, bo.getDeviceCode())
            .last("limit 1"));
    }

    /**
     * 回执任务匹配：优先 taskId，其次"该设备最近一条进行中任务"。
     */
    private IotOtaTask resolveTask(IotDevice device, Long taskId) {
        if (taskId != null) {
            return baseMapper.selectById(taskId);
        }
        return baseMapper.selectOne(Wrappers.<IotOtaTask>lambdaQuery()
            .eq(IotOtaTask::getDeviceId, device.getDeviceId())
            .in(IotOtaTask::getStatus, IotOtaStatus.ACTIVE)
            .orderByDesc(IotOtaTask::getTaskId)
            .last("limit 1"));
    }

    private String describeDevice(IotOtaReportBo bo) {
        return bo.getDeviceId() != null ? String.valueOf(bo.getDeviceId()) : bo.getDeviceCode();
    }

    private Map<String, Object> parsePayload(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("OTA 事件载荷解析失败（按空载荷处理）：{}", e.getMessage());
            return Map.of();
        }
    }

    private static long segment(String[] parts, int index) {
        if (index >= parts.length) {
            return 0L;
        }
        String digits = parts[index].replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean isSuccess(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "success".equals(s) || "ok".equals(s);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static Long parseLong(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
