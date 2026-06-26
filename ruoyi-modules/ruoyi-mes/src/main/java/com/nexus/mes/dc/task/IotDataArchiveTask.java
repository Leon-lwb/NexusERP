package com.nexus.mes.dc.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.mes.dc.domain.DcCollectionPoint;
import com.nexus.mes.dc.domain.DcDataRecord;
import com.nexus.mes.dc.mapper.DcCollectionPointMapper;
import com.nexus.mes.dc.mapper.DcDataRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * IoT 自动采集归档定时任务
 * <p>
 * 周期性扫描 dc_collection_point 中 point_type=AUTO 且关联 iot_device_id 的采集点，
 * 调 IotDeviceDataQueryService.getHistoryData 拉取 IoT 历史数据，
 * 写入 dc_data_record（source=IoT），通过唯一索引 uk_point_collect_time 去重。
 * <p>
 * 建议频率：5 分钟一次（在 SnailJob 控制台配置 cron: 0 *&#47;5 * * * ?）
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@JobExecutor(name = "iotDataArchiveTask")
public class IotDataArchiveTask {

    private static final String POINT_TYPE_AUTO = "AUTO";
    private static final String SOURCE_IOT = "IoT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 每次拉取时间窗口（分钟）：略大于任务周期以避免漏拉 */
    private static final long PULL_WINDOW_MINUTES = 6L;

    private final DcCollectionPointMapper collectionPointMapper;
    private final DcDataRecordMapper dataRecordMapper;
    private final IotDeviceDataQueryService iotDeviceDataQueryService;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("[IoT归档任务] 开始执行");
        try {
            Date endTime = new Date();
            Date startTime = new Date(System.currentTimeMillis() - Duration.ofMinutes(PULL_WINDOW_MINUTES).toMillis());
            int totalInserted = archiveAutoPoints(startTime, endTime);
            SnailJobLog.REMOTE.info("[IoT归档任务] 完成，归档 {} 条记录", totalInserted);
            return ExecuteResult.success("归档 " + totalInserted + " 条");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("[IoT归档任务] 失败: {}", e.getMessage());
            return ExecuteResult.failure("归档失败: " + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int archiveAutoPoints(Date startTime, Date endTime) {
        LambdaQueryWrapper<DcCollectionPoint> wrapper = Wrappers.<DcCollectionPoint>lambdaQuery()
            .eq(DcCollectionPoint::getPointType, POINT_TYPE_AUTO)
            .isNotNull(DcCollectionPoint::getIotDeviceId)
            .eq(DcCollectionPoint::getStatus, STATUS_ACTIVE);
        List<DcCollectionPoint> points = collectionPointMapper.selectList(wrapper);

        int inserted = 0;
        for (DcCollectionPoint point : points) {
            try {
                inserted += archiveOne(point, startTime, endTime);
            } catch (Exception e) {
                log.warn("[IoT归档任务] 采集点 {} 归档失败: {}", point.getPointId(), e.getMessage(), e);
            }
        }
        return inserted;
    }

    private int archiveOne(DcCollectionPoint point, Date startTime, Date endTime) {
        List<Map<String, Object>> history = iotDeviceDataQueryService.getHistoryData(
            point.getIotDeviceId(), startTime, endTime);
        if (history == null || history.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Map<String, Object> row : history) {
            // IoT 数据结构（IotDeviceDataQueryServiceImpl.toMap）：
            // {dataId, deviceId, deviceCode, propertyCode, dataType,
            //  numericValue, textValue, boolValue, collectTime, quality}
            Object ctObj = row.get("collectTime");
            Object pcObj = row.get("propertyCode");
            if (ctObj == null) {
                continue;
            }
            // 仅归档采集点配置的属性（若配置了 iotPropertyCode 则过滤，否则归档全部属性）
            String propertyCode = pcObj == null ? null : String.valueOf(pcObj);
            if (point.getIotPropertyCode() != null && !point.getIotPropertyCode().isEmpty()
                && !point.getIotPropertyCode().equals(propertyCode)) {
                continue;
            }

            Date collectTime = ctObj instanceof Date ? (Date) ctObj : parseDate(ctObj);
            if (collectTime == null) {
                continue;
            }

            Object valObj = extractValue(row);
            if (valObj == null) {
                continue;
            }

            DcDataRecord record = new DcDataRecord();
            record.setPointId(point.getPointId());
            record.setPointCode(point.getPointCode());
            record.setPropertyCode(propertyCode);
            record.setCollectTime(collectTime);
            record.setSource(SOURCE_IOT);
            // collectBy 字段为 Long（手动采集时记录采集人ID），IoT 自动采集留空

            String dataType = point.getDataType();
            if ("NUMERIC".equalsIgnoreCase(dataType)) {
                try {
                    record.setNumericValue(new BigDecimal(String.valueOf(valObj)));
                } catch (NumberFormatException ignore) {
                    record.setTextValue(String.valueOf(valObj));
                }
            } else {
                record.setTextValue(String.valueOf(valObj));
            }

            try {
                int rows = dataRecordMapper.insert(record);
                if (rows > 0) {
                    count++;
                }
            } catch (org.springframework.dao.DuplicateKeyException dke) {
                // 唯一索引 uk_point_collect_time 兜底去重
                log.debug("[IoT归档任务] 重复记录跳过: pointId={}, collectTime={}",
                    point.getPointId(), collectTime);
            }
        }
        return count;
    }

    /**
     * 从 IoT 历史数据 Map 中提取实际值。
     * IoT 侧 IotDeviceData 根据 dataType 决定哪个值字段有效：
     * NUMERIC → numericValue, BOOL → boolValue, 其他 → textValue。
     */
    private Object extractValue(Map<String, Object> row) {
        Object dtObj = row.get("dataType");
        String dataType = dtObj == null ? null : String.valueOf(dtObj);
        if ("NUMERIC".equalsIgnoreCase(dataType)) {
            return row.get("numericValue");
        }
        if ("BOOL".equalsIgnoreCase(dataType)) {
            return row.get("boolValue");
        }
        Object v = row.get("textValue");
        if (v == null) {
            // 兜底：尝试 numericValue
            v = row.get("numericValue");
        }
        return v;
    }

    private Date parseDate(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Date) {
            return (Date) obj;
        }
        if (obj instanceof java.time.LocalDateTime) {
            return java.sql.Timestamp.valueOf((java.time.LocalDateTime) obj);
        }
        if (obj instanceof java.time.Instant) {
            return Date.from((java.time.Instant) obj);
        }
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }
}
