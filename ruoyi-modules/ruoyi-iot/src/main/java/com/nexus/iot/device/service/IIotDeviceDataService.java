package com.nexus.iot.device.service;

import com.nexus.iot.device.domain.IotDeviceData;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * IoT设备数据 Service 接口
 *
 * @author nexus
 */
public interface IIotDeviceDataService {

    /** 历史查询默认返回条数（未指定 limit 时生效）。 */
    int DEFAULT_HISTORY_LIMIT = 1000;

    /** 对外历史查询（设备数据接口）最大返回条数。 */
    int MAX_HISTORY_LIMIT = 1000;

    /** 内部查询（MES 归档回写等）绝对上限，防止单次拉取过大导致内存膨胀。 */
    int ABSOLUTE_MAX_QUERY_LIMIT = 50000;

    /**
     * 归一化对外历史查询条数：null / 非正数取默认值，超过上限截断到上限。
     *
     * @param limit 请求条数（可空）
     * @return 安全的查询条数
     */
    static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }

    /**
     * 批量写入设备数据（落 iot_device_data，走 iot 数据源）。
     *
     * @param dataList 设备数据集合
     * @return 是否成功
     */
    boolean batchInsert(Collection<IotDeviceData> dataList);

    /**
     * 单条写入设备数据（批量失败时隔离毒消息的回退路径）。
     *
     * @param data 设备数据
     * @return 是否成功
     */
    boolean insert(IotDeviceData data);

    /**
     * 查询设备历史数据（采集时间区间），LIMIT 下推到 DB。
     * <p>
     * IOT-DATA-01：历史数据单设备单日可达数万行，禁止先全量载入内存再截断；
     * limit 由调用方给出并下推到 SQL（{@code LIMIT n}）。
     *
     * @param deviceId 设备ID
     * @param start    起始时间
     * @param end      截止时间
     * @param limit    返回条数上限（null/非正数取默认值，超过绝对上限截断）
     * @return 数据列表（按采集时间倒序）
     */
    List<IotDeviceData> selectHistory(Long deviceId, Date start, Date end, Integer limit);

    /**
     * 删除采集时间早于 deadline 的设备数据（Week 36 清理任务调用）。
     *
     * @param deadline 截止时间（早于此时间的数据被删除）
     * @return 删除行数
     */
    int deleteBefore(Date deadline);

}
