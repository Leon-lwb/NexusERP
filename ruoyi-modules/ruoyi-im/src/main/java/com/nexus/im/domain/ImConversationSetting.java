package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Date;

/**
 * 会话配置实体
 * <p>记录用户对单个会话（私聊/群聊）的个性化设置，如置顶、免打扰，按用户+会话维度唯一。
 * 属于 IM（即时通讯）模块，继承租户实体自动带租户字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_conversation_setting")
public class ImConversationSetting extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId("id")
    private Long id;
    /** 配置所属用户 ID。 */
    private Long userId;
    /** 会话类型：PRIVATE（私聊）/ GROUP（群聊）。 */
    private String chatType;
    /** 会话目标 ID：私聊为对端 userId，群聊为 groupId。 */
    private Long targetId;
    /** 是否置顶：1 置顶，0 不置顶。 */
    private Integer isPinned;

    /** 置顶操作时间，取消置顶时为 null。 */
    private Date pinnedAt;

    /** 是否免打扰：1 免打扰，0 不静音。 */
    private Integer isMuted;
}
