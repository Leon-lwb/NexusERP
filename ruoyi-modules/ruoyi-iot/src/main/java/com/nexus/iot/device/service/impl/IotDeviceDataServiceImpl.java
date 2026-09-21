package com.nexus.iot.device.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.mapper.IotDeviceDataMapper;
import com.nexus.iot.device.service.IIotDeviceDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * IoT设备数据 Service 实现
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceDataServiceImpl implements IIotDeviceDataService {

    private final IotDeviceDataMapper baseMapper;

    /**
     * 批量写入设备数据，空集合直接返回成功（避免无意义事务）。
     *
     * @param dataList 设备数据集合
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchInsert(Collection<IotDeviceData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(dataList);
    }

    /**
     * 单条写入设备数据，作为批量失败时的毒消息隔离回退路径。
     *
     * @param data 单条设备数据
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insert(IotDeviceData data) {
        return baseMapper.insert(data) > 0;
    }

    /**
     * 查询设备历史数据，按采集时间区间过滤并倒序返回，LIMIT 下推到 DB。
     * <p>
     * IOT-DATA-01：原实现无 DB 层 LIMIT，先全量载入内存再由 Controller {@code subList} 截断，
     * 单设备 24h（1Hz 上报即 86400 行）即可造成数百 MB 堆占用。现由调用方传入上限并下推 SQL。
     *
     * @param deviceId 设备 ID
     * @param start    起始采集时间（可空）
     * @param end      截止采集时间（可空）
     * @param limit    返回条数上限（null/非正数取默认值，超过绝对上限截断）
     * @return 历史数据列表
     */
    @Override
    public List<IotDeviceData> selectHistory(Long deviceId, Date start, Date end, Integer limit) {
        int safeLimit = safeLimit(limit);
        LambdaQueryWrapper<IotDeviceData> lqw = Wrappers.<IotDeviceData>lambdaQuery()
            .eq(IotDeviceData::getDeviceId, deviceId)
            .ge(start != null, IotDeviceData::getCollectTime, start)
            .le(end != null, IotDeviceData::getCollectTime, end)
            .orderByDesc(IotDeviceData::getCollectTime)
            // LIMIT 下推到 DB（idx_device_time 支撑该查询），禁止全量载入内存后再截断
            .last("LIMIT " + safeLimit);
        return baseMapper.selectList(lqw);
    }

    /**
     * 归一化内部查询条数：null/非正数取默认值，超过绝对上限则截断。
     *
     * @param limit 请求条数
     * @return 安全的查询条数
     */
    private static int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return IIotDeviceDataService.DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, IIotDeviceDataService.ABSOLUTE_MAX_QUERY_LIMIT);
    }

    /** 单批删除上限，避免长事务锁表 */
    private static final int DELETE_BATCH_SIZE = 1000;
    /** 单次删除最大循环次数，防止积压场景下长时间占用调度线程 */
    private static final int DELETE_MAX_BATCH_LOOP = 1000;

    /**
     * 删除采集时间早于 deadline 的设备数据，由清理定时任务调用。
     * <p>参照 ImMessageClearTask 分批删除: 每批 LIMIT 1000 独立提交(无包裹事务),
     * 避免大表单条 DELETE 的长事务锁表; 循环上限兜底防止积压场景无限循环,
     * 中途失败保留已删批次, 下次调度继续。</p>
     *
     * @param deadline 截止时间（可空，空则跳过）
     * @return 删除行数
     */
    @Override
    public int deleteBefore(Date deadline) {
        if (deadline == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < DELETE_MAX_BATCH_LOOP; i++) {
            int deleted = baseMapper.delete(Wrappers.<IotDeviceData>lambdaQuery()
                .lt(IotDeviceData::getCollectTime, deadline)
                .last("LIMIT " + DELETE_BATCH_SIZE));
            total += deleted;
            if (deleted < DELETE_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

}
