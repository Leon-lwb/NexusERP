package com.nexus.mes.pro.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MES安灯 Service 业务层实现
 * <p>
 * 状态机: CALLED -> RESPONDED -> RESOLVED / CANCELLED
 * 每次状态变更通过 SSE(type=ANDON) 实时推送到同租户在线用户的安灯看板。
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

    @Override
    public TableDataInfo<ProAndonVo> selectPageList(ProAndonBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProAndon> lqw = buildQueryWrapper(bo);
        Page<ProAndonVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public ProAndonVo selectById(Long andonId) {
        return baseMapper.selectVoById(andonId);
    }

    @Override
    public Map<String, Object> dashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        // 活跃呼叫数(CALLED + RESPONDED)
        Long activeCount = baseMapper.selectCount(Wrappers.<ProAndon>lambdaQuery()
            .eq(ProAndon::getAndonStatus, STATUS_CALLED)
            .or()
            .eq(ProAndon::getAndonStatus, STATUS_RESPONDED));
        stats.put("activeCount", activeCount);

        // 按状态分组计数
        Map<String, Long> statusCount = new HashMap<>();
        for (String status : new String[]{STATUS_CALLED, STATUS_RESPONDED, STATUS_RESOLVED, STATUS_CANCELLED}) {
            statusCount.put(status, baseMapper.selectCount(Wrappers.<ProAndon>lambdaQuery()
                .eq(ProAndon::getAndonStatus, status)));
        }
        stats.put("statusCount", statusCount);

        // 按呼叫类型分组计数(活跃)
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
        // 1. 校验呼叫类型合法
        if (!isValidCallType(callType)) {
            throw new RuntimeException("非法呼叫类型: " + callType);
        }

        // 2. 冗余关联字段(工单号/流转卡号)
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

        // 3. 构造安灯记录
        ProAndon andon = new ProAndon();
        andon.setAndonNo(generateAndonNo());
        andon.setMesWoId(mesWoId);
        andon.setWoNo(woNo);
        andon.setCardId(cardId);
        andon.setCardNo(cardNo);
        andon.setStepRecordId(stepRecordId);
        andon.setCallType(callType);
        andon.setCallReason(callReason);
        andon.setAndonStatus(STATUS_CALLED);
        andon.setCallTime(new Date());
        andon.setSource(SOURCE_MANUAL);
        // 手动路径填写呼叫人身份(IoT 路径由 MesAndonServiceImpl 在租户上下文内调用, callerId 可能为 null)
        Long callerId = LoginHelper.getUserId();
        if (callerId != null) {
            andon.setCallerId(callerId);
            andon.setCallerName(LoginHelper.getUsername());
        }
        baseMapper.insert(andon);

        ProAndonVo vo = baseMapper.selectVoById(andon.getAndonId());
        log.info("安灯呼叫成功: andonNo={}, callType={}, callerId={}", andon.getAndonNo(), callType, callerId);

        // 4. SSE 实时推送
        publishAndonSse(vo, STATUS_CALLED);
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
        publishAndonSse(baseMapper.selectVoById(andonId), STATUS_RESPONDED);
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
        publishAndonSse(baseMapper.selectVoById(andonId), STATUS_RESOLVED);
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
        publishAndonSse(baseMapper.selectVoById(andonId), STATUS_CANCELLED);
    }

    // ==================== 私有辅助方法 ====================

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
     * SSE 实时推送安灯状态变更到同租户在线用户。
     * 镜像 IotAlertNotifier.collectOnlineUsersByTenant + AiSseMessageHelper 编码方式。
     */
    private void publishAndonSse(ProAndonVo vo, String action) {
        if (vo == null || !SseMessageUtils.isEnable()) {
            return;
        }
        Set<Long> userIds = collectOnlineUsersByTenant(LoginHelper.getTenantId());
        if (CollUtil.isEmpty(userIds)) {
            log.debug("无同租户在线用户, 安灯推送仅落日志: andonId={}", vo.getAndonId());
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ANDON");
        payload.put("action", action);
        payload.put("data", vo);
        SseMessageDto dto = new SseMessageDto();
        dto.setUserIds(List.copyOf(userIds));
        dto.setMessage(JsonUtils.toJsonString(payload));
        SseMessageUtils.publishMessage(dto);
        log.info("安灯已推送: andonId={}, action={}, targetUsers={}", vo.getAndonId(), action, userIds.size());
    }

    /**
     * 扫描 Sa-Token 在线 Token, 过滤指定租户的在线用户ID集合。
     * 与 ruoyi-iot IotAlertNotifier 同名方法逻辑一致。
     */
    private Set<Long> collectOnlineUsersByTenant(String tenantId) {
        Set<Long> userIds = new LinkedHashSet<>();
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
                if (tenantId == null || tenantId.equals(loginUser.getTenantId())) {
                    userIds.add(loginUser.getUserId());
                }
            } catch (Exception ignore) {
                // 单个 token 解析失败不影响整体
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
