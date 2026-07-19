-- im_conversation_setting: 用户会话级配置（置顶/免打扰）
-- 支持私聊和群聊：chat_type=PRIVATE 时 target_id=user_id，chat_type=GROUP 时 target_id=group_id
CREATE TABLE IF NOT EXISTS `im_conversation_setting` (
  `id`            BIGINT(20)    NOT NULL                COMMENT '主键',
  `user_id`       BIGINT(20)    NOT NULL                COMMENT '所属用户 ID',
  `chat_type`     VARCHAR(10)   NOT NULL                COMMENT '会话类型: PRIVATE/GROUP',
  `target_id`     BIGINT(20)    NOT NULL                COMMENT '目标 ID（私聊=对方 userId，群聊=groupId）',
  `is_pinned`     TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否置顶: 0=否 1=是',
  `pinned_at`     DATETIME(3)   NULL                    COMMENT '置顶时间（用于多会话置顶排序）',
  `is_muted`      TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '是否免打扰: 0=否 1=是',
  `tenant_id`     VARCHAR(20)   DEFAULT '000000'        COMMENT '租户编号',
  `create_by`     BIGINT(20)    DEFAULT NULL            COMMENT '创建者',
  `create_time`   DATETIME(3)   DEFAULT NULL            COMMENT '创建时间',
  `update_by`     BIGINT(20)    DEFAULT NULL            COMMENT '更新者',
  `update_time`   DATETIME(3)   DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_chat_target` (`user_id`, `chat_type`, `target_id`, `tenant_id`),
  KEY `idx_user_pinned` (`user_id`, `is_pinned`, `pinned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM 会话级配置（置顶/免打扰）';
