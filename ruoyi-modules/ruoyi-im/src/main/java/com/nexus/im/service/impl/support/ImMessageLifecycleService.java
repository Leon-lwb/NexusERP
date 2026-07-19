package com.nexus.im.service.impl.support;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.push.ImRealtimePush;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1-7: 消息生命周期子 Service
 * <p>从 ImChatServiceImpl 拆分，负责消息撤回等生命周期操作。
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class ImMessageLifecycleService {

    /** 消息撤回时间窗（毫秒），超过则不允许撤回 */
    private static final long REVOKE_WINDOW_MS = 2 * 60 * 1000L;

    private final ImMessageMapper imMessageMapper;
    private final ImGroupMapper imGroupMapper;
    private final ImGroupMemberMapper imGroupMemberMapper;
    private final ImRealtimePush imRealtimePush;

    /**
     * 撤回自己的消息（仅限发送者，2 分钟内）。
     */
    @Transactional(rollbackFor = Exception.class)
    public R<Void> revokeMessage(Long messageId) {
        Long me = LoginHelper.getUserId();
        ImMessage msg = imMessageMapper.selectById(messageId);
        if (msg == null) {
            return R.fail("消息不存在");
        }
        if (!me.equals(msg.getFromUserId())) {
            return R.fail("只能撤回自己的消息");
        }
        if (msg.getCreateTime() == null || System.currentTimeMillis() - msg.getCreateTime().getTime() > REVOKE_WINDOW_MS) {
            return R.fail("超过2分钟，无法撤回");
        }
        ImMessage patch = new ImMessage();
        patch.setMessageId(messageId);
        patch.setRevoked("1");
        imMessageMapper.updateById(patch);

        // 推送撤回事件给接收方（复用 IM_MESSAGE 信封，payload 带 action=revoke，前端按 action 处理）
        if (msg.getGroupId() == null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chatType", "PRIVATE");
            payload.put("messageId", messageId);
            payload.put("fromUserId", msg.getFromUserId());
            payload.put("toUserId", msg.getToUserId());
            payload.put("action", "revoke");
            // P0-2: 推送移到事务提交后，避免回滚时接收方收到撤回通知但实际未撤回
            imRealtimePush.publishToUserAfterCommit(msg.getToUserId(), ImEnvelopeHelper.imMessage(payload));
        } else {
            // P4-1(B2): 群撤回事件单次批量推送
            // 原实现 for 循环逐成员注册 N 个 afterCommit 钩子，1000 人群 = 1000 次注册。
            // 改为：构造一次 envelope，调用 publishToUsersAfterCommit 单次批量推送所有成员。
            ImGroup group = imGroupMapper.selectById(msg.getGroupId());
            List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
                .eq(ImGroupMember::getGroupId, msg.getGroupId()));
            if (CollUtil.isEmpty(members)) {
                return R.ok();
            }
            List<Long> targetUserIds = new ArrayList<>(members.size());
            for (ImGroupMember m : members) {
                if (!m.getUserId().equals(me)) {
                    targetUserIds.add(m.getUserId());
                }
            }
            if (!targetUserIds.isEmpty()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("chatType", "GROUP");
                payload.put("messageId", messageId);
                payload.put("groupId", msg.getGroupId());
                payload.put("fromUserId", msg.getFromUserId());
                payload.put("operatorId", me);
                payload.put("action", "revoke");
                if (group != null) {
                    payload.put("groupName", group.getGroupName());
                }
                imRealtimePush.publishToUsersAfterCommit(targetUserIds, ImEnvelopeHelper.imMessage(payload));
            }
        }
        return R.ok();
    }
}
