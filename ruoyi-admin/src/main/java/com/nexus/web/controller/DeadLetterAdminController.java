package com.nexus.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mq.domain.BaseMessage;
import com.nexus.common.mq.domain.DeadLetterRecord;
import com.nexus.common.mq.domain.DeadLetterRecordMapper;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * MQ 死信消息管理接口(死信运维闭环: 查询/详情/人工重投/删除)
 * <p>死信由 DeadLetterMessageConsumer 落库(PENDING), 本控制器提供人工处理能力:
 * 重投按记录的 message_type(取自 __TypeId__ 头) 类型化反序列化后经
 * ReliableMessageProducer 完整可靠链路重新发送, 成功后记录置 RESENT。</p>
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/monitor/dead-letter")
public class DeadLetterAdminController extends BaseController {

    private final DeadLetterRecordMapper recordMapper;
    private final ReliableMessageProducer messageProducer;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询死信记录
     *
     * @param messageId  消息ID(模糊)
     * @param routingKey 路由键(模糊)
     * @param status     状态 PENDING/RESENT
     * @return 死信记录分页
     */
    @SaCheckPermission("monitor:dead-letter:list")
    @GetMapping("/list")
    public TableDataInfo<DeadLetterRecord> list(String messageId, String routingKey, String status, PageQuery pageQuery) {
        LambdaQueryWrapper<DeadLetterRecord> lqw = Wrappers.<DeadLetterRecord>lambdaQuery()
            .like(StringUtils.isNotBlank(messageId), DeadLetterRecord::getMessageId, messageId)
            .like(StringUtils.isNotBlank(routingKey), DeadLetterRecord::getRoutingKey, routingKey)
            .eq(StringUtils.isNotBlank(status), DeadLetterRecord::getStatus, status)
            .orderByDesc(DeadLetterRecord::getCreateTime);
        Page<DeadLetterRecord> page = recordMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 死信记录详情(含消息体 JSON)
     *
     * @param id 死信记录ID
     */
    @SaCheckPermission("monitor:dead-letter:query")
    @GetMapping("/{id}")
    public R<DeadLetterRecord> getInfo(@PathVariable Long id) {
        return R.ok(recordMapper.selectById(id));
    }

    /**
     * 人工重投死信消息(类型化反序列化 -> 完整可靠发送链路)
     *
     * @param id 死信记录ID
     */
    @SaCheckPermission("monitor:dead-letter:redeliver")
    @Log(title = "死信消息重投", businessType = BusinessType.UPDATE)
    @PostMapping("/redeliver/{id}")
    public R<Void> redeliver(@PathVariable Long id) {
        DeadLetterRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new ServiceException("死信记录不存在: " + id);
        }
        if (!"PENDING".equals(record.getStatus())) {
            throw new ServiceException("仅待处理状态的死信可重投");
        }
        if (StringUtils.isBlank(record.getMessageType())) {
            throw new ServiceException("死信消息缺少类型信息(历史数据), 无法重投");
        }
        BaseMessage message = deserialize(record);
        // 走完整可靠发送链路: 记录 SENDING -> 发送 -> SENT; 消费端自身有 messageId 幂等兜底
        messageProducer.send(record.getExchange(), record.getRoutingKey(), message);

        DeadLetterRecord update = new DeadLetterRecord();
        update.setId(id);
        update.setStatus("RESENT");
        update.setRetryCount((record.getRetryCount() == null ? 0 : record.getRetryCount()) + 1);
        recordMapper.updateById(update);
        log.info("死信消息人工重投成功: id={}, messageId={}, exchange={}, routingKey={}",
            id, record.getMessageId(), record.getExchange(), record.getRoutingKey());
        return R.ok();
    }

    /**
     * 删除死信记录
     *
     * @param ids 死信记录ID数组
     */
    @SaCheckPermission("monitor:dead-letter:remove")
    @Log(title = "死信消息删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        recordMapper.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }

    /** 按记录的消息类型全限定名反序列化消息体 */
    private BaseMessage deserialize(DeadLetterRecord record) {
        Class<?> clazz;
        try {
            clazz = Class.forName(record.getMessageType());
        } catch (ClassNotFoundException e) {
            throw new ServiceException("消息类型不存在: " + record.getMessageType());
        }
        if (!BaseMessage.class.isAssignableFrom(clazz)) {
            throw new ServiceException("消息类型不是 BaseMessage 子类, 无法重投: " + record.getMessageType());
        }
        try {
            return (BaseMessage) objectMapper.readValue(record.getMessageBody(), clazz);
        } catch (Exception e) {
            throw new ServiceException("消息体反序列化失败: " + e.getMessage());
        }
    }
}
