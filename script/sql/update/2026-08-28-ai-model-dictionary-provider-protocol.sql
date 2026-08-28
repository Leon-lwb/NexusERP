-- =============================================
-- Nexus-ERP - AI 模型字典 + 厂商协议 + 状态语义统一迁移
-- （自 nexus-school 同名升级移植，2026-08-28；按 ERP 字典/菜单 ID 段适配）
-- 目标库: ry-vue（本文件不含 USE，请以 `mysql ... ry-vue < 本文件` 方式执行）
-- 内容:
--   1) 业务字典: ai_provider_type 扩至 18 家 / ai_model_type CHAT→TEXT + VISION/AUDIO/IMAGE /
--      新增能力/协议/币种/向量库/SQL示例分类/AB测试状态/文档-图谱-抽取-索引状态/消息反馈；
--      删除被 sys_normal_disable 取代的 ai_tool_status 字典
--   2) ai_model 新增 5 列: context_window / input_capabilities /
--      output_capabilities / api_protocol / embedding_dimension
--   3) ai_knowledge 新增 4 列: vector_index_status / vector_index_progress /
--      vector_index_error / vector_index_updated_time
--   4) 存量迁移: status 语义翻转为 RuoYi 规范(0正常 1停用, 原为 1正常 0停用);
--      is_default 由 1/0 翻转为 Y/N; ai_tool 由 ENABLED/DISABLED 改 0/1;
--      模型输入/输出能力按 model_type 回填; 厂商类型按 api_host 归一(仅已知主机);
--      ai_model.api_protocol 按厂商类型回填(仅已知类型, 未知保持 NULL)
--   5) 按钮权限: ai:model:test(1724, 挂 1702 模型配置) / ai:knowledge:rebuild(1744, 挂 1704 知识库管理),
--      授权角色 3(与同组既有按钮一致)
--   6) ai_sql_example status 补充翻转: 与上述 5 表翻转分标记守护,
--      已跑过前面翻转的库仍需补做本节
-- 幂等: 字典 INSERT IGNORE / UPDATE 自守护; ALTER 由 information_schema 列存在性自守护;
--       一次性 status 翻转由 sys_migration_log 完成标记守护: 翻转+标记同事务,
--       中断即回滚(重跑补做), 成功即提交(重跑跳过); 权威 seed 文件(ry-vue.sql)已自带标记,
--       新装库免翻转; 其余 UPDATE 天然幂等(能力回填/厂商归一另加守卫防覆盖管理员改动),
--       重跑零错误且结果不变
-- =============================================

