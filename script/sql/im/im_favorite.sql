-- im_favorite: 用户收藏的消息
CREATE TABLE IF NOT EXISTS `im_favorite` (
  `id`            BIGINT(20)    NOT NULL                COMMENT '主键',
  `user_id`       BIGINT(20)    NOT NULL                COMMENT '收藏者用户 ID',
  `message_id`    BIGINT(20)    NOT NULL                COMMENT '被收藏的消息 ID',
  `chat_type`     VARCHAR(10)   NOT NULL                COMMENT '来源会话类型: PRIVATE/GROUP',
  `target_id`     BIGINT(20)    NOT NULL                COMMENT '来源会话目标 ID',
  `from_user_id`  BIGINT(20)    NULL                    COMMENT '消息原发送者（冗余便于列表展示）',
  `msg_type`      VARCHAR(20)   NULL                    COMMENT '消息类型（冗余）',
  `content_snippet` VARCHAR(500) NULL                   COMMENT '内容摘要（避免列表 JOIN im_message）',
  `tenant_id`     VARCHAR(20)   DEFAULT '000000'        COMMENT '租户编号',
  `create_by`     BIGINT(20)    DEFAULT NULL            COMMENT '创建者',
  `create_time`   DATETIME(3)   DEFAULT NULL            COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_message` (`user_id`, `message_id`, `tenant_id`),
  KEY `idx_user_create` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM 用户收藏消息';
