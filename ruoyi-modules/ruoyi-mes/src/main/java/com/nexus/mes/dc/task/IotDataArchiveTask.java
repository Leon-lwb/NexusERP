package com.nexus.mes.dc.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.baomidou.dynamic.datasource.annotation.DS;
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
 * 建议频率：5 分钟一次（在 SnailJob 控制台配置 cron: 0 * 5 * * * ?）
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DS("mes")
@JobExecutor(name = "iotDataArchiveTask")
public class IotDataArchiveTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（IOT-MES-01）。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #jobExecute}，IoT→MES 的 {@code dc_data_record} 归档链路因此完全断开
     * （MES 的 SPC/采集点/健康趋势全部拿不到 IoT 数据）。cron 与 {@code sj_job} 种子一致
     * （每 5 分钟，拉取 {@link #PULL_WINDOW_MINUTES} 分钟窗口，靠 {@code uk_point_collect_time} 去重）。
     * Lock4j 按任务名加分布式锁，多实例下仅一个节点执行，抢锁失败静默跳过。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 */5 * * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "iotDataArchiveTask", 300000L, () -> jobExecute(null));
    }

    private static final String POINT_TYPE_AUTO = "AUTO";
    private static final String SOURCE_IOT = "IoT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 每次拉取时间窗口（分钟）：略大于任务周期以避免漏拉 */
    private static final long PULL_WINDOW_MINUTES = 6L;
    /** 单次拉取上限（分钟）：超过则分块拉取，避免单次拉取过载 */
    private static final long MAX_CHUNK_MINUTES = 120L;
    /** 绝对截断上限（分钟）：超过则截断并告警（避免拉数天数据） */
    private static final long MAX_GAP_MINUTES = 1440L;

    private final DcCollectionPointMapper collectionPointMapper;
    private final DcDataRecordMapper dataRecordMapper;
    private final IotDeviceDataQueryService iotDeviceDataQueryService;

    /**
     * 定时任务执行入口，由调度框架回调。
     *
     * @param jobArgs 任务执行参数
     * @return 任务执行结果
     */
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

    /**
     * 归档采集点
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 数量/结果
     */
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

    /**
     * 归档单个采集点：游标优先 + 分块拉取 + 推进游标（F8-6 数据补偿核心）。
     * <p>
     * effectiveStart = lastArchiveTime ?: windowStart（首次运行退回滑窗，行为与改造前一致）；
     * gap &gt; {@value #MAX_CHUNK_MINUTES} 分钟分块拉取避免单次过载；
     * gap &gt; {@value #MAX_GAP_MINUTES} 分钟截断并告警（避免拉数天数据）；
     * 归档后推进 lastArchiveTime 到 endTime（空结果也推进：已扫描无数据 ≠ 应重扫）。
     * <p>
     * 游标推进置于末尾：任一分块异常则不推进，下轮从原游标重拉，由 uk_point_collect_time 去重，保证不丢数据。
     *
     * @param point      采集点
     * @param windowStart 滑窗起始（lastArchiveTime 为空时退回）
     * @param endTime    归档截止时间
     * @return 本次归档条数
     */
    int archiveOne(DcCollectionPoint point, Date windowStart, Date endTime) {
        Date cursor = point.getLastArchiveTime() != null ? point.getLastArchiveTime() : windowStart;
        long gapMin = (endTime.getTime() - cursor.getTime()) / 60000L;
        if (gapMin <= 0) {
            return 0;
        }
        if (gapMin > MAX_GAP_MINUTES) {
            log.warn("[IoT归档任务] 采集点 {} 断线 {} 分钟超 {} 分钟上限，截断回补",
                point.getPointId(), gapMin, MAX_GAP_MINUTES);
            cursor = new Date(endTime.getTime() - MAX_GAP_MINUTES * 60000L);
        }
        int total = 0;
        Date segStart = cursor;
        while (segStart.before(endTime)) {
            Date segEnd = new Date(Math.min(segStart.getTime() + MAX_CHUNK_MINUTES * 60000L, endTime.getTime()));
            total += archiveSegment(point, segStart, segEnd);
            segStart = segEnd;
        }
        // 推进游标（空结果也推进：已扫描无数据 ≠ 应重扫）。任一分块异常时不会执行到此处。
        collectionPointMapper.updateLastArchiveTime(point.getPointId(), endTime);
        return total;
    }

    /**
     * 归档单个时间段的 IoT 历史数据到 dc_data_record（source=IoT）。
     * <p>
     * 按 iotPropertyCode 过滤属性，按 dataType 区分 numericValue/textValue，
     * 唯一索引 uk_point_collect_time 兜底去重。
     *
     * @param point    采集点
     * @param segStart 段起始时间
     * @param segEnd   段截止时间
     * @return 本段归档条数
     */
    private int archiveSegment(DcCollectionPoint point, Date segStart, Date segEnd) {
        List<Map<String, Object>> history = iotDeviceDataQueryService.getHistoryData(
            point.getIotDeviceId(), segStart, segEnd);
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