-- ===== 0. 迁移完成标记表: 一次性(非幂等)DML 的原子守护 =====
CREATE TABLE IF NOT EXISTS sys_migration_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  script_name VARCHAR(128) NOT NULL,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_script_name (script_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL迁移完成标记';

-- ===== 1.1 字典: AI 厂商类型扩充至 18 家(已有 OPENAI/DASHSCOPE/ZHIPU/OLLAMA/DEEPSEEK, 补 13 家) =====
INSERT IGNORE INTO `sys_dict_data` VALUES (620100, '000000', 6,  '火山方舟/豆包',        'ARK',             'ai_provider_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '字节跳动火山方舟');
INSERT IGNORE INTO `sys_dict_data` VALUES (620101, '000000', 7,  '月之暗面/Kimi',        'MOONSHOT',        'ai_provider_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '月之暗面 Kimi');
INSERT IGNORE INTO `sys_dict_data` VALUES (620102, '000000', 8,  '腾讯混元',             'TENCENT_HUNYUAN', 'ai_provider_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '腾讯混元大模型');
INSERT IGNORE INTO `sys_dict_data` VALUES (620103, '000000', 9,  'MiniMax',              'MINIMAX',         'ai_provider_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, 'MiniMax 大模型');
INSERT IGNORE INTO `sys_dict_data` VALUES (620104, '000000', 10, '小米MiMo',             'XIAOMI',          'ai_provider_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '小米 MiMo 开放平台');
INSERT IGNORE INTO `sys_dict_data` VALUES (620105, '000000', 11, '硅基流动',             'SILICONFLOW',     'ai_provider_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '硅基流动聚合平台');
INSERT IGNORE INTO `sys_dict_data` VALUES (620106, '000000', 12, 'Anthropic/Claude',     'ANTHROPIC',       'ai_provider_type', '', 'success', 'N', 103, 1, sysdate(), null, null, 'Anthropic Claude');
INSERT IGNORE INTO `sys_dict_data` VALUES (620107, '000000', 13, 'Google Gemini',        'GEMINI',          'ai_provider_type', '', 'success', 'N', 103, 1, sysdate(), null, null, 'Google Gemini');
INSERT IGNORE INTO `sys_dict_data` VALUES (620108, '000000', 14, 'Mistral AI',           'MISTRAL',         'ai_provider_type', '', 'success', 'N', 103, 1, sysdate(), null, null, 'Mistral AI');
INSERT IGNORE INTO `sys_dict_data` VALUES (620109, '000000', 15, 'xAI/Grok',             'XAI',             'ai_provider_type', '', 'success', 'N', 103, 1, sysdate(), null, null, 'xAI Grok');
INSERT IGNORE INTO `sys_dict_data` VALUES (620110, '000000', 16, 'OpenRouter',           'OPENROUTER',      'ai_provider_type', '', 'info',    'N', 103, 1, sysdate(), null, null, 'OpenRouter 聚合网关');
INSERT IGNORE INTO `sys_dict_data` VALUES (620111, '000000', 17, 'OpenCode Go',          'OPENCODE_GO',     'ai_provider_type', '', 'info',    'N', 103, 1, sysdate(), null, null, 'OpenCode Go 平台');
INSERT IGNORE INTO `sys_dict_data` VALUES (620112, '000000', 18, '自定义OpenAI兼容API',  'CUSTOM_API',      'ai_provider_type', '', 'warning', 'N', 103, 1, sysdate(), null, null, '自建/代理的 OpenAI 兼容接口');

-- ===== 1.2 字典: AI 模型类型 CHAT→TEXT, 新增 VISION/AUDIO/IMAGE =====
UPDATE `sys_dict_data` SET dict_value = 'TEXT', dict_label = '文本模型', remark = '文本模型(对话/推理/生成, 原 CHAT)'
WHERE dict_type = 'ai_model_type' AND dict_value = 'CHAT';
INSERT IGNORE INTO `sys_dict_data` VALUES (620120, '000000', 4, '视觉模型', 'VISION', 'ai_model_type', '', 'success', 'N', 103, 1, sysdate(), null, null, '多模态图像理解, 可对话');
INSERT IGNORE INTO `sys_dict_data` VALUES (620121, '000000', 5, '语音模型', 'AUDIO',  'ai_model_type', '', 'info',    'N', 103, 1, sysdate(), null, null, 'ASR/TTS, 预留');
INSERT IGNORE INTO `sys_dict_data` VALUES (620122, '000000', 6, '图像生成', 'IMAGE',  'ai_model_type', '', 'danger',  'N', 103, 1, sysdate(), null, null, '文生图');

-- ===== 1.3 字典: AI 模型能力(7) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620000, '000000', 'AI模型能力', 'ai_model_capability', 103, 1, sysdate(), null, null, '模型输入/输出能力(逗号分隔存储)');
INSERT IGNORE INTO `sys_dict_data` VALUES (620130, '000000', 1, '文本',   'TEXT',    'ai_model_capability', '', 'primary', 'Y', 103, 1, sysdate(), null, null, '文本输入/输出');
INSERT IGNORE INTO `sys_dict_data` VALUES (620131, '000000', 2, '图片',   'IMAGE',   'ai_model_capability', '', 'success', 'N', 103, 1, sysdate(), null, null, '图片理解或生成');
INSERT IGNORE INTO `sys_dict_data` VALUES (620132, '000000', 3, '视频',   'VIDEO',   'ai_model_capability', '', 'warning', 'N', 103, 1, sysdate(), null, null, '视频理解或生成');
INSERT IGNORE INTO `sys_dict_data` VALUES (620133, '000000', 4, '音频',   'AUDIO',   'ai_model_capability', '', 'info',    'N', 103, 1, sysdate(), null, null, '音频理解或生成');
INSERT IGNORE INTO `sys_dict_data` VALUES (620134, '000000', 5, '文件',   'FILE',    'ai_model_capability', '', 'default','N', 103, 1, sysdate(), null, null, '文件/文档输入');
INSERT IGNORE INTO `sys_dict_data` VALUES (620135, '000000', 6, '向量',   'VECTOR',  'ai_model_capability', '', 'info',    'N', 103, 1, sysdate(), null, null, '向量化(Embedding)输出');
INSERT IGNORE INTO `sys_dict_data` VALUES (620136, '000000', 7, '重排序', 'RANKING', 'ai_model_capability', '', 'danger', 'N', 103, 1, sysdate(), null, null, '重排序(Rerank)输出');

-- ===== 1.4 字典: AI 模型调用协议(7) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620001, '000000', 'AI模型调用协议', 'ai_model_api_protocol', 103, 1, sysdate(), null, null, '模型调用协议类型(OpenAI兼容/官方原生)');
INSERT IGNORE INTO `sys_dict_data` VALUES (620140, '000000', 1, 'OpenAI Chat Completions',      'OPENAI_CHAT_COMPLETIONS',   'ai_model_api_protocol', '', 'primary', 'Y', 103, 1, sysdate(), null, null, 'OpenAI Chat Completions(业界兼容事实标准)');
INSERT IGNORE INTO `sys_dict_data` VALUES (620141, '000000', 2, 'OpenAI Responses',             'OPENAI_RESPONSES',          'ai_model_api_protocol', '', 'success', 'N', 103, 1, sysdate(), null, null, 'OpenAI Responses API');
INSERT IGNORE INTO `sys_dict_data` VALUES (620142, '000000', 3, 'Anthropic Messages',           'ANTHROPIC_MESSAGES',        'ai_model_api_protocol', '', 'warning', 'N', 103, 1, sysdate(), null, null, 'Anthropic Messages API');
INSERT IGNORE INTO `sys_dict_data` VALUES (620143, '000000', 4, 'Google Gemini GenerateContent','GEMINI_GENERATE_CONTENT',   'ai_model_api_protocol', '', 'info',    'N', 103, 1, sysdate(), null, null, 'Google Gemini generateContent API');
INSERT IGNORE INTO `sys_dict_data` VALUES (620144, '000000', 5, '百炼原生协议',                 'DASHSCOPE_NATIVE',          'ai_model_api_protocol', '', 'default', 'N', 103, 1, sysdate(), null, null, '阿里云百炼原生协议');
INSERT IGNORE INTO `sys_dict_data` VALUES (620145, '000000', 6, '智谱原生协议',                 'ZHIPU_NATIVE',              'ai_model_api_protocol', '', 'default', 'N', 103, 1, sysdate(), null, null, '智谱 AI 原生协议');
INSERT IGNORE INTO `sys_dict_data` VALUES (620146, '000000', 7, 'Ollama原生协议',               'OLLAMA_NATIVE',             'ai_model_api_protocol', '', 'default', 'N', 103, 1, sysdate(), null, null, 'Ollama 本地原生 API');

-- ===== 1.5 字典: AI 模型币种(3) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620002, '000000', 'AI模型币种', 'ai_model_currency', 103, 1, sysdate(), null, null, '模型计价币种');
INSERT IGNORE INTO `sys_dict_data` VALUES (620150, '000000', 1, '人民币', 'CNY', 'ai_model_currency', '', 'primary', 'Y', 103, 1, sysdate(), null, null, '人民币');
INSERT IGNORE INTO `sys_dict_data` VALUES (620151, '000000', 2, '美元',   'USD', 'ai_model_currency', '', 'success', 'N', 103, 1, sysdate(), null, null, '美元');
INSERT IGNORE INTO `sys_dict_data` VALUES (620152, '000000', 3, '欧元',   'EUR', 'ai_model_currency', '', 'info',    'N', 103, 1, sysdate(), null, null, '欧元');

-- ===== 1.6 字典: AI 向量存储类型(2) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620003, '000000', 'AI向量存储类型', 'ai_vector_store_type', 103, 1, sysdate(), null, null, '知识库向量存储后端');
INSERT IGNORE INTO `sys_dict_data` VALUES (620160, '000000', 1, 'Milvus',     'milvus',   'ai_vector_store_type', '', 'primary', 'Y', 103, 1, sysdate(), null, null, 'Milvus 向量数据库');
INSERT IGNORE INTO `sys_dict_data` VALUES (620161, '000000', 2, '内存向量库', 'inmemory', 'ai_vector_store_type', '', 'info',    'N', 103, 1, sysdate(), null, null, '内存向量库(重启丢失)');

-- ===== 1.7 字典: AI SQL 示例分类(ERP 5 类在用) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620004, '000000', 'AI SQL示例分类', 'ai_sql_example_category', 103, 1, sysdate(), null, null, 'SQL Few-Shot 示例业务分类');
INSERT IGNORE INTO `sys_dict_data` VALUES (620170, '000000', 1, '通用', 'GENERAL',    'ai_sql_example_category', '', 'default', 'Y', 103, 1, sysdate(), null, null, '通用查询示例');
INSERT IGNORE INTO `sys_dict_data` VALUES (620171, '000000', 2, '库存', 'INVENTORY',  'ai_sql_example_category', '', 'info',    'N', 103, 1, sysdate(), null, null, '库存类查询');
INSERT IGNORE INTO `sys_dict_data` VALUES (620172, '000000', 3, '销售', 'SALES',      'ai_sql_example_category', '', 'success', 'N', 103, 1, sysdate(), null, null, '销售类查询');
INSERT IGNORE INTO `sys_dict_data` VALUES (620173, '000000', 4, '采购', 'PURCHASE',   'ai_sql_example_category', '', 'warning', 'N', 103, 1, sysdate(), null, null, '采购类查询');
INSERT IGNORE INTO `sys_dict_data` VALUES (620174, '000000', 5, '生产', 'PRODUCTION', 'ai_sql_example_category', '', 'danger',  'N', 103, 1, sysdate(), null, null, '生产类查询');

-- ===== 1.8 字典: AI AB 测试状态(3) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620005, '000000', 'AI AB测试状态', 'ai_ab_test_status', 103, 1, sysdate(), null, null, 'A/B 测试执行状态');
INSERT IGNORE INTO `sys_dict_data` VALUES (620180, '000000', 1, '待运行', 'PENDING',   'ai_ab_test_status', '', 'info',    'Y', 103, 1, sysdate(), null, null, '尚未执行');
INSERT IGNORE INTO `sys_dict_data` VALUES (620181, '000000', 2, '运行中', 'RUNNING',   'ai_ab_test_status', '', 'warning', 'N', 103, 1, sysdate(), null, null, '执行中');
INSERT IGNORE INTO `sys_dict_data` VALUES (620182, '000000', 3, '已完成', 'COMPLETED', 'ai_ab_test_status', '', 'success', 'N', 103, 1, sysdate(), null, null, '已出结果');

-- ===== 1.9 字典: AI 知识库文档状态(4) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620006, '000000', 'AI知识库文档状态', 'ai_knowledge_document_status', 103, 1, sysdate(), null, null, '知识库文档解析状态');
INSERT IGNORE INTO `sys_dict_data` VALUES (620190, '000000', 1, '待处理', '0', 'ai_knowledge_document_status', '', 'info',    'Y', 103, 1, sysdate(), null, null, '待解析');
INSERT IGNORE INTO `sys_dict_data` VALUES (620191, '000000', 2, '处理中', '1', 'ai_knowledge_document_status', '', 'warning', 'N', 103, 1, sysdate(), null, null, '解析中');
INSERT IGNORE INTO `sys_dict_data` VALUES (620192, '000000', 3, '已完成', '2', 'ai_knowledge_document_status', '', 'success', 'N', 103, 1, sysdate(), null, null, '解析完成');
INSERT IGNORE INTO `sys_dict_data` VALUES (620193, '000000', 4, '失败',   '3', 'ai_knowledge_document_status', '', 'danger',  'N', 103, 1, sysdate(), null, null, '解析失败');

