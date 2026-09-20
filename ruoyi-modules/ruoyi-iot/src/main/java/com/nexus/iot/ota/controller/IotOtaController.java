package com.nexus.iot.ota.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.ota.domain.IotOtaTask;
import com.nexus.iot.ota.domain.bo.IotOtaBatchTaskBo;
import com.nexus.iot.ota.domain.bo.IotOtaReportBo;
import com.nexus.iot.ota.domain.bo.IotOtaTaskBo;
import com.nexus.iot.ota.domain.vo.IotOtaBatchResultVo;
import com.nexus.iot.ota.domain.vo.IotOtaVersionCheckVo;
import com.nexus.iot.ota.service.IIotOtaTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT OTA 固件升级任务 Controller（OTA MVP）。
 * <p>
 * 端点：
 * <ul>
 *   <li>任务：列表/详情、单设备创建、按分组批量创建、人工重试、人工取消；</li>
 *   <li>回执：{@code POST /iot/ota/report} —— 设备网关/运维代报进度与结果
 *       （设备直连 MQTT 时走 {@code event/post} 的 {@code OTA_PROGRESS}/{@code OTA_RESULT}
 *       事件，由事件流旁路处理，见 {@code IotOtaTaskServiceImpl#handleOtaEvent}）；</li>
 *   <li>版本一致性：{@code GET /iot/ota/versionCheck}。</li>
 * </ul>
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/ota")
public class IotOtaController extends BaseController {

    private final IIotOtaTaskService otaTaskService;

    /**
     * 分页查询升级任务
     */
    @SaCheckPermission("iot:ota:list")
    @GetMapping("/list")
    public TableDataInfo<IotOtaTask> list(@RequestParam(required = false) Long deviceId,
                                          @RequestParam(required = false) Long firmwareId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String batchNo,
                                          PageQuery pageQuery) {
        return otaTaskService.selectPageList(deviceId, firmwareId, status, batchNo, pageQuery);
    }

    /**
     * 查询升级任务详情
     */
    @SaCheckPermission("iot:ota:query")
    @GetMapping("/{taskId}")
    public R<IotOtaTask> getInfo(@PathVariable Long taskId) {
        return R.ok(otaTaskService.selectById(taskId));
    }

    /**
     * 单设备下发升级任务
     */
    @SaCheckPermission("iot:ota:create")
    @Log(title = "IoT OTA-单设备下发", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public R<IotOtaTask> create(@Validated @RequestBody IotOtaTaskBo bo) {
        return R.ok(otaTaskService.createTask(bo));
    }

    /**
     * 按分组批量下发升级任务（部分成功语义：被禁用/重复的设备跳过并回传原因）
     */
    @SaCheckPermission("iot:ota:create")
    @Log(title = "IoT OTA-按分组批量下发", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public R<IotOtaBatchResultVo> batch(@Validated @RequestBody IotOtaBatchTaskBo bo) {
        return R.ok(otaTaskService.createBatchTask(bo));
    }

    /**
     * 人工重试（仅 FAILED/TIMEOUT）
     */
    @SaCheckPermission("iot:ota:retry")
    @Log(title = "IoT OTA-重试", businessType = BusinessType.UPDATE)
    @PutMapping("/{taskId}/retry")
    public R<IotOtaTask> retry(@PathVariable Long taskId) {
        return R.ok(otaTaskService.retry(taskId));
    }

    /**
     * 人工取消（仅 PENDING/SENT/UPGRADING）
     */
    @SaCheckPermission("iot:ota:cancel")
    @Log(title = "IoT OTA-取消", businessType = BusinessType.UPDATE)
    @PutMapping("/{taskId}/cancel")
    public R<IotOtaTask> cancel(@PathVariable Long taskId,
                               @RequestParam(required = false) String reason) {
        return R.ok(otaTaskService.cancel(taskId, reason));
    }

    /**
     * 设备上报升级进度/结果（网关/运维代报；MQTT 设备走 event/post 旁路）
     */
    @SaCheckPermission("iot:ota:report")
    @Log(title = "IoT OTA-回执上报", businessType = BusinessType.UPDATE)
    @PostMapping("/report")
    public R<IotOtaTask> report(@Validated @RequestBody IotOtaReportBo bo) {
        return R.ok(otaTaskService.report(bo));
    }

    /**
     * 版本一致性核对：设备当前版本 vs 目标版本（默认返回全部不一致设备）
     *
     * @param firmwareId 固件ID
     * @param onlyBehind 仅返回落后设备（默认 false）
     */
    @SaCheckPermission("iot:ota:versionCheck")
    @GetMapping("/versionCheck")
    public R<List<IotOtaVersionCheckVo>> versionCheck(@RequestParam Long firmwareId,
                                                      @RequestParam(required = false) Boolean onlyBehind) {
        return R.ok(otaTaskService.checkVersion(firmwareId, onlyBehind));
    }
}
