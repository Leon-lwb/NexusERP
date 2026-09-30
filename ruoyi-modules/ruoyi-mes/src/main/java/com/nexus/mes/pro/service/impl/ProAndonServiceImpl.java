package com.nexus.mes.pro.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.iot.dto.IotAlert;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.pro.domain.ProAndon;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.bo.ProAndonBo;
import com.nexus.mes.pro.domain.vo.ProAndonVo;
import com.nexus.mes.pro.mapper.ProAndonMapper;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.pro.service.IProAndonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MES 安灯业务层服务实现
 * <p>
 * 状态机: CALLED -> RESPONDED -> RESOLVED / CANCELLED
 * 每次状态变更: ① 站内通知落库(sys_user_notification, 经 UserNotificationEvent 由 ruoyi-im 监听)
 * ② SSE(type=ANDON) 实时推送到同租户在线用户的安灯看板(MES-PRO-08: 离线不再丢事件)。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proAndonService")
public class ProAndonServiceImpl implements IProAndonService {

    private final ProAndonMapper baseMapper;
    private final ProFlowCardMapper flowCardMapper;
    private final ProWorkOrderMapper workOrderMapper;
    /** 站内通知事件发布器: 复用既有通知渠道落库(ruoyi-im 的 UserNotificationEventListener -> sys_user_notification) */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    /**
     * 查询
     * @param bo 安灯查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    public TableDataInfo<ProAndonVo> selectPageList(ProAndonBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProAndon> lqw = buildQueryWrapper(bo);
        Page<ProAndonVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    /**
     * 查询
     * @param andonId 安灯ID
     * @return 返回视图对象
     */
    public ProAndonVo selectById(Long andonId) {
        return baseMapper.selectVoById(andonId);
    }

    @Override
    /**
     * dashboardStats 操作
     * @return 返回结果映射
     */
    public Map<String, Object> dashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        /** 活跃呼叫数(CALLED + RESPONDED) */
        Long activeCount = baseMapper.selectCount(Wrappers.<ProAndon>lambdaQuery()
            .eq(ProAndon::getAndonStatus, STATUS_CALLED)
            .or()
            .eq(ProAndon::getAndonStatus, STATUS_RESPONDED));
        stats.put("activeCount", activeCount);

        /** 按状态分组计数 */
        Map<String, Long> statusCount = new HashMap<>();
        for (String status : new String[]{STATUS_CALLED, STATUS_RESPONDED, STATUS_RESOLVED, STATUS_CANCELLED}) {
            statusCount.put(status, baseMapper.selectCount(Wrappers.<ProAndon>lambdaQuery()
                .eq(ProAndon::getAndonStatus, status)));
        }
        stats.put("statusCount", statusCount);