-- ===== 1.10 字典: AI 知识图谱状态(4) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620007, '000000', 'AI知识图谱状态', 'ai_knowledge_graph_status', 103, 1, sysdate(), null, null, '知识图谱构建状态');
INSERT IGNORE INTO `sys_dict_data` VALUES (620200, '000000', 1, '未构建', '0', 'ai_knowledge_graph_status', '', 'info',    'Y', 103, 1, sysdate(), null, null, '未构建');
INSERT IGNORE INTO `sys_dict_data` VALUES (620201, '000000', 2, '构建中', '1', 'ai_knowledge_graph_status', '', 'warning', 'N', 103, 1, sysdate(), null, null, '构建中');
INSERT IGNORE INTO `sys_dict_data` VALUES (620202, '000000', 3, '已完成', '2', 'ai_knowledge_graph_status', '', 'success', 'N', 103, 1, sysdate(), null, null, '构建完成');
INSERT IGNORE INTO `sys_dict_data` VALUES (620203, '000000', 4, '失败',   '3', 'ai_knowledge_graph_status', '', 'danger',  'N', 103, 1, sysdate(), null, null, '构建失败');

-- ===== 1.11 字典: AI 知识图谱抽取状态(4) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620008, '000000', 'AI知识图谱抽取状态', 'ai_knowledge_extraction_status', 103, 1, sysdate(), null, null, '文档切片图谱抽取状态');
INSERT IGNORE INTO `sys_dict_data` VALUES (620210, '000000', 1, '待处理', '0', 'ai_knowledge_extraction_status', '', 'info',    'Y', 103, 1, sysdate(), null, null, '待抽取');
INSERT IGNORE INTO `sys_dict_data` VALUES (620211, '000000', 2, '处理中', '1', 'ai_knowledge_extraction_status', '', 'warning', 'N', 103, 1, sysdate(), null, null, '抽取中');
INSERT IGNORE INTO `sys_dict_data` VALUES (620212, '000000', 3, '已完成', '2', 'ai_knowledge_extraction_status', '', 'success', 'N', 103, 1, sysdate(), null, null, '抽取完成');
INSERT IGNORE INTO `sys_dict_data` VALUES (620213, '000000', 4, '失败',   '3', 'ai_knowledge_extraction_status', '', 'danger',  'N', 103, 1, sysdate(), null, null, '抽取失败');

