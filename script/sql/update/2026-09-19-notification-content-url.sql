-- 消息盒子跳转修复 — sys_user_notification 增加 content_url（跳转链接）列
-- 背景: 流程待办/流程结束通知的跳转地址（含 procInsId/taskId，由 flowable.message-base-url 拼出）
--       此前只随实时推送信封下发、未落库；用户刷新页面后从 /im/notification/page 读回的通知
--       没有任何跳转目标（ref_id 为空、content 为纯文本），消息盒子点击只能弹出详情窗，
--       无法跳转到流程审批详情。本次补齐持久化能力。
-- 目标库: ry-vue（系统库）。
-- 注意: 必须先执行本脚本再部署对应后端版本 —— 实体新增字段后 INSERT 会带上新列，缺列将导致通知落库失败。
-- 存量数据: 升级前产生的通知 content_url 为空，无法回溯补齐（旧数据未保存跳转地址），点击仍走详情弹窗；
--           升级后新产生的流程待办/结束通知可正常跳转。

ALTER TABLE sys_user_notification ADD COLUMN content_url VARCHAR(500) NULL DEFAULT NULL COMMENT '跳转链接（如流程详情页地址，含 procInsId/taskId）';