        /** 按呼叫类型分组计数(活跃) */
        Map<String, Long> typeCount = new HashMap<>();
        for (String type : new String[]{TYPE_QUALITY, TYPE_MATERIAL, TYPE_EQUIPMENT, TYPE_OTHER}) {
            typeCount.put(type, baseMapper.selectCount(Wrappers.<ProAndon>lambdaQuery()
                .eq(ProAndon::getCallType, type)
                .ne(ProAndon::getAndonStatus, STATUS_RESOLVED)
                .ne(ProAndon::getAndonStatus, STATUS_CANCELLED)));
        }
        stats.put("typeCount", typeCount);
        return stats;
    }

    /**
     * 手动呼叫安灯(CREATED -> CALLED)
     * <p>
     * 呼叫为纯插入操作(每次生成新记录), 无需按 cardId 加锁; 且 IoT 路径 cardId 为 null,
     * 故此处不加 Lock4j。状态转换方法(respond/resolve)按 andonId 加锁防并发。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProAndonVo callAndon(Long mesWoId, Long cardId, Long stepRecordId,
                                 String callType, String callReason) {
        return callAndon(mesWoId, cardId, stepRecordId, callType, callReason, null, null);
    }

    /**
     * IoT 内部入口：验证可信身份后切换到告警租户，来源和呼叫人不受请求或登录态控制。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProAndonVo callIotAndon(IotAlert alert) {
        if (alert == null || !isValidTenantId(alert.getTenantId())) {
            throw new IllegalArgumentException("IoT告警租户无效");
        }
        if (alert.getDeviceId() == null || alert.getDeviceId() <= 0
            || StringUtils.isBlank(alert.getDeviceCode()) || alert.getDeviceCode().length() > 64
            || (alert.getMesEquipmentId() != null && alert.getMesEquipmentId() <= 0)) {
            throw new IllegalArgumentException("IoT告警设备身份无效");
        }
        String identity = "deviceCode=" + alert.getDeviceCode() + ", deviceId=" + alert.getDeviceId();
        if (alert.getMesEquipmentId() != null) {
            identity += ", mesEquipmentId=" + alert.getMesEquipmentId();
        }
        /** 真实身份置前，长告警描述仍适配既有 call_reason varchar(500)，不截掉设备关联信息。 */
        String reason = StringUtils.substring(String.format("IoT设备告警[%s] (%s): %s (值=%s, 阈值=%s)",
            alert.getAlertType(), identity, alert.getAlertMessage(), alert.getValue(), alert.getThreshold()), 0, 500);
        return TenantHelper.dynamic(alert.getTenantId(), () -> createAndon(null, null, null,
            TYPE_EQUIPMENT, reason, null, null, SOURCE_IOT, alert.getTenantId()));
    }

    /**
     * 呼叫安灯(完整参数版): 旧签名内部转调, 行为不变; 设备通道额外携带 stationCode/operatorCode。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProAndonVo callAndon(Long mesWoId, Long cardId, Long stepRecordId,
                                 String callType, String callReason, String stationCode, String operatorCode) {
        return createAndon(mesWoId, cardId, stepRecordId, callType, callReason,
            stationCode, operatorCode, SOURCE_MANUAL, currentNotificationTenant());
    }

    /** 来源只由内部入口固定，不能由 控制器/Station 请求任意指定。 */
    private ProAndonVo createAndon(Long mesWoId, Long cardId, Long stepRecordId,
                                   String callType, String callReason, String stationCode, String operatorCode,
                                   String source, String tenantId) {
        /** 1. 校验呼叫类型合法 */
        if (!isValidCallType(callType)) {
            throw new RuntimeException("非法呼叫类型: " + callType);
        }

        /** 2. 冗余关联字段(工单号/流转卡号) */
        String woNo = null;
        String cardNo = null;
        if (mesWoId != null) {
            ProWorkOrder wo = workOrderMapper.selectById(mesWoId);
            if (wo != null) {
                woNo = wo.getWoNo();
            }
        }
        if (cardId != null) {
            ProFlowCard card = flowCardMapper.selectById(cardId);
            if (card != null) {
                cardNo = card.getCardNo();
                if (woNo == null) {
                    woNo = card.getWoNo();
                }
                if (mesWoId == null) {
                    mesWoId = card.getMesWoId();
                }
            }
        }

        /** 3. 构造安灯记录 */
        ProAndon andon = new ProAndon();
        andon.setAndonNo(generateAndonNo());
        andon.setMesWoId(mesWoId);
        andon.setWoNo(woNo);
        andon.setCardId(cardId);
        andon.setCardNo(cardNo);
        andon.setStepRecordId(stepRecordId);
        andon.setStationCode(stationCode);
        andon.setCallType(callType);
        andon.setCallReason(callReason);
        andon.setAndonStatus(STATUS_CALLED);
        andon.setCallTime(new Date());
        andon.setSource(source);
        if (SOURCE_IOT.equals(source)) {
            /** IoT 无登录上下文，显式保留 DTO 的真实租户；启用租户插件时也与动态上下文一致。 */
            andon.setTenantId(tenantId);
        }
        Long callerId = null;
        if (SOURCE_MANUAL.equals(source)) {
            /** 手工/Station 保持登录身份优先、无登录时记录实际 operatorCode 的既有行为。 */
            callerId = LoginHelper.getUserId();
            if (callerId != null) {
                andon.setCallerId(callerId);
                andon.setCallerName(LoginHelper.getUsername());
            } else if (StringUtils.isNotBlank(operatorCode)) {
                andon.setCallerName(operatorCode);
            }
        }
        baseMapper.insert(andon);

        ProAndonVo vo = baseMapper.selectVoById(andon.getAndonId());
        log.info("安灯呼叫成功: andonNo={}, callType={}, callerId={}", andon.getAndonNo(), callType, callerId);

        /** 4. 通知: 站内通知落库 + SSE 实时推送 */
        notifyAndon(vo, STATUS_CALLED, tenantId);
        return vo;
    }

    /**
     * 响应安灯(CALLED -> RESPONDED)
     */
    @Lock4j(keys = {"#andonId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void respondAndon(Long andonId) {
        int rows = baseMapper.update(null, Wrappers.<ProAndon>lambdaUpdate()
            .set(ProAndon::getAndonStatus, STATUS_RESPONDED)
            .set(ProAndon::getResponderId, LoginHelper.getUserId())
            .set(ProAndon::getResponderName, LoginHelper.getUsername())
            .set(ProAndon::getRespondTime, new Date())
            .eq(ProAndon::getAndonId, andonId)
            .eq(ProAndon::getAndonStatus, STATUS_CALLED));
        if (rows == 0) {
            throw new RuntimeException("安灯状态已变更, 仅已呼叫可响应");
        }
        log.info("安灯响应成功: andonId={}", andonId);
        notifyAndon(baseMapper.selectVoById(andonId), STATUS_RESPONDED);
    }

    /**
     * 解决安灯(RESPONDED -> RESOLVED), 计算 duration_minutes
     */
    @Lock4j(keys = {"#andonId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveAndon(Long andonId, String remark) {
        ProAndon andon = baseMapper.selectById(andonId);
        if (andon == null) {
            throw new RuntimeException("安灯不存在: " + andonId);
        }
        Date resolveTime = new Date();
        Integer durationMinutes = null;
        if (andon.getCallTime() != null) {
            durationMinutes = (int) ((resolveTime.getTime() - andon.getCallTime().getTime()) / 60000L);
        }
        int rows = baseMapper.update(null, Wrappers.<ProAndon>lambdaUpdate()
            .set(ProAndon::getAndonStatus, STATUS_RESOLVED)
            .set(ProAndon::getResolveTime, resolveTime)
            .set(ProAndon::getDurationMinutes, durationMinutes)
            .set(StringUtils.isNotBlank(remark), ProAndon::getRemark, remark)
            .eq(ProAndon::getAndonId, andonId)
            .eq(ProAndon::getAndonStatus, STATUS_RESPONDED));
        if (rows == 0) {
            throw new RuntimeException("安灯状态已变更, 仅已响应可解决");
        }
        log.info("安灯解决成功: andonId={}, durationMinutes={}", andonId, durationMinutes);
        notifyAndon(baseMapper.selectVoById(andonId), STATUS_RESOLVED);
    }

    /**
     * 取消安灯(CALLED -> CANCELLED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAndon(Long andonId) {
        int rows = baseMapper.update(null, Wrappers.<ProAndon>lambdaUpdate()
            .set(ProAndon::getAndonStatus, STATUS_CANCELLED)
            .eq(ProAndon::getAndonId, andonId)
            .eq(ProAndon::getAndonStatus, STATUS_CALLED));
        if (rows == 0) {
            throw new RuntimeException("安灯状态已变更, 仅已呼叫可取消");
        }
        log.info("安灯取消成功: andonId={}", andonId);
        notifyAndon(baseMapper.selectVoById(andonId), STATUS_CANCELLED);
    }

    /** ==================== 私有辅助方法 ==================== */

    private boolean isValidCallType(String callType) {
        return TYPE_QUALITY.equals(callType) || TYPE_MATERIAL.equals(callType)
            || TYPE_EQUIPMENT.equals(callType) || TYPE_OTHER.equals(callType);
    }

    /**
     * 生成安灯编号: AND + yyyyMMdd + 时间戳后4位
     */
    private String generateAndonNo() {
        java.time.LocalDate today = java.time.LocalDate.now();
        long suffix = System.currentTimeMillis() % 10000L;
        return "AND-" + today.toString().replace("-", "") + "-" + String.format("%04d", suffix);
    }

    /**
     * 安灯状态变更统一通知(MES-PRO-08): ① 站内通知落库 ② SSE 实时推送。
     * <p>
     * 原实现仅 SSE: 无同租户在线用户时只 log.debug 后 return, 离线期间的安灯事件彻底丢失(不落库、无通知)。
     * 现统一复用既有站内通知渠道({@link UserNotificationEvent}, 由 ruoyi-im 的 UserNotificationEventListener
     * 落 sys_user_notification 并实时推送, 与工作流待办/流程编排通知同渠道), 离线用户上线后可在通知中心补看;
     * 呼叫通知以 notify_time 做 CAS 幂等(同一 andonId 的呼叫只通知一次)。
     * </p>
     */
    private void notifyAndon(ProAndonVo vo, String action) {
        notifyAndon(vo, action, currentNotificationTenant());
    }

    private String currentNotificationTenant() {
        return TenantHelper.isEnable() ? TenantHelper.getTenantId() : LoginHelper.getTenantId();
    }

    private boolean isValidTenantId(String tenantId) {
        return tenantId != null && tenantId.matches("[A-Za-z0-9_-]{1,20}");
    }

    private void notifyAndon(ProAndonVo vo, String action, String tenantId) {
        if (vo == null) {
            return;
        }
        /** 无有效租户时拒绝广播，也不消耗 notify_time CAS，避免遗漏上下文被当成全部租户。 */
        if (!isValidTenantId(tenantId)) {
            log.warn("安灯通知租户无效, 跳过通知: andonId={}, action={}", vo.getAndonId(), action);
            return;
        }
        Set<Long> onlineUsers = collectOnlineUsersByTenant(tenantId);
        if (CollUtil.isEmpty(onlineUsers)) {
            /** 离线不再"静默": 明确告警 + 仍走站内通知落库 */
            log.warn("安灯通知无同租户在线用户, SSE 不可达, 已转为站内通知落库: andonId={}, tenantId={}, action={}",
                vo.getAndonId(), tenantId, action);
        }
        /** 被通知人: 同租户在线用户; 全员离线时兜底通知呼叫人, 保证至少有一条可追溯的站内通知 */
        Set<Long> targets = new LinkedHashSet<>(onlineUsers);
        if (targets.isEmpty() && vo.getCallerId() != null) {
            targets.add(vo.getCallerId());
        }
        /** 呼叫通知幂等: 仅首次(notify_time 为空)通知; 响应/解决/取消由状态 CAS 保证仅一次 */
        if (STATUS_CALLED.equals(action) && !markCallNotified(vo, targets)) {
            log.info("安灯呼叫通知已发出过, 跳过重复通知: andonId={}", vo.getAndonId());
            return;
        }
        if (!targets.isEmpty()) {
            publishNotificationsAfterCommit(vo, action, targets, tenantId);
        }
        pushAndonSse(vo, action, onlineUsers);
    }

    /**
     * 呼叫通知落库 + 幂等(CAS: notify_time 为空才写入)
     *
     * @return true 表示本次为首次通知
     */
    private boolean markCallNotified(ProAndonVo vo, Set<Long> targets) {
        int rows = baseMapper.update(null, Wrappers.<ProAndon>lambdaUpdate()
            .set(ProAndon::getNotifyUserIds, targets.isEmpty() ? null : CollUtil.join(targets, ","))
            .set(ProAndon::getNotifyTime, new Date())
            .eq(ProAndon::getAndonId, vo.getAndonId())
            .isNull(ProAndon::getNotifyTime));
        return rows > 0;
    }

    /**
     * 事务提交后发布站内通知(回滚不产生"幽灵通知")
     */
    private void publishNotificationsAfterCommit(ProAndonVo vo, String action, Set<Long> targets, String tenantId) {
        Runnable task = () -> {
            try {
                SpringUtils.getAopProxy(this).publishAndonNotifications(vo, action, targets, tenantId);
            } catch (Exception e) {
                log.error("安灯站内通知落库失败(不影响安灯主流程): andonId={}, action={}", vo.getAndonId(), action, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 站内通知落库 + 实时推送(MES-PRO-08)
     * <p>
     * sys_user_notification 在 ry-vue(master)库, 而本类事务已绑定 mes 连接(类级 @DS("mes") 无法切换);
     * 故显式 @DS("master") + REQUIRES_NEW: 挂起 mes 业务事务后在 master 库新事务写入
     * (同 {@code MessageRecordService} 的跨库写入模式)。监听器异常仅告警, 不影响安灯状态流转。
     * </p>
     */
    @DS("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void publishAndonNotifications(ProAndonVo vo, String action, Set<Long> targets, String tenantId) {
        String title = "安灯" + actionLabel(action) + ": " + vo.getAndonNo();
        String content = buildNotifyContent(vo, action);
        Map<String, Object> ext = new HashMap<>();
        ext.put("andonId", vo.getAndonId());
        ext.put("andonNo", vo.getAndonNo());
        ext.put("action", action);
        ext.put("andonStatus", vo.getAndonStatus());
        ext.put("callType", vo.getCallType());
        ext.put("mesWoId", vo.getMesWoId());
        ext.put("woNo", vo.getWoNo());
        ext.put("cardNo", vo.getCardNo());
        ext.put("stationCode", vo.getStationCode());
        String extJson = JsonUtils.toJsonString(ext);
        for (Long userId : targets) {
            UserNotificationEvent event = new UserNotificationEvent();
            event.setTenantId(tenantId);
            event.setTargetUserId(userId);
            event.setCategory("mes_andon");
            event.setRefType("pro_andon");
            event.setRefId(String.valueOf(vo.getAndonId()));
            event.setTitle(title);
            event.setContent(content);
            event.setExtJson(extJson);
            eventPublisher.publishEvent(event);
        }
        log.info("安灯站内通知已发布: andonId={}, action={}, targets={}", vo.getAndonId(), action, targets.size());
    }

    private String actionLabel(String action) {
        if (STATUS_CALLED.equals(action)) {
            return "呼叫";
        }
        if (STATUS_RESPONDED.equals(action)) {
            return "响应";
        }
        if (STATUS_RESOLVED.equals(action)) {
            return "解决";
        }
        if (STATUS_CANCELLED.equals(action)) {
            return "取消";
        }
        return action;
    }

    private String buildNotifyContent(ProAndonVo vo, String action) {
        StringBuilder sb = new StringBuilder();
        sb.append("安灯 ").append(vo.getAndonNo()).append(" 已").append(actionLabel(action));
        if (StringUtils.isNotBlank(vo.getWoNo())) {
            sb.append(", 工单 ").append(vo.getWoNo());
        }
        if (StringUtils.isNotBlank(vo.getStationCode())) {
            sb.append(", 工位 ").append(vo.getStationCode());
        }
        sb.append(", 类型 ").append(vo.getCallType());
        if (StringUtils.isNotBlank(vo.getCallReason())) {
            sb.append(", 原因: ").append(vo.getCallReason());
        }
        if (STATUS_RESOLVED.equals(action) && vo.getDurationMinutes() != null) {
            sb.append(", 持续 ").append(vo.getDurationMinutes()).append(" 分钟");
        }
        return sb.toString();
    }

    /**
     * SSE 实时推送安灯状态变更到同租户在线用户。
     * 镜像 IotAlertNotifier.collectOnlineUsersByTenant + AiSseMessageHelper 编码方式。
     * 无在线用户时 SSE 不可达, 由 {@link #notifyAndon} 的站内通知落库兜底(不丢事件)。
     */
    private void pushAndonSse(ProAndonVo vo, String action, Set<Long> onlineUsers) {
        /** 先判空在线用户: 无人在线时无需触碰 SSE 组件(离线场景由 notifyAndon 的站内通知落库兜底) */
        if (CollUtil.isEmpty(onlineUsers) || !SseMessageUtils.isEnable()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ANDON");
        payload.put("action", action);
        payload.put("data", vo);
        SseMessageDto dto = new SseMessageDto();
        dto.setUserIds(List.copyOf(onlineUsers));
        dto.setMessage(JsonUtils.toJsonString(payload));
        SseMessageUtils.publishMessage(dto);
        log.info("安灯已推送: andonId={}, action={}, targetUsers={}", vo.getAndonId(), action, onlineUsers.size());
    }

    /**
     * 扫描 Sa-Token 在线 Token, 过滤指定租户的在线用户ID集合。
     * 与 ruoyi-iot IotAlertNotifier 同名方法逻辑一致。
     */
    private Set<Long> collectOnlineUsersByTenant(String tenantId) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (!isValidTenantId(tenantId)) {
            return userIds;
        }
        List<String> keys;
        try {
            keys = StpUtil.searchTokenValue("", 0, -1, false);
        } catch (Exception e) {
            log.warn("查询在线 Token 失败, 跳过安灯推送: {}", e.getMessage());
            return userIds;
        }
        if (CollUtil.isEmpty(keys)) {
            return userIds;
        }
        for (String key : keys) {
            int idx = key.lastIndexOf(':');
            String token = idx >= 0 ? key.substring(idx + 1) : key;
            try {
                LoginUser loginUser = LoginHelper.getLoginUser(token);
                if (loginUser == null || loginUser.getUserId() == null) {
                    continue;
                }
                if (tenantId.equals(loginUser.getTenantId())) {
                    userIds.add(loginUser.getUserId());
                }
            } catch (Exception ignore) {
                /** 单个 token 解析失败不影响整体 */
            }
        }
        return userIds;
    }

    private LambdaQueryWrapper<ProAndon> buildQueryWrapper(ProAndonBo bo) {
        return Wrappers.<ProAndon>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getAndonNo()), ProAndon::getAndonNo, bo.getAndonNo())
            .eq(StringUtils.isNotBlank(bo.getCallType()), ProAndon::getCallType, bo.getCallType())
            .eq(StringUtils.isNotBlank(bo.getAndonStatus()), ProAndon::getAndonStatus, bo.getAndonStatus())
            .eq(StringUtils.isNotBlank(bo.getSource()), ProAndon::getSource, bo.getSource())
            .eq(bo.getMesWoId() != null, ProAndon::getMesWoId, bo.getMesWoId())
            .eq(bo.getCardId() != null, ProAndon::getCardId, bo.getCardId())
            .orderByDesc(ProAndon::getCreateTime);
    }
}
