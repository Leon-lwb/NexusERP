package com.nexus.iot.device.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.service.IIotDeviceDataService;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.mqtt.IotDeviceStatusCache;
import com.nexus.iot.pipeline.IotDeviceLatestCache;
import com.nexus.iot.realtime.IotRealtimeSseManager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * IoT设备 Controller
 * <p>
 * 管理设备实例，含在线状态、密钥、关联产品等。新增时自动生成 device_secret。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/iot/device")
public class IotDeviceController extends BaseController {

    private final IIotDeviceService iotDeviceService;
    private final IIotDeviceDataService iotDeviceDataService;
    private final IotDeviceLatestCache latestCache;
    private final IotDeviceStatusCache statusCache;
    private final IotRealtimeSseManager realtimeSseManager;

    /**
     * 分页查询设备列表
     */
    @SaCheckPermission("iot:device:list")
    @GetMapping("/list")
    public TableDataInfo<IotDeviceVo> list(IotDeviceBo bo, PageQuery pageQuery) {
        return iotDeviceService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出设备数据到 Excel
     */
    @SaCheckPermission("iot:device:export")
    @Log(title = "IoT设备", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(IotDeviceBo bo, HttpServletResponse response) {
        List<IotDeviceVo> list = iotDeviceService.selectList(bo);
        ExcelUtil.exportExcel(list, "IoT设备", IotDeviceVo.class, response);
    }

    /**
     * 根据主键获取设备详情
     */
    @SaCheckPermission("iot:device:query")
    @GetMapping("/{id}")
    public R<IotDeviceVo> getInfo(@PathVariable Long id) {
        return R.ok(iotDeviceService.selectById(id));
    }

    /**
     * 新增设备（自动生成 device_secret，回填 product_name）
     */
    @SaCheckPermission("iot:device:add")
    @Log(title = "IoT设备", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody IotDeviceBo bo) {
        return toAjax(iotDeviceService.insertByBo(bo));
    }

    /**
     * 修改设备
     */
    @SaCheckPermission("iot:device:edit")
    @Log(title = "IoT设备", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody IotDeviceBo bo) {
        return toAjax(iotDeviceService.updateByBo(bo));
    }

    /**
     * 批量删除设备
     */
    @SaCheckPermission("iot:device:remove")
    @Log(title = "IoT设备", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(iotDeviceService.deleteByIds(ids));
    }

    /**
     * 查询设备最新属性值（从 Redis iot:latest 缓存读取）
     */
    @SaCheckPermission("iot:device:query")
    @GetMapping("/{deviceId}/latest")
    public R<Map<String, String>> latest(@PathVariable Long deviceId) {
        if (iotDeviceService.selectById(deviceId) == null) {
            return R.fail("设备不存在或无权限访问");
        }
        return R.ok(latestCache.getAll(deviceId));
    }

    /**
     * 查询设备在线状态（从 Redis iot:status 缓存读取）
     */
    @SaCheckPermission("iot:device:query")
    @GetMapping("/{deviceId}/status")
    public R<Map<String, String>> status(@PathVariable Long deviceId) {
        if (iotDeviceService.selectById(deviceId) == null) {
            return R.fail("设备不存在或无权限访问");
        }
        return R.ok(Map.of("connectionStatus", statusCache.get(deviceId)));
    }

    /**
     * 查询设备历史数据（采集时间区间，默认近 24 小时，最多 limit 条）
     */
    @SaCheckPermission("iot:device:query")
    @GetMapping("/{deviceId}/data")
    public TableDataInfo<IotDeviceData> history(@PathVariable Long deviceId,
                                                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date start,
                                                @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date end,
                                                @RequestParam(defaultValue = "1000") Integer limit) {
        if (iotDeviceService.selectById(deviceId) == null) {
            return TableDataInfo.build(List.of());
        }
        // 默认窗口：近 24 小时
        Date actualEnd = end != null ? end : new Date();
        Date actualStart = start != null ? start : new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        List<IotDeviceData> list = iotDeviceDataService.selectHistory(deviceId, actualStart, actualEnd);
        // selectHistory 已按 collect_time 倒序，内存截断 limit 条
        int max = limit == null || limit <= 0 ? 1000 : Math.min(limit, 1000);
        if (list.size() > max) {
            list = list.subList(0, max);
        }
        return TableDataInfo.build(list);
    }

    /**
     * 订阅设备实时数据 SSE 流（每 3 秒推送 iot:latest + iot:status 缓存快照）。
     * <p>
     * 浏览器 EventSource 无法设置请求头，token 走 query 参数，与全局 /resource/sse 同模式。
     */
    @SaIgnore
    @GetMapping(value = "/{deviceId}/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter realtime(@PathVariable Long deviceId, HttpServletResponse response) {
        if (!StpUtil.isLogin()) {
            return null;
        }
        StpUtil.checkPermission("iot:device:query");
        if (iotDeviceService.selectById(deviceId) == null) {
            return null;
        }
        // 关闭 nginx/代理缓冲，保证实时性
        response.setHeader("X-Accel-Buffering", "no");
        String sessionKey = LoginHelper.getUserId() + ":" + IdUtil.fastSimpleUUID();
        return realtimeSseManager.connect(deviceId, sessionKey);
    }
}