-- ===== 1.12 字典: AI 知识库向量索引状态(3) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620009, '000000', 'AI知识库向量索引状态', 'ai_knowledge_index_status', 103, 1, sysdate(), null, null, '知识库向量索引状态(重建调度用)');
INSERT IGNORE INTO `sys_dict_data` VALUES (620220, '000000', 1, '就绪',   'READY',     'ai_knowledge_index_status', '', 'success', 'Y', 103, 1, sysdate(), null, null, '索引就绪可用');
INSERT IGNORE INTO `sys_dict_data` VALUES (620221, '000000', 2, '重建中', 'REBUILDING', 'ai_knowledge_index_status', '', 'warning', 'N', 103, 1, sysdate(), null, null, '向量索引重建中');
INSERT IGNORE INTO `sys_dict_data` VALUES (620222, '000000', 3, '失败',   'FAILED',    'ai_knowledge_index_status', '', 'danger',  'N', 103, 1, sysdate(), null, null, '重建失败');

-- ===== 1.13 字典: AI 对话消息反馈(2) =====
INSERT IGNORE INTO `sys_dict_type` VALUES (620010, '000000', 'AI对话消息反馈', 'ai_message_feedback', 103, 1, sysdate(), null, null, '对话消息用户反馈');
INSERT IGNORE INTO `sys_dict_data` VALUES (620230, '000000', 1, '点赞', 'LIKE',    'ai_message_feedback', '', 'primary', 'Y', 103, 1, sysdate(), null, null, '用户点赞');
INSERT IGNORE INTO `sys_dict_data` VALUES (620231, '000000', 2, '点踩', 'DISLIKE', 'ai_message_feedback', '', 'danger',  'N', 103, 1, sysdate(), null, null, '用户点踩');

