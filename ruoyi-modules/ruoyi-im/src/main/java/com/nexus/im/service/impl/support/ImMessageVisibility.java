package com.nexus.im.service.impl.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.domain.ImMessage;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 按租户和用户隐藏消息，保留共享记录，隐藏寿命与消息保留期一致。 */
@Component
@RequiredArgsConstructor
public class ImMessageVisibility {
    private static final int RETENTION_DAYS = 180;
    private static final int MAX_HIDDEN_MESSAGES = 10_000;
    private final RedissonClient redissonClient;

    public Set<Long> hiddenIds() {
        try {
            Set<Long> hidden = hiddenSet().readAll();
            if (hidden.size() > MAX_HIDDEN_MESSAGES) {
                // 异常旧数据也不能无限传输或静默截断，避免被遗漏的隐藏消息重新出现。
                throw new ServiceException("隐藏消息数量超过上限，请稍后重试");
            }
            return hidden;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // 无法确认本人的隐藏状态时拒绝查询，避免重新暴露已隐藏内容。
            throw new ServiceException("消息隐藏记录暂时不可用，请稍后重试");
        }
    }

    public void hide(Long messageId) {
        try {
            RSetCache<Long> set = hiddenSet();
            if (set.size() >= MAX_HIDDEN_MESSAGES && !set.contains(messageId)) {
                throw new ServiceException("隐藏消息数量已达上限，请等待历史消息自动清理");
            }
            set.add(messageId, RETENTION_DAYS, TimeUnit.DAYS);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("消息隐藏失败，请稍后重试");
        }
    }

    public void excludeHidden(LambdaQueryWrapper<ImMessage> query) {
        Set<Long> hidden = hiddenIds();
        if (!hidden.isEmpty()) {
            query.notIn(ImMessage::getMessageId, hidden);
        }
    }

    private RSetCache<Long> hiddenSet() {
        Long userId = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        if (userId == null || (TenantHelper.isEnable() && (tenantId == null || tenantId.isBlank()))) {
            throw new ServiceException("无法确认当前用户和租户");
        }
        return redissonClient.getSetCache("im:hidden:" + (tenantId == null ? "default" : tenantId) + ":" + userId);
    }
}
