package com.nexus.station.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.mapper.StationDeviceMapper;
import com.nexus.station.service.StationDeviceLinkageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工位终端设备台账管理接口 (plus-ui 管理端, 登录鉴权)
 * <p>
 * 只读运维视图: 设备在线状态(心跳超时判定)/绑定工位/终端版本;
 * M3 联动视图: {deviceId}/equipments 工位终端→工作中心设备清单,
 * by-workcenter/{workcenterId} 工作中心→工位终端清单(设备台账详情页反向展示)。
 * 台账数据由设备心跳(C10)自动 upsert, 管理端不提供增删改。
 * URL 前缀用 /mes/station-admin(不在 security.excludes 的 /mes/station/** 内,
 * 走 sa-token 登录鉴权 — 设备通道与管理通道分离)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/station-admin/device")
public class StationDeviceAdminController extends BaseController {

    /** 在线判定: 心跳在 3 拍(60s/拍)内视为在线 */
    private static final long ONLINE_THRESHOLD_MS = 3 * 60 * 1000L;

    private final StationDeviceMapper deviceMapper;
    private final StationDeviceLinkageService linkageService;

    /**
     * 设备台账列表(分页 + 在线状态计算列)
     * <p>在线判定: shutdown_flag=1(应用正常退出)即时离线; 否则心跳 3 拍(60s/拍)内为在线。</p>
     */
    @SaCheckPermission("mes:station:device:list")
    @GetMapping("/list")
    @DS("mes")
    public TableDataInfo<Map<String, Object>> list(PageQuery pageQuery) {
        List<MesStationDevice> rows = deviceMapper.selectList(Wrappers.<MesStationDevice>lambdaQuery()
            .orderByDesc(MesStationDevice::getLastHeartbeat));
        Date now = new Date();
        List<Map<String, Object>> data = rows.stream().map(d -> {
            Map<String, Object> m = new HashMap<>(12);
            m.put("deviceId", d.getDeviceId());
            m.put("deviceCode", d.getDeviceCode());
            m.put("stationCode", d.getStationCode());
            m.put("processCode", d.getProcessCode());
            m.put("appVersion", d.getAppVersion());
            m.put("operatorCode", d.getOperatorCode());
            m.put("operatorName", d.getOperatorName());
            m.put("shutdown", "1".equals(d.getShutdownFlag()));
            m.put("lastHeartbeat", d.getLastHeartbeat());
            boolean online = !"1".equals(d.getShutdownFlag())
                && d.getLastHeartbeat() != null
                && now.getTime() - d.getLastHeartbeat().getTime() < ONLINE_THRESHOLD_MS;
            m.put("online", online);
            return m;
        }).toList();
        return TableDataInfo.build(data);
    }

    /**
     * 台账汇总(顶卡): 总数/在线数/离线数(离线含正常退出与心跳超时)
     */
    @SaCheckPermission("mes:station:device:list")
    @GetMapping("/stats")
    @DS("mes")
    public R<Map<String, Object>> stats() {
        List<MesStationDevice> rows = deviceMapper.selectList(null);
        Date now = new Date();
        long online = rows.stream().filter(d -> !"1".equals(d.getShutdownFlag()) && d.getLastHeartbeat() != null
            && now.getTime() - d.getLastHeartbeat().getTime() < ONLINE_THRESHOLD_MS).count();
        Map<String, Object> data = new HashMap<>(4);
        data.put("total", rows.size());
        data.put("onlineCount", online);
        data.put("offlineCount", rows.size() - online);
        return R.ok(data);
    }

    /**
     * 工位终端 → 工作中心设备清单(M3 联动视图·正向)
     * <p>关联链: 台账工序编码 → bd_process 默认工作中心 → eq_equipment 反查(纯展示, 不改关联数据)。</p>
     */
    @SaCheckPermission("mes:station:device:list")
    @GetMapping("/{deviceId}/equipments")
    @DS("mes")
    public R<Map<String, Object>> equipments(@PathVariable Long deviceId) {
        return R.ok(linkageService.equipmentsOfDevice(deviceId));
    }

    /**
     * 工作中心 → 工位终端清单(M3 联动视图·反向, 设备台账详情页展示"由哪台工位机操控 + 在线状态")
     */
    @SaCheckPermission("mes:station:device:list")
    @GetMapping("/by-workcenter/{workcenterId}")
    @DS("mes")
    public R<Map<String, Object>> byWorkcenter(@PathVariable Long workcenterId) {
        return R.ok(linkageService.devicesOfWorkcenter(workcenterId));
    }
}
