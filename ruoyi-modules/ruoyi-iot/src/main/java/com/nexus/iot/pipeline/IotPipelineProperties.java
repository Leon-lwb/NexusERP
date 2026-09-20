package com.nexus.iot.pipeline;

import com.nexus.common.iot.util.EwmaUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * IoT 数据管道配置属性（前缀 {@code nexus.iot.pipeline}）。
 *
 * @author nexus
 */
@Data
@ConfigurationProperties(prefix = "nexus.iot.pipeline")
public class IotPipelineProperties {

    /**
     * Redis Stream key
     */
    private String streamKey = "iot:data:stream";

    /**
     * 消费者组名
     */
    private String consumerGroup = "iot-consumers";

    /**
     * 消费者名（单机开发阶段固定；多实例时改 iot-consumer-{instanceId}）
     */
    private String consumerName = "iot-consumer-1";

    /**
     * 单批拉取消息数
     */
    private int batchSize = 100;

    /**
     * XREADGROUP BLOCK 超时（毫秒）
     */
    private long blockMs = 1000;

    /**
     * 毒消息最大重试次数，超过则单条 ACK 跳过并记 ERROR
     */
    private int maxRetry = 3;

    /**
     * 是否启用消费线程（false 时不启动 StreamConsumer，仅生产 XADD）
     */
    private boolean consumerEnabled = true;

    /**
     * collect_time 合理性校验：允许设备时间戳超前服务端时间的上限（毫秒），超出则改用服务端时间。
     * <p>默认 5 分钟，容忍设备与服务端的轻微时钟漂移与网络延迟。
     */
    private long collectTimeFutureToleranceMs = 5 * 60 * 1000L;

    /**
     * collect_time 合理性校验：允许设备时间戳滞后服务端时间的下限（毫秒），超出则改用服务端时间。
     * <p>默认 7 天：与 90 天保留策略配合，避免历史时间戳被清理任务立刻删除造成静默丢数。
     */
    private long collectTimePastToleranceMs = 7L * 24 * 60 * 60 * 1000;

    /**
     * YAML 兜底告警阈值：propertyCode → 阈值（numericValue 超过即告警）。
     * <p>
     * IOT-ALERT-02 起，告警规则以数据库表 {@code iot_alert_rule} 为准（支持租户隔离、产品/设备范围、
     * 比较符、持续时长、去抖与级别）；本属性**仅在库内无匹配规则时兜底**，
     * 优先级：设备规则 &gt; 产品规则 &gt; 全局规则 &gt; 本 YAML 默认值。
     * 键匹配大小写不敏感（修正原实现"首字母大写属性永不触发"的问题）。
     */
    private Map<String, java.math.BigDecimal> alertThresholds = new HashMap<>();

    /**
     * EWMA 统计异常检测配置（预测性维护-实时侧）
     */
    private Anomaly anomaly = new Anomaly();

    /**
     * EWMA 异常检测配置项(默认值统一取 {@link EwmaUtil} 共享常量,
     * MES 设备健康离线重放读取同一组键, 保证两侧口径一致)
     */
    @Data
    public static class Anomaly {

        /**
         * 是否启用异常检测
         */
        private boolean enabled = true;

        /**
         * EWMA 平滑系数 λ(0-1, 越大越敏感)
         */
        private double lambda = EwmaUtil.DEFAULT_LAMBDA;

        /**
         * 预热样本数(达到后才开始判定, 避免开局误报)
         */
        private int warmup = EwmaUtil.DEFAULT_WARMUP;

        /**
         * 告警 z-score 阈值(MEDIUM)
         */
        private double zWarn = EwmaUtil.DEFAULT_Z_WARN;

        /**
         * 高级告警 z-score 阈值(HIGH)
         */
        private double zHigh = EwmaUtil.DEFAULT_Z_HIGH;
    }

}
