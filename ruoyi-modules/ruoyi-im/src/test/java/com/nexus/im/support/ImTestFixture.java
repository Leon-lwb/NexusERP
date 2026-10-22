package com.nexus.im.support;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.im.domain.ImGroup;
import com.nexus.im.domain.ImGroupMember;
import com.nexus.im.domain.ImMessage;
import com.nexus.im.domain.SysUserNotification;

import java.util.Date;
import java.util.List;

/**
 * IM 测试数据构造工具。
 * <p>
 * 集中管理测试数据构造逻辑，避免每个测试类重复 new + set 字段。
 * 所有方法返回独立实例，测试间无状态共享。
 */
public final class ImTestFixture {

    private ImTestFixture() {
    }

    /**
     * 构造一条 IM 消息（默认未读、未撤回）。
     *
     * @param messageId  消息 ID（测试用 1L/2L 即可，雪花 ID 场景用 {@link #snowflakeId(long)}）
     * @param fromUserId 发送者
     * @param toUserId   接收者（群聊时为 null）
     * @param groupId    群组 ID（私聊时为 null）
     * @param content    消息内容
     */
    public static ImMessage buildMessage(Long messageId, Long fromUserId, Long toUserId, Long groupId, String content) {
        ImMessage m = new ImMessage();
        m.setMessageId(messageId);
        m.setFromUserId(fromUserId);
        m.setToUserId(toUserId);
        m.setGroupId(groupId);
        m.setContent(content);
        m.setMsgType("text");
        m.setIsRead("0");
        m.setRevoked("0");
        m.setCreateTime(new Date());
        return m;
    }

    /**
     * 构造一个群组（默认状态 "0" = 正常）。
     */
    public static ImGroup buildGroup(Long groupId, String groupName, Long ownerUserId) {
        ImGroup g = new ImGroup();
        g.setGroupId(groupId);
        g.setGroupName(groupName);
        g.setOwnerUserId(ownerUserId);
        g.setStatus("0");
        return g;
    }

    /**
     * 构造一个群成员关系。
     */
    public static ImGroupMember buildGroupMember(Long groupId, Long userId) {
        ImGroupMember m = new ImGroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setJoinTime(new Date());
        return m;
    }

    /**
     * 批量构造群成员关系（按 userIds 顺序）。
     */
    public static List<ImGroupMember> buildGroupMembers(Long groupId, List<Long> userIds) {
        return userIds.stream()
            .map(uid -> buildGroupMember(groupId, uid))
            .toList();
    }

    /**
     * 构造一条用户通知。
     */
    public static SysUserNotification buildNotification(Long notificationId, Long userId, String category) {
        SysUserNotification n = new SysUserNotification();
        n.setNotificationId(notificationId);
        n.setUserId(userId);
        n.setCategory(category);
        n.setReadFlag("0");
        n.setCreateTime(new Date());
        return n;
    }

    /**
     * 构造雪花 ID（19 位长整型，超出 JS Number 安全整数范围 2^53-1 = 9007199254740991）。
     * <p>
     * 用于验证 {@code BigNumberSerializer} 将 Long 序列化为字符串，避免 JS 精度丢失。
     *
     * @param seq 序号（0-9999...），加到基数 1900000000000000000L 上
     */
    public static Long snowflakeId(long seq) {
        return 1_900_000_000_000_000_000L + seq;
    }

    /** 同时支持当前源码和已安装依赖的分页构造器。 */
    public static PageQuery pageQuery(int pageSize, int pageNum) {
        try {
            PageQuery query;
            try {
                query = PageQuery.class.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                query =
                        PageQuery.class
                                .getDeclaredConstructor(Integer.class, Integer.class)
                                .newInstance(pageSize, pageNum);
            }
            query.setPageSize(pageSize);
            query.setPageNum(pageNum);
            return query;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法构造分页参数", e);
        }
    }

}
