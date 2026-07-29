-- ============================================================
-- NexusERP AI 二阶段 S2-B 数据库变更
-- 推理模式 + 厂商策略模式 + 专用 Rerank
-- ============================================================

-- 1. ai_message 增加推理过程内容列（存储 enableThinking 时的思考过程）
--    注意：ai_message 的种子 INSERT 使用无列名定位（ry-vue.sql），
--    故通过此迁移脚本演进 schema，不直接改动 ry-vue.sql 中的建表与种子语句。
ALTER TABLE `ai_message`
    ADD COLUMN `thinking_content` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '推理过程内容（enableThinking 时存）';

-- ============================================================
-- 说明（无需 DDL，仅文档）：
-- 2. ai_provider.provider_type 新增可选值：
--      DEEPSEEK / MINIMAX / XIAOMI(MiMo) / CUSTOM_API / SILICONFLOW
--    provider_type 为自由 varchar(32)，无枚举表约束；
--    前端「AI 厂商管理」页下拉选项需同步新增（见 B.2/B.5 前端）。
--    新厂商需在管理页配置真实 API Key 后启用（api_key NOT NULL）。
-- 3. ai_model.model_type 新增可选值：IMAGE（S2-C 文生图，varchar(32) 足够）。
-- 4. 专用 Rerank：Rerank 模型需选 provider_type 为 DASHSCOPE/ZHIPU/SILICONFLOW
--    且 model_type=RERANK（如 BAAI/bge-reranker-v2-m3）。
-- ============================================================
