package com.nexus.erp.wm.service.mq;

import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import com.nexus.erp.wm.service.IWmSpareStockPostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MES 备件 → ERP 仓储过账消费者（MES-EQ-04）
 * <p>
 * 消费 {@code QueueConstants.MES_SPARE_ERP_QUEUE}：
 * <ul>
 *   <li>{@code SPARE_STOCK}：调用 {@link IWmSpareStockPostingService} 过账到 {@code wm_material_stock}（幂等）；</li>
 *   <li>{@code SPARE_PURCHASE}：低库存一键请购，复用 ERP 采购申请服务
 *       {@link IPuPurchaseRequisitionService#insertByBo} 落草稿单。</li>
 * </ul>
 * <p>
 * 队列用 {@code queuesToDeclare} 在本模块声明（{@code RabbitMqInitializer} 不在本次改动所有权内），
 * 因此生产端发往默认交换机（routingKey = 队列名）。
 * <p>
 * <b>租户</b>：消息携带 {@code tenantId} 时用 {@code TenantHelper.dynamic} 包裹执行，避免 {@code tenant_id} 落 0。
 * <p>
 * <b>失败可见</b>：处理异常时记录 ERROR 并累计 Redis 重试计数；未达上限抛出以触发重投，
 * 达上限则记录 ERROR 后确认消息（避免毒消息无限重投）——MES 侧过账同步台账保持 PENDING/FAILED 可人工重投。
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MesSpareErpConsumer {

    /** 消费重试上限 */
    private static final int MAX_RETRY = 3;
    /** 消费重试计数键前缀 */
    private static final String RETRY_KEY_PREFIX = "mes:spare:erp:retry:";
    /** 请购单号序列键前缀 */
    private static final String PR_NO_KEY_PREFIX = "mes:spare:pr:no:";
    /** 请购幂等键前缀 */
    private static final String PR_DEDUP_PREFIX = "mes:spare:pr:consumed:";

    private final IWmSpareStockPostingService spareStockPostingService;
    private final IPuPurchaseRequisitionService purchaseRequisitionService;

    /**
     * 监听备件过账队列
     *
     * @param payload 消息体（Map，字段见 {@link MesSpareErpMessage}）
     */
    @RabbitListener(queuesToDeclare = @Queue(name = QueueConstants.MES_SPARE_ERP_QUEUE, durable = "true"))
    public void onMessage(Map<String, Object> payload) {
        MesSpareErpMessage message = MesSpareErpMessage.fromMap(payload);
        if (message == null || StringUtils.isBlank(message.getBizKey())) {
            log.error("备件过账消息非法(缺少 bizKey), 丢弃: payload={}", payload);
            return;
        }
        try {
            String tenantId = message.getTenantId();
            if (StringUtils.isNotBlank(tenantId)) {
                TenantHelper.dynamic(tenantId, () -> handle(message));
            } else {
                log.warn("备件过账消息未携带 tenantId, 按当前上下文执行: bizKey={}", message.getBizKey());
                handle(message);
            }
        } catch (RuntimeException e) {
            long retry = incrementRetry(message.getBizKey());
            if (retry >= MAX_RETRY) {
                log.error("备件过账消费连续失败达上限({}), 已确认消息, 请通过 MES 过账台账重投: bizKey={}, error={}",
                    MAX_RETRY, message.getBizKey(), e.getMessage(), e);
                return;
            }
            log.error("备件过账消费失败, 将重投({}/{}): bizKey={}, error={}",
                retry, MAX_RETRY, message.getBizKey(), e.getMessage());
            throw e;
        }
    }

    /**
     * 按业务类型分发
     *
     * @param message 消息
     */
    private void handle(MesSpareErpMessage message) {
        if (MesSpareErpMessage.BIZ_PURCHASE.equals(message.getBizType())) {
            handlePurchaseRequest(message);
            return;
        }
        IWmSpareStockPostingService.WmSpareStockPostingResult result = spareStockPostingService.postSpareStock(message);
        clearRetry(message.getBizKey());
        log.info("备件过账消费完成: bizKey={}, duplicated={}, erpAvailable={}",
            message.getBizKey(), result.duplicated(), result.erpAvailable());
    }

    /**
     * 低库存一键请购：复用 ERP 采购申请服务生成草稿单
     *
     * @param message 消息
     */
    private void handlePurchaseRequest(MesSpareErpMessage message) {
        String dedupKey = PR_DEDUP_PREFIX + message.getBizKey();
        if (!RedisUtils.setObjectIfAbsent(dedupKey, "1", Duration.ofDays(3))) {
            log.info("备件请购消息重复, 已跳过: bizKey={}", message.getBizKey());
            return;
        }
        List<PuPurchaseRequisitionLine> lines = new ArrayList<>();
        for (Map<String, Object> line : message.getLines()) {
            Long materialId = toLong(line.get("materialId"));
            BigDecimal applyQuantity = toDecimal(line.get("applyQuantity"));
            if (materialId == null || applyQuantity == null || applyQuantity.signum() <= 0) {
                log.warn("备件请购行非法, 已跳过: bizKey={}, line={}", message.getBizKey(), line);
                continue;
            }
            PuPurchaseRequisitionLine entity = new PuPurchaseRequisitionLine();
            entity.setMaterialId(materialId);
            entity.setMaterialCode(str(line.get("materialCode")));
            entity.setMaterialName(str(line.get("materialName")));
            entity.setUnit(str(line.get("unit")));
            entity.setApplyQuantity(applyQuantity);
            entity.setShortageQuantity(applyQuantity);
            entity.setRequiredDate(new Date());
            entity.setSourceType("MES_SPARE_LOW_STOCK");
            entity.setSourceId(toLong(line.get("spareId")));
            entity.setRemark("MES备件低库存请购, 备件编码=" + str(line.get("spareCode")));
            lines.add(entity);
        }
        if (lines.isEmpty()) {
            log.warn("备件请购消息无有效行, 已跳过: bizKey={}", message.getBizKey());
            return;
        }
        PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
        bo.setRequisitionNo(nextRequisitionNo());
        bo.setRequisitionName("MES备件低库存自动请购");
        bo.setApplicantName("MES备件预警");
        bo.setRequisitionDate(new Date());
        bo.setSourceType("MES_SPARE_LOW_STOCK");
        bo.setStatus(IPuPurchaseRequisitionService.STATUS_DRAFT);
        bo.setRemark(StringUtils.isNotBlank(message.getRemark())
            ? message.getRemark() : "MES 备件低库存自动请购");
        bo.setPuPurchaseRequisitionLineList(lines);
        int rows = purchaseRequisitionService.insertByBo(bo);
        clearRetry(message.getBizKey());
        log.info("MES备件低库存请购已生成: requisitionNo={}, 行数={}, rows={}",
            bo.getRequisitionNo(), lines.size(), rows);
    }

    /**
     * 生成请购单号（MESSP + yyyyMMdd + 4位当日序号，Redis 原子自增）
     *
     * @return 请购单号
     */
    private String nextRequisitionNo() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = PR_NO_KEY_PREFIX + day;
        long seq = RedisUtils.incrAtomicValue(key);
        RedisUtils.expire(key, Duration.ofDays(2));
        return "MESSP" + day + String.format("%04d", seq % 10000);
    }

    /**
     * 递增消费重试计数
     *
     * @param bizKey 业务幂等键
     * @return 当前累计失败次数(含本次)
     */
    private long incrementRetry(String bizKey) {
        String key = RETRY_KEY_PREFIX + bizKey;
        long count = RedisUtils.incrAtomicValue(key);
        RedisUtils.expire(key, Duration.ofHours(24));
        return count;
    }

    /**
     * 清除消费重试计数
     *
     * @param bizKey 业务幂等键
     */
    private void clearRetry(String bizKey) {
        try {
            RedisUtils.deleteObject(RETRY_KEY_PREFIX + bizKey);
        } catch (Exception e) {
            log.warn("清除备件过账重试计数失败: bizKey={}", bizKey);
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
