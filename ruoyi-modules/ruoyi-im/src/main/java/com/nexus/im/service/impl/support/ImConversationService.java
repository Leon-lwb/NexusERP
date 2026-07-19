package com.nexus.im.service.impl.support;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.im.domain.ImConversationSetting;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.mapper.ImConversationSettingMapper;
import com.nexus.im.mapper.ImGroupMapper;
import com.nexus.im.mapper.ImGroupMemberMapper;
import com.nexus.im.mapper.ImMessageMapper;
import com.nexus.system.domain.vo.SysUserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * P1-7: 会话子 Service
 * <p>从 ImChatServiceImpl 拆分，负责会话列表聚合（私聊 + 群聊）。
 * <p>P2-1: listConversations 重写为 6 步批量化，消除 5 类 N+1 查询。
 * 10 群组 + 10 私聊场景 SQL 数从 90+ 降到 ≤ 8。
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class ImConversationService {

    private final ImMessageMapper imMessageMapper;
    private final ImGroupMapper imGroupMapper;
    private final ImGroupMemberMapper imGroupMemberMapper;
    private final ImChatSupport support;
    private final ImConversationCache conversationCache;
    private final ImConversationSettingMapper conversationSettingMapper;

    /**
     * 列出当前用户的所有会话（群聊 + 私聊），按最后消息时间倒序。
     * <p>P2-1: 批量化改造，6 步聚合所有群聊会话 + 私聊会话。
     * <p>P4-2: 新增 Redis 缓存层，TTL 60s。命中时直接返回，跳过 8 步 SQL 查询；
     * 未命中时走 DB 查询后回填缓存。缓存失效由发送消息/标记已读/群组变更触发。
     * <ul>
     *   <li>第 1 步：用户参与的群组列表（1 SQL）</li>
     *   <li>第 2 步：批量查群组信息（1 SQL）</li>
     *   <li>第 3 步：批量查每群最后消息（1 SQL，依赖雪花 ID 单调递增）</li>
     *   <li>第 4 步：批量聚合群未读数（1 SQL，GROUP BY）</li>
     *   <li>第 5 步：批量查群成员 + 用户头像（1+1 SQL，按群前 4 名）</li>
     *   <li>第 6 步：组装群聊 VO（无 SQL）</li>
     *   <li>私聊会话复用相同批量化逻辑：1 SQL 取最近 200 条 + 1 SQL 批量对端用户 + 1 SQL 批量未读数</li>
     * </ul>
     */
    public List<ImConversationVo> listConversations() {
        // P4-2: 先查 Redis 缓存，命中则直接返回（跳过所有 SQL）
        List<ImConversationVo> cached = conversationCache.get();
        if (cached != null) {
            return cached;
        }
        Long me = LoginHelper.getUserId();
        List<ImConversationVo> list = new ArrayList<>();

        List<ImConversationVo> groupVos = listGroupConversations(me);
        List<ImConversationVo> privateVos = listPrivateConversations(me);
        list.addAll(groupVos);
        list.addAll(privateVos);

        // P6-B1: 合并当前用户的会话配置（置顶/免打扰）
        applyConversationSettings(me, list);
        // P6-B1: 排序：置顶在前（按 pinnedAt 倒序），非置顶按 lastTime 倒序
        list.sort((a, b) -> {
            int pa = a.getIsPinned() == null ? 0 : a.getIsPinned();
            int pb = b.getIsPinned() == null ? 0 : b.getIsPinned();
            if (pa != pb) return pb - pa;
            if (pa == 1) {
                java.util.Date da = a.getPinnedAt() == null ? new java.util.Date(0) : a.getPinnedAt();
                java.util.Date db = b.getPinnedAt() == null ? new java.util.Date(0) : b.getPinnedAt();
                return db.compareTo(da);
            }
            java.util.Date da = a.getLastTime() == null ? new java.util.Date(0) : a.getLastTime();
            java.util.Date db = b.getLastTime() == null ? new java.util.Date(0) : b.getLastTime();
            return db.compareTo(da);
        });
        // P4-2: 回填缓存
        conversationCache.put(list);
        return list;
    }

    /**
     * 群聊会话批量化加载。
     */
    private List<ImConversationVo> listGroupConversations(Long me) {
        // ── 第 1 步：用户参与的群组（1 SQL） ──
        List<ImGroupMember> memberships = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .eq(ImGroupMember::getUserId, me));
        if (memberships.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> groupIds = memberships.stream()
            .map(ImGroupMember::getGroupId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (groupIds.isEmpty()) {
            return new ArrayList<>();
        }

        // ── 第 2 步：批量查群组信息（1 SQL） ──
        // 注：ImGroupMapper extends BaseMapperPlus<ImGroup, ImGroup>（实体类与 VO 同类型），
        // 不能用 selectVoByIds — MapstructUtils.convert 找不到 ImGroup→ImGroup 同类型转换器，
        // 会抛 ConvertException。改用 selectByIds 直接返回实体列表，跳过 convert 步骤。
        List<ImGroup> groups = imGroupMapper.selectByIds(groupIds);
        if (groups == null || groups.isEmpty()) {
            return new ArrayList<>();
        }
        // 仅保留正常状态（status=0），过滤已解散
        Map<Long, ImGroup> groupMap = groups.stream()
            .filter(g -> g != null && g.getGroupId() != null && "0".equals(g.getStatus()))
            .collect(Collectors.toMap(ImGroup::getGroupId, g -> g, (a, b) -> a, LinkedHashMap::new));
        if (groupMap.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> validGroupIds = new ArrayList<>(groupMap.keySet());

        // ── 第 3 步：批量查每群最后消息（2 SQL，利用雪花 ID 单调递增特性） ──
        // P2-1 修复: 原实现无 LIMIT 一次性加载所有群所有消息到内存（10群×10000消息=10万行）
        // 改为先 selectMaps 取每群 MAX(message_id)（N 行），再用 IN 查完整数据（N 行）
        // messageId 为雪花 ID 单调递增，MAX(message_id) 即为最新消息
        List<Map<String, Object>> maxIdRows = imMessageMapper.selectMaps(new QueryWrapper<ImMessage>()
            .select("MAX(message_id) as maxId")
            .in("group_id", validGroupIds)
            .groupBy("group_id"));
        Set<Long> maxMessageIds = new LinkedHashSet<>();
        for (Map<String, Object> row : maxIdRows) {
            Object idObj = row.get("maxId");
            if (idObj instanceof Number) {
                maxMessageIds.add(((Number) idObj).longValue());
            }
        }
        Map<Long, ImMessage> lastMsgByGroup = new LinkedHashMap<>();
        if (!maxMessageIds.isEmpty()) {
            List<ImMessage> lastMsgs = imMessageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
                .select(ImMessage::getMessageId, ImMessage::getGroupId, ImMessage::getContent,
                    ImMessage::getCreateTime, ImMessage::getMsgType)
                .in(ImMessage::getMessageId, maxMessageIds));
            for (ImMessage m : lastMsgs) {
                if (m.getGroupId() != null) {
                    lastMsgByGroup.put(m.getGroupId(), m);
                }
            }
        }

        // ── 第 4 步：批量聚合群未读数（1 SQL，GROUP BY group_id） ──
        // P2-1 修复: mapUnderscoreToCamelCase=true 会将列名 "group_id" 转为 map key "groupId"
        // 原 row.get("group_id") 返回 null 导致未读数始终为 0；改用驼峰 key
        List<Map<String, Object>> groupUnreadRows = imMessageMapper.selectMaps(new QueryWrapper<ImMessage>()
            .select("group_id", "count(*) as cnt")
            .in("group_id", validGroupIds)
            .ne("from_user_id", me)
            .eq("is_read", "0")
            .groupBy("group_id"));
        Map<Long, Long> groupUnreadMap = new LinkedHashMap<>();
        for (Map<String, Object> row : groupUnreadRows) {
            Object gidObj = row.get("groupId");
            Object cntObj = row.get("cnt");
            if (gidObj instanceof Number && cntObj instanceof Number) {
                groupUnreadMap.put(((Number) gidObj).longValue(), ((Number) cntObj).longValue());
            }
        }

        // ── 第 5 步：批量查群成员 + 用户头像（1+1 SQL） ──
        // 5.1 一次查所有群的成员（按 join_time 升序），后处理每组取前 4 名
        List<ImGroupMember> allMembers = imGroupMemberMapper.selectList(new LambdaQueryWrapper<ImGroupMember>()
            .in(ImGroupMember::getGroupId, validGroupIds)
            .orderByAsc(ImGroupMember::getJoinTime));
        Map<Long, List<ImGroupMember>> membersByGroup = allMembers.stream()
            .filter(m -> m.getGroupId() != null)
            .collect(Collectors.groupingBy(ImGroupMember::getGroupId, LinkedHashMap::new, Collectors.toList()));
        // 5.2 收集所有需要查头像的 userId，每群前 4 名
        Set<Long> avatarUserIds = new LinkedHashSet<>();
        Map<Long, List<ImGroupMember>> top4ByGroup = new LinkedHashMap<>();
        for (Map.Entry<Long, List<ImGroupMember>> e : membersByGroup.entrySet()) {
            List<ImGroupMember> top4 = e.getValue().stream().limit(4).toList();
            top4ByGroup.put(e.getKey(), top4);
            top4.forEach(m -> {
                if (m.getUserId() != null) {
                    avatarUserIds.add(m.getUserId());
                }
            });
        }
        Map<Long, SysUserVo> userMap = support.batchLoadUsers(avatarUserIds);
        // 5.3 收集所有 avatar OSS ID，批量解析 URL
        Set<Long> avatarOssIds = userMap.values().stream()
            .map(SysUserVo::getAvatar)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> avatarUrlMap = support.batchResolveAvatarUrls(avatarOssIds);

        // ── 第 6 步：组装群聊 VO（无 SQL） ──
        List<ImConversationVo> groupVos = new ArrayList<>();
        for (Long gid : validGroupIds) {
            ImGroup g = groupMap.get(gid);
            if (g == null) {
                continue;
            }
            ImMessage last = lastMsgByGroup.get(gid);
            ImConversationVo vo = new ImConversationVo();
            vo.setChatType("GROUP");
            vo.setTargetId(String.valueOf(g.getGroupId()));
            vo.setTitle(g.getGroupName());
            vo.setAvatar(g.getAvatar());
            vo.setMemberAvatars(buildMemberAvatars(top4ByGroup.get(gid), userMap, avatarUrlMap));
            vo.setLastContent(last != null ? last.getContent() : "");
            vo.setLastTime(last != null ? last.getCreateTime() : g.getCreateTime());
            vo.setUnreadCount(groupUnreadMap.getOrDefault(gid, 0L));
            groupVos.add(vo);
        }
        return groupVos;
    }

    /**
     * 根据群前 4 名成员构建头像 URL 列表（无 SQL，依赖外部批量加载的 Map）。
     */
    private List<String> buildMemberAvatars(List<ImGroupMember> top4Members,
                                            Map<Long, SysUserVo> userMap,
                                            Map<Long, String> avatarUrlMap) {
        List<String> avatars = new ArrayList<>();
        if (top4Members == null || top4Members.isEmpty()) {
            return avatars;
        }
        for (ImGroupMember m : top4Members) {
            if (m.getUserId() == null) {
                continue;
            }
            SysUserVo u = userMap.get(m.getUserId());
            if (u == null || u.getAvatar() == null) {
                continue;
            }
            String url = avatarUrlMap.get(u.getAvatar());
            if (StrUtil.isNotBlank(url)) {
                avatars.add(url);
            }
        }
        return avatars;
    }

    /**
     * 私聊会话批量化加载。
     * <p>P4-10 B6: 第 1 步 SQL 改写为 DB 侧去重（LEAST/GREATEST + MAX(message_id) 子查询），
     * 替代原"取 200 条 + 内存 LinkedHashMap 去重"。返回行数 = peer 数（通常 ≤ 50），
     * 避免高频对端挤占 200 条限额导致其他 peer 漏算。
     */
    private List<ImConversationVo> listPrivateConversations(Long me) {
        // ── 第 1 步：DB 侧按对端 pair 去重，每 peer 返回最新一条消息（1 SQL） ──
        List<ImMessage> recent = imMessageMapper.selectLatestPrivatePerPeer(me);
        if (recent.isEmpty()) {
            return new ArrayList<>();
        }

        // 按 peer 提取（DB 已按 create_time DESC 返回，putIfAbsent 保留首条 = 最新）
        Map<Long, ImMessage> latestByPeer = new LinkedHashMap<>();
        for (ImMessage m : recent) {
            Long peer;
            if (me.equals(m.getFromUserId())) {
                peer = m.getToUserId();
            } else {
                peer = m.getFromUserId();
            }
            if (peer == null) {
                continue;
            }
            latestByPeer.putIfAbsent(peer, m);
        }
        if (latestByPeer.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> peerIds = new ArrayList<>(latestByPeer.keySet());

        // ── 第 2 步：批量查对端用户（1 SQL） ──
        Map<Long, SysUserVo> userMap = support.batchLoadUsers(peerIds);
        // 批量解析 avatar OSS URL（1 SQL）
        Set<Long> avatarOssIds = userMap.values().stream()
            .map(SysUserVo::getAvatar)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> avatarUrlMap = support.batchResolveAvatarUrls(avatarOssIds);

        // ── 第 3 步：批量聚合私聊未读数（1 SQL，GROUP BY from_user_id） ──
        // P2-1 修复: mapUnderscoreToCamelCase=true 会将列名 "from_user_id" 转为 map key "fromUserId"
        // 原 row.get("from_user_id") 返回 null 导致未读数始终为 0；改用驼峰 key
        List<Map<String, Object>> privateUnreadRows = imMessageMapper.selectMaps(new QueryWrapper<ImMessage>()
            .select("from_user_id", "count(*) as cnt")
            .eq("to_user_id", me)
            .in("from_user_id", peerIds)
            .eq("is_read", "0")
            .isNull("group_id")
            .groupBy("from_user_id"));
        Map<Long, Long> privateUnreadMap = new LinkedHashMap<>();
        for (Map<String, Object> row : privateUnreadRows) {
            Object fromObj = row.get("fromUserId");
            Object cntObj = row.get("cnt");
            if (fromObj instanceof Number && cntObj instanceof Number) {
                privateUnreadMap.put(((Number) fromObj).longValue(), ((Number) cntObj).longValue());
            }
        }

        // ── 第 4 步：组装私聊 VO（无 SQL） ──
        List<ImConversationVo> privateVos = new ArrayList<>();
        for (Map.Entry<Long, ImMessage> e : latestByPeer.entrySet()) {
            Long peer = e.getKey();
            ImMessage last = e.getValue();
            SysUserVo u = userMap.get(peer);
            ImConversationVo vo = new ImConversationVo();
            vo.setChatType("PRIVATE");
            vo.setTargetId(String.valueOf(peer));
            vo.setTitle(u != null ? StrUtil.blankToDefault(u.getNickName(), u.getUserName()) : String.valueOf(peer));
            if (u != null && u.getAvatar() != null) {
                vo.setAvatar(avatarUrlMap.getOrDefault(u.getAvatar(), ""));
            } else {
                vo.setAvatar("");
            }
            vo.setLastContent(last.getContent());
            vo.setLastTime(last.getCreateTime());
            vo.setUnreadCount(privateUnreadMap.getOrDefault(peer, 0L));
            privateVos.add(vo);
        }
        return privateVos;
    }

    /**
     * P6-B1: 合并当前用户的会话配置（置顶/免打扰）到会话 VO 列表。
     * <p>一次 SQL 查询当前用户所有会话配置，按 chatType:targetId 索引后填充到 VO。
     */
    private void applyConversationSettings(Long userId, List<ImConversationVo> list) {
        if (list == null || list.isEmpty()) return;
        List<ImConversationSetting> settings = conversationSettingMapper.selectList(
            new LambdaQueryWrapper<ImConversationSetting>()
                .eq(ImConversationSetting::getUserId, userId));
        if (settings == null || settings.isEmpty()) return;
        Map<String, ImConversationSetting> map = settings.stream().collect(
            Collectors.toMap(s -> s.getChatType() + ":" + s.getTargetId(), s -> s, (a, b) -> a));
        for (ImConversationVo vo : list) {
            String key = vo.getChatType() + ":" + vo.getTargetId();
            ImConversationSetting s = map.get(key);
            if (s != null) {
                vo.setIsPinned(s.getIsPinned() == null ? 0 : s.getIsPinned());
                vo.setIsMuted(s.getIsMuted() == null ? 0 : s.getIsMuted());
                vo.setPinnedAt(s.getPinnedAt());
            }
        }
    }

    /**
     * P6-B1: 失效当前用户的会话列表缓存。
     * <p>供 AppImController 在置顶/免打扰配置变更后调用，避免 60s TTL 内返回旧数据。
     */
    public void evictConversationCache() {
        conversationCache.evict();
    }
}
