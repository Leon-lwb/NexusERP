package com.nexus.iot.event.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.IdUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.event.domain.IotDeviceEvent;
import com.nexus.iot.event.domain.bo.IotDeviceEventBo;
import com.nexus.iot.event.service.IIotDeviceEventService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IoT设备事件 Controller（IOT-PIPE-02 上行事件）。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/event")
public class IotDeviceEventController extends BaseController {

    private final IIotDeviceEventService deviceEventService;

    /**
     * 分页查询设备事件
     */
    @SaCheckPermission("iot:event:list")
    @GetMapping("/list")
    public TableDataInfo<IotDeviceEvent> list(IotDeviceEventBo bo, PageQuery pageQuery) {
        return deviceEventService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出设备事件到 Excel
     */
    @SaCheckPermission("iot:event:export")
    @Log(title = "IoT设备事件", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(IotDeviceEventBo bo, HttpServletResponse response) {
        List<IotDeviceEvent> list = deviceEventService.selectList(bo);
        ExcelUtil.exportExcel(list, "IoT设备事件", IotDeviceEvent.class, response);
    }

    /**
     * 查询事件详情
     */
    @SaCheckPermission("iot:event:query")
    @GetMapping("/{eventId}")
    public R<IotDeviceEvent> getInfo(@PathVariable Long eventId) {
        return R.ok(deviceEventService.selectById(eventId));
    }

    /**
     * 标记事件处理状态（已处理/已忽略/处理失败）
     */
    @SaCheckPermission("iot:event:process")
    @Log(title = "IoT设备事件处理", businessType = BusinessType.UPDATE)
    @PutMapping("/process")
    public R<Void> process(@Validated @RequestBody EventProcessDto dto) {
        return toAjax(deviceEventService.markProcessed(dto.getEventId(), dto.getProcessStatus(), dto.getRemark()));
    }

    /**
     * 事件处理请求体。
     */
    @Data
    public static class EventProcessDto {
        /** 事件ID */
        private Long eventId;
        /** 目标处理状态: PROCESSED/IGNORED/FAILED */
        private String processStatus;
        /** 处理说明 */
        private String remark;
    }
}
