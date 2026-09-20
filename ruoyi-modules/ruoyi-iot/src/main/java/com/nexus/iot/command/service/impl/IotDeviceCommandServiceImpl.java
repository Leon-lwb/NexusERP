package com.nexus.iot.command.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.command.domain.IotDeviceCommand;
import com.nexus.iot.command.domain.bo.IotDeviceCommandBo;
import com.nexus.iot.command.mapper.IotDeviceCommandMapper;
import com.nexus.iot.command.service.IIotDeviceCommandService;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceStatus;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.mqtt.IotMqttPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IoT下行指令Service业务层处理（IOT-PIPE-02 下行通道）。
 * <p>
 * 关键设计：
 * <ol>
 *   <li><b>先留痕后下发</b>：先 insert（PENDING）拿到雪花 ID，再用该 ID 作为指令号下发，
 *       即使发布失败也有完整记录（"可下发 + 有留痕 + 有查询"）；</li>
 *   <li><b>状态校验</b>：仅 ACTIVE 设备可下发（禁用/注销设备直接拒绝，与 MQTT 认证口径一致）；</li>
 *   <li><b>回执幂等</b>：{@code ack_msg_id}（Stream entry id）唯一 + CAS，重复回执不改写；</li>
 *   <li><b>限制如实说明</b>：若 Broker 未启用（{@code nexus.iot.mqtt.enabled=false}），
 *       发布返回 false，指令落 FAILED 并写明原因，不会伪造"已下发"。</li>
 * </ol>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceCommandServiceImpl implements IIotDeviceCommandService {

    /** 指令类型：属性设置 */
    public static final String TYPE_PROPERTY_SET = "PROPERTY_SET";
    /** 指令类型：服务调用 */
    public static final String TYPE_SERVICE_INVOKE = "SERVICE_INVOKE";

    /** 状态：已登记未下发 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已下发待回执 */
    public static final String STATUS_SENT = "SENT";
    /** 状态：设备回执成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 状态：下发或回执失败 */
    public static final String STATUS_FAILED = "FAILED";

    private final IotDeviceCommandMapper baseMapper;
    private final IIotDeviceService deviceService;
    private final IotMqttPublisher mqttPublisher;
    /**
     * 注入全局 Jackson ObjectMapper（而非静态 {@code JsonUtils}：后者静态初始化依赖 Spring 容器，
     * 单测无法打桩；注入后行为与 {@code JsonUtils} 一致，仍复用全局 long→String 序列化配置）。
     */
    private final ObjectMapper objectMapper;

    /**
     * 分页查询指令留痕。
     *
     * @param deviceId  设备ID（可空）
     * @param status    状态（可空）
     * @param pageQuery 分页参数
     * @return 指令分页结果
     */
    @Override
    public TableDataInfo<IotDeviceCommand> selectPageList(Long deviceId, String status, PageQuery pageQuery) {
        LambdaQueryWrapper<IotDeviceCommand> lqw = Wrappers.lambdaQuery();
        lqw.eq(deviceId != null, IotDeviceCommand::getDeviceId, deviceId);
        lqw.eq(StringUtils.isNotBlank(status), IotDeviceCommand::getStatus, status);
        lqw.orderByDesc(IotDeviceCommand::getCommandId);
        Page<IotDeviceCommand> page = baseMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询指令详情。
     *
     * @param commandId 指令ID
     * @return 指令实体，不存在返回 null
     */
    @Override
    public IotDeviceCommand selectById(Long commandId) {
        return commandId == null ? null : baseMapper.selectById(commandId);
    }

    /**
     * 下发属性设置指令。
     *
     * @param bo 指令业务对象
     * @return 落库后的指令
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotDeviceCommand sendPropertySet(IotDeviceCommandBo bo) {
        return send(bo, TYPE_PROPERTY_SET);
    }

    /**
     * 下发服务调用指令。
     *
     * @param bo 指令业务对象
     * @return 落库后的指令
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotDeviceCommand sendServiceInvoke(IotDeviceCommandBo bo) {
        if (StringUtils.isBlank(bo.getServiceName())) {
            throw new ServiceException("服务调用必须指定服务名(serviceName)");
        }
        return send(bo, TYPE_SERVICE_INVOKE);
    }

    /**
     * 重发指令。
     *
     * @param commandId 指令ID
     * @return 重发后的指令
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotDeviceCommand resend(Long commandId) {
        if (commandId == null) {
            throw new ServiceException("指令ID不能为空");
        }
        IotDeviceCommand cmd = baseMapper.selectById(commandId);
        if (cmd == null) {
            throw new ServiceException("指令不存在：" + commandId);
        }
        if (STATUS_SUCCESS.equals(cmd.getStatus())) {
            throw new ServiceException("指令已执行成功，无需重发");
        }
        if (StringUtils.isBlank(cmd.getTopic()) || StringUtils.isBlank(cmd.getPayload())) {
            throw new ServiceException("指令缺少 Topic 或载荷，无法重发");
        }
        Date now = new Date();
        // CAS 领取：状态未成功即允许重发，同时累加重发次数
        int rows = baseMapper.update(null, Wrappers.<IotDeviceCommand>lambdaUpdate()
            .eq(IotDeviceCommand::getCommandId, commandId)
            .ne(IotDeviceCommand::getStatus, STATUS_SUCCESS)
            .setSql("retry_count = retry_count + 1")
            .set(IotDeviceCommand::getStatus, STATUS_SENT)
            .set(IotDeviceCommand::getSentTime, now)
            .set(IotDeviceCommand::getResultMessage, "人工重发")
            .set(IotDeviceCommand::getUpdateTime, now));
        if (rows == 0) {
            throw new ServiceException("指令重发失败：状态已被其他操作变更，请刷新后重试");
        }
        boolean ok = mqttPublisher.publish(cmd.getTopic(), cmd.getPayload());
        if (!ok) {
            baseMapper.update(null, Wrappers.<IotDeviceCommand>lambdaUpdate()
                .eq(IotDeviceCommand::getCommandId, commandId)
                .set(IotDeviceCommand::getStatus, STATUS_FAILED)
                .set(IotDeviceCommand::getResultMessage, "重发失败：MQTT Broker 未启用或发布异常，指令仅登记未下发")
                .set(IotDeviceCommand::getUpdateTime, new Date()));
            log.warn("指令重发失败（Broker 未启用或发布异常）：commandId={}, topic={}", commandId, cmd.getTopic());
        }
        return baseMapper.selectById(commandId);
    }

    /**
     * 消费端入口：处理设备回执。
     *
     * @param device   设备记录
     * @param entry    回执 entry
     * @param streamId Stream entry id
     */
    @Override
    public void handleReply(IotDevice device, Map<Object, Object> entry, String streamId) {
        if (device == null || entry == null || StringUtils.isBlank(device.getTenantId())) {
            return;
        }
        TenantHelper.dynamic(device.getTenantId(), () -> doHandleReply(device, entry, streamId));
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 下发主体：校验设备 → 先登记 → 组包 → 发布 → 回写状态。
     */
    private IotDeviceCommand send(IotDeviceCommandBo bo, String type) {
        if (bo.getDeviceId() == null) {
            throw new ServiceException("设备ID不能为空");
        }
        IotDeviceVo device = deviceService.selectById(bo.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在或无权限访问：" + bo.getDeviceId());
        }
        if (!IotDeviceStatus.canConnect(device.getStatus())) {
            throw new ServiceException("设备当前状态为 " + device.getStatus() + "，已禁用/注销的设备不可下发指令");
        }
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("无法确定当前租户，指令未下发");
        }
        String topic = "iot/" + tenantId + "/" + device.getDeviceCode() + "/"
            + (TYPE_SERVICE_INVOKE.equals(type) ? "event/set" : "property/set");

        IotDeviceCommand entity = new IotDeviceCommand();
        entity.setDeviceId(device.getDeviceId());
        entity.setDeviceCode(device.getDeviceCode());
        entity.setCommandType(type);
        entity.setTopic(topic);
        entity.setStatus(STATUS_PENDING);
        entity.setRetryCount(0);
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setOperatorName(LoginHelper.getUsername());
        // 先留痕（拿到雪花指令号），再下发：即使发布失败也有完整记录
        baseMapper.insert(entity);

        String payload = buildPayload(entity, bo, type);
        boolean ok = mqttPublisher.publish(topic, payload);
        Date now = new Date();
        baseMapper.update(null, Wrappers.<IotDeviceCommand>lambdaUpdate()
            .eq(IotDeviceCommand::getCommandId, entity.getCommandId())
            .set(IotDeviceCommand::getPayload, payload)
            .set(IotDeviceCommand::getStatus, ok ? STATUS_SENT : STATUS_FAILED)
            .set(ok, IotDeviceCommand::getSentTime, now)
            .set(IotDeviceCommand::getResultMessage, ok
                ? "已通过 MQTT 下发，等待设备回执" + (StringUtils.isNotBlank(bo.getRemark()) ? "（" + bo.getRemark() + "）" : "")
                : "下发失败：MQTT Broker 未启用或发布异常，指令仅登记未下发")
            .set(IotDeviceCommand::getUpdateTime, now));
        if (ok) {
            log.info("下行指令已下发：commandId={}, type={}, device={}, topic={}",
                entity.getCommandId(), type, device.getDeviceCode(), topic);
        } else {
            log.warn("下行指令下发失败（已留痕）：commandId={}, device={}, topic={}",
                entity.getCommandId(), device.getDeviceCode(), topic);
        }
        return baseMapper.selectById(entity.getCommandId());
    }

    /**
     * 组装下发载荷：{@code {commandId, msgId, type, service, params, ts}}。
     * <p>指令号由服务端生成并回传给设备，设备回执时原样带回（{@code commandId}），用于精确匹配。
     */
    private String buildPayload(IotDeviceCommand entity, IotDeviceCommandBo bo, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", String.valueOf(entity.getCommandId()));
        payload.put("msgId", String.valueOf(entity.getCommandId()));
        payload.put("type", type);
        if (TYPE_SERVICE_INVOKE.equals(type)) {
            payload.put("service", bo.getServiceName());
        }
        payload.put("params", bo.getParams() == null ? Map.of() : bo.getParams());
        payload.put("ts", System.currentTimeMillis());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new ServiceException("指令载荷序列化失败：" + e.getMessage());
        }
    }

    /**
     * 回执处理主体：按 commandId 匹配（缺失时回落到该设备最近一条待回执指令），CAS 更新。
     */
    private void doHandleReply(IotDevice device, Map<Object, Object> entry, String streamId) {
        IotDeviceCommand cmd = matchCommand(device, entry);
        if (cmd == null) {
            log.warn("收到未匹配的指令回执（可能指令已被清理或设备号不符）：device={}, entry={}",
                device.getDeviceCode(), entry);
            return;
        }
        boolean success = !"false".equalsIgnoreCase(String.valueOf(entry.get("success")));
        String message = StringUtils.substring(str(entry.get("message")), 0, 500);
        Date now = new Date();
        // CAS + 幂等：仅当尚未回执（ack_msg_id 为空）时更新
        int rows = baseMapper.update(null, Wrappers.<IotDeviceCommand>lambdaUpdate()
            .eq(IotDeviceCommand::getCommandId, cmd.getCommandId())
            .isNull(IotDeviceCommand::getAckMsgId)
            .set(IotDeviceCommand::getStatus, success ? STATUS_SUCCESS : STATUS_FAILED)
            .set(IotDeviceCommand::getAckTime, now)
            .set(IotDeviceCommand::getAckPayload, str(entry.get("payload")))
            .set(IotDeviceCommand::getAckMsgId, streamId)
            .set(IotDeviceCommand::getResultMessage, StringUtils.isNotBlank(message)
                ? message : (success ? "设备回执成功" : "设备回执失败"))
            .set(IotDeviceCommand::getUpdateTime, now));
        if (rows == 0) {
            log.info("指令回执重复（已处理），忽略：commandId={}, streamId={}", cmd.getCommandId(), streamId);
            return;
        }
        log.info("指令回执已更新：commandId={}, device={}, status={}",
            cmd.getCommandId(), device.getDeviceCode(), success ? STATUS_SUCCESS : STATUS_FAILED);
    }

    /**
     * 匹配回执对应的指令：优先按设备回传的 commandId，其次按"该设备最近一条未回执指令"。
     */
    private IotDeviceCommand matchCommand(IotDevice device, Map<Object, Object> entry) {
        String commandIdStr = str(entry.get("command_id"));
        if (StringUtils.isNotBlank(commandIdStr)) {
            try {
                IotDeviceCommand byId = baseMapper.selectById(Long.parseLong(commandIdStr.trim()));
                if (byId != null) {
                    return byId;
                }
            } catch (NumberFormatException e) {
                log.warn("指令回执 commandId 非法：{}", commandIdStr);
            }
        }
        return baseMapper.selectOne(Wrappers.<IotDeviceCommand>lambdaQuery()
            .eq(IotDeviceCommand::getDeviceCode, device.getDeviceCode())
            .in(IotDeviceCommand::getStatus, List.of(STATUS_SENT, STATUS_PENDING))
            .isNull(IotDeviceCommand::getAckMsgId)
            .orderByDesc(IotDeviceCommand::getCommandId)
            .last("limit 1"));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
