-- ============================================================
-- NexusERP AI 厂商与模型增量配置（2026-06-30）— 幂等重发版
--
-- 主键冲突修复：原脚本用固定 id（9001/9101...）在已落库环境会主键冲突。
--   本版改用「先按唯一键删除、再 INSERT 雪花 id」的幂等模式，可安全重复执行。
--   字典用 INSERT IGNORE；厂商/模型用 DELETE + INSERT，避免固定 id 冲突。
--
-- 后端支持的 8 个 providerType：
--   OPENAI / DEEPSEEK / DASHSCOPE / ZHIPU / OLLAMA / MINIMAX / XIAOMI / CUSTOM_API
--
-- 接入方式：
--   - DASHSCOPE / ZHIPU 走原生 SDK，apiHost 由 SDK 内置官方地址，填值仅作标识；
--   - XIAOMI / MINIMAX / DEEPSEEK / OPENAI 走 OpenAI 兼容接口，apiHost 必须是真实 endpoint；
--   - OLLAMA 走原生 SDK，apiHost 必填（本地 http://localhost:11434）。
--
-- 文生图（IMAGE）：后端仅有 TongYiWanxImageService（通义万相），providerName=Tongyiwanx，
--   故 IMAGE 模型只能挂在 DashScope 厂商下（wanx 系列），其他厂商暂无文生图实现。
--
-- ⚠️ API Key 均为占位符（sk-PLEASE_REPLACE_*），执行后必须在「AI 管理 - 厂商管理」页面
--    填入真实 Key 并保存（会自动 Jasypt 加密），否则调用返回 401/鉴权失败。
-- ============================================================

-- ============================================================
-- 0. 关闭外键检查（ai_model.provider_id 引用 ai_provider.id，删插期间临时关闭）
-- ============================================================
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;


-- ============================================================
-- 1. 字典补全（INSERT IGNORE，已有则跳过）
-- ============================================================

-- ai_provider_type 补 MINIMAX / XIAOMI / CUSTOM_API（原字典仅有 OPENAI/DASHSCOPE/ZHIPU/OLLAMA/DEEPSEEK）
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600200, '000000', 6, 'MiniMax',   'MINIMAX',    'ai_provider_type', '', 'primary', 'N', 103, 1, '2026-06-30 00:00:00', NULL, NULL, 'MiniMax 大模型'),
(600201, '000000', 7, '小米 MiMo', 'XIAOMI',     'ai_provider_type', '', 'success', 'N', 103, 1, '2026-06-30 00:00:00', NULL, NULL, '小米 MiMo'),
(600202, '000000', 8, '自定义 API', 'CUSTOM_API', 'ai_provider_type', '', 'info',    'N', 103, 1, '2026-06-30 00:00:00', NULL, NULL, 'OpenAI 兼容自定义接口');