-- ===== 1.14 删除废弃字典: ai_tool_status(启用状态统一复用 sys_normal_disable) =====
DELETE FROM `sys_dict_data` WHERE dict_type = 'ai_tool_status';
DELETE FROM `sys_dict_type` WHERE dict_type = 'ai_tool_status';

-- ===== 2. 表结构: ai_model 加 5 列(以 context_window 不存在为守卫, 重跑整体跳过) =====
SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_model ADD COLUMN context_window INT NULL COMMENT ''上下文窗口'' AFTER model_type, ADD COLUMN input_capabilities VARCHAR(128) NOT NULL DEFAULT ''TEXT'' COMMENT ''输入能力，逗号分隔'' AFTER context_window, ADD COLUMN output_capabilities VARCHAR(128) NOT NULL DEFAULT ''TEXT'' COMMENT ''输出能力，逗号分隔'' AFTER input_capabilities, ADD COLUMN api_protocol VARCHAR(64) NULL COMMENT ''调用协议'' AFTER output_capabilities, ADD COLUMN embedding_dimension INT NULL COMMENT ''向量维度，仅 EMBEDDING 使用'' AFTER api_protocol',
    'SELECT ''ai_model 新列已存在，跳过'' AS msg')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_model' AND column_name = 'context_window'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===== 2.2 表结构: ai_knowledge 加 4 列(以 vector_index_status 不存在为守卫) =====
