package com.nexus.common.iot.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * IoT 设备数据查询服务接口（MES 调用 IoT）。
 * <p>
 * 跨模块契约：MES 数据采集模块通过此接口从 IoT 侧读取设备最新数据 / 在线状态 / 历史数据，
 * 对齐 IoT 文档 17.1 的三个查询端点。实现方为 {@code ruoyi-iot}。
 * <p>
 * 返回类型刻意使用 {@code Map} / 基础类型，避免本公共模块依赖 {@code ruoyi-iot} 的实体类。
 *
 * @author nexus
 */
public interface IotDeviceDataQueryService {

    /**
     * 查询设备最新属性值（从 Redis 缓存读取）。
     *
     * @param deviceId 设备ID
     * @return propertyCode → value；无缓存返回空 Map
     */
    Map<String, String> getLatestData(Long deviceId);

    /**
     * 查询设备在线状态（从 Redis 缓存读取）。
     *
     * @param deviceId 设备ID
     * @return ONLINE / OFFLINE / UNKNOWN
     */
    String getOnlineStatus(Long deviceId);

    /**
     * 查询设备历史数据（从 MySQL 读取）。
     *
     * @param deviceId 设备ID
     * @param start    起始时间（采集时间）
     * @param end      截止时间（采集时间）
     * @return 历史数据列表，每条为字段→值映射
     */
    List<Map<String, Object>> getHistoryData(Long deviceId, Date start, Date end);

}
