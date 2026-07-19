-- P6-B3: im_message 表新增引用消息字段
-- 注意：基础建表 SQL（ry-vue.sql 中的 im_message）未包含 mention_user_ids，
--       该字段由后续迁移脚本添加，因此此处去掉 AFTER 子句，将新字段追加到表末尾。
ALTER TABLE `im_message`
  ADD COLUMN `reply_message_id` BIGINT(20) NULL COMMENT '引用的消息 ID',
  ADD COLUMN `reply_snippet`    VARCHAR(500) NULL COMMENT '引用消息内容摘要';

ALTER TABLE `im_message`
  ADD INDEX `idx_reply_msg` (`reply_message_id`);