SET @ddl := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_knowledge ADD COLUMN vector_index_status VARCHAR(16) NOT NULL DEFAULT ''READY'' COMMENT ''向量索引状态'' AFTER vector_store, ADD COLUMN vector_index_progress TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''向量索引重建进度 0-100'' AFTER vector_index_status, ADD COLUMN vector_index_error VARCHAR(500) NULL COMMENT ''向量索引错误信息'' AFTER vector_index_progress, ADD COLUMN vector_index_updated_time DATETIME NULL COMMENT ''向量索引最近完成时间'' AFTER vector_index_error',
    'SELECT ''ai_knowledge 新列已存在，跳过'' AS msg')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_knowledge' AND column_name = 'vector_index_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===== 3. 存量迁移: status 语义翻转(原 1正常/0停用 -> RuoYi 0正常/1停用) =====
-- 注: 互换型 UPDATE 重跑会翻回, 属一次性操作; 5 条翻转 + 完成标记写入同一事务:
--     标记存在则整体跳过; 中途失败则全部回滚, 重跑补做(消除 ALTER 后中断的半完成窗口)
START TRANSACTION;
SELECT COUNT(*) INTO @done FROM sys_migration_log WHERE script_name = '2026-08-28-ai-model-dictionary-provider-protocol-status-flip';
SET @done = IF(@done = 0, 1, 0);

