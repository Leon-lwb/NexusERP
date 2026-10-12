package com.nexus.im.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.im.domain.ImMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Collection;
import java.util.Map;

/**
 * 即时通讯消息Mapper接口
 *
 * @author wanghuan
 */
public interface ImMessageMapper extends BaseMapperPlus<ImMessage, ImMessage> {

    /**
     * P4-10 B6: 查询当前用户的全部私聊会话最近一条消息（按对端去重）。
     * <p>原实现：{@code selectList(... limit 200)} 加载最近 200 条到内存后用 LinkedHashMap 去重，
     * 当某对端高频发消息时 200 条可能只覆盖少数 peer，导致 peerIds 集合过小、后续未读数 SQL 漏算。
     * <p>新实现：DB 侧用 {@code LEAST/GREATEST} 按对端 pair 分组取 {@code MAX(message_id)}，
     * 再 INNER JOIN 主表取完整行；返回行数 = peer 数（通常 ≤ 50），不再受 200 条限制影响。
     * <p>依赖：{@code message_id} 为雪花 ID 单调递增，{@code MAX(message_id)} 即为该 pair 的最新消息。
     * <p>限制：仅支持 MySQL（LEAST/GREATEST + 子查询）。
     *
     * @param me 当前用户 ID
     * @return 按 create_time 倒序的最近私聊消息列表（每对端一行）
     */
    @Select("""
        SELECT m.* FROM im_message m
        INNER JOIN (
            SELECT MAX(message_id) AS max_id FROM im_message
            WHERE group_id IS NULL
              AND (from_user_id = #{me} OR to_user_id = #{me})
            GROUP BY LEAST(from_user_id, to_user_id), GREATEST(from_user_id, to_user_id)
        ) t ON m.message_id = t.max_id
        ORDER BY m.create_time DESC
        LIMIT 200
        """)
    List<ImMessage> selectLatestPrivatePerPeer(@Param("me") Long me);
    /** 隐藏消息在聚合前排除，保证会话摘要回退到最近一条可见消息。 */
    @Select("""
        <script>
        SELECT m.* FROM im_message m INNER JOIN (
            SELECT MAX(message_id) AS max_id FROM im_message
            WHERE group_id IS NULL
              <if test="tenantId != null and tenantId != ''">AND tenant_id = #{tenantId}</if>
              AND (from_user_id = #{me} OR to_user_id = #{me})
              <if test="hiddenIds != null and !hiddenIds.isEmpty()">
                AND message_id NOT IN
                <foreach collection="hiddenIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
              </if>
            GROUP BY LEAST(from_user_id, to_user_id), GREATEST(from_user_id, to_user_id)
        ) t ON m.message_id = t.max_id
        <if test="tenantId != null and tenantId != ''">WHERE m.tenant_id = #{tenantId}</if>
        ORDER BY m.create_time DESC LIMIT 200
        </script>
        """)
    List<ImMessage> selectLatestVisiblePrivatePerPeer(@Param("tenantId") String tenantId, @Param("me") Long me,
                                                   @Param("hiddenIds") Collection<Long> hiddenIds);

    /** 群未读仅统计当前用户的未读通知，兼容历史通知没有 extJson 的情况。 */
    @Select("""
        <script>
        SELECT m.group_id AS groupId, COUNT(DISTINCT m.message_id) AS cnt
        FROM im_message m INNER JOIN sys_user_notification n
          ON n.ref_id = CAST(m.message_id AS CHAR) AND n.tenant_id = m.tenant_id
        WHERE n.user_id = #{me}
          <if test="tenantId != null and tenantId != ''">AND m.tenant_id = #{tenantId} AND n.tenant_id = #{tenantId}</if> AND n.category = 'im_group'
          AND n.ref_type = 'im_message' AND n.read_flag = '0'
          AND m.revoked = '0' AND m.from_user_id != #{me}
          AND m.group_id IN
          <foreach collection="groupIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
          <if test="hiddenIds != null and !hiddenIds.isEmpty()">
            AND m.message_id NOT IN
            <foreach collection="hiddenIds" item="id" open="(" close=")" separator=",">#{id}</foreach>
          </if>
        GROUP BY m.group_id
        </script>
        """)
    List<Map<String, Object>> selectGroupUnreadCounts(@Param("tenantId") String tenantId, @Param("me") Long me,
                                                    @Param("groupIds") Collection<Long> groupIds,
                                                    @Param("hiddenIds") Collection<Long> hiddenIds);
}
