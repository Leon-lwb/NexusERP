package com.nexus.iot.command.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.command.domain.IotDeviceCommand;
import com.nexus.iot.command.domain.bo.IotDeviceCommandBo;
import com.nexus.iot.command.service.IIotDeviceCommandService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * IoT下行指令 Controller（IOT-PIPE-02）。
 * <p>
 * 提供"下发属性设置 / 下发服务调用 / 重发 / 查询留痕"四类端点；
 * 设备回执经 {@code property/set_reply} → 事件流 → 消费端更新指令状态。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/command")
public class IotDeviceCommandController extends BaseController {

    private final IIotDeviceCommandService deviceCommandService;

    /**
     * 分页查询指令留痕
     */
    @SaCheckPermission("iot:command:list")
    @GetMapping("/list")
    public TableDataInfo<IotDeviceCommand> list(@RequestParam(required = false) Long deviceId,
                                                @RequestParam(required = false) String status,
                                                PageQuery pageQuery) {
        return deviceCommandService.selectPageList(deviceId, status, pageQuery);
    }

    /**
     * 查询指令详情（含回执载荷）
     */
    @SaCheckPermission("iot:command:query")
    @GetMapping("/{commandId}")
    public R<IotDeviceCommand> getInfo(@PathVariable Long commandId) {
        return R.ok(deviceCommandService.selectById(commandId));
    }

    /**
     * 下发属性设置指令（property/set）
     */
    @SaCheckPermission("iot:command:send")
    @Log(title = "IoT下行指令-属性设置", businessType = BusinessType.INSERT)
    @PostMapping("/propertySet")
    public R<IotDeviceCommand> propertySet(@Validated @RequestBody IotDeviceCommandBo bo) {
        return R.ok(deviceCommandService.sendPropertySet(bo));
    }

    /**
     * 下发服务调用指令（event/set）
     */
    @SaCheckPermission("iot:command:send")
    @Log(title = "IoT下行指令-服务调用", businessType = BusinessType.INSERT)
    @PostMapping("/serviceInvoke")
    public R<IotDeviceCommand> serviceInvoke(@Validated @RequestBody IotDeviceCommandBo bo) {
        return R.ok(deviceCommandService.sendServiceInvoke(bo));
    }

    /**
     * 重发指令（已成功的指令不可重发）
     */
    @SaCheckPermission("iot:command:resend")
    @Log(title = "IoT下行指令-重发", businessType = BusinessType.UPDATE)
    @PutMapping("/{commandId}/resend")
    public R<IotDeviceCommand> resend(@PathVariable Long commandId) {
        return R.ok(deviceCommandService.resend(commandId));
    }
}
