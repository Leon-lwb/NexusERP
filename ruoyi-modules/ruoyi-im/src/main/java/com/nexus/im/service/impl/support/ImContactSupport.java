package com.nexus.im.service.impl.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.im.domain.ImFriend;
import com.nexus.im.domain.vo.ImContactDeptVo;
import com.nexus.im.domain.vo.ImContactUserVo;
import com.nexus.im.domain.vo.ImFriendVo;
import com.nexus.im.mapper.ImFriendMapper;
import com.nexus.system.domain.SysDept;
import com.nexus.system.domain.SysUser;
import com.nexus.system.domain.SysUserPost;
import com.nexus.system.domain.vo.SysDeptVo;
import com.nexus.system.domain.vo.SysPostVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysDeptMapper;
import com.nexus.system.mapper.SysPostMapper;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.mapper.SysUserPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IM 通讯录辅助组件
 * <p>提供 App 通讯录所需的两块核心能力：
 * <ul>
 *   <li>{@link #buildContactTree()}：部门树 + 全员聚合（仅登录即可访问，绕开 @DataPermission 的 Mapper 默认方法）</li>
 *   <li>{@link #buildFriendList(Long)}：好友列表批量组装（批量查用户/头像/在线状态，避免 N+1）</li>
 * </ul>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
public class ImContactSupport {

    private static final String ONLINE = "online";
    private static final String OFFLINE = "offline";

    /** 部门 Mapper（仅使用无 @DataPermission 注解的 default 方法）。 */
    private final SysDeptMapper deptMapper;
    /** 用户 Mapper（selectVoList 无数据权限注解，多租户拦截器自动注入租户条件）。 */
    private final SysUserMapper userMapper;
    /** 用户-岗位关联 Mapper（sys_user_post 在租户排除表中，无 tenant_id）。 */
    private final SysUserPostMapper userPostMapper;
    /** 岗位 Mapper。 */
    private final SysPostMapper postMapper;
    /** 好友关系 Mapper。 */
    private final ImFriendMapper friendMapper;
    /** 共享辅助：批量用户/头像加载。 */
    private final ImChatSupport chatSupport;
    /** 在线状态缓存。 */
    private final ImOnlineCache onlineCache;

    /**
     * 构建通讯录聚合数据：部门树（含直属成员、直属/累计人数）。
     * <p>仅查正常状态（status=0）的部门与用户。
     *
     * @return 根部门列表（parentId=0），按 orderNum 排序
     */
    public List<ImContactDeptVo> buildContactTree() {
        List<SysDeptVo> depts = deptMapper.selectVoList(new LambdaQueryWrapper<SysDept>()
            .eq(SysDept::getStatus, "0")
            .orderByAsc(SysDept::getParentId)
            .orderByAsc(SysDept::getOrderNum));
        List<SysUserVo> users = userMapper.selectVoList(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, "0")
            .orderByAsc(SysUser::getUserId));
        if (CollUtil.isEmpty(depts)) {
            return Collections.emptyList();
        }

        Map<Long, List<SysUserVo>> usersByDept = users.stream()
            .filter(u -> u != null && u.getDeptId() != null)
            .collect(Collectors.groupingBy(SysUserVo::getDeptId));

        // userId 集合，用于岗位与在线状态批量查询
        Set<Long> userIds = users.stream()
            .map(SysUserVo::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> postNames = batchPostNames(userIds);
        Map<Long, String> avatarUrls = chatSupport.batchResolveAvatarUrls(
            users.stream().map(SysUserVo::getAvatar).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Map<String, Object>> onlineInfos = onlineCache.batchOnlineInfo(userIds);

        // deptId → VO（含直属成员）
        Map<Long, ImContactDeptVo> voMap = new LinkedHashMap<>();
        for (SysDeptVo dept : depts) {
            ImContactDeptVo vo = new ImContactDeptVo();
            vo.setDeptId(dept.getDeptId());
            vo.setParentId(dept.getParentId());
            vo.setDeptName(dept.getDeptName());
            vo.setOrderNum(dept.getOrderNum());
            vo.setStatus(dept.getStatus());
            List<SysUserVo> deptUsers = usersByDept.getOrDefault(dept.getDeptId(), Collections.emptyList());
            vo.setUsers(deptUsers.stream()
                .map(u -> toContactUser(u, postNames, avatarUrls, onlineInfos))
                .collect(Collectors.toList()));
            vo.setUserCount(vo.getUsers().size());
            voMap.put(dept.getDeptId(), vo);
        }

        // 组树（parentId 兜底防孤儿）
        List<ImContactDeptVo> roots = new ArrayList<>();
        for (ImContactDeptVo vo : voMap.values()) {
            ImContactDeptVo parent = vo.getParentId() != null ? voMap.get(vo.getParentId()) : null;
            if (parent != null && !parent.getDeptId().equals(vo.getDeptId())) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }

        // 后序遍历累计人数
        sumTotalUserCount(roots);
        return roots;
    }

    /**
     * 构建好友列表：批量查用户资料、头像、在线状态，附带我的备注。
     */
    public List<ImFriendVo> buildFriendList(Long userId) {
        List<ImFriend> relations = friendMapper.selectList(new LambdaQueryWrapper<ImFriend>()
            .eq(ImFriend::getUserId, userId));
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyList();
        }

        List<Long> friendIds = relations.stream()
            .map(ImFriend::getFriendUserId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, SysUserVo> userMap = chatSupport.batchLoadUsers(friendIds);
        Map<Long, String> avatarUrls = chatSupport.batchResolveAvatarUrls(
            userMap.values().stream().map(SysUserVo::getAvatar).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Map<String, Object>> onlineInfos = onlineCache.batchOnlineInfo(friendIds);

        List<ImFriendVo> result = new ArrayList<>(relations.size());
        for (ImFriend rel : relations) {
            SysUserVo u = userMap.get(rel.getFriendUserId());
            if (u == null) {
                // 好友用户已被删除时跳过，避免列表出现空行
                continue;
            }
            ImFriendVo vo = new ImFriendVo();
            vo.setFriendUserId(u.getUserId());
            vo.setUserName(u.getUserName());
            vo.setNickName(u.getNickName());
            vo.setAvatar(avatarUrls.getOrDefault(u.getAvatar(), ""));
            vo.setRemark(StrUtil.blankToDefault(rel.getRemark(), null));
            vo.setFriendTime(rel.getCreateTime());
            Map<String, Object> online = onlineInfos.get(u.getUserId());
            boolean isOnline = online != null && Boolean.TRUE.equals(online.get("online"));
            vo.setOnlineStatus(isOnline ? ONLINE : OFFLINE);
            Object lastActive = online != null ? online.get("lastActiveTime") : null;
            vo.setLastActiveTime(lastActive != null ? String.valueOf(lastActive) : null);
            result.add(vo);
        }
        return result;
    }

    /**
     * 更新好友备注（仅关系持有方可改）。
     *
     * @return 更新成功返回 true；好友关系不存在返回 false
     */
    public boolean updateFriendRemark(Long userId, Long friendUserId, String remark) {
        ImFriend rel = friendMapper.selectOne(new LambdaQueryWrapper<ImFriend>()
            .eq(ImFriend::getUserId, userId)
            .eq(ImFriend::getFriendUserId, friendUserId));
        if (rel == null) {
            return false;
        }
        // updateById 默认忽略 null 字段，清空备注需用 UpdateWrapper 显式 set null
        return friendMapper.update(null, new LambdaUpdateWrapper<ImFriend>()
            .eq(ImFriend::getId, rel.getId())
            .set(ImFriend::getRemark, StrUtil.blankToDefault(remark, null))) > 0;
    }

    private ImContactUserVo toContactUser(SysUserVo u, Map<Long, String> postNames,
                                          Map<Long, String> avatarUrls, Map<Long, Map<String, Object>> onlineInfos) {
        ImContactUserVo vo = new ImContactUserVo();
        vo.setUserId(u.getUserId());
        vo.setUserName(u.getUserName());
        vo.setNickName(u.getNickName());
        vo.setAvatar(avatarUrls.getOrDefault(u.getAvatar(), ""));
        vo.setDeptId(u.getDeptId());
        vo.setDeptName(u.getDeptName());
        vo.setPostName(postNames.get(u.getUserId()));
        Map<String, Object> online = onlineInfos.get(u.getUserId());
        boolean isOnline = online != null && Boolean.TRUE.equals(online.get("online"));
        vo.setOnlineStatus(isOnline ? ONLINE : OFFLINE);
        Object lastActive = online != null ? online.get("lastActiveTime") : null;
        vo.setLastActiveTime(lastActive != null ? String.valueOf(lastActive) : null);
        return vo;
    }

    /**
     * 批量查询用户岗位名称：一次查 sys_user_post + 一次查 sys_post，聚合为 "岗位A、岗位B"。
     */
    private Map<Long, String> batchPostNames(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<SysUserPost> links = userPostMapper.selectList(new LambdaQueryWrapper<SysUserPost>()
            .in(SysUserPost::getUserId, userIds));
        if (CollUtil.isEmpty(links)) {
            return Collections.emptyMap();
        }
        Set<Long> postIds = links.stream().map(SysUserPost::getPostId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> postNameMap = postIds.isEmpty() ? Collections.emptyMap()
            : postMapper.selectVoByIds(postIds).stream()
                .filter(p -> p != null && p.getPostId() != null)
                .collect(Collectors.toMap(SysPostVo::getPostId, SysPostVo::getPostName, (a, b) -> a));
        Map<Long, List<String>> byUser = new HashMap<>();
        for (SysUserPost link : links) {
            String name = postNameMap.get(link.getPostId());
            if (StrUtil.isNotBlank(name)) {
                byUser.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(name);
            }
        }
        return byUser.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.join("、", e.getValue())));
    }

    /**
     * 后序遍历统计含子部门的累计人数。
     */
    private int sumTotalUserCount(List<ImContactDeptVo> nodes) {
        int total = 0;
        for (ImContactDeptVo node : nodes) {
            int childTotal = node.getChildren().isEmpty() ? 0 : sumTotalUserCount(node.getChildren());
            node.setTotalUserCount(node.getUserCount() + childTotal);
            total += node.getTotalUserCount();
        }
        return total;
    }
}
