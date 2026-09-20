package com.nexus.iot.forward;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.domain.IotDeviceData;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.forward.domain.IotForwardLog;
import com.nexus.iot.forward.domain.IotForwardRule;
import com.nexus.iot.forward.mapper.IotForwardLogMapper;
import com.nexus.iot.forward.mapper.IotForwardRuleMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * IoT 数据转发调度器（§4#6 最小可用实现）。
 * <p>
 * 链路：消费端 {@code afterPersist} → {@link #dispatch}（同步，只做规则匹配 + 落留痕 + 提交异步任务）
 * → 线程池投递 → 更新留痕；失败按退避写 {@code next_retry_time}，
 * 由 {@link #retryFailed()}（{@code @Scheduled} + CAS 领取）重试，多实例下不会重复投递。
 * <p>
 * 安全：URL 在"登记留痕时"与"实际投递前"两次通过 {@link IotForwardUrlValidator} 校验（防 DNS rebinding）。
 * 租户：消费线程无登录上下文，全程 {@code TenantHelper.dynamic} 传播租户，避免跨租户读写。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotForwardDispatcher {

    /** 投递状态：待投递 */
    public static final String STATUS_PENDING = "PENDING";
    /** 投递状态：成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 投递状态：失败（可能待重试） */
    public static final String STATUS_FAILED = "FAILED";
    /** 单轮重试扫描上限 */
    private static final int RETRY_SCAN_LIMIT = 50;
    /** 设备 → 产品ID 缓存有效期（毫秒），用于产品范围规则匹配 */
    private static final long PRODUCT_CACHE_MS = 10 * 60 * 1000L;

    private final IotForwardRuleMapper ruleMapper;
    private final IotForwardLogMapper logMapper;
    private final IotForwardHttpClient httpClient;
    private final IIotDeviceService deviceService;
    /**
     * 注入全局 Jackson ObjectMapper（而非静态 {@code JsonUtils}：后者静态初始化依赖 Spring 容器，单测无法打桩）。
     */
    private final ObjectMapper objectMapper;

    private final Map<Long, Long> productCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> productCacheAt = new ConcurrentHashMap<>();

    private ExecutorService executor;

    /**
     * 初始化投递线程池（守护线程，避免拖住应用关闭）。
     */
    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "iot-forward-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        log.info("IoT 数据转发调度器已启动：线程池=2, 单轮重试上限={}", RETRY_SCAN_LIMIT);
    }

    /**
     * 关闭线程池。
     */
    @PreDestroy
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 消费端入口：把一条设备数据按命中规则登记并异步投递。
     *
     * @param data 已落库的设备数据
     * @return 登记（新建留痕）条数
     */
    public int dispatch(IotDeviceData data) {
        if (data == null || data.getDeviceId() == null || StringUtils.isBlank(data.getTenantId())) {
            return 0;
        }
        return TenantHelper.dynamic(data.getTenantId(), () -> doDispatch(data));
    }

    /**
     * 重试扫描（每分钟一轮）：CAS 领取失败留痕后重新投递，多实例安全。
     *
     * @return 本轮领取并重新投递的条数
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000, scheduler = "iotTaskScheduler")
    public int retryFailed() {
        Date now = new Date();
        List<IotForwardLog> candidates;
        try {
            candidates = logMapper.selectList(Wrappers.<IotForwardLog>lambdaQuery()
                .eq(IotForwardLog::getStatus, STATUS_FAILED)
                .isNotNull(IotForwardLog::getNextRetryTime)
                .le(IotForwardLog::getNextRetryTime, now)
                .orderByAsc(IotForwardLog::getNextRetryTime)
                .last("limit " + RETRY_SCAN_LIMIT));
        } catch (Exception e) {
            log.warn("转发重试扫描失败：{}", e.getMessage());
            return 0;
        }
        int claimed = 0;
        for (IotForwardLog row : candidates) {
            try {
                // CAS 领取：仅当仍为 FAILED 时改为 PENDING，避免多实例重复投递
                int rows = logMapper.update(null, Wrappers.<IotForwardLog>lambdaUpdate()
                    .eq(IotForwardLog::getLogId, row.getLogId())
                    .eq(IotForwardLog::getStatus, STATUS_FAILED)
                    .setSql("retry_count = retry_count + 1")
                    .set(IotForwardLog::getStatus, STATUS_PENDING)
                    .set(IotForwardLog::getLastAttemptTime, now)
                    .set(IotForwardLog::getNextRetryTime, null));
                if (rows == 0) {
                    continue;
                }
                submit(row.getLogId(), row.getTenantId());
                claimed++;
            } catch (Exception e) {
                log.warn("转发重试领取失败：logId={}, err={}", row.getLogId(), e.getMessage());
            }
        }
        if (claimed > 0) {
            log.info("IoT 数据转发重试：本轮重新投递 {} 条", claimed);
        }
        return claimed;
    }

    /**
     * 实际投递一条留痕（供单测直接调用；生产路径由线程池调用）。
     *
     * @param logId 留痕ID
     * @return 投递成功返回 true
     */
    public boolean deliver(Long logId) {
        IotForwardLog row = logMapper.selectById(logId);
        if (row == null) {
            return false;
        }
        IotForwardRule rule = ruleMapper.selectById(row.getRuleId());
        if (rule == null) {
            markFailed(row, -1, "转发规则已被删除", false);
            return false;
        }
        // 投递前二次 SSRF 校验（防 DNS rebinding）
        String url = IotForwardUrlValidator.sanitize(
            StringUtils.isNotBlank(row.getTargetUrl()) ? row.getTargetUrl() : rule.getTargetUrl());
        if (url == null) {
            markFailed(row, -1, "投递前 SSRF 校验未通过（目标可能被解析到内网/保留地址）", false);
            return false;
        }
        IotForwardHttpClient.DeliveryResult result = httpClient.post(url, rule.getHttpMethod(),
            parseHeaders(rule.getHeaders()), row.getPayload(),
            rule.getTimeoutMs() == null ? 5000 : rule.getTimeoutMs());
        Date now = new Date();
        if (result.success()) {
            logMapper.update(null, Wrappers.<IotForwardLog>lambdaUpdate()
                .eq(IotForwardLog::getLogId, row.getLogId())
                .set(IotForwardLog::getStatus, STATUS_SUCCESS)
                .set(IotForwardLog::getHttpStatus, result.httpStatus())
                .set(IotForwardLog::getErrorMsg, null)
                .set(IotForwardLog::getLastAttemptTime, now)
                .set(IotForwardLog::getNextRetryTime, null)
                .set(IotForwardLog::getUpdateTime, now));
            log.info("IoT 数据转发成功：logId={}, ruleId={}, url={}, status={}",
                row.getLogId(), row.getRuleId(), url, result.httpStatus());
            return true;
        }
        int retry = row.getRetryCount() == null ? 0 : row.getRetryCount();
        int maxRetry = rule.getMaxRetry() == null ? 3 : rule.getMaxRetry();
        markFailed(row, result.httpStatus(), result.message(), retry < maxRetry);
        return false;
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 规则匹配 + 留痕 + 提交异步投递。
     */
    private int doDispatch(IotDeviceData data) {
        List<IotForwardRule> rules = ruleMapper.selectList(Wrappers.<IotForwardRule>lambdaQuery()
            .eq(IotForwardRule::getEnabled, "1")
            .and(w -> w.eq(IotForwardRule::getDeviceId, 0L).or().eq(IotForwardRule::getDeviceId, data.getDeviceId())));
        if (rules.isEmpty()) {
            return 0;
        }
        Long productId = resolveProductId(data.getDeviceId());
        int created = 0;
        for (IotForwardRule rule : rules) {
            if (!matchProduct(rule, productId) || !matchProperty(rule.getPropertyCodes(), data.getPropertyCode())) {
                continue;
            }
            String url = IotForwardUrlValidator.sanitize(rule.getTargetUrl());
            IotForwardLog row = new IotForwardLog();
            row.setTenantId(data.getTenantId());
            row.setRuleId(rule.getRuleId());
            row.setDeviceId(data.getDeviceId());
            row.setDeviceCode(data.getDeviceCode());
            row.setPropertyCode(data.getPropertyCode());
            row.setTargetUrl(url == null ? rule.getTargetUrl() : url);
            row.setPayload(buildPayload(data));
            row.setRetryCount(0);
            row.setCreateTime(new Date());
            if (url == null) {
                // 配置阶段已校验，此处再次失败（域名被改解析）→ 直接落失败留痕，不再重试
                row.setStatus(STATUS_FAILED);
                row.setErrorMsg("目标URL未通过 SSRF 校验（仅允许 http/https 且非内网/保留地址）");
                logMapper.insert(row);
                created++;
                continue;
            }
            row.setStatus(STATUS_PENDING);
            logMapper.insert(row);
            created++;
            submit(row.getLogId(), data.getTenantId());
        }
        return created;
    }

    /**
     * 提交异步投递（工作线程无租户上下文，需重新传播）。
     */
    private void submit(Long logId, String tenantId) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.submit(() -> {
            try {
                TenantHelper.dynamic(tenantId, () -> deliver(logId));
            } catch (Exception e) {
                log.warn("IoT 数据转发投递异常：logId={}, err={}", logId, e.getMessage());
            }
        });
    }

    /**
     * 写失败留痕：未超重试上限时按退避写 next_retry_time（30s×次数，最长 5 分钟）。
     */
    private void markFailed(IotForwardLog row, int httpStatus, String message, boolean willRetry) {
        Date now = new Date();
        int retry = row.getRetryCount() == null ? 0 : row.getRetryCount();
        Date next = willRetry ? new Date(now.getTime() + Math.min(300_000L, 30_000L * (retry + 1))) : null;
        logMapper.update(null, Wrappers.<IotForwardLog>lambdaUpdate()
            .eq(IotForwardLog::getLogId, row.getLogId())
            .set(IotForwardLog::getStatus, STATUS_FAILED)
            .set(IotForwardLog::getHttpStatus, httpStatus)
            .set(IotForwardLog::getErrorMsg, StringUtils.substring(message, 0, 500))
            .set(IotForwardLog::getLastAttemptTime, now)
            .set(IotForwardLog::getNextRetryTime, next)
            .set(IotForwardLog::getUpdateTime, now));
        if (willRetry) {
            log.warn("IoT 数据转发失败，将重试：logId={}, status={}, err={}, nextRetry={}",
                row.getLogId(), httpStatus, message, next);
        } else {
            log.error("IoT 数据转发失败且不再重试：logId={}, status={}, err={}", row.getLogId(), httpStatus, message);
        }
    }

    /**
     * 产品范围匹配：{@code product_id=0} 表示不限；设备产品ID解析失败时按"不匹配"处理（保守）。
     */
    private boolean matchProduct(IotForwardRule rule, Long productId) {
        Long scope = rule.getProductId() == null ? 0L : rule.getProductId();
        if (scope == 0L) {
            return true;
        }
        return productId != null && scope.equals(productId);
    }

    /**
     * 属性白名单匹配：空 = 全部；否则逗号分隔精确匹配（大小写不敏感）。
     */
    private boolean matchProperty(String propertyCodes, String propertyCode) {
        if (StringUtils.isBlank(propertyCodes)) {
            return true;
        }
        if (StringUtils.isBlank(propertyCode)) {
            return false;
        }
        for (String code : propertyCodes.split(",")) {
            if (propertyCode.equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析设备产品ID（10 分钟内存缓存，避免每条数据都查设备表）。
     */
    private Long resolveProductId(Long deviceId) {
        Long cachedAt = productCacheAt.get(deviceId);
        long now = System.currentTimeMillis();
        if (cachedAt != null && now - cachedAt < PRODUCT_CACHE_MS && productCache.containsKey(deviceId)) {
            return productCache.get(deviceId);
        }
        try {
            IotDeviceVo device = deviceService.selectById(deviceId);
            Long productId = device == null ? null : device.getProductId();
            if (productId != null) {
                productCache.put(deviceId, productId);
                productCacheAt.put(deviceId, now);
            }
            return productId;
        } catch (Exception e) {
            log.warn("解析设备产品ID失败：deviceId={}, err={}", deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 组装投递载荷（JSON）。
     */
    private String buildPayload(IotDeviceData data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", data.getTenantId());
        payload.put("deviceId", data.getDeviceId());
        payload.put("deviceCode", data.getDeviceCode());
        payload.put("propertyCode", data.getPropertyCode());
        payload.put("dataType", data.getDataType());
        if (data.getNumericValue() != null) {
            payload.put("value", data.getNumericValue());
        } else if (data.getBoolValue() != null) {
            payload.put("value", data.getBoolValue());
        } else {
            payload.put("value", data.getTextValue());
        }
        payload.put("quality", data.getQuality());
        payload.put("source", data.getSource());
        payload.put("collectTime", data.getCollectTime() == null ? null : data.getCollectTime().getTime());
        payload.put("receiveTime", data.getReceiveTime() == null ? null : data.getReceiveTime().getTime());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("转发载荷序列化失败：device={}, property={}, err={}",
                data.getDeviceCode(), data.getPropertyCode(), e.getMessage());
            return "{}";
        }
    }

    /**
     * 解析自定义请求头 JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(String headersJson) {
        if (StringUtils.isBlank(headersJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(headersJson, new TypeReference<Map<String, Object>>() {
            });
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, String> headers = new LinkedHashMap<>();
            raw.forEach((k, v) -> headers.put(k, v == null ? null : String.valueOf(v)));
            return headers;
        } catch (Exception e) {
            log.warn("转发自定义请求头解析失败，已忽略：{}", e.getMessage());
            return Map.of();
        }
    }
}
