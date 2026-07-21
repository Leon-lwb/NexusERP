package com.nexus.im.service.impl.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * P1-7: IM 聊天共享辅助组件
 * <p>从 ImChatServiceImpl 抽出的跨子 Service 共享方法：
 * <ul>
 *   <li>头像 URL 解析</li>
 *   <li>租户用户校验</li>
 *   <li>群成员判定</li>
 *   <li>群成员头像加载</li>
 *   <li>群名规整 / 用户 ID 列表规整</li>
 *   <li>client_msg_id 幂等查询</li>
 *   <li>历史消息行构建</li>
 *   <li>未读数统计</li>
 * </ul>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
public class ImChatSupport {

    /** 群名称最大长度上限。 */
    private static final int MAX_GROUP_NAME_LENGTH = 50;

    /** 消息表 Mapper。 */
    private final ImMessageMapper imMessageMapper;
    /** 群成员表 Mapper。 */
    private final ImGroupMemberMapper imGroupMemberMapper;
    /** 系统用户服务（按 ID 查用户）。 */
    private final ISysUserService sysUserService;
    /** 系统 OSS 服务（按 ID 批量查文件 URL）。 */
    private final ISysOssService sysOssService;
    /**
     * P2-1/P2-2: 直接注入 SysUserMapper 用于批量查询用户 VO（含 avatar 字段）。
     * <p>不走 ISysUserService.selectUserByIds（其 SQL 仅 select userId/userName/nickName，无 avatar）；
     * 也不走 @DataPermission 标注的方法，避免批量查询被数据权限裁剪。
     */
    private final SysUserMapper sysUserMapper;