SET @sql := IF(@done = 1,
  'UPDATE ai_provider SET status = CASE status WHEN ''1'' THEN ''0'' WHEN ''0'' THEN ''1'' ELSE status END',
  'SELECT ''ai_provider status 已迁移，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(@done = 1,
  'UPDATE ai_model SET status = CASE status WHEN ''1'' THEN ''0'' WHEN ''0'' THEN ''1'' ELSE status END',
  'SELECT ''ai_model status 已迁移，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(@done = 1,
  'UPDATE ai_knowledge SET status = CASE status WHEN ''1'' THEN ''0'' WHEN ''0'' THEN ''1'' ELSE status END',
  'SELECT ''ai_knowledge status 已迁移，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(@done = 1,
  'UPDATE ai_prompt_template SET status = CASE status WHEN ''1'' THEN ''0'' WHEN ''0'' THEN ''1'' ELSE status END',
  'SELECT ''ai_prompt_template status 已迁移，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(@done = 1,
  'UPDATE ai_mcp_market SET status = CASE status WHEN ''1'' THEN ''0'' WHEN ''0'' THEN ''1'' ELSE status END',
  'SELECT ''ai_mcp_market status 已迁移，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO sys_migration_log (script_name) VALUES ('2026-08-28-ai-model-dictionary-provider-protocol-status-flip');
COMMIT;

-- ===== 3.2 ai_tool 状态: ENABLED/DISABLED -> 0/1(天然幂等) =====
UPDATE ai_tool SET status = CASE status WHEN 'ENABLED' THEN '0' WHEN 'DISABLED' THEN '1' ELSE status END;

-- ===== 3.3 is_default: 1/0 -> Y/N(天然幂等) =====
UPDATE ai_model           SET is_default = CASE is_default WHEN '1' THEN 'Y' WHEN '0' THEN 'N' ELSE is_default END;
UPDATE ai_prompt_template SET is_default = CASE is_default WHEN '1' THEN 'Y' WHEN '0' THEN 'N' ELSE is_default END;

-- ===== 3.4 模型类型兜底: 遗留 CHAT -> TEXT =====
UPDATE ai_model SET model_type = 'TEXT' WHERE model_type = 'CHAT';

-- ===== 3.5 模型输入/输出能力回填(按 model_type, 仅覆盖仍处列默认值的行) =====
-- TEXT/CHAT->TEXT|TEXT  VISION->TEXT,IMAGE|TEXT  AUDIO->TEXT|TEXT
-- EMBEDDING->TEXT|VECTOR  RERANK->TEXT|RANKING  IMAGE->TEXT|IMAGE
-- 守卫说明: 新列建表默认值即 TEXT/TEXT, 重跑时已迁移的 VISION/EMBEDDING/RERANK/IMAGE 行
--           及管理员改过的行必然偏离默认值组合而被排除, 不会被回填覆盖
UPDATE ai_model SET
  input_capabilities = CASE model_type
    WHEN 'TEXT' THEN 'TEXT' WHEN 'CHAT' THEN 'TEXT' WHEN 'AUDIO' THEN 'TEXT'
    WHEN 'EMBEDDING' THEN 'TEXT' WHEN 'RERANK' THEN 'TEXT' WHEN 'VISION' THEN 'TEXT,IMAGE' WHEN 'IMAGE' THEN 'TEXT'
    ELSE input_capabilities END,
  output_capabilities = CASE model_type
    WHEN 'TEXT' THEN 'TEXT' WHEN 'CHAT' THEN 'TEXT' WHEN 'AUDIO' THEN 'TEXT'
    WHEN 'EMBEDDING' THEN 'VECTOR' WHEN 'RERANK' THEN 'RANKING' WHEN 'VISION' THEN 'TEXT' WHEN 'IMAGE' THEN 'IMAGE'
    ELSE output_capabilities END
WHERE model_type IN ('TEXT', 'CHAT', 'VISION', 'AUDIO', 'EMBEDDING', 'RERANK', 'IMAGE')
  AND input_capabilities = 'TEXT' AND output_capabilities = 'TEXT';

-- ===== 3.6 厂商类型按 api_host 归一(仅已知官方主机, 允许携带路径后缀; 未知代理/自建保持原值) =====
-- 守卫说明: AND provider_type='OPENAI' —— 存量误归类的行均为 OPENAI 类型; 重跑时已被纠正
--           或管理员重新标注过的行不再是 OPENAI, 不会再被改写
UPDATE ai_provider SET provider_type = 'DEEPSEEK'  WHERE provider_type = 'OPENAI' AND api_host LIKE '%api.deepseek.com%';
UPDATE ai_provider SET provider_type = 'ARK'       WHERE provider_type = 'OPENAI' AND api_host LIKE '%.volces.com%';
UPDATE ai_provider SET provider_type = 'MINIMAX'   WHERE provider_type = 'OPENAI' AND api_host LIKE '%api.minimax.io%';
UPDATE ai_provider SET provider_type = 'DASHSCOPE' WHERE provider_type = 'OPENAI' AND api_host LIKE '%dashscope.aliyuncs.com%';
UPDATE ai_provider SET provider_type = 'ZHIPU'     WHERE provider_type = 'OPENAI' AND api_host LIKE '%open.bigmodel.cn%';
UPDATE ai_provider SET provider_type = 'XIAOMI'    WHERE provider_type = 'OPENAI' AND api_host LIKE '%token-plan-cn.xiaomimimo.com%';

-- ===== 3.7 api_protocol 按厂商类型回填(仅已知类型; 未知保持 NULL; ai_model 无 provider_type 列, 联 ai_provider) =====
UPDATE ai_model m
  JOIN ai_provider p ON p.id = m.provider_id
   SET m.api_protocol = CASE p.provider_type
        WHEN 'DASHSCOPE' THEN 'DASHSCOPE_NATIVE'
        WHEN 'ZHIPU' THEN 'ZHIPU_NATIVE'
        WHEN 'OLLAMA' THEN 'OLLAMA_NATIVE'
        WHEN 'ANTHROPIC' THEN 'ANTHROPIC_MESSAGES'
        WHEN 'GEMINI' THEN 'GEMINI_GENERATE_CONTENT'
        ELSE 'OPENAI_CHAT_COMPLETIONS' END
 WHERE m.api_protocol IS NULL
   AND p.provider_type IN ('DASHSCOPE', 'ZHIPU', 'OLLAMA', 'ANTHROPIC', 'GEMINI',
        'ARK', 'DEEPSEEK', 'MOONSHOT', 'TENCENT_HUNYUAN', 'MINIMAX', 'XIAOMI',
        'SILICONFLOW', 'OPENAI', 'MISTRAL', 'XAI', 'OPENROUTER', 'OPENCODE_GO', 'CUSTOM_API');

-- ===== 4. 按钮权限: 模型测试(挂 1702 模型配置)/重建向量索引(挂 1704 知识库管理), 授权角色 3(与同组按钮一致) =====
INSERT IGNORE INTO `sys_menu` VALUES (1724, '模型测试', 1702, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:model:test', '#', 103, 1, sysdate(), NULL, NULL, '模型连通性测试');
INSERT IGNORE INTO `sys_menu` VALUES (1744, '重建向量索引', 1704, 5, '', '', '', 1, 0, 'F', '0', '0', 'ai:knowledge:rebuild', '#', 103, 1, sysdate(), NULL, NULL, '知识库向量索引重建');
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 1724);
INSERT IGNORE INTO `sys_role_menu` VALUES (3, 1744);

-- ===== 4.1 ai_sql_example status 补充翻转 =====
-- 背景: §3 的 5 表翻转未含 ai_sql_example —— 该表存量数据 status='1' 为旧语义启用
--       (DDL 旧注释"1正常 0停用")。后端已统一按 sys_normal_disable(0=启用/1=停用)消费,
--       存量 '1' 若不翻转会被当作停用, SQL few-shot 示例静默失效。
-- 独立标记(不复用 §3 标记): 翻转+标记同事务, 中断回滚(重跑补做), 成功提交(重跑跳过);
--       权威 seed 文件(ry-vue.sql)亦写入本标记, 新装库天然免翻转
START TRANSACTION;
SELECT COUNT(*) INTO @done FROM sys_migration_log WHERE script_name = '2026-08-28-ai-model-dictionary-provider-protocol-sqlexample-status-flip';
SET @done = IF(@done = 0, 1, 0);

SET @sql := IF(@done = 1,
  'UPDATE ai_sql_example SET status = CASE status WHEN ''1'' THEN ''0'' WHEN ''0'' THEN ''1'' ELSE status END',
  'SELECT ''ai_sql_example status 已迁移，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO sys_migration_log (script_name) VALUES ('2026-08-28-ai-model-dictionary-provider-protocol-sqlexample-status-flip');
COMMIT;

-- ===== 5. 后置校验(每次执行后输出应完全一致) =====
SELECT dict_type, COUNT(*) AS count
FROM sys_dict_data
WHERE dict_type IN (
  'ai_provider_type', 'ai_model_type', 'ai_model_capability', 'ai_model_api_protocol',
  'ai_model_currency', 'ai_vector_store_type', 'ai_tool_type',
  'ai_sql_example_category', 'ai_ab_test_status',
  'ai_knowledge_document_status', 'ai_knowledge_graph_status',
  'ai_knowledge_extraction_status', 'ai_knowledge_index_status',
  'ai_message_feedback'
)
GROUP BY dict_type
ORDER BY dict_type;

SELECT status, COUNT(*) FROM ai_model GROUP BY status;
SELECT is_default, COUNT(*) FROM ai_model GROUP BY is_default;
SELECT status, COUNT(*) FROM ai_tool GROUP BY status;
SELECT status, COUNT(*) FROM ai_sql_example GROUP BY status;
SELECT provider_type, api_host FROM ai_provider ORDER BY id;
