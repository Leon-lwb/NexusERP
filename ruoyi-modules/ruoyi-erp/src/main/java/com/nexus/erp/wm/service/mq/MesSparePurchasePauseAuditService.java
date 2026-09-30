package com.nexus.erp.wm.service.mq;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.domain.DeadLetterRecord;
import com.nexus.common.mq.domain.DeadLetterRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将暂停的采购消息保存在现有 master 审计待处理箱中，绝不将其视作采购草稿、
 * 收货、库存或已履约需求。通过代理执行的独立事务必须提交并返回后，
 * AUTO 确认监听器才可正常结束；系统库审计不构成 ERP 采购执行授权。
 */
@Service
@RequiredArgsConstructor
public class MesSparePurchasePauseAuditService {
    private static final int MAX_AUDIT_BODY_BYTES = 60_000;
    private final DeadLetterRecordMapper mapper;
    /** 使用应用配置的 ObjectMapper，不绕过其雪花 ID 序列化器。 */
    private final ObjectMapper objectMapper;

    @DS("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long persist(Map<String, Object> payload) {
        if (payload == null || !MesSpareErpMessage.BIZ_PURCHASE.equals(payload.get("bizType"))) {
            throw new ServiceException("不是采购暂停消息，禁止写入采购暂停审计");
        }
        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new ServiceException("采购暂停消息无法完整序列化，禁止确认消息");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_AUDIT_BODY_BYTES) {
            throw new ServiceException("采购暂停消息超出审计容量，不得截断或确认；请人工保全原消息");
        }
        DeadLetterRecord record = new DeadLetterRecord();
        record.setMessageId(DigestUtil.sha256Hex("MES_SPARE_PURCHASE_PAUSED:" + body));
        record.setExchange(""); /** EqSpareErpMqPublisher 通过默认交换机发送消息。 */
        record.setRoutingKey(QueueConstants.MES_SPARE_ERP_QUEUE);
        record.setMessageBody(body);
        record.setMessageType(LinkedHashMap.class.getName()); /** 绝不接受调用方提供的类名。 */
        record.setErrorMessage("PAUSED_SOURCE_CONTRACT: MES备件低库存采购未接入逐需求来源占用，未创建采购申请；须人工审查后处理");
        record.setRetryCount(0);
        record.setStatus("PENDING");
        record.setCreateTime(new Date());
        if (mapper.insert(record) != 1 || record.getId() == null || record.getId() <= 0) {
            throw new ServiceException("采购暂停消息审计未确认落库，禁止确认消息");
        }
        return record.getId();
    }
}