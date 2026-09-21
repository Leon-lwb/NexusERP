package com.nexus.iot.ota.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.ota.domain.IotOtaTask;
import com.nexus.iot.ota.domain.bo.IotOtaBatchTaskBo;
import com.nexus.iot.ota.domain.bo.IotOtaReportBo;
import com.nexus.iot.ota.domain.bo.IotOtaTaskBo;
import com.nexus.iot.ota.domain.vo.IotOtaBatchResultVo;
import com.nexus.iot.ota.domain.vo.IotOtaVersionCheckVo;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * IoT OTA 固件升级任务Service接口。
 *
 * @author nexus
 */
public interface IIotOtaTaskService {

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
    TableDataInfo<IotOtaTask> selectPageList(Long deviceId, Long firmwareId, String status,
                                            String batchNo, PageQuery pageQuery);

    /**
     * 查询任务详情。
     *
     * @param taskId 任务ID
     * @return 任务实体，不存在返回 null
     */
    IotOtaTask selectById(Long taskId);

    /**
     * 单设备下发升级任务：先留痕（PENDING）→ 复用下行指令通道下发 → 回写 SENT/FAILED。
     *
     * @param bo 任务业务对象
     * @return 落库后的任务
     */
    IotOtaTask createTask(IotOtaTaskBo bo);

    /**
     * 按分组批量下发升级任务（复用分组关系表；部分成功语义，单台失败不整体回滚）。
     *
     * @param bo 批量任务业务对象
     * @return 批量结果（含成功任务与跳过原因）
     */
    IotOtaBatchResultVo createBatchTask(IotOtaBatchTaskBo bo);

    /**
     * 人工重试（仅 FAILED/TIMEOUT）：CAS 置 SENT 并重新下发一条新指令。
     *
     * @param taskId 任务ID
     * @return 重试后的任务
     */
    IotOtaTask retry(Long taskId);

    /**
     * 人工取消（仅 PENDING/SENT/UPGRADING）：CAS 置 CANCELLED。
     *
     * @param taskId 任务ID
     * @param reason 取消原因（可空）
     * @return 取消后的任务
     */
    IotOtaTask cancel(Long taskId, String reason);

    /**
     * 处理设备上报的进度/结果（HTTP 回执端点）。
     *
     * @param bo 回执业务对象
     * @return 更新后的任务
     */
    IotOtaTask report(IotOtaReportBo bo);

    /**
     * 消费端旁路入口：从设备事件（{@code event/post}，事件码 {@code OTA_*}）解析升级进度/结果。
     * <p>非 OTA 事件直接返回；任何异常都只记 WARN，**不阻断事件流消费**。
     *
     * @param device   设备记录
     * @param entry    事件流 entry
     * @param streamId Stream entry id（作为回执幂等/取证键）
     */
    void handleOtaEvent(IotDevice device, Map<Object, Object> entry, String streamId);

    /**
     * 超时兜底：把 {@code SENT}/{@code UPGRADING} 且下发时间早于 deadline 的任务置 TIMEOUT。
     * <p>调用方（{@code IotOtaTimeoutTask}）需以 {@code TenantHelper.ignore} 包裹以跨租户扫描。
     *
     * @param deadline 超时截止时间
     * @return 被置为 TIMEOUT 的任务数
     */
    int markTimeout(Date deadline);

    /**
     * 版本一致性核对：返回"设备当前版本 ≠ 目标版本"的在用设备清单（默认含落后与超前）。
     *
     * @param firmwareId 固件ID
     * @param onlyBehind 仅返回**落后**于目标版本的设备
     * @return 不一致设备清单
     */
    List<IotOtaVersionCheckVo> checkVersion(Long firmwareId, Boolean onlyBehind);
}
