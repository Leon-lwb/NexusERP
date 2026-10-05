package com.nexus.mes.common.mq;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.EquipmentStatusMessage;
import com.nexus.common.mq.message.IpqcCompletedMessage;
import com.nexus.common.mq.message.IqcCompletedMessage;
import com.nexus.common.mq.message.MesWorkOrderCompletedMessage;
import com.nexus.common.mq.message.OqcCompletedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES模块MQ消息发送服务
 * <p>
 * 质检回写(IQC/OQC/IPQC)为<b>同步投递</b>: 由调用点(Qc*ServiceImpl.inspect 的 afterCommit)在
 * 事务提交线程内同步发送, 不再 {@code @Async} fire-and-forget —— 投递失败在同一线程内可见/可观测。
 * 可靠性由 {@link ReliableMessageProducer} 兜底: 发送前先落 mq_message_record(状态 SENDING),
 * 投递异常时记录保持 SENDING, 由 MessageCompensationTask / ReconciliationTask 按重试上限补偿重投,
 * 因此"检验单已 COMPLETED 但 ERP 未回写"不会再静默丢失。
 * 工单完工/设备状态回写仍为 @Async(非质检关键链路)。
 * <p>
 * 因为 mq_message_record 表位于 ry-vue(master) 库而非 ry-mes 业务库，
 * 发送前手动 push("master") 确保消息记录写入正确库。
 * <p>
 * 注意: 消息发往 TOPIC_EXCHANGE，与 {@code RabbitMqInitializer} 中
 * {@code erp.workorder.completed.#} / {@code erp.iqc.completed.#} 队列绑定一致。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MesMqSendService {

    private final ReliableMessageProducer messageProducer;

    /**
     * 异步发送MES工单完工消息到ERP
     *
     * @param mesWoId      MES工单ID
     * @param erpWoId      ERP工单ID
     * @param woNo         工单编号
     * @param completedQty 完工数量
     * @param scrappedQty  报废数量
     * @param tenantId     租户ID
     */
    @Async
    public void sendWorkOrderCompleted(Long mesWoId, Long erpWoId, String woNo,
                                       BigDecimal completedQty,
                                       BigDecimal scrappedQty,
                                       String tenantId) {
        log.info("异步发送MES工单完工消息: mesWoId={}, erpWoId={}, woNo={}", mesWoId, erpWoId, woNo);
        MesWorkOrderCompletedMessage msg = new MesWorkOrderCompletedMessage();
        msg.setMesWoId(mesWoId);
        msg.setErpWoId(erpWoId);
        msg.setWoNo(woNo);
        msg.setCompletedQty(completedQty);
        msg.setScrappedQty(scrappedQty);
        // 设置租户ID，供ERP消费端恢复租户上下文
        if (tenantId != null) {
            try {
                msg.setTenantId(Long.parseLong(tenantId));
            } catch (NumberFormatException e) {
                log.warn("无效的tenantId: {}", tenantId);
            }
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.ERP_WORKORDER_COMPLETED, msg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 异步发送IQC检验完成消息到ERP，触发ERP收货单质检状态回写
     *
     * @param iqcId         IQC检验ID
     * @param iqcCode       IQC检验编号
     * @param receiveId     ERP收货单ID
     * @param materialId    物料ID(ERP 据此按行回写 wm_arrival_notice_line)
     * @param inspectResult 检验结果(QUALIFIED/UNQUALIFIED)
     * @param quantity      检验数量
     * @param tenantId      租户ID
     */
    public void sendIqcCompleted(Long iqcId, String iqcCode, Long receiveId, Long materialId,
                                 String inspectResult, BigDecimal quantity, String tenantId) {
        log.info("异步发送IQC检验完成消息: iqcId={}, iqcCode={}, receiveId={}, materialId={}, result={}",
            iqcId, iqcCode, receiveId, materialId, inspectResult);
        IqcCompletedMessage msg = new IqcCompletedMessage();
        msg.setIqcId(iqcId);
        msg.setIqcCode(iqcCode);
        msg.setReceiveId(receiveId);
        msg.setMaterialId(materialId);
        msg.setInspectResult(inspectResult);
        msg.setQuantity(quantity);
        if (tenantId != null) {
            try {
                msg.setTenantId(Long.parseLong(tenantId));
            } catch (NumberFormatException e) {
                log.warn("无效的tenantId: {}", tenantId);
            }
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.ERP_IQC_COMPLETED, msg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 异步发送OQC检验完成消息到ERP，触发ERP销售订单行OQC状态回写
     *
     * @param oqcId         OQC检验ID
     * @param oqcCode       OQC检验编号
     * @param soId          ERP销售订单头ID
     * @param materialId    物料ID(用于定位销售订单行)
     * @param materialCode  物料编码
     * @param inspectResult 检验结果(QUALIFIED/UNQUALIFIED)
     * @param quantity      检验数量
     * @param tenantId      租户ID
     */
    public void sendOqcCompleted(Long oqcId, String oqcCode, Long soId, Long materialId,
                                 String materialCode, String inspectResult,
                                 BigDecimal quantity, String tenantId) {
        log.info("异步发送OQC检验完成消息: oqcId={}, oqcCode={}, soId={}, result={}",
            oqcId, oqcCode, soId, inspectResult);
        OqcCompletedMessage msg = new OqcCompletedMessage();
        msg.setOqcId(oqcId);
        msg.setOqcCode(oqcCode);
        msg.setSoId(soId);
        msg.setMaterialId(materialId);
        msg.setMaterialCode(materialCode);
        msg.setInspectResult(inspectResult);
        msg.setQuantity(quantity);
        if (tenantId != null) {
            try {
                msg.setTenantId(Long.parseLong(tenantId));
            } catch (NumberFormatException e) {
                log.warn("无效的tenantId: {}", tenantId);
            }
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.ERP_OQC_COMPLETED, msg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 异步发送IPQC检验完成消息到ERP，触发ERP工单聚合质检状态回写
     *
     * @param ipqcId        IPQC检验ID
     * @param ipqcNo        IPQC检验编号
     * @param mesWoId       MES工单ID
     * @param erpWoId       ERP工单ID(消费端定位 PrdWorkOrder 的关键)
     * @param woNo          工单编号
     * @param inspectResult 检验结果(QUALIFIED/UNQUALIFIED)
     * @param defectQty     不良数量
     * @param defectRate    不良率(%)
     * @param inspectorId   检验员ID
     * @param inspectorName 检验员姓名
     * @param inspectTime   原 IPQC 实际检验时间，与 MES 持久化值一致，非消息发送时间
     * @param tenantId      租户ID
     */
    public void sendIpqcCompleted(Long ipqcId, String ipqcNo, Long mesWoId, Long erpWoId, String woNo,
                                  String inspectResult, BigDecimal defectQty, BigDecimal defectRate,
                                  Long inspectorId, String inspectorName, Date inspectTime, String tenantId) {
        log.info("异步发送IPQC检验完成消息: ipqcId={}, ipqcNo={}, mesWoId={}, erpWoId={}, result={}",
            ipqcId, ipqcNo, mesWoId, erpWoId, inspectResult);
        IpqcCompletedMessage msg = new IpqcCompletedMessage();
        msg.setIpqcId(ipqcId);
        msg.setIpqcNo(ipqcNo);
        msg.setMesWoId(mesWoId);
        msg.setErpWoId(erpWoId);
        msg.setWoNo(woNo);
        msg.setInspectResult(inspectResult);
        msg.setDefectQty(defectQty);
        msg.setDefectRate(defectRate);
        msg.setInspectorId(inspectorId);
        msg.setInspectorName(inspectorName);
        msg.setInspectTime(inspectTime);
        if (tenantId != null) {
            try {
                msg.setTenantId(Long.parseLong(tenantId));
            } catch (NumberFormatException e) {
                log.warn("无效的tenantId: {}", tenantId);
            }
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.ERP_IPQC_COMPLETED, msg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 异步发送设备状态变更消息(供排产/工单模块异步感知设备故障/恢复)
     *
     * @param equipmentId   设备ID
     * @param equipmentCode 设备编码
     * @param equipmentName 设备名称
     * @param oldStatus     变更前状态
     * @param newStatus     变更后状态
     * @param reason        变更原因
     * @param tenantId      租户ID
     */
    @Async
    public void sendEquipmentStatus(Long equipmentId, String equipmentCode, String equipmentName,
                                    String oldStatus, String newStatus, String reason, String tenantId) {
        log.info("异步发送设备状态变更消息: equipmentId={}, {}->{}", equipmentId, oldStatus, newStatus);
        EquipmentStatusMessage msg = new EquipmentStatusMessage();
        msg.setEquipmentId(equipmentId);
        msg.setEquipmentCode(equipmentCode);
        msg.setEquipmentName(equipmentName);
        msg.setOldStatus(oldStatus);
        msg.setNewStatus(newStatus);
        msg.setReason(reason);
        if (tenantId != null) {
            try {
                msg.setTenantId(Long.parseLong(tenantId));
            } catch (NumberFormatException e) {
                log.warn("无效的tenantId: {}", tenantId);
            }
        }
        DynamicDataSourceContextHolder.push("master");
        try {
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.MES_EQUIPMENT_STATUS, msg);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
