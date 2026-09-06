package com.nexus.station.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.service.StationPassService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 工位终端设备心跳接口 (nexus-station, M2 契约 C10)
 * <p>
 * URL 前缀 /mes/flash/report/station 与 FlashReportController 对齐
 * (security.excludes 已放行 /mes/flash/report/**), X-API-KEY 鉴权, 租户固定 000000。
 * 心跳 upsert mes_station_device 台账, 顺带维护 工位码↔工序码 绑定(供过站工位解析)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/flash/report/station")
public class StationHeartbeatController {

    private final StationDeviceMapper deviceMapper;
    private final StationPassService stationPassService;

    /** 设备上报密钥(与 FlashReportController/StationDeviceController 共用) */
    @Value("${mes.api-key:}")
    private String apiKey;

    /**
     * 设备心跳上报(C10): 按 device_code upsert 台账, 更新绑定工位/工序/版本/当前操作员/心跳时间。
     * <p>心跳 payload: {deviceCode, stationCode?, appVersion, stationTime, shutdown?, operatorCode?, operatorName?}。</p>
     * <p>shutdown=true 为应用正常退出的「下线心跳」: 置 shutdown_flag=1 并清空当前操作员,
     * 管理页据此即时判定离线(不等 3 分钟心跳超时); 下次启动心跳自动复位。</p>
     */
    @PostMapping("/heartbeat")
    @DS("mes")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> heartbeat(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                            @RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        checkApiKey(headerKey, request);
        String deviceCode = body.get("deviceCode") == null ? null : String.valueOf(body.get("deviceCode")).trim();
        if (StringUtils.isBlank(deviceCode)) {
            throw new ServiceException("deviceCode 不能为空");
        }
        // 工位码归一化: 剥离第 6 段设备编码(设备属性不属于工位标识), 防止 6 段码落库导致台账工位标识分裂
        String stationCode = StationPassService.normalizeStationCode(
            body.get("stationCode") == null ? null : String.valueOf(body.get("stationCode")).trim());
        String appVersion = body.get("appVersion") == null ? null : String.valueOf(body.get("appVersion")).trim();
        String operatorCode = body.get("operatorCode") == null ? null : String.valueOf(body.get("operatorCode")).trim();
        String operatorName = body.get("operatorName") == null ? null : String.valueOf(body.get("operatorName")).trim();
        boolean shutdown = Boolean.parseBoolean(String.valueOf(body.get("shutdown")));
        // 工位码 → 工序码(心跳注册绑定优先, 工位码格式解析兜底)
        String processCode = stationPassService.resolveProcessCode(stationCode);
        Date now = new Date();

        TenantHelper.dynamic("000000", () -> {
            MesStationDevice device = deviceMapper.selectOne(Wrappers.<MesStationDevice>lambdaQuery()
                .eq(MesStationDevice::getDeviceCode, deviceCode)
                .last("LIMIT 1"));
            if (device == null) {
                device = new MesStationDevice();
                device.setDeviceCode(deviceCode);
                device.setStationCode(StringUtils.blankToDefault(stationCode, null));
                device.setProcessCode(processCode);
                device.setAppVersion(appVersion);
                device.setOperatorCode(StringUtils.blankToDefault(operatorCode, null));
                device.setOperatorName(StringUtils.blankToDefault(operatorName, null));
                device.setShutdownFlag(shutdown ? "1" : "0");
                device.setLastHeartbeat(now);
                deviceMapper.insert(device);
            } else {
                deviceMapper.update(null, Wrappers.<MesStationDevice>lambdaUpdate()
                    .set(MesStationDevice::getStationCode, StringUtils.blankToDefault(stationCode, null))
                    .set(MesStationDevice::getProcessCode, processCode)
                    .set(MesStationDevice::getAppVersion, StringUtils.blankToDefault(appVersion, null))
                    // 下线心跳清空操作员; 普通心跳更新操作员(无操作员时清空, 反映下岗)
                    .set(MesStationDevice::getOperatorCode, shutdown ? null : StringUtils.blankToDefault(operatorCode, null))
                    .set(MesStationDevice::getOperatorName, shutdown ? null : StringUtils.blankToDefault(operatorName, null))
                    .set(MesStationDevice::getShutdownFlag, shutdown ? "1" : "0")
                    .set(MesStationDevice::getLastHeartbeat, now)
                    .eq(MesStationDevice::getDeviceId, device.getDeviceId()));
            }
            return null;
        });
        log.debug("工位终端心跳: device={}, station={}, version={}, operator={}, shutdown={}",
            deviceCode, stationCode, appVersion, operatorCode, shutdown);
        return R.ok(new HashMap<>());
    }

    /**
     * API 密钥校验(fail-closed): 与 StationDeviceController 一致。
     */
    private void checkApiKey(String headerKey, HttpServletRequest request) {
        if (StringUtils.isBlank(apiKey)) {
            log.error("mes.api-key 未配置（生产环境请通过环境变量 MES_API_KEY 注入），拒绝工位设备通道请求");
            throw new ServiceException("工位服务未配置访问密钥，请联系管理员");
        }
        if (StringUtils.isBlank(headerKey) || !apiKey.equals(headerKey.trim())) {
            log.warn("工位设备通道密钥校验失败: ip={}, uri={}, key={}", request.getRemoteAddr(), request.getRequestURI(), headerKey);
            throw new ServiceException("无效的API密钥");
        }
    }
}