    /**
     * 将用户头像 OSS ID 解析为可直接访问的 URL（与消息历史中的 senderAvatar 保持一致）。
     */
    public String resolveAvatarUrl(Long avatarId) {
        if (avatarId == null) {
            return "";
        }
        try {
            List<SysOssVo> list = sysOssService.listByIds(List.of(avatarId));
            return list.isEmpty() || list.get(0) == null ? "" : StrUtil.blankToDefault(list.get(0).getUrl(), "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * P2-1/P2-2: 批量加载用户信息（避免 N+1）。
     * <p>使用 SysUserMapper.selectVoByIds 获取完整 VO（含 avatar），不走 @DataPermission。
     * 多租户拦截器会自动注入 tenant_id 条件。
     *
     * @param userIds 用户ID集合（可为空）
     * @return userId → SysUserVo 映射；入参为空时返回空 Map
     */
    public Map<Long, SysUserVo> batchLoadUsers(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        Set<Long> validIds = userIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUserVo> list = sysUserMapper.selectVoByIds(validIds);
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream()
            .filter(u -> u != null && u.getUserId() != null)
            .collect(Collectors.toMap(SysUserVo::getUserId, u -> u, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * P2-1/P2-2: 批量解析头像 OSS URL（避免 N+1）。
     * <p>一次调用 ISysOssService.listByIds 拿到所有 OSS 记录，构建 ossId → url 映射。
     *
     * @param ossIds OSS ID集合（可为空，可含 null）
     * @return ossId → url 映射；入参为空时返回空 Map
     */
    public Map<Long, String> batchResolveAvatarUrls(Collection<Long> ossIds) {
        if (CollUtil.isEmpty(ossIds)) {
            return Collections.emptyMap();
        }
        Set<Long> validIds = ossIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysOssVo> list = sysOssService.listByIds(validIds);
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream()
            .filter(o -> o != null && o.getOssId() != null)
            .collect(Collectors.toMap(SysOssVo::getOssId, o -> StrUtil.blankToDefault(o.getUrl(), ""), (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 校验目标用户存在且与当前登录用户同租户；返回该用户信息。
     */
    public SysUserVo requireSameTenantUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException("用户ID无效");
        }
        SysUserVo u = sysUserService.selectUserById(userId);
        if (u == null) {
            throw new ServiceException("用户不存在");
        }
        if (TenantHelper.isEnable() && !StringUtils.equals(TenantHelper.getTenantId(), u.getTenantId())) {
            throw new ServiceException("不可跨租户通讯");
        }
        return u;
    }

    /**
     * 判断用户是否为指定群成员。
     */
    public boolean isGroupMember(Long groupId, Long userId) {
        return imGroupMemberMapper.selectCount(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .eq(ImGroupMember::getUserId, userId)) > 0;
    }

    /**
     * 加载群前 4 名成员头像（用于会话列表合成头像）。
     */
    public List<String> loadGroupMemberAvatars(Long groupId) {
        List<ImGroupMember> members = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getGroupId, groupId)
            .orderByAsc(ImGroupMember::getJoinTime)
            .last("limit 4"));
        List<String> avatars = new ArrayList<>();
        for (ImGroupMember member : members) {
            SysUserVo user = sysUserService.selectUserById(member.getUserId());
            String avatar = resolveAvatarUrl(user != null ? user.getAvatar() : null);
            if (StrUtil.isNotBlank(avatar)) {
                avatars.add(avatar);
            }
        }
        return avatars;
    }

    /**
     * 规整群名：去空白、长度校验。
     */
    public String normalizeGroupName(String groupName) {
        String value = StrUtil.trim(groupName);
        if (StrUtil.isBlank(value)) {
            throw new ServiceException("群名称不能为空");
        }
        if (value.length() > MAX_GROUP_NAME_LENGTH) {
            throw new ServiceException("群名称不能超过" + MAX_GROUP_NAME_LENGTH + "个字符");
        }
        return value;
    }

    /**
     * 规整用户 ID 列表：去 null/非正数，保持插入顺序去重。
     */
    public LinkedHashSet<Long> normalizeUserIds(List<Long> userIds) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long uid : userIds) {
                if (uid != null && uid > 0) {
                    ids.add(uid);
                }
            }
        }
        return ids;
    }

    /**
     * P1-4: 按 client_msg_id 查询已存在的消息ID，用于重发幂等。
     * <p>仅当 clientMsgId 非空时查询；额外带上 fromUserId/groupId 校验，
     * 防止恶意用户用同一 clientMsgId 在不同会话占用唯一索引。
     * <p>注意：tenant_id 由多租户拦截器自动注入。
     *
     * @param clientMsgId 客户端消息ID（可为空）
     * @param fromUserId  发送者ID
     * @param toUserId    私聊接收者ID（群聊传 null）
     * @param groupId     群ID（私聊传 null）
     * @return 命中则返回旧 messageId，否则返回 null
     */
    public Long findExistingByClientMsgId(String clientMsgId, Long fromUserId, Long toUserId, Long groupId) {
        String normalized = StrUtil.trimToNull(clientMsgId);
        if (normalized == null) {
            return null;
        }
        LambdaQueryWrapper<ImMessage> w = new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getClientMsgId, normalized)
            .eq(ImMessage::getFromUserId, fromUserId);
        if (groupId != null) {
            w.eq(ImMessage::getGroupId, groupId);
        } else {
            w.eq(ImMessage::getToUserId, toUserId).isNull(ImMessage::getGroupId);
        }
        w.select(ImMessage::getMessageId).last("limit 1");
        ImMessage existing = imMessageMapper.selectOne(w);
        return existing != null ? existing.getMessageId() : null;
    }

    /**
     * 统计私聊未读数。
     */
    public long countUnreadPrivate(Long me, Long peer) {
        return imMessageMapper.selectCount(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getToUserId, me)
            .eq(ImMessage::getFromUserId, peer)
            .eq(ImMessage::getIsRead, "0")
            .isNull(ImMessage::getGroupId));
    }

    /**
     * 统计群聊未读数。
     */
    public long countUnreadGroup(Long me, Long groupId) {
        return imMessageMapper.selectCount(new LambdaQueryWrapper<ImMessage>()
            .eq(ImMessage::getGroupId, groupId)
            .ne(ImMessage::getFromUserId, me)
            .eq(ImMessage::getIsRead, "0"));
    }

    /**
     * 构建带发送者信息的消息分页结果。
     * <p>P2-2: 批量化改造，先一次性批量加载所有发送者用户与 OSS 头像 URL，再循环组装行。
     * 50 条历史消息 SQL 数从 100+ 降到 3。
     */
    public TableDataInfo<Map<String, Object>> buildSenderMessagePage(Page<ImMessage> page) {
        List<ImMessage> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return new TableDataInfo<>(Collections.emptyList(), page.getTotal());
        }

        // ── 第 1 步：收集所有 fromUserId，批量查用户（1 SQL） ──
        Set<Long> senderIds = records.stream()
            .map(ImMessage::getFromUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SysUserVo> userMap = batchLoadUsers(senderIds);

        // ── 第 2 步：收集所有 avatarId，批量查 OSS URL（1 SQL） ──
        Set<Long> avatarIds = userMap.values().stream()
            .map(SysUserVo::getAvatar)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> avatarUrlMap = batchResolveAvatarUrls(avatarIds);

        // ── 第 3 步：组装行（无 SQL） ──
        List<Map<String, Object>> rows = records.stream()
            .map(m -> buildSenderMessageRow(m, userMap, avatarUrlMap))
            .toList();
        return new TableDataInfo<>(rows, page.getTotal());
    }

    /**
     * 构建单条带发送者信息的消息行。
     * <p>P1-5: 携带 mentionUserIds（解析为 List<Long>）便于前端判断 @ 提及。
     * <p>注意：此方法内部仍走 N+1 查询（sysUserService.selectUserById + resolveAvatarUrl），
     * 仅用于非批量路径（如单条消息回执）。批量场景请使用 {@link #buildSenderMessageRow(ImMessage, Map, Map)}。
     */
    public Map<String, Object> buildSenderMessageRow(ImMessage msg) {
        Map<String, Object> row = buildSenderMessageRowBase(msg);
        SysUserVo sender = msg.getFromUserId() == null ? null : sysUserService.selectUserById(msg.getFromUserId());
        if (sender != null) {
            row.put("senderId", sender.getUserId());
            row.put("senderName", StrUtil.blankToDefault(sender.getNickName(), sender.getUserName()));
            row.put("senderAvatar", resolveAvatarUrl(sender.getAvatar()));
        }
        return row;
    }

    /**
     * P2-2: 批量化版本的单行构建（无 SQL，依赖外部批量加载的 Map）。
     * <p>由 {@link #buildSenderMessagePage} 调用，避免每条消息都查 user + oss。
     */
    public Map<String, Object> buildSenderMessageRow(ImMessage msg,
                                                     Map<Long, SysUserVo> userMap,
                                                     Map<Long, String> avatarUrlMap) {
        Map<String, Object> row = buildSenderMessageRowBase(msg);
        SysUserVo sender = msg.getFromUserId() == null ? null : userMap.get(msg.getFromUserId());
        if (sender != null) {
            row.put("senderId", sender.getUserId());
            row.put("senderName", StrUtil.blankToDefault(sender.getNickName(), sender.getUserName()));
            Long avatarId = sender.getAvatar();
            row.put("senderAvatar", avatarId == null ? "" : avatarUrlMap.getOrDefault(avatarId, ""));
        }
        return row;
    }

    /**
     * P2-2: 单行基础字段构建（无 SQL，无发送者信息）。
     * <p>被 {@link #buildSenderMessageRow(ImMessage)} 和 {@link #buildSenderMessageRow(ImMessage, Map, Map)} 共用。
     */
    private Map<String, Object> buildSenderMessageRowBase(ImMessage msg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("messageId", msg.getMessageId());
        row.put("fromUserId", msg.getFromUserId());
        row.put("toUserId", msg.getToUserId());
        row.put("groupId", msg.getGroupId());
        row.put("content", msg.getContent());
        row.put("msgType", msg.getMsgType());
        row.put("messageType", msg.getMsgType());
        row.put("isRead", msg.getIsRead());
        row.put("revoked", msg.getRevoked());
        row.put("forwardFrom", msg.getForwardFrom());
        row.put("createTime", msg.getCreateTime());
        if (StrUtil.isNotBlank(msg.getMentionUserIds())) {
            row.put("mentionUserIds", JsonUtils.parseArray(msg.getMentionUserIds(), Long.class));
        }
        return row;
    }
}
