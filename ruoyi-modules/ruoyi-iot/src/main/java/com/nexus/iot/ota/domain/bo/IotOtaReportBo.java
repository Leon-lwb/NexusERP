package com.nexus.iot.ota.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * IoT OTA 升级进度/结果回执业务对象。
 * <p>
 * 两种上报通道共用本对象：
 * <ol>
 *   <li><b>HTTP</b>：{@code POST /iot/ota/report}（需登录，用于设备网关/运维代报）；</li>
 *   <li><b>MQTT 旁路</b>：设备经 {@code event/post} 上报 {@code OTA_PROGRESS}/{@code OTA_RESULT} 事件，
 *       由事件流消费者旁路调用（设备身份已由 MQTT 认证保证），见
 *       {@code IIotOtaTaskService#handleOtaEvent}。</li>
 * </ol>
 * 任务匹配：优先 {@code taskId}；未带时回落"该设备最近一条进行中任务"。
 * 幂等：状态机 CAS —— 终态任务不接受任何后续回执（重复回执被拒并计入日志）。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IotOtaReportBo extends BaseEntity {

    /**
     * 设备ID（与 deviceCode 至少提供一个）
     */
    private Long deviceId;

    /**
     * 设备编码（与 deviceId 至少提供一个）
     */
    @Size(max = 64, message = "设备编码长度不能超过 64")
    private String deviceCode;

    /**
     * 升级任务ID（可空；空则按设备匹配最近一条进行中任务）
     */
    private Long taskId;

    /**
     * 回执状态: UPGRADING（进度）/ SUCCESS（成功）/ FAILED（失败）
     */
    @NotBlank(message = "回执状态不能为空")
    private String status;

    /**
     * 升级进度 0-100（可空；SUCCESS 时服务端强制 100，且进度单调不回退）
     */
    private Integer progress;

    /**
     * 设备上报的当前版本（可空，仅留档）
     */
    @Size(max = 64, message = "版本号长度不能超过 64")
    private String version;

    /**
     * 失败原因（status=FAILED 时使用）
     */
    @Size(max = 500, message = "失败原因长度不能超过 500")
    private String failReason;

    /**
     * 回执消息ID（幂等/取证用；MQTT 通道由 Stream entry id 填充）
     */
    @Size(max = 64, message = "回执消息ID长度不能超过 64")
    private String msgId;
}