-- ai_model_type 补 IMAGE（代码已使用但字典缺失）
INSERT IGNORE INTO `sys_dict_data` (`dict_code`, `tenant_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(600203, '000000', 4, '文生图模型', 'IMAGE', 'ai_model_type', '', 'danger', 'N', 103, 1, '2026-06-30 00:00:00', NULL, NULL, 'Image 图像生成模型');

-- 字典缓存需刷新：执行后若下拉未更新，重启应用或在「字典管理」点刷新缓存


-- ============================================================
-- 2. 新增 4 厂商（按 name 幂等：先删后插）
--    注：先删旧再插新，id 用应用雪花算法自动分配，杜绝固定 id 冲突。
--    ⚠️ 若你的库已为这些厂商填了真实 Key，DELETE 会一并清掉，请在执行前备份 ai_provider 表。
-- ============================================================

DELETE FROM `ai_provider` WHERE `name` IN ('通义千问', '智谱AI', '小米MiMo', 'MiniMax');

INSERT INTO `ai_provider` (`id`, `tenant_id`, `name`, `api_host`, `api_key`, `provider_type`, `status`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `attr1`, `attr2`, `attr3`, `attr4`)
SELECT
  FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, '000000', t.name, t.api_host, t.api_key, t.provider_type, '1', 103, 1, NOW(), NULL, NULL, '0', NULL, NULL, NULL, NULL
FROM (
  SELECT '通义千问' AS name, 'https://dashscope.aliyuncs.com' AS api_host, 'sk-PLEASE_REPLACE_DASHSCOPE_KEY' AS api_key, 'DASHSCOPE' AS provider_type
  UNION ALL SELECT '智谱AI',   'https://open.bigmodel.cn',     'sk-PLEASE_REPLACE_ZHIPU_KEY',    'ZHIPU'
  UNION ALL SELECT '小米MiMo', 'https://api.xiaomimimo.com/v1', 'sk-PLEASE_REPLACE_MIMO_KEY',     'XIAOMI'
  UNION ALL SELECT 'MiniMax',  'https://api.minimax.io/v1',     'sk-PLEASE_REPLACE_MINIMAX_KEY',  'MINIMAX'
) t
WHERE NOT EXISTS (SELECT 1 FROM `ai_provider` p WHERE p.`name` = t.`name`);


-- ============================================================
-- 3. 新增模型（按 model_name 幂等：先删后插，用子查询取 provider_id）
--    is_default 规则：CHAT/EMBEDDING 默认沿用现有（不动）；新增 RERANK、IMAGE 各设默认（此前无同类）。
-- ============================================================

DELETE FROM `ai_model`
 WHERE `model_name` IN (
   'qwen-max','qwen-plus','qwen-turbo','wanx2.1-t2i-turbo','wanx2.1-t2i-plus','text-embedding-v3',
   'glm-4-plus','glm-4-flash','embedding-3','rerank','mimo-7b','MiniMax-M2','MiniMax-Text-01'
 );

INSERT INTO `ai_model` (`id`, `tenant_id`, `provider_id`, `model_name`, `display_name`, `model_type`, `max_tokens`, `temperature`, `is_default`, `status`, `create_dept`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `attr1`, `attr2`, `attr3`, `attr4`, `input_price_per_million`, `output_price_per_million`, `currency`)
SELECT
  FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, '000000',
  (SELECT p.id FROM `ai_provider` p WHERE p.`name` = t.provider_name),
  t.model_name, t.display_name, t.model_type, t.max_tokens, t.temperature, t.is_default, '1',
  103, 1, NOW(), NULL, NULL, '0', NULL, NULL, NULL, NULL,
  t.input_price, t.output_price, 'CNY'
FROM (
  -- 通义千问 DashScope
  SELECT '通义千问' provider_name, 'qwen-max'          model_name, '通义千问 Max'      display_name, 'CHAT'      model_type, 8192 max_tokens, 0.70 temperature, '0' is_default, 0.020000 input_price, 0.060000 output_price
  UNION ALL SELECT '通义千问', 'qwen-plus',         '通义千问 Plus',     'CHAT',      8192, 0.70, '0', 0.000800, 0.002000
  UNION ALL SELECT '通义千问', 'qwen-turbo',        '通义千问 Turbo',    'CHAT',      8192, 0.70, '0', 0.000300, 0.000600
  UNION ALL SELECT '通义千问', 'wanx2.1-t2i-turbo', '通义万相 文生图(快)', 'IMAGE',    4096, 0.70, '1', NULL,      0.080000
  UNION ALL SELECT '通义千问', 'wanx2.1-t2i-plus',  '通义万相 文生图(高清)','IMAGE',   4096, 0.70, '0', NULL,      0.160000
  UNION ALL SELECT '通义千问', 'text-embedding-v3', '通义向量 v3',      'EMBEDDING', 2048, 0.00, '0', 0.000700, NULL
  -- 智谱 AI
  UNION ALL SELECT '智谱AI', 'glm-4-plus',  '智谱 GLM-4 Plus',   'CHAT',      4096, 0.70, '0', 0.050000, 0.050000
  UNION ALL SELECT '智谱AI', 'glm-4-flash', '智谱 GLM-4 Flash',  'CHAT',      4096, 0.70, '0', 0.000000, 0.000000
  UNION ALL SELECT '智谱AI', 'embedding-3', '智谱向量 Embedding-3','EMBEDDING',2048, 0.00, '0', 0.000500, NULL
  UNION ALL SELECT '智谱AI', 'rerank',      '智谱 Rerank',       'RERANK',    NULL, 0.00, '1', 0.000500, NULL
  -- 小米 MiMo
  UNION ALL SELECT '小米MiMo', 'mimo-7b', '小米 MiMo-7B', 'CHAT', 4096, 0.70, '0', NULL, NULL
  -- MiniMax
  UNION ALL SELECT 'MiniMax', 'MiniMax-M2',      'MiniMax-M2',      'CHAT', 8192, 0.70, '0', 0.001000, 0.001000
  UNION ALL SELECT 'MiniMax', 'MiniMax-Text-01', 'MiniMax-Text-01', 'CHAT', 8192, 0.70, '0', 0.001000, 0.001000
) t;


-- ============================================================
-- 4. 恢复外键检查
-- ============================================================
SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;


-- ============================================================
-- 5. 验证 SQL（执行后自检）
-- ============================================================
-- SELECT id, name, provider_type, api_host FROM ai_provider WHERE name IN ('通义千问','智谱AI','小米MiMo','MiniMax');
-- SELECT m.model_name, m.display_name, m.model_type, m.is_default, p.name AS provider
--   FROM ai_model m JOIN ai_provider p ON m.provider_id = p.id
--  WHERE m.model_name IN ('qwen-max','glm-4-plus','mimo-7b','MiniMax-M2','wanx2.1-t2i-turbo','rerank','embedding-3')
--  ORDER BY p.name, m.model_type;
-- SELECT dict_value, dict_label FROM sys_dict_data WHERE dict_type IN ('ai_provider_type','ai_model_type') ORDER BY dict_type, dict_sort;
